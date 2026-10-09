import React, { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react';
import { db, firebaseAuth, callable, SECURE_AUTH, setRememberDevice } from '../config/firebase';
import { ref, onValue, set, update, remove, get, runTransaction } from 'firebase/database';
import { onIdTokenChanged, signInWithCustomToken, signOut } from 'firebase/auth';
import { takaToPoisha, sanitizeFirebaseKey, encodeEmailKey, generateInvoiceNumber } from '../utils/formatters';
import { normalizePin, isValidPin, isWeakPin, cleanAmount, cleanText } from '../utils/security';
import {
  normalizeCategories,
  normalizeProducts,
  normalizeSales,
  normalizeCustomers,
  normalizeExpenses,
  normalizeStaff,
  normalizeSuppliers,
  normalizePurchases,
  normalizeShopStatus,
} from './normalizers';

const ShopContext = createContext(null);

const LEGACY_SESSION_KEY = 'dokan_web_auth';
const LEGACY_SESSION_MS = 12 * 60 * 60 * 1000;
const LEGACY_THROTTLE_KEY = 'dokan_login_throttle';

const DEFAULT_SHOP_INFO = {
  shopName: 'Dokan Pro',
  shopAddress: '',
  shopPhone: '',
  ownerEmail: '',
  tagline: '',
  vatPercentage: 5,
  vatEnabled: false,
  currencySymbol: '৳',
};

const DEFAULT_PERMISSIONS = {
  allowStaffDiscount: true,
  maxStaffDiscountPercent: 10,
  allowStaffViewProfit: false,
  allowStaffViewCostPrice: false,
  allowStaffDeleteSale: false,
  allowStaffEditProducts: false,
  allowStaffViewExpenses: false,
  allowStaffDuePayment: true,
  posMaintenanceMode: false,
};

const DEFAULT_SECURITY = {
  requirePinForAdminPanel: true,
  autoLockMinutes: 15,
  twoStepConfirmDanger: true,
};

// ---------- legacy (pre-Cloud-Functions) session helpers ----------

function readLegacySession() {
  try {
    const s = JSON.parse(localStorage.getItem(LEGACY_SESSION_KEY) || 'null');
    if (!s?.shopCode || !['owner', 'staff'].includes(s.role) || !(s.expiresAt > Date.now())) {
      localStorage.removeItem(LEGACY_SESSION_KEY);
      return null;
    }
    return s;
  } catch {
    return null;
  }
}

function legacyThrottleCheck() {
  try {
    const t = JSON.parse(localStorage.getItem(LEGACY_THROTTLE_KEY) || '{}');
    if (t.lockedUntil > Date.now()) {
      const mins = Math.ceil((t.lockedUntil - Date.now()) / 60000);
      throw new Error(`অনেকবার ভুল পিন দেওয়া হয়েছে। ${mins} মিনিট পর আবার চেষ্টা করুন।`);
    }
  } catch (e) {
    if (e instanceof SyntaxError) localStorage.removeItem(LEGACY_THROTTLE_KEY);
    else throw e;
  }
}

function legacyThrottleFail() {
  try {
    const t = JSON.parse(localStorage.getItem(LEGACY_THROTTLE_KEY) || '{}');
    const fails = (t.fails || 0) + 1;
    const next = fails >= 5 ? { fails: 0, lockedUntil: Date.now() + 15 * 60 * 1000 } : { fails };
    localStorage.setItem(LEGACY_THROTTLE_KEY, JSON.stringify(next));
  } catch {
    /* storage unavailable */
  }
}

function friendlyError(err) {
  // Callable errors arrive as "functions/<code>" with the server's Bengali message.
  if (err?.code?.startsWith?.('functions/')) {
    if (err.code === 'functions/internal' || !err.message) {
      return 'সার্ভারের সাথে সংযোগ করা যাচ্ছে না। ইন্টারনেট সংযোগ যাচাই করুন।';
    }
    return err.message;
  }
  if (err?.code === 'PERMISSION_DENIED' || /permission_denied/i.test(err?.message || '')) {
    return 'এই কাজটি করার অনুমতি আপনার নেই।';
  }
  return err?.message || 'অজানা সমস্যা হয়েছে।';
}

export function ShopProvider({ children }) {
  // ---------- session ----------
  const [auth, setAuth] = useState(() => (SECURE_AUTH ? null : readLegacySession()));
  const [authReady, setAuthReady] = useState(!SECURE_AUTH);
  const [isLocked, setIsLocked] = useState(false);

  // ---------- data ----------
  const [shopInfo, setShopInfo] = useState(DEFAULT_SHOP_INFO);
  const [products, setProducts] = useState([]);
  const [categories, setCategories] = useState([]);
  const [sales, setSales] = useState([]);
  const [customers, setCustomers] = useState([]);
  const [suppliers, setSuppliers] = useState([]);
  const [purchases, setPurchases] = useState([]);
  const [expenses, setExpenses] = useState([]);
  const [staffMembers, setStaffMembers] = useState([]);
  const [permissions, setPermissions] = useState(DEFAULT_PERMISSIONS);
  const [securitySettings, setSecuritySettings] = useState(DEFAULT_SECURITY);
  const [auditLogs, setAuditLogs] = useState([]);
  const [heldCarts, setHeldCarts] = useState([]);

  // ---------- platform ----------
  const [globalNotices, setGlobalNotices] = useState([]);
  const [globalSettings, setGlobalSettings] = useState({ maintenanceMode: false, maintenanceMessage: '' });
  const [currentShopStatus, setCurrentShopStatus] = useState(normalizeShopStatus(null));
  const [shopFeatures, setShopFeatures] = useState({});
  const [activeShopNotice, setActiveShopNotice] = useState(null);

  const [isFirebaseConnected, setIsFirebaseConnected] = useState(false);
  const [isSyncing, setIsSyncing] = useState(false);
  const [lastSyncTime, setLastSyncTime] = useState(null);

  const shopCode = auth?.shopCode || null;
  const role = auth?.role || null;
  const isOwner = role === 'owner';
  const sessionBlocked = Boolean(auth?.pinChangeRequired);

  // ---------- secure-mode session (Firebase Auth custom token) ----------
  useEffect(() => {
    if (!SECURE_AUTH) return undefined;
    return onIdTokenChanged(firebaseAuth, async (user) => {
      if (!user) {
        setAuth(null);
        setAuthReady(true);
        return;
      }
      try {
        const res = await user.getIdTokenResult();
        const c = res.claims;
        if (!c.shopCode || !['owner', 'staff'].includes(c.role)) {
          await signOut(firebaseAuth);
          return;
        }
        setAuth((prev) => {
          const next = {
            shopCode: c.shopCode,
            role: c.role,
            staffId: c.staffId || null,
            userName: c.name || (c.role === 'owner' ? 'মালিক' : 'কর্মচারী'),
            pinChangeRequired: Boolean(c.pinChangeRequired),
            loginTime: Date.parse(res.authTime) || Date.now(),
          };
          // Keep the same object on hourly token refresh so listeners don't resubscribe.
          return prev && JSON.stringify(prev) === JSON.stringify(next) ? prev : next;
        });
      } finally {
        setAuthReady(true);
      }
    });
  }, []);

  // Legacy sessions expire; check once a minute.
  useEffect(() => {
    if (SECURE_AUTH || !auth) return undefined;
    const id = setInterval(() => {
      if (!readLegacySession()) setAuth(null);
    }, 60000);
    return () => clearInterval(id);
  }, [auth]);

  // ---------- global listeners ----------
  useEffect(() => {
    const unsubs = [
      onValue(ref(db, '.info/connected'), (snap) => setIsFirebaseConnected(snap.val() === true)),
      onValue(
        ref(db, 'global_settings'),
        (snap) => snap.exists() && setGlobalSettings((prev) => ({ ...prev, ...snap.val() })),
        () => {}
      ),
    ];
    return () => unsubs.forEach((u) => u());
  }, []);

  useEffect(() => {
    // Notices require a signed-in session once rules are deployed.
    if (SECURE_AUTH && !auth) return undefined;
    return onValue(
      ref(db, 'global_notices'),
      (snap) => {
        const data = snap.val() || {};
        const list = Object.keys(data).map((k) => ({ id: k, ...data[k] }));
        list.sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0));
        setGlobalNotices(list.filter((n) => n.isActive !== false && (!n.expiresAt || n.expiresAt > Date.now())));
      },
      () => setGlobalNotices([])
    );
  }, [auth]);

  // ---------- shop listeners ----------
  useEffect(() => {
    if (!shopCode || sessionBlocked) {
      setShopInfo(DEFAULT_SHOP_INFO);
      setProducts([]);
      setCategories([]);
      setSales([]);
      setCustomers([]);
      setSuppliers([]);
      setPurchases([]);
      setExpenses([]);
      setStaffMembers([]);
      setAuditLogs([]);
      setPermissions(DEFAULT_PERMISSIONS);
      setSecuritySettings(DEFAULT_SECURITY);
      setShopFeatures({});
      setActiveShopNotice(null);
      setCurrentShopStatus(normalizeShopStatus(null));
      setHeldCarts([]);
      return undefined;
    }

    try {
      setHeldCarts(JSON.parse(localStorage.getItem(`dokan_held_carts_${shopCode}`) || '[]'));
    } catch {
      setHeldCarts([]);
    }

    setIsSyncing(true);
    const base = `shops/${shopCode}`;
    const touch = () => setLastSyncTime(new Date());
    const listen = (path, onData, onDenied) =>
      onValue(
        ref(db, `${base}/${path}`),
        (snap) => {
          onData(snap.exists() ? snap.val() : null);
          touch();
        },
        (err) => {
          console.warn(`[sync] ${path}:`, err.message);
          onDenied?.();
        }
      );

    const unsubs = [
      listen('info', (val) => {
        if (!val) return;
        setShopInfo((prev) => ({
          ...prev,
          shopName: val.shopName || prev.shopName,
          shopAddress: val.shopAddress || '',
          shopPhone: val.shopPhone || '',
          ownerEmail: val.ownerEmail || '',
          tagline: val.tagline || '',
          vatPercentage: Number(val.vatPercentage || 0),
          vatEnabled: Boolean(val.vatEnabled),
          currencySymbol: val.currencySymbol || '৳',
        }));
      }),
      listen('categories', (val) => setCategories(normalizeCategories(val))),
      listen(
        'products',
        (val) => {
          setProducts(normalizeProducts(val));
          setIsSyncing(false);
        },
        () => setIsSyncing(false)
      ),
      listen('sales', (val) => setSales(normalizeSales(val))),
      listen('customers', (val) => setCustomers(normalizeCustomers(val))),
      listen('expenses', (val) => setExpenses(normalizeExpenses(val)), () => setExpenses([])),
      listen('suppliers', (val) => setSuppliers(normalizeSuppliers(val))),
      listen('purchases', (val) => setPurchases(normalizePurchases(val))),
      listen('permissions', (val) => setPermissions({ ...DEFAULT_PERMISSIONS, ...(val || {}) })),
      listen('status', (val) => setCurrentShopStatus(normalizeShopStatus(val))),
      listen('features', (val) => setShopFeatures(val || {})),
      listen('notices', (val) => {
        const list = Object.keys(val || {}).map((k) => ({ id: k, ...val[k] }));
        list.sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0));
        setActiveShopNotice(list.find((n) => n.isActive !== false) || null);
      }),
    ];

    // Owner-only nodes: staff records, security policy and the audit trail.
    if (role === 'owner') {
      unsubs.push(
        listen('staff', (val) => setStaffMembers(normalizeStaff(val))),
        listen('security', (val) => {
          // Never keep PIN material in memory, even if a legacy record still has it.
          const safe = { ...(val || {}) };
          ['masterPin', 'masterPinHash', 'masterPinHashBn', 'staffPin'].forEach((k) => delete safe[k]);
          setSecuritySettings({ ...DEFAULT_SECURITY, ...safe });
        }),
        listen('auditLogs', (val) => {
          const list = Object.keys(val || {}).map((k) => ({ id: k, ...val[k] }));
          list.sort((a, b) => (b.timestamp || 0) - (a.timestamp || 0));
          setAuditLogs(list.slice(0, 200));
        })
      );
    }

    return () => unsubs.forEach((u) => u());
  }, [shopCode, role, sessionBlocked]);

  // ---------- guards ----------
  const requireSession = useCallback(() => {
    if (!shopCode) throw new Error('দোকান লগইন করা নেই।');
    if (sessionBlocked) throw new Error('আগে মাস্টার পিন পরিবর্তন করুন।');
    return shopCode;
  }, [shopCode, sessionBlocked]);

  const requireOwner = useCallback(() => {
    const sc = requireSession();
    if (role !== 'owner') throw new Error('এই কাজটি শুধুমাত্র মালিক করতে পারবেন।');
    return sc;
  }, [requireSession, role]);

  // ---------- audit ----------
  const logAuditEvent = useCallback(
    async (action, details) => {
      if (!shopCode) return;
      try {
        const logId = `${Date.now()}_${Math.random().toString(36).substring(2, 7)}`;
        await set(ref(db, `shops/${shopCode}/auditLogs/${logId}`), {
          timestamp: Date.now(),
          actor: auth?.userName || 'অজানা ইউজার',
          role: role || 'unknown',
          action: String(action).slice(0, 64),
          details: cleanText(typeof details === 'string' ? details : JSON.stringify(details), 1000),
          userAgent: navigator.userAgent.slice(0, 200),
        });
      } catch (err) {
        console.warn('Audit logging error:', err.message);
      }
    },
    [shopCode, auth?.userName, role]
  );

  // ---------- login / logout ----------

  const legacyLogin = async (identifier, pin, userRole) => {
    legacyThrottleCheck();

    let resolved = '';
    if (identifier.includes('@')) {
      const key = encodeEmailKey(identifier);
      if (userRole === 'staff') {
        const s = await get(ref(db, `staff_to_shop/${key}`));
        if (s.exists()) resolved = String(s.val()).trim().toUpperCase();
      }
      if (!resolved) {
        const s = await get(ref(db, `email_to_shop/${key}`));
        if (s.exists()) resolved = String(s.val()).trim().toUpperCase();
      }
    } else {
      resolved = sanitizeFirebaseKey(identifier.toUpperCase());
    }
    const fail = () => {
      legacyThrottleFail();
      return new Error('দোকান কোড/ইমেইল অথবা পিন সঠিক নয়।');
    };
    if (!resolved) throw fail();

    const [secSnap, infoSnap, statusSnap, settingsSnap] = await Promise.all([
      get(ref(db, `shops/${resolved}/security`)),
      get(ref(db, `shops/${resolved}/info`)),
      get(ref(db, `shops/${resolved}/status`)),
      get(ref(db, 'global_settings')),
    ]);
    const sec = secSnap.val() || {};
    const info = infoSnap.val() || {};
    if (!secSnap.exists() && !infoSnap.exists()) throw fail();

    let userName = userRole === 'owner' ? 'মালিক' : 'কর্মচারী';
    let staffId = null;
    if (userRole === 'owner') {
      const stored = sec.masterPin ?? info.pinCode;
      // No stored PIN means the shop was never secured — refuse rather than fall back to a default.
      if (stored === undefined || stored === null || String(stored).trim() === '') {
        throw new Error('এই দোকানের কোনো মাস্টার পিন সেট করা নেই। মোবাইল অ্যাপ থেকে পিন সেট করুন।');
      }
      if (normalizePin(stored) !== pin) throw fail();
    } else {
      let matched = false;
      const staffSnap = await get(ref(db, `shops/${resolved}/staff`));
      for (const [key, st] of Object.entries(staffSnap.val() || {})) {
        if (st?.isActive !== false && st?.pin !== undefined && normalizePin(st.pin) === pin) {
          matched = true;
          staffId = key;
          userName = st.name || userName;
          break;
        }
      }
      if (!matched && sec.staffPin !== undefined && normalizePin(sec.staffPin) === pin) {
        matched = true;
        staffId = 'shared';
      }
      if (!matched) throw fail();
    }
    localStorage.removeItem(LEGACY_THROTTLE_KEY);

    const settings = settingsSnap.val();
    if (settings?.maintenanceMode) {
      throw new Error(`সার্ভার মেইনটেন্যান্স: ${settings.maintenanceMessage || 'কিছুক্ষণ পর চেষ্টা করুন।'}`);
    }
    const status = normalizeShopStatus(statusSnap.val());
    if (status.isSuspended) throw new Error(`দোকানের এক্সেস স্থগিত: ${status.reason || 'ডেভেলপারের সাথে যোগাযোগ করুন।'}`);

    const session = {
      shopCode: resolved,
      role: userRole,
      staffId,
      userName,
      loginTime: Date.now(),
      expiresAt: Date.now() + LEGACY_SESSION_MS,
    };
    localStorage.setItem(LEGACY_SESSION_KEY, JSON.stringify(session));
    setAuth(session);
    return session;
  };

  const login = async (codeOrEmail, rawPin, userRole = 'owner', { remember = true } = {}) => {
    const identifier = cleanText(codeOrEmail, 120);
    const pin = normalizePin(rawPin);
    const r = userRole === 'staff' ? 'staff' : 'owner';
    if (!identifier || !pin) throw new Error('দোকান কোড অথবা নিবন্ধিত ইমেইল এবং পিন লিখুন।');
    if (!isValidPin(pin)) throw new Error('পিন শুধুমাত্র ৪-১২টি সংখ্যা হতে পারে।');

    if (!SECURE_AUTH) return legacyLogin(identifier, pin, r);

    try {
      await setRememberDevice(remember);
      const { data } = await callable('shopLogin')({ identifier, pin, role: r });
      await signInWithCustomToken(firebaseAuth, data.token);
      return data;
    } catch (err) {
      throw new Error(friendlyError(err));
    }
  };

  const logout = useCallback(async () => {
    setIsLocked(false);
    if (SECURE_AUTH) {
      await signOut(firebaseAuth).catch(() => {});
    } else {
      localStorage.removeItem(LEGACY_SESSION_KEY);
      setAuth(null);
    }
  }, []);

  // ---------- PIN verification ----------

  const verifyMasterPin = async (inputPin) => {
    requireOwner();
    const pin = normalizePin(inputPin);
    if (!isValidPin(pin)) return false;
    if (SECURE_AUTH) {
      try {
        const { data } = await callable('verifyOwnerPin')({ pin });
        return Boolean(data?.ok);
      } catch (err) {
        throw new Error(friendlyError(err));
      }
    }
    const [secSnap, infoSnap] = await Promise.all([
      get(ref(db, `shops/${shopCode}/security/masterPin`)),
      get(ref(db, `shops/${shopCode}/info/pinCode`)),
    ]);
    const stored = secSnap.val() ?? infoSnap.val();
    const ok = stored !== null && stored !== undefined && normalizePin(stored) === pin;
    await logAuditEvent(ok ? 'ADMIN_AUTH_SUCCESS' : 'ADMIN_AUTH_FAILED', ok ? 'মাস্টার পিন যাচাই সফল' : 'ভুল মাস্টার পিন দিয়ে চেষ্টা');
    return ok;
  };

  const updateMasterPin = async (oldPin, newPin) => {
    if (!shopCode || role !== 'owner') throw new Error('এই কাজটি শুধুমাত্র মালিক করতে পারবেন।');
    const cleanNew = normalizePin(newPin);
    if (!isValidPin(cleanNew)) throw new Error('নতুন পিন ৪-১২ সংখ্যার হতে হবে।');
    if (isWeakPin(cleanNew)) throw new Error('নতুন পিনটি খুব সহজ। ১২৩৪, ০০০০ বা একই সংখ্যা বারবার ব্যবহার করবেন না।');

    if (SECURE_AUTH) {
      try {
        const { data } = await callable('changeOwnerPin')({ oldPin: normalizePin(oldPin), newPin: cleanNew });
        if (data?.token) await signInWithCustomToken(firebaseAuth, data.token);
      } catch (err) {
        throw new Error(friendlyError(err));
      }
      return;
    }

    if (!(await verifyMasterPin(oldPin))) throw new Error('বর্তমান মাস্টার পিনটি সঠিক নয়!');
    await update(ref(db), {
      [`shops/${shopCode}/security/masterPin`]: cleanNew,
      [`shops/${shopCode}/security/updatedAt`]: Date.now(),
      [`shops/${shopCode}/info/pinCode`]: cleanNew,
    });
    await logAuditEvent('MASTER_PIN_CHANGED', 'মাস্টার সিকিউরিটি পিন পরিবর্তন করা হয়েছে');
  };

  // ---------- idle auto-lock ----------
  const lastActivity = useRef(Date.now());
  useEffect(() => {
    if (!auth || sessionBlocked) return undefined;
    const bump = () => {
      lastActivity.current = Date.now();
    };
    const events = ['pointerdown', 'keydown', 'wheel', 'touchstart'];
    events.forEach((e) => window.addEventListener(e, bump, { passive: true }));
    const minutes = Math.min(240, Math.max(1, Number(securitySettings.autoLockMinutes) || 15));
    const id = setInterval(() => {
      if (Date.now() - lastActivity.current > minutes * 60000) {
        if (role === 'owner') setIsLocked(true);
        else logout();
      }
    }, 15000);
    return () => {
      events.forEach((e) => window.removeEventListener(e, bump));
      clearInterval(id);
    };
  }, [auth, role, sessionBlocked, securitySettings.autoLockMinutes, logout]);

  const unlock = async (pin) => {
    const ok = await verifyMasterPin(pin);
    if (ok) {
      lastActivity.current = Date.now();
      setIsLocked(false);
    }
    return ok;
  };

  // ---------- atomic counters (safe when the mobile app writes at the same time) ----------

  const adjustStock = (sc, product, delta) =>
    runTransaction(ref(db, `shops/${sc}/products/${product.firebaseKey || product.id}/stockQty`), (cur) =>
      Math.max(0, Number(cur ?? product.stockQty ?? 0) + delta)
    );

  const adjustDue = (path, deltaPoisha) =>
    runTransaction(ref(db, path), (cur) => Math.max(0, Math.round(Number(cur || 0) + deltaPoisha)));

  // ---------- sales ----------

  const createSale = async (saleInput) => {
    const sc = requireSession();
    const saleId = String(Date.now());
    const invoiceNumber = saleInput.invoiceNumber || generateInvoiceNumber();
    const total = cleanAmount(saleInput.total, 'মোট টাকা');
    const dueAmount = cleanAmount(saleInput.dueAmount || 0, 'বাকি');
    const paidAmount = cleanAmount(saleInput.paidAmount ?? total, 'পরিশোধ');
    const discount = cleanAmount(saleInput.discount || 0, 'ছাড়');
    const items = saleInput.items || [];
    if (items.length === 0) throw new Error('কার্টে কোনো পণ্য নেই।');

    if (role !== 'owner' && discount > 0) {
      if (!permissions.allowStaffDiscount) throw new Error('কর্মচারীদের ছাড় দেওয়ার অনুমতি নেই।');
      const maxPct = Number(permissions.maxStaffDiscountPercent || 0);
      const sub = Number(saleInput.subtotal || 0);
      if (sub > 0 && (discount / sub) * 100 > maxPct + 0.001) {
        throw new Error(`কর্মচারী সর্বোচ্চ ${maxPct}% ছাড় দিতে পারবেন।`);
      }
    }

    const saleRecord = {
      id: Number(saleId),
      invoiceNumber,
      invoiceNo: invoiceNumber,
      customerName: cleanText(saleInput.customerName || 'নগদ ক্রেতা', 120),
      customerPhone: cleanText(saleInput.customerPhone || '', 40),
      customerId: saleInput.customerId || 0,
      subtotalPoisha: takaToPoisha(cleanAmount(saleInput.subtotal, 'সাবটোটাল')),
      discountPoisha: takaToPoisha(discount),
      vatPoisha: takaToPoisha(cleanAmount(saleInput.vat || 0, 'ভ্যাট')),
      totalPoisha: takaToPoisha(total),
      paidPoisha: takaToPoisha(paidAmount),
      paidAmountPoisha: takaToPoisha(paidAmount),
      duePoisha: takaToPoisha(dueAmount),
      dueAmountPoisha: takaToPoisha(dueAmount),
      paymentType: cleanText(saleInput.paymentType || 'নগদ', 40),
      paymentMethod: cleanText(saleInput.paymentType || 'নগদ', 40),
      createdAt: Date.now(),
      saleDate: Date.now(),
      staffName: auth.userName || 'কাউন্টার',
      isReturned: false,
      items: items.map((it) => {
        const pName = cleanText(it.name || it.productName || it.nameBn || it.nameEn || 'পণ্য', 200);
        const pQty = cleanAmount(it.quantity || it.qty || 1, 'পরিমাণ');
        const pPricePoisha = it.pricePoisha || takaToPoisha(cleanAmount(it.salePrice || 0, 'দাম'));
        const pLinePoisha = it.subtotalPoisha || takaToPoisha((it.salePrice || 0) * pQty);
        return {
          id: it.id || Number(it.productId || 0),
          productId: Number(it.productId || it.id || 0),
          name: pName,
          productName: pName,
          unitName: it.unitName || '',
          quantity: pQty,
          qty: pQty,
          salePrice: it.salePrice || pPricePoisha / 100,
          pricePoisha: pPricePoisha,
          unitPricePoisha: pPricePoisha,
          subtotalPoisha: pLinePoisha,
          lineTotalPoisha: pLinePoisha,
        };
      }),
    };

    await set(ref(db, `shops/${sc}/sales/${saleId}`), saleRecord);

    const tasks = [];
    for (const it of items) {
      const p = products.find((x) => String(x.id) === String(it.id));
      if (p) tasks.push(adjustStock(sc, p, -Number(it.quantity || 0)));
    }
    if (saleInput.customerId && dueAmount > 0) {
      const c = customers.find((x) => String(x.id) === String(saleInput.customerId));
      if (c) tasks.push(adjustDue(`shops/${sc}/customers/${c.firebaseKey}/totalDuePoisha`, takaToPoisha(dueAmount)));
    }
    await Promise.all(tasks);

    return { saleId, invoiceNumber };
  };

  // ---------- products ----------

  const canEditProducts = role === 'owner' || permissions.allowStaffEditProducts === true;

  const saveProduct = async (productData) => {
    const sc = requireSession();
    if (!canEditProducts) throw new Error('পণ্য সম্পাদনার অনুমতি নেই।');
    const productId = productData.id ? String(productData.id) : String(Date.now());
    const payload = {
      id: Number(productId),
      nameBn: cleanText(productData.nameBn, 200),
      nameEn: cleanText(productData.nameEn, 200),
      barcode: cleanText(productData.barcode, 64),
      purchasePricePoisha: takaToPoisha(cleanAmount(productData.purchasePrice, 'ক্রয়মূল্য')),
      salePricePoisha: takaToPoisha(cleanAmount(productData.salePrice, 'বিক্রয়মূল্য')),
      wholesalePricePoisha: takaToPoisha(cleanAmount(productData.wholesalePrice || 0, 'পাইকারি মূল্য')),
      stockQty: cleanAmount(productData.stockQty || 0, 'স্টক'),
      minStock: cleanAmount(productData.minStock ?? 5, 'সর্বনিম্ন স্টক'),
      unitName: cleanText(productData.unitName || 'কেজি', 30),
      categoryId: Number(productData.categoryId || 1),
      isActive: productData.isActive !== false,
      updatedAt: Date.now(),
    };
    if (!payload.nameBn && !payload.nameEn) throw new Error('পণ্যের নাম লিখুন।');
    await set(ref(db, `shops/${sc}/products/${sanitizeFirebaseKey(productId)}`), payload);
  };

  const deleteProduct = async (productId) => {
    const sc = requireSession();
    if (!canEditProducts) throw new Error('পণ্য মোছার অনুমতি নেই।');
    await remove(ref(db, `shops/${sc}/products/${sanitizeFirebaseKey(String(productId))}`));
    await logAuditEvent('PRODUCT_DELETED', `পণ্য আইডি ${productId} মুছে ফেলা হয়েছে`);
  };

  // ---------- customers & dues ----------

  const saveCustomer = async (customerData) => {
    const sc = requireSession();
    const customerId = customerData.id ? String(customerData.id) : String(Date.now());
    const name = cleanText(customerData.name, 120);
    if (!name) throw new Error('কাস্টমারের নাম লিখুন।');
    await set(ref(db, `shops/${sc}/customers/${sanitizeFirebaseKey(customerId)}`), {
      id: Number(customerId),
      name,
      phone: cleanText(customerData.phone, 40),
      address: cleanText(customerData.address, 300),
      creditLimitPoisha: takaToPoisha(cleanAmount(customerData.creditLimit || 5000, 'ক্রেডিট লিমিট')),
      totalDuePoisha: takaToPoisha(cleanAmount(customerData.totalDue || 0, 'বাকি')),
      isActive: customerData.isActive !== false,
      createdAt: customerData.createdAt || Date.now(),
    });
  };

  const collectDuePayment = async (customerId, amountTaka, note = '') => {
    const sc = requireSession();
    if (role !== 'owner' && permissions.allowStaffDuePayment === false) {
      throw new Error('কর্মচারীদের বাকি জমা নেওয়ার অনুমতি নেই।');
    }
    const amount = cleanAmount(amountTaka, 'জমার পরিমাণ');
    if (amount <= 0) throw new Error('জমার পরিমাণ ০ এর বেশি হতে হবে।');
    const c = customers.find((x) => String(x.id) === String(customerId));
    if (!c) throw new Error('কাস্টমার পাওয়া যায়নি।');

    await adjustDue(`shops/${sc}/customers/${c.firebaseKey}/totalDuePoisha`, -takaToPoisha(amount));
    const ledgerId = String(Date.now());
    await set(ref(db, `shops/${sc}/due_collections/${ledgerId}`), {
      id: Number(ledgerId),
      customerId: Number(customerId) || String(customerId),
      customerName: c.name,
      amountPoisha: takaToPoisha(amount),
      note: cleanText(note, 300),
      createdAt: Date.now(),
      staffName: auth.userName,
    });
  };

  // ---------- expenses ----------

  const saveExpense = async (expenseData) => {
    const sc = requireSession();
    if (expenseData.id && role !== 'owner') throw new Error('খরচ সম্পাদনার অনুমতি শুধুমাত্র মালিকের।');
    const expenseId = expenseData.id ? String(expenseData.id) : String(Date.now());
    const title = cleanText(expenseData.title, 200);
    if (!title) throw new Error('খরচের বিবরণ লিখুন।');
    await set(ref(db, `shops/${sc}/expenses/${sanitizeFirebaseKey(expenseId)}`), {
      id: Number(expenseId) || expenseId,
      title,
      amountPoisha: takaToPoisha(cleanAmount(expenseData.amount, 'খরচের পরিমাণ')),
      category: cleanText(expenseData.category || 'দোকান খরচ', 60),
      expenseDate: expenseData.expenseDate || Date.now(),
      note: cleanText(expenseData.note, 500),
    });
  };

  // ---------- staff ----------

  const saveStaffMember = async (staffData) => {
    const sc = requireOwner();
    const staffId = sanitizeFirebaseKey(staffData.id ? String(staffData.id) : String(Date.now()));
    const name = cleanText(staffData.name, 120);
    if (!name) throw new Error('কর্মচারীর নাম লিখুন।');
    const pin = staffData.pin ? normalizePin(staffData.pin) : '';
    const isNew = !staffData.id;
    if (isNew && !pin) throw new Error('নতুন কর্মচারীর জন্য একটি পিন দিন।');
    if (pin) {
      if (!isValidPin(pin)) throw new Error('পিন ৪-১২ সংখ্যার হতে হবে।');
      if (isWeakPin(pin)) throw new Error('পিনটি খুব সহজ (যেমন ০০০০ বা ১২৩৪)। অন্য একটি পিন দিন।');
    }

    const email = cleanText(staffData.email, 120).toLowerCase();
    const previous = staffMembers.find((s) => s.id === staffId);
    const payload = {
      id: staffId,
      name,
      email,
      phone: cleanText(staffData.phone, 40),
      role: cleanText(staffData.role || 'staff', 40),
      isActive: staffData.isActive !== false,
      createdAt: staffData.createdAt || previous?.createdAt || Date.now(),
      hasPin: Boolean(pin) || Boolean(previous?.hasPin),
    };
    if (!SECURE_AUTH && pin) payload.pin = pin;
    if (!SECURE_AUTH && !pin && previous) {
      // Legacy mode keeps the existing plaintext PIN untouched.
      delete payload.hasPin;
      await update(ref(db, `shops/${sc}/staff/${staffId}`), payload);
    } else {
      await update(ref(db, `shops/${sc}/staff/${staffId}`), payload);
    }

    if (SECURE_AUTH && pin) {
      try {
        await callable('setStaffPin')({ staffId, pin });
      } catch (err) {
        throw new Error(friendlyError(err));
      }
    }

    if (email.includes('@')) {
      await set(ref(db, `staff_to_shop/${encodeEmailKey(email)}`), sc).catch(() => {});
    }
    await logAuditEvent(isNew ? 'STAFF_ADDED' : 'STAFF_UPDATED', `কর্মচারী ${name} ${isNew ? 'যোগ' : 'আপডেট'} করা হয়েছে`);
  };

  const deleteStaffMember = async (staffId) => {
    const sc = requireOwner();
    if (SECURE_AUTH) {
      try {
        await callable('removeStaff')({ staffId: String(staffId) });
      } catch (err) {
        throw new Error(friendlyError(err));
      }
      return;
    }
    await remove(ref(db, `shops/${sc}/staff/${sanitizeFirebaseKey(String(staffId))}`));
    await logAuditEvent('STAFF_REMOVED', `কর্মচারী আইডি ${staffId} মুছে ফেলা হয়েছে`);
  };

  // ---------- settings ----------

  const updateShopSettings = async (settings) => {
    const sc = requireOwner();
    const allowed = {};
    for (const [k, max] of Object.entries({ shopName: 120, shopAddress: 300, shopPhone: 40, tagline: 200, ownerEmail: 120, currencySymbol: 8 })) {
      if (settings[k] !== undefined) allowed[k] = cleanText(settings[k], max);
    }
    if (settings.vatPercentage !== undefined) {
      const v = cleanAmount(settings.vatPercentage, 'ভ্যাট');
      if (v > 100) throw new Error('ভ্যাট ১০০% এর বেশি হতে পারে না।');
      allowed.vatPercentage = v;
    }
    if (settings.vatEnabled !== undefined) allowed.vatEnabled = Boolean(settings.vatEnabled);
    await update(ref(db, `shops/${sc}/info`), allowed);
    await logAuditEvent('SHOP_SETTINGS_UPDATED', 'দোকানের সাধারণ সেটিংস আপডেট করা হয়েছে');
  };

  const updatePermissions = async (newPerms) => {
    const sc = requireOwner();
    const clean = {};
    for (const k of Object.keys(DEFAULT_PERMISSIONS)) {
      if (newPerms[k] === undefined) continue;
      clean[k] = k === 'maxStaffDiscountPercent' ? Math.min(100, cleanAmount(newPerms[k], 'ছাড়')) : Boolean(newPerms[k]);
    }
    await update(ref(db, `shops/${sc}/permissions`), clean);
    await logAuditEvent('PERMISSIONS_UPDATED', 'কর্মচারী পারমিশন আপডেট করা হয়েছে');
  };

  const updateSecuritySettings = async (newSettings) => {
    const sc = requireOwner();
    const clean = {};
    if (newSettings.autoLockMinutes !== undefined) {
      clean.autoLockMinutes = Math.min(240, Math.max(1, Math.round(cleanAmount(newSettings.autoLockMinutes, 'অটো-লক'))));
    }
    if (newSettings.twoStepConfirmDanger !== undefined) clean.twoStepConfirmDanger = Boolean(newSettings.twoStepConfirmDanger);
    if (newSettings.requirePinForAdminPanel !== undefined) clean.requirePinForAdminPanel = Boolean(newSettings.requirePinForAdminPanel);
    await update(ref(db, `shops/${sc}/security`), clean);
    await logAuditEvent('SECURITY_POLICY_UPDATED', 'নিরাপত্তা ও অটো-লক পলিসি আপডেট করা হয়েছে');
  };

  // ---------- backup / danger zone ----------

  const exportFullDatabase = async () => {
    requireOwner();
    const backup = {
      meta: {
        shopCode,
        shopName: shopInfo.shopName,
        exportTimestamp: Date.now(),
        exportedAt: new Date().toLocaleString('bn-BD'),
        exportedBy: auth.userName,
        version: 'Dokan Pro Web 2.0',
      },
      shopInfo,
      permissions,
      security: {
        autoLockMinutes: securitySettings.autoLockMinutes,
        twoStepConfirmDanger: securitySettings.twoStepConfirmDanger,
      },
      categories,
      products,
      sales,
      customers,
      suppliers,
      purchases,
      expenses,
      // PINs are never exported.
      staffMembers: staffMembers.map(({ id, name, email, phone, role: r, createdAt }) => ({ id, name, email, phone, role: r, createdAt })),
    };
    await logAuditEvent('FULL_DATABASE_BACKUP_EXPORTED', 'সম্পূর্ণ ডাটাবেসের JSON ব্যাকআপ ডাউনলোড করা হয়েছে');
    return backup;
  };

  const clearTestSales = async (pin) => {
    const sc = requireOwner();
    if (!(await verifyMasterPin(pin))) throw new Error('ভুল মাস্টার পিন! বিক্রয় ডাটা মোছার অনুমতি পাওয়া যায়নি।');
    await remove(ref(db, `shops/${sc}/sales`));
    await logAuditEvent('DANGER_ZONE_CLEAR_SALES', 'সকল বিক্রয়ের ইতিহাস মুছে ফেলা হয়েছে');
  };

  const recalculateCustomerBalances = async (pin) => {
    const sc = requireOwner();
    if (!(await verifyMasterPin(pin))) throw new Error('ভুল মাস্টার পিন!');
    const dueMap = {};
    sales.forEach((s) => {
      if (s.customerId && !s.isReturned && Number(s.dueAmount) > 0) {
        dueMap[s.customerId] = (dueMap[s.customerId] || 0) + Number(s.dueAmount);
      }
    });
    const updates = {};
    customers.forEach((c) => {
      updates[`shops/${sc}/customers/${c.firebaseKey}/totalDuePoisha`] = takaToPoisha(dueMap[c.id] || 0);
    });
    if (Object.keys(updates).length > 0) await update(ref(db), updates);
    await logAuditEvent('CUSTOMER_BALANCES_RECALCULATED', 'কাস্টমারদের বকেয়া হিসাব রিক্যালকুলেট করা হয়েছে');
  };

  const restoreFullDatabase = async (jsonContent, pin) => {
    const sc = requireOwner();
    if (!(await verifyMasterPin(pin))) throw new Error('ভুল মাস্টার পিন! ডাটা রিস্টোরের অনুমতি পাওয়া যায়নি।');
    if (typeof jsonContent === 'string' && jsonContent.length > 20 * 1024 * 1024) {
      throw new Error('ব্যাকআপ ফাইলটি অনেক বড় (সর্বোচ্চ ২০ MB)।');
    }
    let data;
    try {
      data = typeof jsonContent === 'string' ? JSON.parse(jsonContent) : jsonContent;
    } catch {
      throw new Error('অবৈধ JSON ফাইল ফরম্যাট!');
    }
    if (!data || typeof data !== 'object' || Array.isArray(data)) throw new Error('অবৈধ ব্যাকআপ ফাইল।');
    if (data.meta?.shopCode && data.meta.shopCode !== sc) {
      throw new Error(`এই ব্যাকআপটি অন্য দোকানের (${cleanText(data.meta.shopCode, 40)})। নিজের দোকানের ব্যাকআপ ব্যবহার করুন।`);
    }

    const updates = {};
    const key = (v, fallback) => sanitizeFirebaseKey(String(v || fallback)).slice(0, 64);
    let seq = 0;
    const nextId = () => `${Date.now()}${seq++}`;

    (Array.isArray(data.products) ? data.products : []).slice(0, 20000).forEach((p) => {
      const pId = key(p.id, nextId());
      updates[`shops/${sc}/products/${pId}`] = {
        id: Number(pId) || pId,
        nameBn: cleanText(p.nameBn || p.name, 200),
        nameEn: cleanText(p.nameEn, 200),
        barcode: cleanText(p.barcode, 64),
        purchasePricePoisha: takaToPoisha(cleanAmount(p.purchasePrice || 0)),
        salePricePoisha: takaToPoisha(cleanAmount(p.salePrice || 0)),
        wholesalePricePoisha: takaToPoisha(cleanAmount(p.wholesalePrice || 0)),
        stockQty: cleanAmount(p.stockQty || 0),
        minStock: cleanAmount(p.minStock ?? 5),
        unitName: cleanText(p.unitName || 'কেজি', 30),
        categoryId: Number(p.categoryId || 1),
        isActive: p.isActive !== false,
        updatedAt: Date.now(),
      };
    });
    (Array.isArray(data.customers) ? data.customers : []).slice(0, 20000).forEach((c) => {
      const cId = key(c.id, nextId());
      updates[`shops/${sc}/customers/${cId}`] = {
        id: Number(cId) || cId,
        name: cleanText(c.name, 120),
        phone: cleanText(c.phone, 40),
        address: cleanText(c.address, 300),
        totalDuePoisha: takaToPoisha(cleanAmount(c.totalDue || 0)),
        creditLimitPoisha: takaToPoisha(cleanAmount(c.creditLimit || 5000)),
        isActive: c.isActive !== false,
        createdAt: c.createdAt || Date.now(),
      };
    });
    (Array.isArray(data.suppliers) ? data.suppliers : []).slice(0, 5000).forEach((s) => {
      const sId = key(s.id, nextId());
      updates[`shops/${sc}/suppliers/${sId}`] = {
        id: sId,
        name: cleanText(s.name, 120),
        company: cleanText(s.company, 120),
        phone: cleanText(s.phone, 40),
        address: cleanText(s.address, 300),
        totalDuePoisha: takaToPoisha(cleanAmount(s.totalDue || 0)),
        isActive: s.isActive !== false,
        createdAt: s.createdAt || Date.now(),
      };
    });
    (Array.isArray(data.expenses) ? data.expenses : []).slice(0, 50000).forEach((e) => {
      const eId = key(e.id, nextId());
      updates[`shops/${sc}/expenses/${eId}`] = {
        id: Number(eId) || eId,
        title: cleanText(e.title, 200),
        amountPoisha: takaToPoisha(cleanAmount(e.amount || 0)),
        category: cleanText(e.category || 'অন্যান্য', 60),
        expenseDate: Number(e.expenseDate) || Date.now(),
        note: cleanText(e.note, 500),
      };
    });

    if (Object.keys(updates).length === 0) throw new Error('ব্যাকআপ ফাইলে রিস্টোর করার মতো কোনো ডাটা নেই।');
    await update(ref(db), updates);
    await logAuditEvent('FULL_DATABASE_RESTORED', `JSON ব্যাকআপ থেকে ${Object.keys(updates).length}টি রেকর্ড রিস্টোর করা হয়েছে`);
  };

  // ---------- suppliers & purchases ----------

  const saveSupplier = async (supplierData) => {
    const sc = requireSession();
    const supplierId = sanitizeFirebaseKey(String(supplierData.id || Date.now()));
    const name = cleanText(supplierData.name, 120);
    if (!name) throw new Error('সাপ্লায়ারের নাম লিখুন।');
    await set(ref(db, `shops/${sc}/suppliers/${supplierId}`), {
      id: supplierId,
      name,
      company: cleanText(supplierData.company, 120),
      phone: cleanText(supplierData.phone, 40),
      address: cleanText(supplierData.address, 300),
      totalDuePoisha: takaToPoisha(cleanAmount(supplierData.totalDue || 0, 'দেনা')),
      isActive: supplierData.isActive !== false,
      createdAt: supplierData.createdAt || Date.now(),
    });
    await logAuditEvent('SUPPLIER_SAVED', `সাপ্লায়ার ${name} সংরক্ষিত হয়েছে`);
  };

  const deleteSupplier = async (supplierId) => {
    const sc = requireOwner();
    await remove(ref(db, `shops/${sc}/suppliers/${sanitizeFirebaseKey(String(supplierId))}`));
    await logAuditEvent('SUPPLIER_DELETED', `সাপ্লায়ার আইডি ${supplierId} মুছে ফেলা হয়েছে`);
  };

  const paySupplierDue = async (supplierId, amountTaka, paymentMethod = 'নগদ', note = '') => {
    const sc = requireSession();
    const amount = cleanAmount(amountTaka, 'পরিশোধের পরিমাণ');
    if (amount <= 0) throw new Error('পরিশোধের পরিমাণ ০ টাকার বেশি হতে হবে');
    const sup = suppliers.find((s) => String(s.id) === String(supplierId));
    if (!sup) throw new Error('সাপ্লায়ার পাওয়া যায়নি');

    await adjustDue(`shops/${sc}/suppliers/${sup.firebaseKey}/totalDuePoisha`, -takaToPoisha(amount));
    const expenseId = `${Date.now()}_sup_pay`;
    await set(ref(db, `shops/${sc}/expenses/${expenseId}`), {
      id: expenseId,
      title: `মহাজন বাকি পরিশোধ (${sup.name})`,
      amountPoisha: takaToPoisha(amount),
      category: 'মহাজন বাকি',
      paymentMethod: cleanText(paymentMethod, 40),
      expenseDate: Date.now(),
      note: cleanText(note || `সাপ্লায়ার: ${sup.name}, ভাউচার পেমেন্ট`, 500),
    });
    await logAuditEvent('SUPPLIER_DUE_PAID', `সাপ্লায়ার ${sup.name}-কে ${amount} ৳ পরিশোধ করা হয়েছে`);
  };

  const savePurchase = async (purchaseData) => {
    const sc = requireSession();
    const purchaseId = sanitizeFirebaseKey(String(purchaseData.id || Date.now()));
    const invoiceNo = cleanText(purchaseData.invoiceNo || `PUR-${Math.floor(1000 + Math.random() * 9000)}`, 40);
    const items = purchaseData.items || [];
    const totalAmount = cleanAmount(purchaseData.totalAmount || 0, 'মোট টাকা');
    const paidAmount = cleanAmount(purchaseData.paidAmount || 0, 'পরিশোধ');
    const dueAmount = Math.max(0, totalAmount - paidAmount);

    const payload = {
      id: purchaseId,
      invoiceNo,
      supplierId: String(purchaseData.supplierId || ''),
      supplierName: cleanText(purchaseData.supplierName || 'সাধারণ মহাজন', 120),
      purchaseDate: purchaseData.purchaseDate || Date.now(),
      totalAmountPoisha: takaToPoisha(totalAmount),
      paidAmountPoisha: takaToPoisha(paidAmount),
      dueAmountPoisha: takaToPoisha(dueAmount),
      paymentMethod: cleanText(purchaseData.paymentMethod || 'নগদ', 40),
      notes: cleanText(purchaseData.notes, 500),
      createdAt: Date.now(),
      items: items.map((it) => ({
        id: it.id || `${Date.now()}_${Math.random().toString(36).substring(2, 5)}`,
        productId: it.productId ? String(it.productId) : '',
        productName: cleanText(it.productName || it.name, 200),
        unitName: cleanText(it.unitName || 'কেজি', 30),
        purchasePricePoisha: takaToPoisha(cleanAmount(it.purchasePrice || 0, 'ক্রয়মূল্য')),
        quantity: cleanAmount(it.quantity || it.qty || 1, 'পরিমাণ'),
        lineTotalPoisha: takaToPoisha(cleanAmount(it.lineTotal || Number(it.purchasePrice || 0) * Number(it.quantity || 1))),
      })),
    };

    await set(ref(db, `shops/${sc}/purchases/${purchaseId}`), payload);

    const tasks = [];
    const priceUpdates = {};
    for (const it of items) {
      const prod = it.productId && products.find((p) => String(p.id) === String(it.productId));
      if (!prod) continue;
      tasks.push(adjustStock(sc, prod, Number(it.quantity || it.qty || 0)));
      if (Number(it.purchasePrice) > 0) {
        priceUpdates[`shops/${sc}/products/${prod.firebaseKey || prod.id}/purchasePricePoisha`] = takaToPoisha(it.purchasePrice);
      }
    }
    if (dueAmount > 0 && purchaseData.supplierId) {
      const sup = suppliers.find((s) => String(s.id) === String(purchaseData.supplierId));
      if (sup) tasks.push(adjustDue(`shops/${sc}/suppliers/${sup.firebaseKey}/totalDuePoisha`, takaToPoisha(dueAmount)));
    }
    if (Object.keys(priceUpdates).length) tasks.push(update(ref(db), priceUpdates));
    await Promise.all(tasks);

    await logAuditEvent('PURCHASE_CREATED', `ক্রয় চালান ${invoiceNo} (${totalAmount} ৳) সংরক্ষিত ও স্টক বৃদ্ধি করা হয়েছে`);
    return payload;
  };

  const deletePurchase = async (purchaseId) => {
    const sc = requireOwner();
    const purchase = purchases.find((p) => String(p.id) === String(purchaseId));
    if (!purchase) return;

    const tasks = [];
    for (const it of purchase.items || []) {
      const prod = it.productId && products.find((p) => String(p.id) === String(it.productId));
      if (prod) tasks.push(adjustStock(sc, prod, -Number(it.quantity || 0)));
    }
    if (purchase.dueAmount > 0 && purchase.supplierId) {
      const sup = suppliers.find((s) => String(s.id) === String(purchase.supplierId));
      if (sup) tasks.push(adjustDue(`shops/${sc}/suppliers/${sup.firebaseKey}/totalDuePoisha`, -takaToPoisha(purchase.dueAmount)));
    }
    await Promise.all(tasks);
    await remove(ref(db, `shops/${sc}/purchases/${purchase.firebaseKey}`));
    await logAuditEvent('PURCHASE_DELETED', `ক্রয় চালান ${purchase.invoiceNo} মুছে ফেলা হয়েছে ও স্টক সমন্বয় করা হয়েছে`);
  };

  // ---------- held carts (local to this browser) ----------

  const persistHeld = (list) => {
    setHeldCarts(list);
    try {
      if (shopCode) localStorage.setItem(`dokan_held_carts_${shopCode}`, JSON.stringify(list));
    } catch {
      /* storage full or blocked */
    }
  };

  const holdCart = (cartItems, customerInfo = {}) => {
    if (!cartItems || cartItems.length === 0) return;
    persistHeld([
      {
        id: `${Date.now()}`,
        timestamp: Date.now(),
        cart: cartItems,
        customer: customerInfo,
        totalQty: cartItems.reduce((sum, it) => sum + (Number(it.quantity) || 1), 0),
        grandTotal: cartItems.reduce((sum, it) => sum + Number(it.salePrice) * (Number(it.quantity) || 1), 0),
      },
      ...heldCarts,
    ]);
  };

  const restoreHeldCart = (index) => {
    const target = heldCarts[index];
    if (!target) return null;
    persistHeld(heldCarts.filter((_, i) => i !== index));
    return target;
  };

  const deleteHeldCart = (index) => persistHeld(heldCarts.filter((_, i) => i !== index));

  // ---------- returns ----------

  const processProductReturn = async ({ productId, quantity, refundAmount, customerName, reason }) => {
    const sc = requireSession();
    const prod = products.find((p) => String(p.id) === String(productId));
    if (!prod) throw new Error('পণ্যটি খুঁজে পাওয়া যায়নি');
    const retQty = Math.max(1, cleanAmount(quantity || 1, 'পরিমাণ'));
    const refund = cleanAmount(refundAmount || prod.salePrice * retQty, 'রিফান্ড');

    await adjustStock(sc, prod, retQty);
    const returnId = `${Date.now()}_ret`;
    await set(ref(db, `shops/${sc}/expenses/${returnId}`), {
      id: returnId,
      title: `পণ্য ফেরত ও রিফান্ড (${prod.nameBn || prod.nameEn})`,
      amountPoisha: takaToPoisha(refund),
      category: 'পণ্য ফেরত',
      expenseDate: Date.now(),
      note: cleanText(`ক্রেতা: ${customerName || 'নগদ ক্রেতা'}, পরিমাণ: ${retQty} ${prod.unitName}, কারণ: ${reason || 'পণ্য ফেরত'}`, 500),
    });
    await logAuditEvent('PRODUCT_RETURNED', `${prod.nameBn || prod.nameEn} ${retQty}টি ফেরত নেওয়া হয়েছে`);
  };

  return (
    <ShopContext.Provider
      value={{
        auth,
        authReady,
        isOwner,
        isLocked,
        lockScreen: () => role === 'owner' && setIsLocked(true),
        unlock,
        login,
        logout,
        secureMode: SECURE_AUTH,
        globalNotices,
        globalSettings,
        currentShopStatus,
        shopFeatures,
        activeShopNotice,
        shopInfo,
        products,
        categories,
        sales,
        customers,
        suppliers,
        purchases,
        expenses,
        heldCarts,
        staffMembers,
        permissions,
        securitySettings,
        auditLogs,
        isFirebaseConnected,
        isSyncing,
        lastSyncTime,
        createSale,
        saveProduct,
        deleteProduct,
        saveCustomer,
        collectDuePayment,
        saveSupplier,
        deleteSupplier,
        paySupplierDue,
        savePurchase,
        deletePurchase,
        holdCart,
        restoreHeldCart,
        deleteHeldCart,
        processProductReturn,
        restoreFullDatabase,
        saveExpense,
        saveStaffMember,
        deleteStaffMember,
        updateShopSettings,
        logAuditEvent,
        verifyMasterPin,
        updateMasterPin,
        updatePermissions,
        updateSecuritySettings,
        exportFullDatabase,
        clearTestSales,
        recalculateCustomerBalances,
      }}
    >
      {children}
    </ShopContext.Provider>
  );
}

export function useShop() {
  const ctx = useContext(ShopContext);
  if (!ctx) throw new Error('useShop must be used within a ShopProvider');
  return ctx;
}
