import React, { useEffect, useMemo, useRef, useState } from 'react';
import { AlertTriangle, Bell, BellRing, CheckCheck, Headset, Megaphone, X } from 'lucide-react';
import { useShop } from '../context/ShopContext';
import { useSupport } from '../utils/supportStore';

const READ_KEY = 'dokan_notif_read_at';
const HIDDEN_KEY = 'dokan_notif_hidden';
const getJSON = (k, d) => { try { const v = localStorage.getItem(k); return v ? JSON.parse(v) : d; } catch { return d; } };
const setJSON = (k, v) => { try { localStorage.setItem(k, JSON.stringify(v)); } catch { /* blocked */ } };

const ago = (ts) => {
  const m = Math.round((Date.now() - ts) / 60000);
  if (m < 1) return 'এইমাত্র';
  if (m < 60) return `${m} মিনিট আগে`;
  const h = Math.round(m / 60);
  if (h < 24) return `${h} ঘণ্টা আগে`;
  return new Intl.DateTimeFormat('bn-BD', { day: 'numeric', month: 'short' }).format(new Date(ts));
};

/**
 * Bell in the top bar: notices from the admin (all shops / this shop), announcements from the
 * Telegram support group and replies from support, plus a low-stock alert — like the mobile app.
 */
export default function NotificationBell({ onNavigate }) {
  const { globalNotices = [], shopNotices = [], products = [] } = useShop();
  const { messages, broadcasts } = useSupport();
  const [open, setOpen] = useState(false);
  const [readAt, setReadAt] = useState(() => {
    const v = getJSON(READ_KEY, null);
    if (v) return v;
    const now = Date.now();
    setJSON(READ_KEY, now);
    return now;
  });
  const [hidden, setHidden] = useState(() => getJSON(HIDDEN_KEY, []));
  const [permission, setPermission] = useState(() => (typeof Notification === 'undefined' ? 'unsupported' : Notification.permission));
  const boxRef = useRef(null);

  const items = useMemo(() => {
    const list = [
      ...globalNotices.map((n) => ({ id: `g_${n.id}`, kind: 'notice', title: n.title || 'নোটিশ', text: n.message, ts: Number(n.createdAt) || 0 })),
      ...shopNotices.map((n) => ({ id: `s_${n.id}`, kind: 'notice', title: n.title || 'আপনার দোকানের জন্য নোটিশ', text: n.message, ts: Number(n.createdAt) || 0 })),
      ...broadcasts.map((b) => ({ id: b.id, kind: 'notice', title: 'সার্বজনীন নোটিশ', text: b.text || (b.media === 'video' ? '🎬 একটি ভিডিও' : '📷 একটি ছবি'), ts: b.ts })),
      ...messages.filter((m) => m.sender === 'support').map((m) => ({ id: m.id, kind: 'support', title: 'সাপোর্ট টিমের উত্তর', text: m.text || (m.media === 'video' ? '🎬 একটি ভিডিও' : '📷 একটি ছবি'), ts: m.ts })),
    ];
    return list.filter((i) => !hidden.includes(i.id)).sort((a, b) => b.ts - a.ts).slice(0, 40);
  }, [globalNotices, shopNotices, broadcasts, messages, hidden]);

  const lowStock = products.filter((p) => p.isActive && Number(p.stockQty) <= Number(p.minStock || 0)).length;
  const unread = items.filter((i) => i.ts > readAt).length;

  // System notification for new items while the app is open (only after the user allowed it).
  const seen = useRef(new Set(items.map((i) => i.id)));
  useEffect(() => {
    const fresh = items.filter((i) => !seen.current.has(i.id) && i.ts > readAt);
    items.forEach((i) => seen.current.add(i.id));
    if (!fresh.length || permission !== 'granted') return;
    const n = fresh[0];
    try {
      new Notification(n.title, { body: n.text, icon: '/icon-192.png', tag: n.id });
    } catch {
      /* some browsers only allow this from a service worker */
    }
  }, [items, readAt, permission]);

  useEffect(() => {
    if (!open) return undefined;
    const close = (e) => { if (boxRef.current && !boxRef.current.contains(e.target)) setOpen(false); };
    document.addEventListener('pointerdown', close);
    return () => document.removeEventListener('pointerdown', close);
  }, [open]);

  const markAll = () => { const now = Date.now(); setJSON(READ_KEY, now); setReadAt(now); };
  const hide = (id) => { const next = [...hidden, id].slice(-200); setJSON(HIDDEN_KEY, next); setHidden(next); };
  const askPermission = async () => {
    try { setPermission(await Notification.requestPermission()); } catch { /* ignore */ }
  };

  return (
    <div className="notif-wrap" ref={boxRef}>
      <button type="button" className="icon-btn notif-btn" onClick={() => setOpen((o) => !o)} aria-label={`নোটিফিকেশন${unread ? ` (${unread}টি নতুন)` : ''}`} aria-expanded={open}>
        {unread ? <BellRing size={17} /> : <Bell size={17} />}
        {unread > 0 && <span className="notif-count">{unread > 9 ? '9+' : unread}</span>}
      </button>

      {open && (
        <div className="notif-panel" role="dialog" aria-label="নোটিফিকেশন">
          <div className="notif-head">
            <strong>নোটিফিকেশন</strong>
            {unread > 0 && <button type="button" onClick={markAll}><CheckCheck size={15} /> সব পড়া হয়েছে</button>}
            <button type="button" className="notif-close" onClick={() => setOpen(false)} aria-label="বন্ধ করুন"><X size={17} /></button>
          </div>

          {permission === 'default' && (
            <button type="button" className="notif-enable" onClick={askPermission}><BellRing size={15} /> ফোন/কম্পিউটারে নোটিফিকেশন চালু করুন</button>
          )}

          {lowStock > 0 && (
            <button type="button" className="notif-item warn" onClick={() => { onNavigate('products'); setOpen(false); }}>
              <span className="notif-icon warn"><AlertTriangle size={16} /></span>
              <span className="notif-body"><b>{lowStock}টি পণ্যের স্টক কম</b><small>স্টক দেখে নতুন করে অর্ডার দিন</small></span>
            </button>
          )}

          <div className="notif-list">
            {items.length === 0 ? (
              <div className="notif-empty"><Bell size={24} /><span>কোনো নোটিফিকেশন নেই</span></div>
            ) : items.map((i) => (
              <div key={i.id} className={`notif-item ${i.ts > readAt ? 'unread' : ''}`}>
                <button type="button" className="notif-main" onClick={() => { if (i.kind === 'support') { onNavigate('support'); setOpen(false); } }}>
                  <span className={`notif-icon ${i.kind}`}>{i.kind === 'support' ? <Headset size={16} /> : <Megaphone size={16} />}</span>
                  <span className="notif-body"><b>{i.title}</b><small>{i.text}</small><em>{ago(i.ts)}</em></span>
                </button>
                <button type="button" className="notif-hide" onClick={() => hide(i.id)} aria-label="মুছুন"><X size={14} /></button>
              </div>
            ))}
          </div>

          <button type="button" className="notif-chat" onClick={() => { onNavigate('support'); setOpen(false); }}><Headset size={16} /> লাইভ চ্যাটে সাপোর্ট নিন</button>
        </div>
      )}
    </div>
  );
}
