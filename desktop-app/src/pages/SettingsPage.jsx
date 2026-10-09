import React, { useEffect, useState } from 'react';
import { useShop } from '../context/ShopContext';
import { Save, Store, Percent, ShieldCheck, Users, History, KeyRound, Info } from 'lucide-react';
import { notify } from '../components/Feedback';
import { PinInput } from '../components/StatusScreens';
import { formatDateTime } from '../utils/formatters';

const TABS = [
  { id: 'shop', label: 'দোকানের তথ্য', icon: Store },
  { id: 'security', label: 'নিরাপত্তা', icon: ShieldCheck },
  { id: 'permissions', label: 'কর্মচারী অনুমতি', icon: Users },
  { id: 'audit', label: 'অডিট লগ', icon: History },
];

function shopForm(info) {
  return {
    shopName: info.shopName || 'Dokan Pro',
    shopAddress: info.shopAddress || '',
    shopPhone: info.shopPhone || '',
    tagline: info.tagline || '',
    vatPercentage: info.vatPercentage ?? 5,
    vatEnabled: Boolean(info.vatEnabled),
    currencySymbol: info.currencySymbol || '৳',
  };
}

function ShopTab() {
  const { shopInfo, updateShopSettings, auth } = useShop();
  const [formData, setFormData] = useState(() => shopForm(shopInfo));
  const [dirty, setDirty] = useState(false);
  const [isSaving, setIsSaving] = useState(false);

  // Follow live updates (e.g. from the mobile app) until the user starts editing.
  useEffect(() => {
    if (!dirty) setFormData(shopForm(shopInfo));
  }, [shopInfo, dirty]);

  const setField = (k, v) => {
    setDirty(true);
    setFormData((f) => ({ ...f, [k]: v }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    try {
      setIsSaving(true);
      await updateShopSettings({ ...formData, vatPercentage: Number(formData.vatPercentage) });
      setDirty(false);
      notify('দোকানের সেটিংস সংরক্ষিত ও ক্লাউডে সিঙ্ক হয়েছে।', 'success');
    } catch (err) {
      notify('সেটিংস সংরক্ষণে সমস্যা: ' + err.message);
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <form onSubmit={handleSubmit}>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: 20 }}>
        <div className="card">
          <h3 className="card-title" style={{ marginBottom: 18 }}>
            <Store size={18} color="var(--primary)" /> দোকানের বিবরণ
          </h3>
          <div className="input-group">
            <label className="input-label">দোকানের নাম*</label>
            <input className="input-field" maxLength={120} value={formData.shopName} onChange={(e) => setField('shopName', e.target.value)} required />
          </div>
          <div className="input-group">
            <label className="input-label">ট্যাগলাইন / স্লোগান</label>
            <input className="input-field" maxLength={200} placeholder="যেমন: সেরা মানের বিশ্বস্ত প্রতিষ্ঠান" value={formData.tagline} onChange={(e) => setField('tagline', e.target.value)} />
          </div>
          <div className="input-group">
            <label className="input-label">দোকানের ঠিকানা</label>
            <input className="input-field" maxLength={300} placeholder="যেমন: দোকান নং ১২, নিউ মার্কেট, ঢাকা" value={formData.shopAddress} onChange={(e) => setField('shopAddress', e.target.value)} />
          </div>
          <div className="input-group" style={{ marginBottom: 0 }}>
            <label className="input-label">মোবাইল / যোগাযোগের নম্বর</label>
            <input type="tel" className="input-field" maxLength={40} placeholder="017XXXXXXXX" value={formData.shopPhone} onChange={(e) => setField('shopPhone', e.target.value)} />
          </div>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
          <div className="card">
            <h3 className="card-title" style={{ marginBottom: 18 }}>
              <Percent size={18} color="var(--accent-cyan)" /> ভ্যাট ও মুদ্রা
            </h3>
            <label className="checkbox-row" style={{ marginBottom: 16, color: 'var(--text-primary)', fontWeight: 600 }}>
              <input type="checkbox" checked={formData.vatEnabled} onChange={(e) => setField('vatEnabled', e.target.checked)} />
              বিক্রয়ে ভ্যাট (VAT) স্বয়ংক্রিয়ভাবে যোগ করুন
            </label>
            {formData.vatEnabled && (
              <div className="input-group">
                <label className="input-label">ভ্যাট হার (%)</label>
                <input type="number" step="0.1" min="0" max="100" className="input-field" value={formData.vatPercentage} onChange={(e) => setField('vatPercentage', e.target.value)} />
              </div>
            )}
            <div className="input-group" style={{ marginBottom: 0 }}>
              <label className="input-label">মুদ্রার প্রতীক</label>
              <input className="input-field" maxLength={8} value={formData.currencySymbol} onChange={(e) => setField('currencySymbol', e.target.value)} />
            </div>
          </div>

          <div className="card" style={{ background: 'var(--bg-sunken)' }}>
            <h4 style={{ fontSize: '0.9rem', fontWeight: 700, marginBottom: 8 }}>অ্যাকাউন্ট</h4>
            <div style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', display: 'grid', gap: 4 }}>
              <div>
                দোকান কোড: <strong style={{ fontFamily: 'var(--font-mono)', color: 'var(--text-primary)' }}>{auth?.shopCode}</strong>
              </div>
              <div>
                ভূমিকা: <strong style={{ color: 'var(--text-primary)' }}>মালিক (Owner)</strong>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div style={{ marginTop: 20, display: 'flex', justifyContent: 'flex-end', gap: 10 }}>
        {dirty && (
          <button type="button" className="btn btn-ghost" onClick={() => setDirty(false)}>
            পরিবর্তন বাতিল
          </button>
        )}
        <button type="submit" disabled={isSaving || !dirty} className="btn btn-primary btn-lg">
          <Save size={17} />
          {isSaving ? 'সংরক্ষণ হচ্ছে…' : 'সেটিংস সংরক্ষণ করুন'}
        </button>
      </div>
    </form>
  );
}

function SecurityTab() {
  const { updateMasterPin, secureMode } = useShop();
  const [oldPin, setOldPin] = useState('');
  const [newPin, setNewPin] = useState('');
  const [confirmPin, setConfirmPin] = useState('');
  const [busy, setBusy] = useState(false);


  const changePin = async (e) => {
    e.preventDefault();
    if (newPin !== confirmPin) {
      notify('নতুন পিন দুটি মিলছে না।', 'error');
      return;
    }
    setBusy(true);
    try {
      await updateMasterPin(oldPin, newPin);
      setOldPin('');
      setNewPin('');
      setConfirmPin('');
      notify('মাস্টার পিন সফলভাবে পরিবর্তন হয়েছে। অন্য ডিভাইসের সেশন বাতিল করা হয়েছে।', 'success');
    } catch (err) {
      notify(err.message, 'error');
    } finally {
      setBusy(false);
    }
  };


  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: 20, alignItems: 'start' }}>
      <form className="card" onSubmit={changePin}>
        <h3 className="card-title" style={{ marginBottom: 6 }}>
          <KeyRound size={18} color="var(--primary)" /> মাস্টার পিন পরিবর্তন
        </h3>
        <p style={{ fontSize: '0.84rem', color: 'var(--text-secondary)', marginBottom: 18 }}>
          কমপক্ষে ৬ সংখ্যার পিন ব্যবহার করুন। ১২৩৪, ০০০০ বা জন্মসাল এড়িয়ে চলুন।
        </p>
        <div className="input-group">
          <label className="input-label">বর্তমান পিন</label>
          <PinInput value={oldPin} onChange={setOldPin} placeholder="বর্তমান পিন" />
        </div>
        <div className="input-group">
          <label className="input-label">নতুন পিন</label>
          <PinInput value={newPin} onChange={setNewPin} placeholder="৪-১২ সংখ্যা" />
        </div>
        <div className="input-group">
          <label className="input-label">নতুন পিন আবার দিন</label>
          <PinInput value={confirmPin} onChange={setConfirmPin} placeholder="নিশ্চিত করুন" />
        </div>
        <button className="btn btn-primary" disabled={busy || !oldPin || !newPin || !confirmPin}>
          {busy ? 'পরিবর্তন হচ্ছে…' : 'পিন পরিবর্তন করুন'}
        </button>
      </form>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
        <div className={`alert ${secureMode ? 'alert-info' : 'alert-warning'}`} style={{ marginBottom: 0 }}>
          <Info size={16} />
          <span>
            {secureMode
              ? 'সুরক্ষিত মোড চালু: পিন সার্ভারে এনক্রিপ্ট (scrypt) করে রাখা হয়, ভুল পিনে অ্যাকাউন্ট স্বয়ংক্রিয়ভাবে লক হয় এবং ডাটাবেস নিয়ম দিয়ে সুরক্ষিত।'
              : 'সার্ভার-নিরাপত্তা এখনো চালু হয়নি। সম্পূর্ণ সুরক্ষার জন্য ডেভেলপারকে firebase/ ফোল্ডারের নিরাপত্তা আপডেট ডিপ্লয় করতে বলুন।'}
          </span>
        </div>
      </div>
    </div>
  );
}

