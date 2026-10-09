import React, { useMemo, useState } from 'react';
import {
  ArrowLeft, ArrowRight, BadgeCheck, BriefcaseBusiness, Check, ChevronRight, CircleHelp,
  CloudDownload, Crown, Gift, KeyRound, LoaderCircle, LockKeyhole, Mail, MonitorSmartphone,
  PackagePlus, ShieldCheck, ShoppingBag, Store, UserRound, Wallet,
} from 'lucide-react';
import { useShop } from '../context/ShopContext';
import { activateDesktopLicense, getDesktopDeviceId, startDesktopTrial } from '../utils/activation';
import { PinInput } from '../components/StatusScreens';

const sampleProducts = [
  ['চাল', 'Rice', 58, 68, 40, 'কেজি'], ['সয়াবিন তেল', 'Soybean Oil', 165, 185, 24, 'লিটার'],
  ['মসুর ডাল', 'Lentils', 112, 128, 30, 'কেজি'], ['চিনি', 'Sugar', 118, 135, 25, 'কেজি'],
  ['আটা', 'Flour', 48, 58, 35, 'কেজি'], ['লবণ', 'Salt', 28, 35, 40, 'প্যাকেট'],
  ['ডিম', 'Egg', 11, 13, 60, 'পিস'], ['দুধ', 'Milk', 82, 95, 18, 'লিটার'],
  ['চা পাতা', 'Tea', 390, 450, 10, 'প্যাকেট'], ['বিস্কুট', 'Biscuits', 18, 25, 45, 'প্যাকেট'],
  ['সাবান', 'Soap', 38, 48, 20, 'পিস'], ['শ্যাম্পু', 'Shampoo', 8, 10, 75, 'পিস'],
  ['টুথপেস্ট', 'Toothpaste', 75, 90, 12, 'পিস'], ['নুডলস', 'Noodles', 14, 20, 40, 'প্যাকেট'],
  ['আলু', 'Potato', 32, 42, 50, 'কেজি'], ['পেঁয়াজ', 'Onion', 55, 68, 35, 'কেজি'],
  ['রসুন', 'Garlic', 190, 225, 12, 'কেজি'], ['মরিচ', 'Chili', 95, 115, 14, 'কেজি'],
  ['পানি', 'Water', 18, 25, 36, 'বোতল'], ['কফি', 'Coffee', 260, 310, 8, 'প্যাকেট'],
].map(([nameBn, nameEn, purchasePrice, salePrice, stockQty, unitName]) => ({ nameBn, nameEn, purchasePrice, salePrice, stockQty, unitName }));

function ActionCard({ icon: Icon, title, description, tone = 'green', onClick }) {
  return (
    <button type="button" className={`activation-action-card ${tone}`} onClick={onClick}>
      <span className="activation-action-icon"><Icon size={23} /></span>
      <span className="activation-action-copy"><strong>{title}</strong><small>{description}</small></span>
      <ChevronRight size={18} className="activation-chevron" />
    </button>
  );
}

function Field({ label, icon: Icon, ...props }) {
  return (
    <div className="input-group">
      <label className="input-label" htmlFor={props.id}>{label}</label>
      <div className="input-with-icon">
        {Icon && <Icon size={16} />}
        <input className="input-field" {...props} />
      </div>
    </div>
  );
}

function Stepper({ step }) {
  const steps = ['দোকানের তথ্য', 'পছন্দ', 'শুরু করুন'];
  return (
    <div className="activation-stepper" aria-label={`ধাপ ${step} / ৩`}>
      {steps.map((label, index) => {
        const number = index + 1;
        return (
          <React.Fragment key={label}>
            <div className={`activation-step ${number <= step ? 'active' : ''}`}>
              <span>{number < step ? <Check size={15} /> : number}</span><small>{label}</small>
            </div>
            {index < steps.length - 1 && <i className={number < step ? 'complete' : ''} />}
          </React.Fragment>
        );
      })}
    </div>
  );
}

