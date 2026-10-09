import React, { useState } from 'react';
import { AlertTriangle, ShieldAlert, Lock, KeyRound, Store, Eye, EyeOff, LogOut } from 'lucide-react';
import { useShop } from '../context/ShopContext';

export function SplashScreen() {
  return (
    <div className="splash">
      <div className="sidebar-logo-icon" style={{ width: 48, height: 48, borderRadius: 14 }}>
        <Store size={24} />
      </div>
      <div style={{ fontSize: '0.9rem' }}>নিরাপদ সংযোগ স্থাপন করা হচ্ছে…</div>
    </div>
  );
}

function StatusScreen({ tone, icon: Icon, title, children, onLogout }) {
  return (
    <div className="status-screen">
      <div className="status-card">
        <div className={`status-icon ${tone}`}>
          <Icon size={30} />
        </div>
        <h2 className="status-title">{title}</h2>
        {children}
        {onLogout && (
          <button onClick={onLogout} className="btn btn-secondary" style={{ marginTop: 22 }}>
            <LogOut size={15} /> লগআউট
          </button>
        )}
      </div>
    </div>
  );
}

export function MaintenanceScreen({ message, onLogout }) {
  return (
    <StatusScreen tone="warning" icon={AlertTriangle} title="রক্ষণাবেক্ষণ কাজ চলছে" onLogout={onLogout}>
      <p className="status-text">
        {message || 'প্ল্যাটফর্মে জরুরি রক্ষণাবেক্ষণ কাজ চলছে। কিছুক্ষণের মধ্যে আবার সচল হবে।'}
      </p>
    </StatusScreen>
  );
}

export function SuspendedScreen({ shopCode, reason, onLogout }) {
  return (
    <StatusScreen tone="danger" icon={ShieldAlert} title="দোকানের এক্সেস স্থগিত" onLogout={onLogout}>
      <p className="status-text" style={{ marginBottom: 14 }}>
        দোকান কোড: <strong style={{ fontFamily: 'var(--font-mono)' }}>{shopCode}</strong>
      </p>
      <div className="alert alert-error" style={{ textAlign: 'left' }}>
        {reason || 'বিল বকেয়া বা সিস্টেম পলিসির কারণে এই দোকানের এক্সেস সাময়িকভাবে স্থগিত রয়েছে।'}
      </div>
      <p className="status-text" style={{ fontSize: '0.82rem' }}>পুনরায় চালু করতে সফটওয়্যার ডেভেলপারের সাথে যোগাযোগ করুন।</p>
    </StatusScreen>
  );
}

export function TrialExpiredScreen({ shopCode, onLogout }) {
  return (
    <StatusScreen tone="warning" icon={AlertTriangle} title="ট্রায়ালের মেয়াদ শেষ" onLogout={onLogout}>
      <p className="status-text" style={{ marginBottom: 14 }}>
        দোকান কোড: <strong style={{ fontFamily: 'var(--font-mono)' }}>{shopCode}</strong>
      </p>
      <div className="alert alert-warning" style={{ textAlign: 'left' }}>
        আপনার ফ্রি ট্রায়ালের সময়সীমা শেষ হয়েছে। Dokan Pro ব্যবহার চালিয়ে যেতে ডেভেলপারের কাছ থেকে ফুল লাইসেন্স নিন।
      </div>
    </StatusScreen>
  );
}

export function LockedFeatureCard({ title }) {
  return (
    <div className="page-wrapper">
      <div className="status-card" style={{ margin: '40px auto' }}>
        <div className="status-icon danger">
          <Lock size={28} />
        </div>
        <h3 className="status-title">{title} লক করা আছে</h3>
        <p className="status-text">
          এই ফিচারটি আপনার প্যাকেজে বর্তমানে বন্ধ রয়েছে। সক্রিয় বা আপগ্রেড করতে ডেভেলপারের সাথে যোগাযোগ করুন।
        </p>
      </div>
    </div>
  );
}