const PERMISSION_ITEMS = [
  { key: 'allowStaffDiscount', label: 'ছাড় দিতে পারবে', desc: 'পিওএস-এ বিক্রয়ে ডিসকাউন্ট দেওয়ার অনুমতি' },
  { key: 'allowStaffDuePayment', label: 'বাকি জমা নিতে পারবে', desc: 'কাস্টমারের বকেয়া টাকা গ্রহণ' },
  { key: 'allowStaffEditProducts', label: 'পণ্য সম্পাদনা', desc: 'পণ্য যোগ, দাম পরিবর্তন ও মুছে ফেলা' },
  { key: 'allowStaffDeleteSale', label: 'বিক্রয় মুছতে পারবে', desc: 'বিক্রয় খাতা থেকে রেকর্ড মোছা' },
  { key: 'allowStaffViewExpenses', label: 'খরচ দেখতে পারবে', desc: 'খরচের খাতা পড়ার অনুমতি' },
  { key: 'allowStaffViewProfit', label: 'লাভ দেখতে পারবে', desc: 'ড্যাশবোর্ডে লাভের হিসাব' },
  { key: 'allowStaffViewCostPrice', label: 'ক্রয়মূল্য দেখতে পারবে', desc: 'পণ্যের কেনা দাম দেখা' },
];

