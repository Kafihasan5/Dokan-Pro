/**
 * Dokan Pro — server-side authentication (platform-independent handlers).
 * Wrapped as Firebase callables in ../index.js and as HTTPS endpoints in ../netlify/functions/api.mjs.
 *
 * All PIN checks happen here, never in the browser. A successful login returns a
 * Firebase custom token carrying { shopCode, role, staffId } claims; the Realtime
 * Database rules (../database.rules.json) use those claims to decide access.
 * PIN hashes live under /shop_secrets, which no client can read or write.
 */
import { getAuth } from 'firebase-admin/auth';
import { getDatabase, ServerValue } from 'firebase-admin/database';
import { HttpsError } from 'firebase-functions/v2/https';
import {
  normalizePin,
  isValidPinFormat,
  isWeakPin,
  hashPin,
  verifyPinHash,
  needsRehash,
  matchesLegacyMasterPin,
} from './pin.js';

// Resolved lazily so the hosting platform can initialise firebase-admin first.
let _db;
const db = { ref: (...args) => (_db ??= getDatabase()).ref(...args) };

const SHOP_MAX_FAILS = 5;
const SHOP_LOCK_MS = 15 * 60 * 1000;
const IP_MAX_FAILS = 25;
const IP_WINDOW_MS = 60 * 60 * 1000;

const GENERIC_FAIL = 'দোকান কোড/ইমেইল অথবা পিন সঠিক নয়।';
const SUPABASE_URL = process.env.SUPABASE_URL || 'https://fbkyfxghhzjonihnntip.supabase.co';
const SUPABASE_ANON_KEY = process.env.SUPABASE_ANON_KEY || 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImZia3lmeGdoaHpqb25paG5udGlwIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODk0NDEyMTYsImV4cCI6MjEwNTAxNzIxNn0.Sn_4D24ZyVRbqa34WyV9lXZ0EBlHmqwVoSU0csRSPbo';

// ---------- helpers ----------