function PinInput({ value, onChange, placeholder, autoFocus, id }) {
  const [show, setShow] = useState(false);
  return (
    <div className="input-with-icon">
      <KeyRound size={16} />
      <input
        id={id}
        type={show ? 'text' : 'password'}
        inputMode="numeric"
        autoComplete="off"
        maxLength={12}
        className="input-field"
        style={{ fontFamily: 'var(--font-mono)', letterSpacing: show ? '0.1em' : '0.3em' }}
        value={value}
        onChange={(e) => onChange(e.target.value.replace(/[^0-9০-৯]/g, ''))}
        placeholder={placeholder}
        autoFocus={autoFocus}
      />
      <button type="button" className="input-action" onClick={() => setShow(!show)} aria-label={show ? 'পিন লুকান' : 'পিন দেখুন'}>
        {show ? <EyeOff size={16} /> : <Eye size={16} />}
      </button>
    </div>
  );
}

export { PinInput };

// Idle auto-lock overlay (owner sessions). Data stays loaded underneath but is blurred and inert.
export function LockScreen() {
  const { unlock, logout, auth, shopInfo } = useShop();
  const [pin, setPin] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    if (!pin) return;
    setBusy(true);
    setError('');
    try {
      const ok = await unlock(pin);
      if (!ok) setError('ভুল মাস্টার পিন।');
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
      setPin('');
    }
  };

  return (
    <div className="lock-overlay" role="dialog" aria-modal="true" aria-label="স্ক্রিন লক">
      <form className="status-card" onSubmit={submit}>
        <div className="status-icon primary">
          <Lock size={28} />
        </div>
        <h2 className="status-title">স্ক্রিন লক করা হয়েছে</h2>
        <p className="status-text" style={{ marginBottom: 18 }}>
          {shopInfo.shopName} • {auth?.userName}
          <br />
          নিষ্ক্রিয়তার কারণে লক হয়েছে। চালিয়ে যেতে মাস্টার পিন দিন।
        </p>
        {error && <div className="alert alert-error">{error}</div>}
        <PinInput value={pin} onChange={setPin} placeholder="মাস্টার পিন" autoFocus />
        <button className="btn btn-primary btn-lg btn-block" style={{ marginTop: 14 }} disabled={busy || !pin}>
          {busy ? 'যাচাই হচ্ছে…' : 'আনলক করুন'}
        </button>
        <button type="button" className="btn btn-ghost btn-sm" style={{ marginTop: 10 }} onClick={logout}>
          অন্য অ্যাকাউন্টে লগইন
        </button>
      </form>
    </div>
  );
}

// Shown when the server accepted a weak PIN via e-mail login: the session can do nothing until it's changed.
export function ForcePinChangeScreen() {
  const { updateMasterPin, logout } = useShop();
  const [oldPin, setOldPin] = useState('');
  const [newPin, setNewPin] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    if (newPin !== confirm) {
      setError('নতুন পিন দুটি মিলছে না।');
      return;
    }
    setBusy(true);
    try {
      await updateMasterPin(oldPin, newPin);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="status-screen">
      <form className="status-card" style={{ textAlign: 'left' }} onSubmit={submit}>
        <div style={{ textAlign: 'center' }}>
          <div className="status-icon warning">
            <KeyRound size={28} />
          </div>
          <h2 className="status-title">নতুন মাস্টার পিন সেট করুন</h2>
          <p className="status-text" style={{ marginBottom: 18 }}>
            আপনার বর্তমান পিনটি সহজে অনুমানযোগ্য। দোকানের তথ্য সুরক্ষিত রাখতে একটি শক্তিশালী পিন (৬ সংখ্যা বা বেশি সুপারিশকৃত) দিন।
          </p>
        </div>
        {error && <div className="alert alert-error">{error}</div>}
        <div className="input-group">
          <label className="input-label">বর্তমান পিন</label>
          <PinInput value={oldPin} onChange={setOldPin} placeholder="বর্তমান পিন" autoFocus />
        </div>
        <div className="input-group">
          <label className="input-label">নতুন পিন</label>
          <PinInput value={newPin} onChange={setNewPin} placeholder="৪-১২ সংখ্যা" />
        </div>
        <div className="input-group">
          <label className="input-label">নতুন পিন আবার দিন</label>
          <PinInput value={confirm} onChange={setConfirm} placeholder="নিশ্চিত করুন" />
        </div>
        <button className="btn btn-primary btn-lg btn-block" disabled={busy || !oldPin || !newPin}>
          {busy ? 'সংরক্ষণ হচ্ছে…' : 'পিন পরিবর্তন করে চালিয়ে যান'}
        </button>
        <button type="button" className="btn btn-ghost btn-sm btn-block" style={{ marginTop: 10 }} onClick={logout}>
          লগআউট
        </button>
      </form>
    </div>
  );
}
