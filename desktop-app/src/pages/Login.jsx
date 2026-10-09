import React, { useState } from 'react';
import { useShop } from '../context/ShopContext';
import { Store, ArrowRight, ShieldCheck, AlertCircle, User, Crown, Mail, Zap, Smartphone, Lock, Loader2 } from 'lucide-react';
import { PinInput } from '../components/StatusScreens';


export default function Login() {
  const { login, secureMode } = useShop();
  const [role, setRole] = useState('owner');
  const [identifier, setIdentifier] = useState('');
  const [pin, setPin] = useState('');
  const [remember, setRemember] = useState(true);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    if (!identifier.trim()) {
      setError(role === 'owner' ? 'দোকান কোড অথবা নিবন্ধিত ইমেইল লিখুন।' : 'মালিকের দেওয়া দোকান কোড লিখুন।');
      return;
    }
    if (!pin.trim()) {
      setError(role === 'owner' ? 'মাস্টার পিন লিখুন।' : 'আপনার কর্মচারী পিন লিখুন।');
      return;
    }
    try {
      setLoading(true);
      await login(identifier, pin, role, { remember });
    } catch (err) {
      setError(err.message || 'লগইন ব্যর্থ হয়েছে। তথ্য যাচাই করুন।');
      setPin('');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-screen">
      <aside className="auth-brand-panel">
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <div
            style={{
              width: 42,
              height: 42,
              borderRadius: 12,
              background: 'rgba(255,255,255,0.14)',
              border: '1px solid rgba(255,255,255,0.22)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <Store size={22} color="#fff" />
          </div>
          <div>
            <div style={{ fontWeight: 800, fontSize: '1.1rem', color: '#fff' }}>Dokan Pro</div>
            <div style={{ fontSize: '0.72rem', letterSpacing: '0.08em', textTransform: 'uppercase', color: 'rgba(236,253,245,0.7)' }}>
              Business Suite
            </div>
          </div>
        </div>

        <div>
          <h1 className="auth-brand-title">আপনার দোকানের পুরো হিসাব, এক জায়গায় — নিরাপদে।</h1>
          <ul className="auth-feature-list">
            <li>
              <Zap size={18} />
              <span>পিওএস, স্টক, বাকি খাতা ও রিপোর্ট — মোবাইল অ্যাপের সাথে রিয়েল-টাইম সিঙ্ক</span>
            </li>
            <li>
              <ShieldCheck size={18} />
              <span>সার্ভার-যাচাইকৃত লগইন, ভুল পিনে স্বয়ংক্রিয় লক ও সম্পূর্ণ অডিট লগ</span>
            </li>
            <li>
              <Smartphone size={18} />
              <span>কম্পিউটার, ট্যাবলেট ও মোবাইল — যেকোনো স্ক্রিনে ব্যবহারযোগ্য</span>
            </li>
          </ul>
        </div>

        <div style={{ fontSize: '0.78rem', color: 'rgba(236,253,245,0.6)' }}>© {new Date().getFullYear()} Dokan Pro</div>
      </aside>

      <main className="auth-form-panel">
        <form className="auth-form" onSubmit={handleSubmit} noValidate>
          <div className="show-mobile" style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 28 }}>
            <div className="sidebar-logo-icon">
              <Store size={20} />
            </div>
            <div className="sidebar-brand-name">Dokan Pro</div>
          </div>

          <h2 style={{ fontSize: '1.6rem', fontWeight: 700, marginBottom: 6 }}>স্বাগতম 👋</h2>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.92rem', marginBottom: 26 }}>
            আপনার দোকানের অ্যাকাউন্টে লগইন করুন
          </p>

          <div className="segmented" role="tablist" aria-label="লগইনের ধরন">
            <button
              type="button"
              role="tab"
              aria-selected={role === 'owner'}
              className={role === 'owner' ? 'active' : ''}
              onClick={() => {
                setRole('owner');
                setError(null);
              }}
            >
              <Crown size={15} /> মালিক
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={role === 'staff'}
              className={role === 'staff' ? 'active' : ''}
              onClick={() => {
                setRole('staff');
                setError(null);
              }}
            >
              <User size={15} /> কর্মচারী
            </button>
          </div>

          {error && (
            <div className="alert alert-error" role="alert">
              <AlertCircle size={16} />
              <span>{error}</span>
            </div>
          )}

          <div className="input-group">
            <label className="input-label" htmlFor="login-id">
              {role === 'owner' ? 'দোকান কোড অথবা ইমেইল' : 'দোকান কোড অথবা আপনার ইমেইল'}
            </label>
            <div className="input-with-icon">
              {identifier.includes('@') ? <Mail size={16} /> : <Store size={16} />}
              <input
                id="login-id"
                type="text"
                className="input-field"
                placeholder="SHOP-XXXXXX বা you@email.com"
                value={identifier}
                onChange={(e) => setIdentifier(e.target.value)}
                autoComplete="username"
                autoCapitalize="characters"
                spellCheck={false}
                maxLength={120}
                autoFocus
              />
            </div>
          </div>

          <div className="input-group">
            <label className="input-label" htmlFor="login-pin">
              {role === 'owner' ? 'মাস্টার পিন' : 'কর্মচারী পিন'}
            </label>
            <PinInput id="login-pin" value={pin} onChange={setPin} placeholder="••••" />
          </div>

          {secureMode && (
            <label className="checkbox-row" style={{ marginBottom: 18 }}>
              <input type="checkbox" checked={remember} onChange={(e) => setRemember(e.target.checked)} />
              এই ডিভাইসে লগইন মনে রাখুন
            </label>
          )}

          <button type="submit" className="btn btn-primary btn-lg btn-block" disabled={loading} style={{ marginTop: 6 }}>
            {loading ? (
              <>
                <Loader2 size={18} className="spin" /> যাচাই করা হচ্ছে…
              </>
            ) : (
              <>
                লগইন করুন <ArrowRight size={18} />
              </>
            )}
          </button>

          <div
            style={{
              marginTop: 26,
              paddingTop: 18,
              borderTop: '1px solid var(--border-subtle)',
              display: 'flex',
              alignItems: 'flex-start',
              gap: 10,
              fontSize: '0.8rem',
              color: 'var(--text-muted)',
              lineHeight: 1.55,
            }}
          >
            <Lock size={15} style={{ flexShrink: 0, marginTop: 2 }} />
            <span>
              {role === 'owner'
                ? 'পিন ভুলে গেলে মোবাইল অ্যাপ থেকে রিসেট করুন অথবা ডেভেলপারের সাথে যোগাযোগ করুন। পিন কখনো কারো সাথে শেয়ার করবেন না।'
                : 'পিন না জানলে দোকানের মালিকের কাছ থেকে নিন। একাধিকবার ভুল পিন দিলে অ্যাকাউন্ট সাময়িকভাবে লক হয়ে যাবে।'}
            </span>
          </div>
        </form>
      </main>
    </div>
  );
}