function sanitizeKey(input) {
  return String(input || '').trim().replace(/[.#$\[\]\/]/g, '_');
}

function encodeEmailKey(email) {
  return String(email).trim().toLowerCase().replace(/\./g, '_dot_').replace(/@/g, '_at_');
}

function requireString(v, name, max = 120) {
  if (typeof v !== 'string' || !v.trim() || v.length > max) {
    throw new HttpsError('invalid-argument', `${name} সঠিক নয়।`);
  }
  return v.trim();
}

function clientIp(request) {
  const raw = request.rawRequest;
  const fwd = raw?.headers?.['x-forwarded-for'];
  return sanitizeKey((typeof fwd === 'string' ? fwd.split(',')[0] : raw?.ip) || 'unknown').slice(0, 60);
}

function requireShopUser(request, { ownerOnly = false } = {}) {
  const t = request.auth?.token;
  if (!t?.shopCode) throw new HttpsError('unauthenticated', 'লগইন প্রয়োজন।');
  if (ownerOnly && t.role !== 'owner') throw new HttpsError('permission-denied', 'শুধুমাত্র মালিকের জন্য।');
  return t;
}

async function readVal(path) {
  const snap = await db.ref(path).get();
  return snap.exists() ? snap.val() : null;
}

async function findShopByOwnerEmail(email) {
  const key = encodeEmailKey(email);
  const indexed = await readVal(`email_to_shop/${key}`);
  if (indexed) return String(indexed).toUpperCase();

  // Older shops may have the email in metadata without an index. Newer shops also
  // store the purchase/license email separately from the shop owner's login email.
  for (const field of ['ownerEmail', 'licenseEmail']) {
    const matches = await db.ref('shops').orderByChild(`info/${field}`).equalTo(email).limitToFirst(1).get();
    let found = '';
    matches.forEach((child) => { found ||= child.key; });
    if (found) {
      await db.ref(`email_to_shop/${key}`).transaction((current) => current || found);
      return found.toUpperCase();
    }
  }
  return '';
}

// Throttle counters live under /_security, which rules make invisible to clients.
async function assertNotLocked(key) {
  const rec = await readVal(`_security/shop_locks/${key}`);
  if (rec?.lockedUntil && rec.lockedUntil > Date.now()) {
    const mins = Math.ceil((rec.lockedUntil - Date.now()) / 60000);
    throw new HttpsError('resource-exhausted', `অনেকবার ভুল পিন দেওয়া হয়েছে। ${mins} মিনিট পর আবার চেষ্টা করুন।`);
  }
}

async function recordShopFailure(key) {
  await db.ref(`_security/shop_locks/${key}`).transaction((rec) => {
    const now = Date.now();
    const r = { fails: 0, strikes: 0, ...(rec || {}) };
    if (r.lockedUntil && r.lockedUntil <= now) r.lockedUntil = null;
    r.fails += 1;
    r.lastFailAt = now;
    if (r.fails >= SHOP_MAX_FAILS) {
      // Each further lockout doubles, capped at 24h. Strikes reset only on a successful login.
      r.strikes += 1;
      r.lockedUntil = now + Math.min(SHOP_LOCK_MS * 2 ** (r.strikes - 1), 24 * 60 * 60 * 1000);
      r.fails = 0;
    }
    return r;
  });
}

async function clearShopFailures(key) {
  await db.ref(`_security/shop_locks/${key}`).remove();
}

async function assertIpAllowed(ip) {
  const rec = await readVal(`_security/ip_fails/${ip}`);
  if (rec && rec.windowStart > Date.now() - IP_WINDOW_MS && rec.count >= IP_MAX_FAILS) {
    throw new HttpsError('resource-exhausted', 'এই নেটওয়ার্ক থেকে অনেকবার ভুল চেষ্টা হয়েছে। কিছুক্ষণ পর চেষ্টা করুন।');
  }
}

async function recordIpFailure(ip) {
  await db.ref(`_security/ip_fails/${ip}`).transaction((rec) => {
    const now = Date.now();
    if (!rec || rec.windowStart < now - IP_WINDOW_MS) return { windowStart: now, count: 1 };
    rec.count += 1;
    return rec;
  });
}

async function audit(shopCode, action, details, actor, role) {
  const id = `${Date.now()}_${Math.random().toString(36).slice(2, 7)}`;
  await db.ref(`shops/${shopCode}/auditLogs/${id}`).set({
    timestamp: ServerValue.TIMESTAMP,
    actor: actor || 'সিস্টেম',
    role: role || 'system',
    action,
    details,
    source: 'server',
  });
}

async function resolveShopCode(identifier, role) {
  if (!identifier.includes('@')) {
    const code = sanitizeKey(identifier.toUpperCase()).replace(/\s+/g, '');
    // The mobile app accepts codes typed without the "SHOP-" prefix.
    if (code && !code.startsWith('SHOP-') && !(await readVal(`shops/${code}/info`)) && (await readVal(`shops/SHOP-${code}/info`))) {
      return `SHOP-${code}`;
    }
    return code;
  }
  const key = encodeEmailKey(identifier);
  if (role === 'staff') {
    const viaStaff = await readVal(`staff_to_shop/${key}`);
    if (viaStaff) return sanitizeKey(String(viaStaff).toUpperCase());
  }
  const viaOwner = await readVal(`email_to_shop/${key}`);
  return viaOwner ? sanitizeKey(String(viaOwner).toUpperCase()) : '';
}

/**
 * Detects whether a licensed device has a cloud shop for this owner email.
 * It returns only a boolean; shop details remain protected behind the Master PIN login.
 */
export async function findExistingOwnerShop(request) {
  const email = requireString(request.data?.email, 'লাইসেন্স ইমেইল', 120).toLowerCase();
  const deviceId = requireString(request.data?.deviceId, 'ডিভাইস আইডি', 160);
  if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) throw new HttpsError('invalid-argument', 'ইমেইল সঠিক নয়।');

  let licenseResponse;
  try {
    licenseResponse = await fetch(`${SUPABASE_URL}/rest/v1/rpc/verify_app_license`, {
      method: 'POST',
      headers: {
        apikey: SUPABASE_ANON_KEY,
        Authorization: `Bearer ${SUPABASE_ANON_KEY}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ p_email: email, p_device_id: deviceId }),
    });
  } catch {
    throw new HttpsError('unavailable', 'লাইসেন্স যাচাই করা যাচ্ছে না। ইন্টারনেট সংযোগ পরীক্ষা করুন।');
  }
  if (!licenseResponse.ok) throw new HttpsError('unavailable', 'লাইসেন্স যাচাই করা যাচ্ছে না। পরে আবার চেষ্টা করুন।');
  const license = await licenseResponse.json().catch(() => null);
  if (license?.valid !== true) throw new HttpsError('permission-denied', 'এই ডিভাইসের সক্রিয় লাইসেন্স যাচাই করা যায়নি।');

  const ip = clientIp(request);
  const ratePath = `_security/owner_lookups/${ip}`;
  const rate = await readVal(ratePath);
  if (rate?.windowStart > Date.now() - IP_WINDOW_MS && rate.count >= 120) {
    throw new HttpsError('resource-exhausted', 'অনেকবার হিসাব যাচাই করা হয়েছে। কিছুক্ষণ পর আবার চেষ্টা করুন।');
  }
  await db.ref(ratePath).transaction((current) => {
    const now = Date.now();
    if (!current || current.windowStart < now - IP_WINDOW_MS) return { windowStart: now, count: 1 };
    current.count += 1;
    return current;
  });

  const shopCode = await findShopByOwnerEmail(email);
  return { hasExistingShop: Boolean(shopCode) };
}

function assertShopUsable(status, settings) {
  if (settings?.maintenanceMode) {
    throw new HttpsError('unavailable', `সার্ভার মেইনটেন্যান্স: ${settings.maintenanceMessage || 'কিছুক্ষণ পর চেষ্টা করুন।'}`);
  }
  if (!status) return;
  if (status.isSuspended || status.status === 'suspended' || status.status === 'banned') {
    throw new HttpsError('permission-denied', `দোকানের এক্সেস স্থগিত: ${status.reason || 'ডেভেলপারের সাথে যোগাযোগ করুন।'}`);
  }
  const isTrial = status.licenseType === 'trial' || status.plan === 'trial';
  if (isTrial && status.expiresAt && Date.now() > status.expiresAt) {
    throw new HttpsError('permission-denied', 'ফ্রি ট্রায়ালের মেয়াদ শেষ হয়েছে। ফুল লাইসেন্সের জন্য ডেভেলপারের সাথে যোগাযোগ করুন।');
  }
}

// Returns { ok, migrate } for the owner PIN.
function checkOwnerPin(pin, secrets, sec, info) {
  if (secrets?.masterPinHash) return { ok: verifyPinHash(pin, secrets.masterPinHash), migrate: needsRehash(secrets.masterPinHash) };
  // Values moved out of the public tree by scripts/migrate-pins.js live under shop_secrets/{code}/legacy.
  const legacy = { ...(sec || {}), ...(secrets?.legacy || {}) };
  return { ok: matchesLegacyMasterPin(pin, legacy, info || {}), migrate: true };
}

// Returns { staffId, name, migrate } or null.
function checkStaffPin(pin, secrets, staffList, sec) {
  for (const [key, st] of Object.entries(staffList || {})) {
    if (!st || st.isActive === false) continue;
    // The record key (not st.id) is used everywhere so rules can look the record up by it.
    const staffId = key;
    const hash = secrets?.staff?.[staffId];
    if (hash ? verifyPinHash(pin, hash) : st.pin !== undefined && normalizePin(st.pin) === pin) {
      return { staffId, name: st.name || 'কর্মচারী', migrate: !hash || needsRehash(hash) };
    }
  }
  const shared = secrets?.staffPinHash
    ? verifyPinHash(pin, secrets.staffPinHash)
    : sec?.staffPin !== undefined && normalizePin(sec.staffPin) === pin;
  return shared ? { staffId: 'shared', name: 'কর্মচারী', migrate: !secrets?.staffPinHash || needsRehash(secrets.staffPinHash) } : null;
}

// ---------- callable: login ----------

export async function shopLogin(request) {
  const identifier = requireString(request.data?.identifier, 'দোকান কোড বা ইমেইল');
  const pin = normalizePin(requireString(request.data?.pin, 'পিন', 20));
  const role = request.data?.role === 'staff' ? 'staff' : 'owner';
  const ip = clientIp(request);

  await assertIpAllowed(ip);
  if (!isValidPinFormat(pin)) {
    await recordIpFailure(ip);
    throw new HttpsError('unauthenticated', GENERIC_FAIL);
  }

  const shopCode = await resolveShopCode(identifier, role);
  if (!shopCode) {
    await recordIpFailure(ip);
    throw new HttpsError('unauthenticated', GENERIC_FAIL);
  }

  const lockKey = `${shopCode}__${role}`;
  await assertNotLocked(lockKey);

  const [info, sec, staffList, status, settings, secrets] = await Promise.all([
    readVal(`shops/${shopCode}/info`),
    readVal(`shops/${shopCode}/security`),
    role === 'staff' ? readVal(`shops/${shopCode}/staff`) : null,
    readVal(`shops/${shopCode}/status`),
    readVal('global_settings'),
    readVal(`shop_secrets/${shopCode}`),
  ]);

  if (!info && !sec) {
    await recordIpFailure(ip);
    throw new HttpsError('unauthenticated', GENERIC_FAIL);
  }

  let claims;
  let pinChangeRequired = false;
  if (role === 'owner') {
    const { ok, migrate } = checkOwnerPin(pin, secrets, sec, info);
    if (!ok) {
      await Promise.all([recordShopFailure(lockKey), recordIpFailure(ip)]);
      await audit(shopCode, 'LOGIN_FAILED', 'ভুল মাস্টার পিন দিয়ে লগইনের চেষ্টা', 'অজানা', 'owner');
      throw new HttpsError('unauthenticated', GENERIC_FAIL);
    }
    if (isWeakPin(pin)) {
      // A guessable PIN is only accepted together with the owner's e-mail (a second secret),
      // and the session is restricted until the PIN is changed.
      // Shops that never registered an e-mail have no second factor; they may log in with the
      // shop code, but only into a restricted session that can do nothing except change the PIN.
      const ownerEmail = info?.ownerEmail || sec?.ownerEmail;
      if (!identifier.includes('@') && ownerEmail) {
        throw new HttpsError(
          'failed-precondition',
          'আপনার মাস্টার পিনটি খুব সহজ (যেমন ১২৩৪)। নিরাপত্তার জন্য দোকান কোডের বদলে নিবন্ধিত ইমেইল দিয়ে লগইন করুন, তারপর পিন পরিবর্তন করুন।'
        );
      }
      pinChangeRequired = true;
    }
    if (migrate && !pinChangeRequired) {
      await db.ref(`shop_secrets/${shopCode}`).update({ masterPinHash: hashPin(pin), migratedAt: ServerValue.TIMESTAMP });
    }
    claims = { shopCode, role: 'owner', name: 'মালিক' };
  } else {
    const match = checkStaffPin(pin, secrets, staffList, sec);
    if (!match) {
      await Promise.all([recordShopFailure(lockKey), recordIpFailure(ip)]);
      await audit(shopCode, 'LOGIN_FAILED', 'ভুল কর্মচারী পিন দিয়ে লগইনের চেষ্টা', 'অজানা', 'staff');
      throw new HttpsError('unauthenticated', GENERIC_FAIL);
    }
    if (isWeakPin(pin)) {
      throw new HttpsError('failed-precondition', 'এই কর্মচারী পিনটি খুব সহজ (যেমন ০০০০)। মালিককে একটি নতুন পিন সেট করে দিতে বলুন।');
    }
    if (match.migrate) {
      const path = match.staffId === 'shared' ? 'staffPinHash' : `staff/${match.staffId}`;
      await db.ref(`shop_secrets/${shopCode}/${path}`).set(hashPin(pin));
    }
    claims = { shopCode, role: 'staff', staffId: match.staffId, name: match.name };
  }

  await clearShopFailures(lockKey);
  assertShopUsable(status, settings);

  if (pinChangeRequired) claims.pinChangeRequired = true;
  const uid = claims.role === 'owner' ? `owner_${shopCode}` : `staff_${shopCode}_${claims.staffId}`;
  const token = await getAuth().createCustomToken(uid.slice(0, 128), claims);
  await audit(shopCode, 'LOGIN_SUCCESS', `ওয়েব লগইন (${ip})`, claims.name, claims.role);
  return { token, pinChangeRequired };
}

// ---------- callable: re-verify owner PIN (danger-zone actions) ----------

export async function verifyOwnerPin(request) {
  const t = requireShopUser(request, { ownerOnly: true });
  const pin = normalizePin(requireString(request.data?.pin, 'পিন', 20));
  const lockKey = `${t.shopCode}__owner`;
  await assertNotLocked(lockKey);

  const [secrets, sec, info] = await Promise.all([
    readVal(`shop_secrets/${t.shopCode}`),
    readVal(`shops/${t.shopCode}/security`),
    readVal(`shops/${t.shopCode}/info`),
  ]);
  const { ok } = checkOwnerPin(pin, secrets, sec, info);
  if (!ok) {
    await recordShopFailure(lockKey);
    await audit(t.shopCode, 'ADMIN_AUTH_FAILED', 'ভুল মাস্টার পিন দিয়ে সুরক্ষিত কাজের চেষ্টা', t.name, t.role);
    return { ok: false };
  }
  await clearShopFailures(lockKey);
  return { ok: true };
}

// ---------- callable: change owner PIN ----------

export async function changeOwnerPin(request) {
  const t = requireShopUser(request, { ownerOnly: true });
  const oldPin = normalizePin(requireString(request.data?.oldPin, 'বর্তমান পিন', 20));
  const newPin = normalizePin(requireString(request.data?.newPin, 'নতুন পিন', 20));
  const lockKey = `${t.shopCode}__owner`;
  await assertNotLocked(lockKey);

  if (!isValidPinFormat(newPin)) throw new HttpsError('invalid-argument', 'নতুন পিন ৪-১২ সংখ্যার হতে হবে।');
  if (isWeakPin(newPin)) throw new HttpsError('invalid-argument', 'নতুন পিনটি খুব সহজ। ১২৩৪, ০০০০ বা একই সংখ্যা বারবার ব্যবহার করবেন না।');

  const [secrets, sec, info] = await Promise.all([
    readVal(`shop_secrets/${t.shopCode}`),
    readVal(`shops/${t.shopCode}/security`),
    readVal(`shops/${t.shopCode}/info`),
  ]);
  if (!checkOwnerPin(oldPin, secrets, sec, info).ok) {
    await recordShopFailure(lockKey);
    throw new HttpsError('permission-denied', 'বর্তমান মাস্টার পিনটি সঠিক নয়।');
  }

  // Store the new hash and scrub every plaintext / unsalted copy.
  await db.ref().update({
    [`shop_secrets/${t.shopCode}/masterPinHash`]: hashPin(newPin),
    [`shop_secrets/${t.shopCode}/updatedAt`]: ServerValue.TIMESTAMP,
    [`shops/${t.shopCode}/security/masterPin`]: null,
    [`shops/${t.shopCode}/security/masterPinHash`]: null,
    [`shops/${t.shopCode}/security/masterPinHashBn`]: null,
    [`shops/${t.shopCode}/security/updatedAt`]: ServerValue.TIMESTAMP,
    [`shops/${t.shopCode}/info/pinCode`]: null,
    [`shops/${t.shopCode}/info/pin`]: null,
    [`shops/${t.shopCode}/info/masterPin`]: null,
  });
  await clearShopFailures(lockKey);

  // Sign out every other owner session (refresh tokens); ID tokens expire within 1h.
  await getAuth().revokeRefreshTokens(`owner_${t.shopCode}`).catch(() => {});
  await audit(t.shopCode, 'MASTER_PIN_CHANGED', 'মাস্টার পিন পরিবর্তন করা হয়েছে', t.name, t.role);

  const token = await getAuth().createCustomToken(`owner_${t.shopCode}`, { shopCode: t.shopCode, role: 'owner', name: 'মালিক' });
  return { ok: true, token };
}

// ---------- callable: set a staff member's PIN ----------

export async function setStaffPin(request) {
  const t = requireShopUser(request, { ownerOnly: true });
  if (t.pinChangeRequired) throw new HttpsError('failed-precondition', 'আগে মাস্টার পিন পরিবর্তন করুন।');
  const staffId = sanitizeKey(requireString(request.data?.staffId, 'কর্মচারী আইডি', 64));
  const pin = normalizePin(requireString(request.data?.pin, 'পিন', 20));
  if (!isValidPinFormat(pin)) throw new HttpsError('invalid-argument', 'পিন ৪-১২ সংখ্যার হতে হবে।');
  if (isWeakPin(pin)) throw new HttpsError('invalid-argument', 'পিনটি খুব সহজ। অন্য একটি পিন দিন।');

  const master = await readVal(`shop_secrets/${t.shopCode}/masterPinHash`);
  if (master && verifyPinHash(pin, master)) {
    throw new HttpsError('invalid-argument', 'কর্মচারীর পিন মালিকের মাস্টার পিনের মতো হতে পারবে না।');
  }

  await db.ref().update({
    [`shop_secrets/${t.shopCode}/staff/${staffId}`]: hashPin(pin),
    [`shops/${t.shopCode}/staff/${staffId}/pin`]: null,
    [`shops/${t.shopCode}/staff/${staffId}/hasPin`]: true,
  });
  await getAuth().revokeRefreshTokens(`staff_${t.shopCode}_${staffId}`).catch(() => {});
  await audit(t.shopCode, 'STAFF_PIN_SET', `কর্মচারী ${staffId}-এর পিন সেট করা হয়েছে`, t.name, t.role);
  return { ok: true };
}

// ---------- callable: remove a staff member (and their secret) ----------

export async function removeStaff(request) {
  const t = requireShopUser(request, { ownerOnly: true });
  const staffId = sanitizeKey(requireString(request.data?.staffId, 'কর্মচারী আইডি', 64));
  const st = await readVal(`shops/${t.shopCode}/staff/${staffId}`);
  const updates = {
    [`shops/${t.shopCode}/staff/${staffId}`]: null,
    [`shop_secrets/${t.shopCode}/staff/${staffId}`]: null,
  };
  if (st?.email) {
    const key = encodeEmailKey(st.email);
    if ((await readVal(`staff_to_shop/${key}`)) === t.shopCode) updates[`staff_to_shop/${key}`] = null;
  }
  await db.ref().update(updates);
  await getAuth().revokeRefreshTokens(`staff_${t.shopCode}_${staffId}`).catch(() => {});
  await audit(t.shopCode, 'STAFF_REMOVED', `কর্মচারী ${st?.name || staffId} মুছে ফেলা হয়েছে`, t.name, t.role);
  return { ok: true };
}

// ---------- callable: register a brand-new shop (first launch of the owner app) ----------

const SHOP_CODE_RE = /^SHOP-[A-Z0-9]{4,16}$/;
const REG_MAX_PER_IP = 5;

export async function registerShop(request) {
  const shopCode = sanitizeKey(requireString(request.data?.shopCode, 'দোকান কোড', 32).toUpperCase());
  const pin = normalizePin(requireString(request.data?.pin, 'পিন', 20));
  const ownerEmail = typeof request.data?.ownerEmail === 'string' ? request.data.ownerEmail.trim().toLowerCase().slice(0, 120) : '';
  const licenseEmail = typeof request.data?.licenseEmail === 'string' ? request.data.licenseEmail.trim().toLowerCase().slice(0, 120) : '';
  const shopName = typeof request.data?.shopName === 'string' ? request.data.shopName.trim().slice(0, 120) : '';
  const ip = clientIp(request);

  if (!SHOP_CODE_RE.test(shopCode)) throw new HttpsError('invalid-argument', 'দোকান কোড সঠিক নয়।');
  if (!isValidPinFormat(pin)) throw new HttpsError('invalid-argument', 'মাস্টার পিন ৪-১২ সংখ্যার হতে হবে।');
  if (isWeakPin(pin)) {
    throw new HttpsError('invalid-argument', 'মাস্টার পিনটি খুব সহজ। ১২৩৪, ০০০০ বা একই সংখ্যা বারবার ব্যবহার করবেন না।');
  }
  if (ownerEmail && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(ownerEmail)) throw new HttpsError('invalid-argument', 'ইমেইল সঠিক নয়।');
  if (licenseEmail && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(licenseEmail)) throw new HttpsError('invalid-argument', 'লাইসেন্স ইমেইল সঠিক নয়।');

  const accountEmails = [...new Set([ownerEmail, licenseEmail].filter(Boolean))];
  for (const email of accountEmails) {
    const linkedShop = await findShopByOwnerEmail(email);
    if (linkedShop && String(linkedShop).toUpperCase() !== shopCode) {
      throw new HttpsError('already-exists', 'এই ইমেইলে সংরক্ষিত হিসাব আছে। নতুন দোকান খোলার আগে হিসাব পুনরুদ্ধার করুন অথবা অন্য ইমেইল ব্যবহার করুন।');
    }
  }

  // Throttle shop creation per network.
  const regRef = db.ref(`_security/reg_ip/${ip}`);
  const reg = await readVal(`_security/reg_ip/${ip}`);
  if (reg && reg.windowStart > Date.now() - IP_WINDOW_MS && reg.count >= REG_MAX_PER_IP) {
    throw new HttpsError('resource-exhausted', 'এই নেটওয়ার্ক থেকে অনেক দোকান খোলা হয়েছে। পরে চেষ্টা করুন।');
  }

  const [existingShop, existingSecret] = await Promise.all([
    db.ref(`shops/${shopCode}`).limitToFirst(1).get(),
    readVal(`shop_secrets/${shopCode}`),
  ]);
  if (existingShop.exists() || existingSecret) {
    throw new HttpsError('already-exists', 'এই দোকান কোড আগে থেকেই নিবন্ধিত। মাস্টার পিন দিয়ে লগইন করুন।');
  }

  const updates = {
    [`shop_secrets/${shopCode}/masterPinHash`]: hashPin(pin),
    [`shop_secrets/${shopCode}/createdAt`]: ServerValue.TIMESTAMP,
    [`shops/${shopCode}/info/shopCode`]: shopCode,
    [`shops/${shopCode}/info/shopName`]: shopName || 'Dokan Pro',
    [`shops/${shopCode}/info/createdAt`]: ServerValue.TIMESTAMP,
  };
  if (ownerEmail) {
    updates[`shops/${shopCode}/info/ownerEmail`] = ownerEmail;
  }
  if (licenseEmail) updates[`shops/${shopCode}/info/licenseEmail`] = licenseEmail;
  for (const email of accountEmails) updates[`email_to_shop/${encodeEmailKey(email)}`] = shopCode;
  await db.ref().update(updates);
  await regRef.transaction((rec) => {
    const now = Date.now();
    if (!rec || rec.windowStart < now - IP_WINDOW_MS) return { windowStart: now, count: 1 };
    rec.count += 1;
    return rec;
  });
  await audit(shopCode, 'SHOP_REGISTERED', `নতুন দোকান নিবন্ধিত (${ip})`, 'মালিক', 'owner');

  const token = await getAuth().createCustomToken(`owner_${shopCode}`, { shopCode, role: 'owner', name: 'মালিক' });
  return { token, shopCode };
}

// ---------- callable: set the shop-wide staff PIN (legacy "staff access PIN") ----------

export async function setSharedStaffPin(request) {
  const t = requireShopUser(request, { ownerOnly: true });
  if (t.pinChangeRequired) throw new HttpsError('failed-precondition', 'আগে মাস্টার পিন পরিবর্তন করুন।');
  const raw = request.data?.pin;
  const updates = { [`shops/${t.shopCode}/security/staffPin`]: null };
  if (raw === null || raw === '') {
    updates[`shop_secrets/${t.shopCode}/staffPinHash`] = null;
  } else {
    const pin = normalizePin(requireString(raw, 'পিন', 20));
    if (!isValidPinFormat(pin)) throw new HttpsError('invalid-argument', 'পিন ৪-১২ সংখ্যার হতে হবে।');
    if (isWeakPin(pin)) throw new HttpsError('invalid-argument', 'কর্মচারী পিনটি খুব সহজ (যেমন ০০০০)। অন্য একটি পিন দিন।');
    const master = await readVal(`shop_secrets/${t.shopCode}/masterPinHash`);
    if (master && verifyPinHash(pin, master)) {
      throw new HttpsError('invalid-argument', 'কর্মচারী পিন মালিকের মাস্টার পিনের মতো হতে পারবে না।');
    }
    updates[`shop_secrets/${t.shopCode}/staffPinHash`] = hashPin(pin);
  }
  await db.ref().update(updates);
  await getAuth().revokeRefreshTokens(`staff_${t.shopCode}_shared`).catch(() => {});
  await audit(t.shopCode, 'STAFF_PIN_SET', 'সাধারণ কর্মচারী পিন পরিবর্তন করা হয়েছে', t.name, t.role);
  return { ok: true };
}

// ---------- admin (dokan-admin panel) ----------
// The admin signs in with Firebase Auth e-mail/password; scripts/set-admin.js grants { admin: true }.

function requireAdmin(request) {
  if (request.auth?.token?.admin !== true) throw new HttpsError('permission-denied', 'শুধুমাত্র অ্যাডমিনের জন্য।');
  return request.auth.token;
}

function cleanNewPin(raw, label) {
  const pin = normalizePin(requireString(raw, label, 20));
  if (!isValidPinFormat(pin)) throw new HttpsError('invalid-argument', `${label} ৪-১২ সংখ্যার হতে হবে।`);
  if (isWeakPin(pin)) throw new HttpsError('invalid-argument', `${label} খুব সহজ। ১২৩৪, ০০০০ বা একই সংখ্যা বারবার ব্যবহার করবেন না।`);
  return pin;
}

async function revokeShopSessions(shopCode) {
  const staff = (await readVal(`shops/${shopCode}/staff`)) || {};
  const uids = [`owner_${shopCode}`, `staff_${shopCode}_shared`, ...Object.keys(staff).map((k) => `staff_${shopCode}_${k}`)];
  await Promise.all(uids.map((uid) => getAuth().revokeRefreshTokens(uid).catch(() => {})));
}

// Reset a shop's master and/or shared staff PIN (e.g. the owner forgot it).
export async function adminSetShopPin(request) {
  const admin = requireAdmin(request);
  const shopCode = sanitizeKey(requireString(request.data?.shopCode, 'দোকান কোড', 32).toUpperCase());
  const hasMaster = typeof request.data?.masterPin === 'string' && request.data.masterPin.trim() !== '';
  const hasStaff = typeof request.data?.staffPin === 'string' && request.data.staffPin.trim() !== '';
  if (!hasMaster && !hasStaff) throw new HttpsError('invalid-argument', 'অন্তত একটি পিন দিন।');
  if (!(await readVal(`shops/${shopCode}/info`))) throw new HttpsError('not-found', 'দোকানটি পাওয়া যায়নি।');

  const updates = {};
  let master = null;
  if (hasMaster) {
    master = cleanNewPin(request.data.masterPin, 'মাস্টার পিন');
    Object.assign(updates, {
      [`shop_secrets/${shopCode}/masterPinHash`]: hashPin(master),
      [`shop_secrets/${shopCode}/legacy`]: null,
      [`shops/${shopCode}/security/masterPin`]: null,
      [`shops/${shopCode}/security/masterPinHash`]: null,
      [`shops/${shopCode}/security/masterPinHashBn`]: null,
      [`shops/${shopCode}/info/pinCode`]: null,
    });
  }
  if (hasStaff) {
    const staff = cleanNewPin(request.data.staffPin, 'কর্মচারী পিন');
    if (master && staff === master) throw new HttpsError('invalid-argument', 'কর্মচারী পিন আর মাস্টার পিন আলাদা হতে হবে।');
    updates[`shop_secrets/${shopCode}/staffPinHash`] = hashPin(staff);
    updates[`shops/${shopCode}/security/staffPin`] = null;
  }
  updates[`shops/${shopCode}/security/updatedAt`] = ServerValue.TIMESTAMP;
  await db.ref().update(updates);
  await Promise.all([clearShopFailures(`${shopCode}__owner`), clearShopFailures(`${shopCode}__staff`)]);
  if (hasMaster) await getAuth().revokeRefreshTokens(`owner_${shopCode}`).catch(() => {});
  if (hasStaff) await getAuth().revokeRefreshTokens(`staff_${shopCode}_shared`).catch(() => {});
  await audit(shopCode, 'ADMIN_PIN_RESET', `অ্যাডমিন (${admin.email || admin.uid}) পিন রিসেট করেছেন`, 'অ্যাডমিন', 'admin');
  return { ok: true };
}

// Create a shop from the admin panel with a hashed master PIN.
export async function adminCreateShop(request) {
  const admin = requireAdmin(request);
  const shopCode = sanitizeKey(requireString(request.data?.shopCode, 'দোকান কোড', 32).toUpperCase());
  if (!SHOP_CODE_RE.test(shopCode)) throw new HttpsError('invalid-argument', 'দোকান কোড SHOP-XXXXXX ফরম্যাটে হতে হবে।');
  const master = cleanNewPin(request.data?.masterPin, 'মাস্টার পিন');
  const str = (v, max) => (typeof v === 'string' ? v.trim().slice(0, max) : '');
  const shopName = str(request.data?.shopName, 120);
  if (!shopName) throw new HttpsError('invalid-argument', 'দোকানের নাম লিখুন।');
  const ownerEmail = str(request.data?.ownerEmail, 120).toLowerCase();
  const licenseType = ['lifetime', 'trial'].includes(request.data?.licenseType) ? request.data.licenseType : 'lifetime';

  const [existingShop, existingSecret] = await Promise.all([
    db.ref(`shops/${shopCode}`).limitToFirst(1).get(),
    readVal(`shop_secrets/${shopCode}`),
  ]);
  if (existingShop.exists() || existingSecret) throw new HttpsError('already-exists', 'এই দোকান কোড আগে থেকেই আছে।');

  const now = Date.now();
  const updates = {
    [`shop_secrets/${shopCode}/masterPinHash`]: hashPin(master),
    [`shop_secrets/${shopCode}/createdAt`]: now,
    [`shops/${shopCode}/info`]: {
      shopCode,
      shopName,
      ownerEmail,
      shopPhone: str(request.data?.shopPhone, 40),
      shopAddress: str(request.data?.shopAddress, 300),
      createdAt: now,
    },
    [`shops/${shopCode}/status`]: {
      status: 'active',
      isSuspended: false,
      licenseType,
      plan: licenseType === 'trial' ? 'trial' : 'lifetime',
      expiresAt: licenseType === 'trial' ? now + 24 * 60 * 60 * 1000 : null,
      reason: '',
      createdAt: now,
    },
    [`shops/${shopCode}/features`]: {
      pos: true, receiptPrint: true, dueKhata: true, staffManagement: true, expenses: true, reports: true, inventoryEdit: true, updatedAt: now,
    },
  };
  if (ownerEmail) {
    const key = encodeEmailKey(ownerEmail);
    const mapped = await readVal(`email_to_shop/${key}`);
    if (mapped && mapped !== shopCode) throw new HttpsError('already-exists', `এই ইমেইল আগে থেকেই ${mapped} দোকানের সাথে যুক্ত।`);
    updates[`email_to_shop/${key}`] = shopCode;
  }
  await db.ref().update(updates);
  await audit(shopCode, 'SHOP_REGISTERED', `অ্যাডমিন (${admin.email || admin.uid}) দোকান তৈরি করেছেন`, 'অ্যাডমিন', 'admin');
  return { ok: true, shopCode };
}

// Permanently delete a shop, its secrets and e-mail links, and sign out every device.
export async function adminDeleteShop(request) {
  requireAdmin(request);
  const shopCode = sanitizeKey(requireString(request.data?.shopCode, 'দোকান কোড', 32).toUpperCase());
  if (request.data?.confirm !== shopCode) throw new HttpsError('failed-precondition', 'নিশ্চিত করতে দোকান কোড হুবহু লিখুন।');

  await revokeShopSessions(shopCode);
  const updates = { [`shops/${shopCode}`]: null, [`shop_secrets/${shopCode}`]: null };
  for (const index of ['email_to_shop', 'staff_to_shop']) {
    const snap = await db.ref(index).orderByValue().equalTo(shopCode).get();
    snap.forEach((c) => {
      updates[`${index}/${c.key}`] = null;
    });
  }
  await db.ref().update(updates);
  return { ok: true };
}

// Live support proxy (Telegram bot token stays on the server).
export { supportOpen, supportSend, supportFetch, telegramWebhook } from './support.js';
