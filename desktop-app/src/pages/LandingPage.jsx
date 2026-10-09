import React, { useEffect, useState } from 'react';
import {
  ArrowRight, BadgeCheck, BarChart3, BookOpenCheck, Boxes, CloudUpload, Download, LockKeyhole,
  Monitor, PlusSquare, ReceiptText, Share, ShieldCheck, ShoppingCart, Smartphone, Store, Users, Wifi,
} from 'lucide-react';
import '../landing.css';
import { openInstallPrompt } from '../components/InstallPrompt';

const RELEASES_URL = 'https://github.com/Kafihasan5/Dokan-Pro/releases/latest';
const ANDROID_APK_URL = 'https://github.com/Kafihasan5/Dokan-Pro/releases/latest/download/app-debug.apk';
const LICENSE_URL = 'https://webixsolution.store/product/dokan-pro';

const FEATURES = [
  { icon: ShoppingCart, title: 'দ্রুত বিক্রি (POS)', text: 'বারকোড বা নাম দিয়ে পণ্য খুঁজে কয়েক সেকেন্ডে বিক্রি, সাথে সাথে রসিদ প্রিন্ট।', tone: 'green' },
  { icon: Boxes, title: 'স্টক ও পণ্য', text: 'কোন পণ্য কতটা আছে, কোনটা ফুরিয়ে আসছে — সব সময় চোখের সামনে।', tone: 'blue' },
  { icon: BookOpenCheck, title: 'বাকি খাতা', text: 'কাস্টমারের বাকি, আদায় আর লেনদেনের পুরো হিসাব, কাগজের খাতা ছাড়াই।', tone: 'amber' },
  { icon: BarChart3, title: 'রিপোর্ট ও লাভ', text: 'আজ, এই সপ্তাহ বা এই মাসে কত বিক্রি, কত খরচ আর কত লাভ — এক নজরে।', tone: 'purple' },
  { icon: Users, title: 'কর্মচারী নিয়ন্ত্রণ', text: 'প্রত্যেক কর্মচারীর আলাদা পিন, কে কী করতে পারবে তা আপনিই ঠিক করুন।', tone: 'cyan' },
  { icon: CloudUpload, title: 'লাইভ ক্লাউড সিঙ্ক', text: 'মোবাইল, কম্পিউটার আর iPhone-এ একই হিসাব; ফোন হারালেও ডাটা নিরাপদ।', tone: 'green' },
];

const STEPS = [
  { n: '১', title: 'শুরু করুন', text: 'ফ্রি ট্রায়াল চালু করুন অথবা লাইসেন্স ইমেইল দিয়ে সক্রিয় করুন।' },
  { n: '২', title: 'দোকান সাজান', text: 'দোকানের নাম আর মাস্টার পিন দিন, চাইলে নমুনা পণ্য দিয়ে শুরু করুন।' },
  { n: '৩', title: 'বিক্রি শুরু', text: 'প্রথম বিক্রি থেকেই হিসাব জমা হতে থাকবে — সব জায়গা থেকে দেখা যাবে।' },
];

const isPhone = () => /Android/i.test(navigator.userAgent) || isIphone();

function isIphone() {
  return /iPhone|iPad|iPod/i.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
}