export default function ActivationFlow() {
  const { login, createOwnerShop } = useShop();
  const [flow, setFlow] = useState('roles');
  const [ownerMode, setOwnerMode] = useState('license');
  const [step, setStep] = useState(1);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [identifier, setIdentifier] = useState('');
  const [pin, setPin] = useState('');
  const [staffName, setStaffName] = useState('');
  const [licenseEmail, setLicenseEmail] = useState(localStorage.getItem('dokan_desktop_license_email') || '');
  const [ownerEmail, setOwnerEmail] = useState(localStorage.getItem('dokan_desktop_license_email') || '');
  const [shopName, setShopName] = useState('');
  const [shopAddress, setShopAddress] = useState('');
  const [shopPhone, setShopPhone] = useState('');
  const [masterPin, setMasterPin] = useState('');
  const [staffPin, setStaffPin] = useState('');
  const [tagline, setTagline] = useState('');
  const [currencySymbol, setCurrencySymbol] = useState('৳');
  const [useBengaliNumerals, setUseBengaliNumerals] = useState(true);
  const [vatEnabled, setVatEnabled] = useState(false);
  const [vatPercentage, setVatPercentage] = useState('5');
  const [themeMode, setThemeMode] = useState('system');

  const shopCode = useMemo(() => {
    const compact = getDesktopDeviceId().replace(/[^a-z0-9]/gi, '').toUpperCase();
    return `SHOP-${compact.slice(-6) || 'DESK01'}`;
  }, []);

  const run = async (action) => {
    setBusy(true);
    setError('');
    try { await action(); }
    catch (err) { setError(err?.message || 'কাজটি সম্পন্ন করা যায়নি। আবার চেষ্টা করুন।'); }
    finally { setBusy(false); }
  };

  const openFlow = (next) => { setError(''); setFlow(next); };
  const backToRoles = () => { setError(''); setFlow('roles'); };
  const handleLicense = () => run(async () => {
    const result = await activateDesktopLicense(licenseEmail);
    setOwnerEmail(licenseEmail.trim().toLowerCase());
    setShopName(result.customer_name ? `${result.customer_name} স্টোর` : '');
    setFlow('setup'); setStep(1);
  });
  const handleTrial = () => run(async () => {
    await startDesktopTrial();
    setOwnerEmail(''); setFlow('setup'); setStep(1);
  });
  const handleLogin = (role) => run(async () => {
    await login(identifier.trim(), pin, role, { remember: true });
  });
  const validateAndContinue = () => {
    setError('');
    if (!shopName.trim()) return setError('দোকানের নাম লিখুন।');
    if (masterPin.length < 4 || masterPin.length > 12 || !/^\d+$/.test(masterPin)) return setError('মাস্টার পিন ৪-১২ সংখ্যার হতে হবে।');
    if (/^(\d)\1+$/.test(masterPin) || ['1234', '12345', '123456', '0000', '1111', '1212'].includes(masterPin)) return setError('সহজে অনুমান করা যায় এমন পিন ব্যবহার করবেন না।');
    if (staffPin && (staffPin.length < 4 || staffPin.length > 12 || !/^\d+$/.test(staffPin))) return setError('কর্মচারী পিন ৪-১২ সংখ্যার হতে হবে।');
    if (staffPin && staffPin === masterPin) return setError('কর্মচারী পিন ও মাস্টার পিন আলাদা দিন।');
    setStep(2);
  };
  const finishSetup = (withDemoProducts) => run(async () => {
    const profile = {
      shopName: shopName.trim(), shopAddress: shopAddress.trim(), shopPhone: shopPhone.trim(),
      ownerEmail: ownerEmail.trim(), tagline: tagline.trim(), currencySymbol, vatEnabled,
      vatPercentage: vatEnabled ? Number(vatPercentage) || 0 : 0, useBengaliNumerals, themeMode,
    };
    const expiry = Number(localStorage.getItem('dokan_desktop_trial_expires_at') || 0);
    if (expiry > 0) localStorage.setItem('dokan_desktop_trial_shop', shopCode);
    await createOwnerShop({
      shopCode, pin: masterPin, ownerEmail: ownerEmail.trim(), shopName: shopName.trim(), profile,
      demoProducts: withDemoProducts ? sampleProducts : [],
    });
  });

  const isNested = flow !== 'roles';
  return (
    <div className="auth-screen">
      <aside className="auth-brand-panel activation-brand-panel">
        <div className="activation-brand-lockup">
          <div className="activation-brand-mark"><Store size={24} /></div>
          <div><strong>Dokan Pro</strong><small>Business Suite</small></div>
        </div>
        <div className="activation-brand-content">
          <span className="activation-eyebrow"><ShieldCheck size={15} /> Webix Solution অফিসিয়াল সফটওয়্যার</span>
          <h1 className="auth-brand-title">আপনার দোকানের হিসাব, সুরক্ষিতভাবে শুরু করুন।</h1>
          <ul className="auth-feature-list">
            <li><BriefcaseBusiness size={18} /><span>দোকানের পণ্য, বিক্রয় ও বকেয়া এক জায়গায় পরিচালনা করুন</span></li>
            <li><ShieldCheck size={18} /><span>লাইসেন্স যাচাই ও PIN সুরক্ষিত লগইন</span></li>
            <li><MonitorSmartphone size={18} /><span>মোবাইল ও কম্পিউটারে একই দোকানের হিসাব ব্যবহার করুন</span></li>
          </ul>
        </div>
        <div className="activation-brand-footer">© {new Date().getFullYear()} Webix Solution</div>
      </aside>

      <main className="auth-form-panel activation-form-panel">
        <div className="auth-form activation-form">
          {isNested && (
            <button type="button" className="activation-back" onClick={flow === 'setup' ? () => setStep((n) => Math.max(1, n - 1)) : backToRoles}>
              <ArrowLeft size={16} /> {flow === 'setup' ? 'পূর্ববর্তী ধাপ' : 'পেছনে যান • ভূমিকা পরিবর্তন'}
            </button>
          )}
          {flow === 'roles' && <section className="activation-role-screen">
            <div className="activation-heading"><span className="activation-kicker">শুরু করুন</span><h2>আপনার ভূমিকা বেছে নিন</h2><p>মোবাইল অ্যাপের মতো ধাপে ধাপে অ্যাক্টিভেশন সম্পন্ন করুন।</p></div>
            <div className="activation-action-list">
              <ActionCard icon={Crown} title="আমি দোকান মালিক" description="নতুন লাইসেন্স চালু বা আগের দোকান ফিরিয়ে আনুন" onClick={() => { setOwnerMode('license'); openFlow('owner'); }} />
              <ActionCard icon={UserRound} title="আমি কর্মচারী" description="মালিকের দেওয়া দোকান কোড ও PIN দিয়ে প্রবেশ করুন" tone="blue" onClick={() => { setIdentifier(''); setPin(''); openFlow('staff'); }} />
            </div>
            <div className="activation-trial-banner">
              <span className="activation-trial-icon"><Gift size={20} /></span>
              <span><strong>২৪ ঘণ্টার ফ্রি ট্রায়াল</strong><small>কেনার আগে সব ফিচার ব্যবহার করে দেখুন</small></span>
              <button type="button" onClick={handleTrial} disabled={busy}>{busy ? <LoaderCircle className="spin" size={17} /> : 'ফ্রি ট্রায়াল'}</button>
            </div>
            <div className="activation-license-banner">
              <span className="activation-license-icon"><BadgeCheck size={21} /></span>
              <span><strong>Webix Solution অফিসিয়াল লাইসেন্স</strong><small>আজীবন লাইসেন্স কিনে সব ফিচার ব্যবহার করুন</small></span>
              <a href="https://webixsolution.store/product/dokan-pro" target="_blank" rel="noreferrer">লাইসেন্স নিন <ArrowRight size={14} /></a>
            </div>
            {error && <div className="alert alert-error"><CircleHelp size={16} /><span>{error}</span></div>}
          </section>}

          {flow === 'owner' && <>
            <div className="activation-heading"><span className="activation-kicker">মালিক • ধাপ ১</span><h2>দোকান শুরু করুন</h2><p>নতুন লাইসেন্স চালু করুন অথবা আগের দোকানের ক্লাউড ডাটা রিস্টোর করুন।</p></div>
            <div className="segmented activation-segmented" role="tablist" aria-label="মালিকের অপশন">
              <button type="button" role="tab" aria-selected={ownerMode === 'license'} className={ownerMode === 'license' ? 'active' : ''} onClick={() => { setOwnerMode('license'); setError(''); }}><KeyRound size={15} /> নতুন লাইসেন্স</button>
              <button type="button" role="tab" aria-selected={ownerMode === 'restore'} className={ownerMode === 'restore' ? 'active' : ''} onClick={() => { setOwnerMode('restore'); setError(''); }}><CloudDownload size={15} /> দোকান রিস্টোর</button>
            </div>
            {ownerMode === 'license' ? <form className="activation-panel-card" onSubmit={(e) => { e.preventDefault(); handleLicense(); }}>
              <div className="activation-panel-title"><span className="activation-mini-icon"><KeyRound size={19} /></span><span><strong>নতুন লাইসেন্স সক্রিয় করুন</strong><small>ক্রয়ের সময় ব্যবহৃত ইমেইল দিন</small></span></div>
              <Field id="license-email" label="নিবন্ধিত লাইসেন্স ইমেইল" icon={Mail} type="email" autoComplete="email" placeholder="yourname@gmail.com" value={licenseEmail} onChange={(e) => setLicenseEmail(e.target.value)} required />
              {error && <div className="alert alert-error"><CircleHelp size={16} /><span>{error}</span></div>}
              <button type="submit" className="btn btn-primary btn-lg btn-block" disabled={busy || !licenseEmail.trim()}>{busy ? <><LoaderCircle className="spin" size={17} /> যাচাই করা হচ্ছে…</> : <>লাইসেন্স সক্রিয় করে এগিয়ে যান <ArrowRight size={17} /></>}</button>
              <p className="activation-hint"><ShieldCheck size={15} /> লাইসেন্স যাচাই হলে দোকান সেটআপের পরের ধাপ খুলবে।</p>
            </form> : <form className="activation-panel-card" onSubmit={(e) => { e.preventDefault(); handleLogin('owner'); }}>
              <div className="activation-panel-title"><span className="activation-mini-icon"><CloudDownload size={19} /></span><span><strong>আগের দোকান ও ক্লাউড ডাটা রিস্টোর</strong><small>মাস্টার PIN দিয়ে নিরাপদে প্রবেশ করুন</small></span></div>
              <Field id="restore-identifier" label="দোকান কোড অথবা নিবন্ধিত ইমেইল" icon={Store} placeholder="SHOP-XXXXXX বা owner@email.com" value={identifier} onChange={(e) => setIdentifier(e.target.value)} autoComplete="username" required />
              <div className="input-group"><label className="input-label" htmlFor="restore-pin">মাস্টার সিকিউরিটি PIN</label><PinInput id="restore-pin" value={pin} onChange={setPin} placeholder="মাস্টার PIN" /></div>
              {error && <div className="alert alert-error"><CircleHelp size={16} /><span>{error}</span></div>}
              <button type="submit" className="btn btn-primary btn-lg btn-block" disabled={busy || !identifier.trim() || !pin}>{busy ? <><LoaderCircle className="spin" size={17} /> দোকানের তথ্য আনা হচ্ছে…</> : <>দোকানের তথ্য রিস্টোর করুন <ArrowRight size={17} /></>}</button>
              <p className="activation-hint"><LockKeyhole size={15} /> PIN যাচাই সফল হলে ক্লাউডের দোকান এই PC-তে সিঙ্ক হবে।</p>
            </form>}
            <div className="activation-inline-trial"><Gift size={16} /><span>এখনও লাইসেন্স নেই?</span><button type="button" onClick={handleTrial} disabled={busy}>২৪ ঘণ্টার ট্রায়াল নিন</button></div>
          </>}

          {flow === 'staff' && <>
            <div className="activation-heading"><span className="activation-kicker">কর্মচারী • নিরাপদ প্রবেশ</span><h2>কর্মচারী হিসেবে লগইন করুন</h2><p>মালিকের দেওয়া দোকান কোড ও আপনার PIN ব্যবহার করুন।</p></div>
            <form className="activation-panel-card" onSubmit={(e) => { e.preventDefault(); handleLogin('staff'); }}>
              <div className="activation-panel-title"><span className="activation-mini-icon blue"><UserRound size={19} /></span><span><strong>দোকানে যুক্ত হন</strong><small>আপনার তথ্য যাচাই করে সংযোগ করা হবে</small></span></div>
              <Field id="staff-identifier" label="দোকান কোড অথবা মালিকের ইমেইল" icon={Store} placeholder="SHOP-XXXXXX বা owner@email.com" value={identifier} onChange={(e) => setIdentifier(e.target.value)} autoComplete="username" required />
              <div className="input-group"><label className="input-label" htmlFor="staff-pin">কর্মচারী PIN</label><PinInput id="staff-pin" value={pin} onChange={setPin} placeholder="আপনার PIN" /></div>
              <Field id="staff-name" label="আপনার নাম (ঐচ্ছিক)" icon={UserRound} placeholder="যেমন: মোঃ করিম" value={staffName} onChange={(e) => setStaffName(e.target.value)} />
              {error && <div className="alert alert-error"><CircleHelp size={16} /><span>{error}</span></div>}
              <button type="submit" className="btn btn-primary btn-lg btn-block" disabled={busy || !identifier.trim() || !pin}>{busy ? <><LoaderCircle className="spin" size={17} /> যাচাই করা হচ্ছে…</> : <>কর্মচারী হিসেবে প্রবেশ করুন <ArrowRight size={17} /></>}</button>
            </form>
          </>}

          {flow === 'setup' && <>
            <div className="activation-heading"><span className="activation-kicker">নতুন দোকান • সেটআপ</span><h2>{step === 1 ? 'দোকানের তথ্য' : step === 2 ? 'আপনার পছন্দ' : 'শুরু করুন'}</h2><p>{step === 1 ? 'এই তথ্যগুলো আপনার দোকানের প্রোফাইলে থাকবে।' : step === 2 ? 'সংখ্যা, মুদ্রা ও থিম পছন্দ করুন।' : 'ডেমো পণ্য দিয়ে দেখুন, অথবা নিজের পণ্য যোগ করুন।'}</p></div>
            <Stepper step={step} />
            {step === 1 && <div className="activation-panel-card">
              <Field id="shop-name" label="দোকানের নাম *" icon={Store} placeholder="যেমন: মায়ের দোয়া স্টোর" value={shopName} onChange={(e) => setShopName(e.target.value)} required />
              <Field id="shop-address" label="দোকানের ঠিকানা" icon={Store} placeholder="বাজার রোড, ঢাকা" value={shopAddress} onChange={(e) => setShopAddress(e.target.value)} />
              <Field id="shop-phone" label="মোবাইল নম্বর" icon={Wallet} type="tel" placeholder="০১৭১১-০০০০০০" value={shopPhone} onChange={(e) => setShopPhone(e.target.value)} />
              <Field id="shop-email" label="মালিকের ইমেইল" icon={Mail} type="email" placeholder="yourname@gmail.com" value={ownerEmail} onChange={(e) => setOwnerEmail(e.target.value)} />
              <div className="input-group"><label className="input-label" htmlFor="master-pin">মাস্টার সিকিউরিটি PIN *</label><PinInput id="master-pin" value={masterPin} onChange={setMasterPin} placeholder="নিরাপদ ৪-১২ সংখ্যার PIN" /></div>
              <div className="input-group"><label className="input-label" htmlFor="setup-staff-pin">কর্মচারী PIN (ঐচ্ছিক)</label><PinInput id="setup-staff-pin" value={staffPin} onChange={setStaffPin} placeholder="খালি রাখলে বন্ধ থাকবে" /></div>
              <Field id="shop-tagline" label="স্লোগান / ট্যাগলাইন" icon={BadgeCheck} placeholder="আপনার বিশ্বস্ত মুদি দোকান" value={tagline} onChange={(e) => setTagline(e.target.value)} />
              {error && <div className="alert alert-error"><CircleHelp size={16} /><span>{error}</span></div>}
              <button type="button" className="btn btn-primary btn-lg btn-block" onClick={validateAndContinue}>পরবর্তী ধাপ <ArrowRight size={17} /></button>
            </div>}
            {step === 2 && <div className="activation-panel-card">
              <div className="input-group"><label className="input-label">সংখ্যার ধরন</label><div className="activation-choice-row"><button type="button" className={useBengaliNumerals ? 'selected' : ''} onClick={() => setUseBengaliNumerals(true)}>বাংলা সংখ্যা · ১২৩</button><button type="button" className={!useBengaliNumerals ? 'selected' : ''} onClick={() => setUseBengaliNumerals(false)}>ইংরেজি সংখ্যা · 123</button></div></div>
              <div className="input-group"><label className="input-label">মুদ্রা</label><div className="activation-choice-row activation-currency-row">{['৳', 'Tk', '$', 'Rs'].map((currency) => <button key={currency} type="button" className={currencySymbol === currency ? 'selected' : ''} onClick={() => setCurrencySymbol(currency)}>{currency}</button>)}</div></div>
              <label className="activation-toggle"><span><strong>ভ্যাট (VAT) সক্রিয় করুন</strong><small>চালানে স্বয়ংক্রিয়ভাবে ভ্যাট যুক্ত হবে</small></span><input type="checkbox" checked={vatEnabled} onChange={(e) => setVatEnabled(e.target.checked)} /></label>
              {vatEnabled && <Field id="vat-percent" label="ভ্যাটের হার (%)" type="number" min="0" max="100" value={vatPercentage} onChange={(e) => setVatPercentage(e.target.value)} />}
              <div className="input-group"><label className="input-label">থিম নির্বাচন</label><div className="activation-choice-row">{[['system', 'সিস্টেম'], ['light', 'লাইট'], ['dark', 'ডার্ক']].map(([mode, label]) => <button key={mode} type="button" className={themeMode === mode ? 'selected' : ''} onClick={() => setThemeMode(mode)}>{label}</button>)}</div></div>
              <div className="activation-step-actions"><button type="button" className="btn btn-secondary" onClick={() => setStep(1)}><ArrowLeft size={16} /> পূর্ববর্তী</button><button type="button" className="btn btn-primary" onClick={() => { setError(''); setStep(3); }}>পরবর্তী ধাপ <ArrowRight size={16} /></button></div>
            </div>}
            {step === 3 && <div className="activation-action-list">
              <div className="activation-setup-code"><span className="activation-mini-icon"><KeyRound size={18} /></span><span><small>আপনার দোকান কোড</small><strong>{shopCode}</strong></span><small>এই কোডটি নিরাপদে রাখুন—অন্য ডিভাইস থেকে রিস্টোরে লাগবে।</small></div>
              <ActionCard icon={PackagePlus} title="২০টি ডেমো পণ্য দিয়ে দেখি" description="নমুনা পণ্য দিয়ে POS ও স্টক পরীক্ষা করুন" onClick={() => finishSetup(true)} />
              <ActionCard icon={ShoppingBag} title="নিজের পণ্য যোগ করব" description="খালি দোকান তৈরি করে পণ্য নিজে যোগ করুন" tone="blue" onClick={() => finishSetup(false)} />
              {busy && <div className="activation-loading"><LoaderCircle className="spin" size={18} /> দোকান নিরাপদে তৈরি হচ্ছে…</div>}
              {error && <div className="alert alert-error"><CircleHelp size={16} /><span>{error}</span></div>}
              <button type="button" className="btn btn-secondary btn-block" onClick={() => setStep(2)} disabled={busy}><ArrowLeft size={16} /> পূর্ববর্তী ধাপ</button>
            </div>}
          </>}

          {isNested && flow !== 'setup' && <div className="activation-footer-link"><ShieldCheck size={15} /> PIN বা দোকান কোড না জানলে দোকানের মালিকের সঙ্গে যোগাযোগ করুন।</div>}
        </div>
      </main>
    </div>
  );
}
