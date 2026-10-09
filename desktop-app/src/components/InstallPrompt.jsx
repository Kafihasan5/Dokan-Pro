import React, { useEffect, useState } from 'react';
import { ArrowDown, Check, Download, MoreHorizontal, PlusSquare, Share, Smartphone, X } from 'lucide-react';

const DISMISS_KEY = 'dokan_install_dismissed_until';
const DISMISS_DAYS = 3;

const isIos = () =>
  /iPhone|iPad|iPod/i.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
const isStandalone = () => window.matchMedia?.('(display-mode: standalone)').matches || navigator.standalone === true;
const isPhoneOrTablet = () => isIos() || /Android/i.test(navigator.userAgent);

function dismissedRecently() {
  try {
    return Number(localStorage.getItem(DISMISS_KEY) || 0) > Date.now();
  } catch {
    return false;
  }
}

/** Opens the install sheet from anywhere (e.g. a button on the landing page). */
export function openInstallPrompt() {
  window.dispatchEvent(new Event('dokan:install'));
}

/**
 * "Add to Home Screen" sheet for the web version.
 * Android/Chrome: one tap installs (the browser's own install prompt).
 * iPhone: Apple allows no programmatic install, so the sheet shows exactly which buttons to tap.
 */
export default function InstallPrompt() {
  const [open, setOpen] = useState(false);
  const [deferred, setDeferred] = useState(() => window.__dokanInstallEvent || null);
  const [installed, setInstalled] = useState(false);
  const ios = isIos();

  useEffect(() => {
    if (isStandalone() || /Electron/i.test(navigator.userAgent)) return undefined;

    const onReady = () => {
      setDeferred(window.__dokanInstallEvent);
      if (!dismissedRecently() && isPhoneOrTablet()) setOpen(true);
    };
    const onManual = () => setOpen(true);
    const onInstalled = () => {
      setInstalled(true);
      setDeferred(null);
      setTimeout(() => setOpen(false), 1800);
    };
    window.addEventListener('dokan:installready', onReady);
    window.addEventListener('dokan:install', onManual);
    window.addEventListener('appinstalled', onInstalled);

    // iPhone never fires an install event, so offer the guide after a short pause.
    let timer;
    if ((ios || (window.__dokanInstallEvent && isPhoneOrTablet())) && !dismissedRecently()) {
      timer = setTimeout(() => setOpen(true), 2500);
    }
    return () => {
      clearTimeout(timer);
      window.removeEventListener('dokan:installready', onReady);
      window.removeEventListener('dokan:install', onManual);
      window.removeEventListener('appinstalled', onInstalled);
    };
  }, [ios]);

  const dismiss = () => {
    try {
      localStorage.setItem(DISMISS_KEY, String(Date.now() + DISMISS_DAYS * 24 * 60 * 60 * 1000));
    } catch {
      /* storage blocked */
    }
    setOpen(false);
  };

  const install = async () => {
    if (!deferred) return;
    deferred.prompt();
    const choice = await deferred.userChoice.catch(() => null);
    window.__dokanInstallEvent = null;
    setDeferred(null);
    if (choice?.outcome === 'accepted') {
      setInstalled(true);
      setTimeout(() => setOpen(false), 1800);
    }
  };

  if (!open || isStandalone()) return null;

  return (
    <div className="install-backdrop no-print" role="presentation" onClick={dismiss}>
      <div className="install-sheet" role="dialog" aria-modal="true" aria-labelledby="install-title" onClick={(e) => e.stopPropagation()}>
        <button type="button" className="install-close" onClick={dismiss} aria-label="বন্ধ করুন"><X size={18} /></button>
        <div className="install-app-row">
          <img src="/apple-touch-icon.png" alt="" className="install-app-icon" />
          <div>
            <h2 id="install-title">Dokan Pro অ্যাপ</h2>
            <p>Home Screen-এ যোগ করুন — অ্যাপের মতো পুরো পর্দায় খুলবে।</p>
          </div>
        </div>

        {installed ? (
          <div className="install-done"><Check size={20} /> Home Screen-এ যোগ হয়েছে!</div>
        ) : deferred ? (
          <>
            <ul className="install-perks">
              <li><Smartphone size={16} /> এক চাপে খুলবে, ব্রাউজার খুঁজতে হবে না</li>
              <li><Download size={16} /> কোনো বড় ডাউনলোড নেই, জায়গাও লাগে না</li>
            </ul>
            <button type="button" className="install-primary" onClick={install}>
              <Download size={18} /> ইনস্টল করুন
            </button>
          </>
        ) : ios ? (
          <>
            <ol className="install-steps">
              <li><span>১</span>নিচের <b>Share</b> বাটনে চাপুন <Share size={17} className="install-ico" /> <small>(না দেখলে আগে <MoreHorizontal size={15} className="install-ico" /> চাপুন)</small></li>
              <li><span>২</span>তালিকা থেকে <b>Add to Home Screen</b> চাপুন <PlusSquare size={17} className="install-ico" /></li>
              <li><span>৩</span>উপরে ডানে <b>Add</b> চাপুন — হয়ে গেল!</li>
            </ol>
            <div className="install-arrow" aria-hidden="true"><ArrowDown size={26} /></div>
          </>
        ) : (
          <ol className="install-steps">
            <li><span>১</span>ব্রাউজারের মেনু <MoreHorizontal size={17} className="install-ico" /> খুলুন</li>
            <li><span>২</span><b>Install app</b> বা <b>Add to Home screen</b> চাপুন</li>
          </ol>
        )}

        {!installed && <button type="button" className="install-later" onClick={dismiss}>পরে করব</button>}
      </div>
    </div>
  );
}