/** Phone mock-up drawn in HTML/CSS: a small dashboard so visitors see the app at a glance. */
function PhonePreview() {
  return (
    <div className="lp-phone" aria-hidden="true">
      <div className="lp-phone-notch" />
      <div className="lp-phone-screen">
        <div className="lp-mini-top">
          <span className="lp-mini-logo"><Store size={13} /></span>
          <span className="lp-mini-shop">রহিম স্টোর</span>
          <span className="lp-mini-live"><Wifi size={10} /> লাইভ</span>
        </div>
        <div className="lp-mini-hero">
          <small>আজকের বিক্রি</small>
          <strong>৳ ১২,৪৫০</strong>
          <span className="lp-mini-up">▲ ১৮% গতকালের চেয়ে বেশি</span>
        </div>
        <div className="lp-mini-stats">
          <div><small>লাভ</small><b>৳ ২,৩১০</b></div>
          <div><small>বাকি আদায়</small><b>৳ ১,২০০</b></div>
        </div>
        <div className="lp-mini-chart">
          {[38, 52, 44, 70, 58, 82, 66].map((h, i) => <i key={i} style={{ height: `${h}%` }} />)}
        </div>
        <div className="lp-mini-list">
          {[['চাল ৫ কেজি', '৳ ৩৪০'], ['সয়াবিন তেল', '৳ ১৮৫'], ['ডিম ১ ডজন', '৳ ১৫৬']].map(([a, b]) => (
            <div key={a}><span><ReceiptText size={11} /> {a}</span><b>{b}</b></div>
          ))}
        </div>
      </div>
    </div>
  );
}