function PermissionsTab() {
  const { permissions, updatePermissions } = useShop();

  const toggle = async (key, value) => {
    try {
      await updatePermissions({ [key]: value });
    } catch (err) {
      notify(err.message, 'error');
    }
  };

  return (
    <div style={{ display: 'grid', gap: 12, maxWidth: 820 }}>
      {PERMISSION_ITEMS.map((p) => (
        <div key={p.key} className="perm-card">
          <div>
            <div style={{ fontWeight: 600, fontSize: '0.92rem' }}>{p.label}</div>
            <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>{p.desc}</div>
          </div>
          <label className="switch">
            <input type="checkbox" checked={Boolean(permissions[p.key])} onChange={(e) => toggle(p.key, e.target.checked)} aria-label={p.label} />
            <span className="slider" />
          </label>
        </div>
      ))}
      <div className="perm-card">
        <div>
          <div style={{ fontWeight: 600, fontSize: '0.92rem' }}>সর্বোচ্চ ছাড় (%)</div>
          <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>কর্মচারী এক বিক্রয়ে সর্বোচ্চ কত শতাংশ ছাড় দিতে পারবে</div>
        </div>
        <select
          className="input-field"
          style={{ width: 110 }}
          value={permissions.maxStaffDiscountPercent ?? 10}
          onChange={(e) => toggle('maxStaffDiscountPercent', Number(e.target.value))}
        >
          {[0, 2, 5, 10, 15, 20, 30, 50].map((v) => (
            <option key={v} value={v}>
              {v}%
            </option>
          ))}
        </select>
      </div>
    </div>
  );
}

