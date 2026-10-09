import React, { useEffect, useRef, useState } from 'react';
import { AlertCircle, Check, CheckCheck, Clock, Headset, ImagePlus, Loader2, Megaphone, Send } from 'lucide-react';
import { useShop } from '../context/ShopContext';
import { notify } from '../components/Feedback';
import {
  loadSupportMedia, sendSupportMedia, sendSupportText, setSupportChatOpen, setSupportProfile, useSupport,
} from '../utils/supportStore';

const QUICK = ['🔑 লাইসেন্স সমস্যা', '💾 ব্যাকআপ সাহায্য', '🛒 বিক্রি বা প্রিন্টার', '💡 নতুন ফিচারের অনুরোধ'];
const timeOf = (ts) => new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit', hour12: true }).format(new Date(ts));

function Media({ msg }) {
  const [url, setUrl] = useState(msg.localUrl || null);
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    if (url || !msg.id.startsWith('srv_') && !msg.id.startsWith('broadcast_')) return;
    let alive = true;
    loadSupportMedia(msg.id).then((u) => alive && setUrl(u)).catch(() => alive && setFailed(true));
    return () => { alive = false; };
  }, [msg.id, url]);
  if (failed) return <div className="chat-media chat-media-empty">ফাইল লোড হয়নি</div>;
  if (!url) return <div className="chat-media chat-media-empty"><Loader2 size={18} className="spin" /> লোড হচ্ছে…</div>;
  return msg.media === 'video'
    ? <video className="chat-media" src={url} controls playsInline preload="metadata" />
    : <a href={url} target="_blank" rel="noreferrer"><img className="chat-media" src={url} alt="ছবি" /></a>;
}

/** Live support chat with the Dokan Pro team (Telegram on the other side), like the mobile app. */
export default function SupportPage() {
  const { shopInfo, auth } = useShop();
  const { messages, broadcasts } = useSupport();
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);
  const listRef = useRef(null);
  const fileRef = useRef(null);

  useEffect(() => {
    setSupportProfile({
      shopName: shopInfo?.shopName,
      shopPhone: shopInfo?.shopPhone || shopInfo?.phone,
      shopAddress: shopInfo?.shopAddress || shopInfo?.address,
      licenseStatus: `ওয়েব • ${auth?.shopCode || ''}`,
    });
    setSupportChatOpen(true);
    return () => setSupportChatOpen(false);
  }, [shopInfo, auth]);

  const items = [
    ...messages,
    ...broadcasts.map((b) => ({ ...b, sender: 'support', broadcast: true })),
  ].sort((a, b) => a.ts - b.ts);

  useEffect(() => {
    listRef.current?.scrollTo({ top: listRef.current.scrollHeight, behavior: 'smooth' });
  }, [items.length]);

  const send = async (value = text) => {
    const v = value.trim();
    if (!v) return;
    setText('');
    try {
      await sendSupportText(v);
    } catch (err) {
      notify(err.message);
    }
  };

  const pickFile = async (e) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    setBusy(true);
    const caption = text.trim();
    setText('');
    try {
      await sendSupportMedia(file, caption);
    } catch (err) {
      notify(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="page-wrapper chat-page">
      <header className="chat-head">
        <span className="chat-avatar"><Headset size={22} /><i /></span>
        <div>
          <h1>দোকান প্রো লাইভ সাপোর্ট</h1>
          <p>সাধারণত কয়েক মিনিটের মধ্যে উত্তর দেওয়া হয়</p>
        </div>
      </header>

      <div className="chat-list" ref={listRef}>
        {items.length === 0 ? (
          <div className="chat-empty">
            <Headset size={34} />
            <strong>লাইভ চ্যাটে স্বাগতম!</strong>
            <span>যেকোনো প্রশ্ন বা সমস্যার কথা লিখুন, ছবি বা ভিডিও পাঠাতে পারেন। আমাদের সাপোর্ট টিম দ্রুত উত্তর দেবে।</span>
          </div>
        ) : items.map((m) => (
          <div key={m.id} className={`chat-row ${m.sender === 'user' ? 'me' : 'them'}`}>
            <div className={`chat-bubble ${m.broadcast ? 'broadcast' : ''} ${m.failed ? 'failed' : ''}`}>
              {m.sender !== 'user' && (
                <span className="chat-from">{m.broadcast ? <><Megaphone size={12} /> সার্বজনীন নোটিশ</> : 'দোকান প্রো সাপোর্ট টিম'}</span>
              )}
              {m.media && <Media msg={m} />}
              {m.text && <p>{m.text}</p>}
              <span className="chat-meta">
                {timeOf(m.ts)}
                {m.sender === 'user' && (m.failed ? <AlertCircle size={13} /> : m.pending ? <Clock size={12} /> : m.id.startsWith('srv_') ? <CheckCheck size={13} /> : <Check size={13} />)}
              </span>
            </div>
          </div>
        ))}
      </div>

      <div className="chat-quick">
        {QUICK.map((q) => <button key={q} type="button" onClick={() => send(q)}>{q}</button>)}
      </div>

      <form className="chat-input" onSubmit={(e) => { e.preventDefault(); send(); }}>
        <input ref={fileRef} type="file" accept="image/*,video/*" hidden onChange={pickFile} />
        <button type="button" className="chat-attach" onClick={() => fileRef.current?.click()} disabled={busy} aria-label="ছবি বা ভিডিও পাঠান">
          {busy ? <Loader2 size={20} className="spin" /> : <ImagePlus size={20} />}
        </button>
        <textarea
          rows={1}
          value={text}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) { e.preventDefault(); send(); } }}
          placeholder="আপনার সমস্যার কথা লিখুন…"
          maxLength={2000}
        />
        <button type="submit" className="chat-send" disabled={!text.trim()} aria-label="পাঠান"><Send size={18} /></button>
      </form>
    </div>
  );
}