export default function LandingPage({ onOpenApp }) {
  const [iphone] = useState(isIphone);

  useEffect(() => {
    const prev = document.title;
    document.title = 'Dokan Pro — দোকানের সম্পূর্ণ হিসাব এক অ্যাপে';
    return () => { document.title = prev; };
  }, []);

  const scrollTo = (id) => document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' });

  return (
    <div className="lp">
      <header className="lp-nav">
        <div className="lp-wrap lp-nav-inner">
          <a className="lp-brand" href="#top" onClick={(e) => { e.preventDefault(); window.scrollTo({ top: 0, behavior: 'smooth' }); }}>
            <span className="lp-brand-icon"><Store size={18} /></span>
            <span>Dokan Pro</span>
          </a>
          <nav className="lp-nav-links" aria-label="পেজের অংশ">
            <button type="button" onClick={() => scrollTo('features')}>ফিচার</button>
            <button type="button" onClick={() => scrollTo('devices')}>ডিভাইস</button>
            <button type="button" onClick={() => scrollTo('install')}>iPhone-এ ইনস্টল</button>
          </nav>
          <button type="button" className="lp-btn lp-btn-primary lp-btn-sm" onClick={onOpenApp}>অ্যাপ খুলুন</button>
        </div>
      </header>

      <main id="top">
        <section className="lp-hero">
          <div className="lp-hero-glow" aria-hidden="true" />
          <div className="lp-wrap lp-hero-grid">
            <div className="lp-hero-copy">
              <span className="lp-eyebrow"><BadgeCheck size={14} /> বাংলাদেশের দোকানের জন্য তৈরি</span>
              <h1>দোকানের পুরো হিসাব,<br /><span>এক অ্যাপে।</span></h1>
              <p className="lp-lead">বিক্রি, স্টক, বাকি খাতা আর লাভের হিসাব — খাতা-কলম ছাড়াই। মোবাইল, কম্পিউটার আর iPhone-এ একই দোকান, সব সময় আপডেট।</p>
              <div className="lp-cta-row">
                <button type="button" className="lp-btn lp-btn-primary" onClick={onOpenApp}>বিনামূল্যে শুরু করুন <ArrowRight size={18} /></button>
                <button type="button" className="lp-btn lp-btn-ghost" onClick={() => (isPhone() ? openInstallPrompt() : scrollTo('devices'))}>
                  {iphone ? <><Smartphone size={18} /> iPhone-এ ইনস্টল করুন</> : /Android/i.test(navigator.userAgent) ? <><Download size={18} /> ফোনে ইনস্টল করুন</> : <><Download size={18} /> ডাউনলোড ও ইনস্টল</>}
                </button>
              </div>
              <ul className="lp-hero-points">
                <li><ShieldCheck size={16} /> পিন-সুরক্ষিত</li>
                <li><CloudUpload size={16} /> ক্লাউড ব্যাকআপ</li>
                <li><Wifi size={16} /> ইন্টারনেট ছাড়াও বিক্রি (মোবাইল অ্যাপে)</li>
              </ul>
            </div>
            <div className="lp-hero-visual">
              <PhonePreview />
              <div className="lp-float lp-float-a"><ReceiptText size={16} /><span><b>রসিদ তৈরি</b><small>মাত্র ২ সেকেন্ডে</small></span></div>
              <div className="lp-float lp-float-b"><BookOpenCheck size={16} /><span><b>বাকি আদায় হয়েছে</b><small>করিম সাহেব • ৳ ৫০০</small></span></div>
            </div>
          </div>
        </section>

        <section id="features" className="lp-section">
          <div className="lp-wrap">
            <div className="lp-section-head">
              <span className="lp-kicker">ফিচার</span>
              <h2>দোকান চালাতে যা যা লাগে, সব এক জায়গায়</h2>
              <p>ছোট মুদি দোকান থেকে বড় শোরুম — প্রতিদিনের কাজ সহজ করতে বানানো।</p>
            </div>
            <div className="lp-features">
              {FEATURES.map(({ icon: Icon, title, text, tone }) => (
                <article key={title} className="lp-feature">
                  <span className={`lp-feature-icon lp-tone-${tone}`}><Icon size={22} /></span>
                  <h3>{title}</h3>
                  <p>{text}</p>
                </article>
              ))}
            </div>
          </div>
        </section>

        <section className="lp-section lp-section-alt">
          <div className="lp-wrap">
            <div className="lp-section-head">
              <span className="lp-kicker">কীভাবে শুরু করবেন</span>
              <h2>মাত্র তিন ধাপে দোকান চালু</h2>
            </div>
            <ol className="lp-steps">
              {STEPS.map((s) => (
                <li key={s.n} className="lp-step">
                  <span className="lp-step-n">{s.n}</span>
                  <h3>{s.title}</h3>
                  <p>{s.text}</p>
                </li>
              ))}
            </ol>
          </div>
        </section>

        <section id="devices" className="lp-section">
          <div className="lp-wrap">
            <div className="lp-section-head">
              <span className="lp-kicker">যেকোনো ডিভাইসে</span>
              <h2>একটি দোকান, সব ডিভাইসে একই হিসাব</h2>
              <p>দোকানে কম্পিউটার, পকেটে মোবাইল — যেখানেই থাকুন, হিসাব আপনার হাতে।</p>
            </div>
            <div className="lp-devices">
              <div className="lp-device">
                <span className="lp-device-icon lp-tone-green"><Smartphone size={24} /></span>
                <h3>Android</h3>
                <p>পুরো ফিচারসহ মোবাইল অ্যাপ, ইন্টারনেট ছাড়াও বিক্রি চলে।</p>
                <a className="lp-btn lp-btn-outline" href={ANDROID_APK_URL} rel="noreferrer"><Download size={16} /> APK ডাউনলোড</a>
              </div>
              <div className={`lp-device ${iphone ? 'is-current' : ''}`}>
                <span className="lp-device-icon lp-tone-blue"><Smartphone size={24} /></span>
                <h3>iPhone</h3>
                <p>কোনো ডাউনলোড লাগবে না — Safari থেকে Home Screen-এ যোগ করুন।</p>
                <button type="button" className="lp-btn lp-btn-outline" onClick={() => (iphone ? openInstallPrompt() : scrollTo('install'))}><PlusSquare size={16} /> {iphone ? 'Home Screen-এ যোগ করুন' : 'ইনস্টলের নিয়ম'}</button>
              </div>
              <div className="lp-device">
                <span className="lp-device-icon lp-tone-purple"><Monitor size={24} /></span>
                <h3>Windows ও Mac</h3>
                <p>বড় পর্দায় পিওএস, রিপোর্ট আর রসিদ প্রিন্ট — দোকানের কাউন্টারের জন্য।</p>
                <a className="lp-btn lp-btn-outline" href={RELEASES_URL} target="_blank" rel="noreferrer"><Download size={16} /> সফটওয়্যার ডাউনলোড</a>
              </div>
            </div>
          </div>
        </section>

        <section id="install" className="lp-section lp-section-alt">
          <div className="lp-wrap lp-install">
            <div className="lp-install-copy">
              <span className="lp-kicker">iPhone-এ ইনস্টল</span>
              <h2>৩০ সেকেন্ডে iPhone-এ Dokan Pro</h2>
              <p>App Store লাগবে না। একবার যোগ করলে Home Screen-এর আইকন থেকে সাধারণ অ্যাপের মতোই পুরো পর্দায় খুলবে।</p>
              {!iphone && <p className="lp-note">এই ধাপগুলো iPhone-এর <b>Safari</b> ব্রাউজারে করতে হবে।</p>}
            </div>
            <ol className="lp-install-steps">
              <li><span className="lp-install-n">১</span><span>iPhone-এ <b>Safari</b> দিয়ে এই পেজটি খুলুন।</span></li>
              <li><span className="lp-install-n">২</span><span>নিচের <b>Share</b> বাটনে চাপুন <Share size={16} className="lp-inline-icon" /></span></li>
              <li><span className="lp-install-n">৩</span><span><b>Add to Home Screen</b> বেছে নিন <PlusSquare size={16} className="lp-inline-icon" /></span></li>
              <li><span className="lp-install-n">৪</span><span>ডানে উপরে <b>Add</b> চাপুন — Home Screen-এ Dokan Pro চলে আসবে।</span></li>
            </ol>
          </div>
        </section>

        <section className="lp-section">
          <div className="lp-wrap lp-security">
            <div>
              <span className="lp-kicker">নিরাপত্তা</span>
              <h2>আপনার হিসাব শুধু আপনার</h2>
              <p>প্রতিটি দোকানের ডাটা আলাদা ও সুরক্ষিত। পিন ছাড়া কেউ দোকানে ঢুকতে পারে না, বারবার ভুল পিন দিলে লক হয়ে যায়।</p>
            </div>
            <ul className="lp-security-list">
              <li><LockKeyhole size={18} /><span><b>পিন-সুরক্ষিত লগইন</b><small>মালিক ও প্রত্যেক কর্মচারীর আলাদা পিন</small></span></li>
              <li><ShieldCheck size={18} /><span><b>এনক্রিপ্টেড সংযোগ</b><small>সব ডাটা নিরাপদ পথে আসা-যাওয়া করে</small></span></li>
              <li><CloudUpload size={18} /><span><b>স্বয়ংক্রিয় ক্লাউড ব্যাকআপ</b><small>ফোন হারালেও হিসাব হারাবে না</small></span></li>
            </ul>
          </div>
        </section>

        <section className="lp-final">
          <div className="lp-wrap lp-final-inner">
            <h2>আজই দোকানের হিসাব ডিজিটাল করুন</h2>
            <p>ফ্রি ট্রায়ালে সব ফিচার ব্যবহার করে দেখুন — কোনো কার্ড লাগবে না।</p>
            <div className="lp-cta-row lp-cta-center">
              <button type="button" className="lp-btn lp-btn-light" onClick={onOpenApp}>বিনামূল্যে শুরু করুন <ArrowRight size={18} /></button>
              <a className="lp-btn lp-btn-glass" href={LICENSE_URL} target="_blank" rel="noreferrer"><BadgeCheck size={18} /> লাইসেন্স নিন</a>
            </div>
          </div>
        </section>
      </main>

      <footer className="lp-footer">
        <div className="lp-wrap lp-footer-inner">
          <span className="lp-brand lp-brand-muted"><span className="lp-brand-icon"><Store size={15} /></span>Dokan Pro</span>
          <span>© {new Date().getFullYear()} Webix Solution</span>
        </div>
      </footer>
    </div>
  );
}