const ACTION_LABELS = {
  LOGIN_SUCCESS: ['সফল লগইন', 'badge-success'],
  LOGIN_FAILED: ['ব্যর্থ লগইন', 'badge-danger'],
  ADMIN_AUTH_FAILED: ['ভুল পিন', 'badge-danger'],
  ADMIN_AUTH_SUCCESS: ['পিন যাচাই', 'badge-success'],
  MASTER_PIN_CHANGED: ['পিন পরিবর্তন', 'badge-warning'],
  DANGER_ZONE_CLEAR_SALES: ['বিক্রয় মোছা', 'badge-danger'],
  FULL_DATABASE_RESTORED: ['রিস্টোর', 'badge-warning'],
  STAFF_REMOVED: ['কর্মচারী মোছা', 'badge-warning'],
};

function AuditTab() {
  const { auditLogs } = useShop();
  const [filter, setFilter] = useState('all');
  const list = auditLogs.filter((l) => filter === 'all' || (filter === 'security' && /LOGIN|AUTH|PIN|DANGER|RESTORE|STAFF/.test(l.action)));

  return (
    <div className="card" style={{ padding: 0 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '16px 20px', borderBottom: '1px solid var(--border-subtle)', gap: 10, flexWrap: 'wrap' }}>
        <div>
          <h3 className="card-title">
            <History size={18} color="var(--primary)" /> কার্যক্রমের ইতিহাস
          </h3>
          <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>সর্বশেষ {auditLogs.length}টি ঘটনা</div>
        </div>
        <div className="segmented" style={{ margin: 0, width: 240 }}>
          <button className={filter === 'all' ? 'active' : ''} onClick={() => setFilter('all')}>
            সব
          </button>
          <button className={filter === 'security' ? 'active' : ''} onClick={() => setFilter('security')}>
            নিরাপত্তা
          </button>
        </div>
      </div>
      <div className="audit-list" style={{ padding: '4px 20px 12px', maxHeight: '62vh', overflowY: 'auto' }}>
        {list.length === 0 && <div className="empty-state">কোনো রেকর্ড নেই</div>}
        {list.map((l) => {
          const [label, cls] = ACTION_LABELS[l.action] || [l.action, ''];
          return (
            <div key={l.id} className="audit-row">
              <span className="audit-time">{formatDateTime(l.timestamp)}</span>
              <span>
                <span className={`badge ${cls}`} style={{ marginRight: 8 }}>
                  {label}
                </span>
                <span style={{ color: 'var(--text-secondary)' }}>{l.details}</span>
              </span>
              <span style={{ color: 'var(--text-muted)', fontSize: '0.78rem', whiteSpace: 'nowrap' }}>{l.actor}</span>
            </div>
          );
        })}
      </div>
    </div>
  );
}

export default function SettingsPage() {
  const [tab, setTab] = useState('shop');
  return (
    <div className="page-wrapper">
      <div className="page-header">
        <div>
          <h1 className="page-title">সেটিংস ও নিরাপত্তা</h1>
          <p className="page-subtitle">দোকানের তথ্য, পিন, কর্মচারীর অনুমতি এবং কার্যক্রমের ইতিহাস</p>
        </div>
      </div>

      <div className="tabs" role="tablist">
        {TABS.map((t) => {
          const Icon = t.icon;
          return (
            <button key={t.id} role="tab" aria-selected={tab === t.id} className={`tab-btn ${tab === t.id ? 'active' : ''}`} onClick={() => setTab(t.id)}>
              <Icon size={16} /> {t.label}
            </button>
          );
        })}
      </div>

      {tab === 'shop' && <ShopTab />}
      {tab === 'security' && <SecurityTab />}
      {tab === 'permissions' && <PermissionsTab />}
      {tab === 'audit' && <AuditTab />}
    </div>
  );
}
