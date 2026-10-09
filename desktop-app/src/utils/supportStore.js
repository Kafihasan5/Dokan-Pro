/**
 * Live support chat + broadcasts for the web version (same server as the mobile app:
 * supportOpen / supportSend / supportFetch / supportUpload / supportMedia on the Worker).
 * Each browser gets its own support thread; its random key stays in this browser only.
 */
import { useSyncExternalStore } from 'react';
import { API_BASE, callable } from '../config/firebase';
import { getDesktopDeviceId } from './activation';

const THREAD_KEY = 'dokan_support_thread';
const CACHE_KEY = 'dokan_support_cache';
const READ_KEY = 'dokan_support_read_at';
const MAX_UPLOAD = 10 * 1024 * 1024;
const POLL_IDLE = 60_000;
const POLL_OPEN = 5_000;

const read = (k, fallback) => {
  try {
    const v = localStorage.getItem(k);
    return v ? JSON.parse(v) : fallback;
  } catch {
    return fallback;
  }
};
const write = (k, v) => {
  try {
    localStorage.setItem(k, JSON.stringify(v));
  } catch {
    /* storage full or blocked */
  }
};

const cached = read(CACHE_KEY, { messages: [], broadcasts: [], since: 0 });
let state = {
  messages: cached.messages || [],
  broadcasts: cached.broadcasts || [],
  // First visit: older messages and announcements don't count as unread.
  readAt: read(READ_KEY, null) ?? (write(READ_KEY, Date.now()), Date.now()),
  sending: false,
};
let since = cached.since || 0;
const listeners = new Set();
const emit = () => listeners.forEach((l) => l());
const set = (patch) => {
  state = { ...state, ...patch };
  write(CACHE_KEY, { messages: state.messages.filter((m) => !m.pending), broadcasts: state.broadcasts, since });
  emit();
};

export function useSupport() {
  return useSyncExternalStore((l) => (listeners.add(l), () => listeners.delete(l)), () => state);
}

const thread = () => read(THREAD_KEY, null);
let profile = {};
/** Shop details shown to the support team when a new thread opens. */
export function setSupportProfile(p) {
  profile = p || {};
}

async function ensureThread() {
  const t = thread();
  if (t?.threadId && t?.threadKey) return t;
  const res = await callable('supportOpen')({
    deviceId: getDesktopDeviceId(),
    shopName: profile.shopName || '',
    shopPhone: profile.shopPhone || '',
    shopAddress: profile.shopAddress || '',
    appVersion: 'web',
    licenseStatus: profile.licenseStatus || 'ওয়েব সংস্করণ',
  });
  const fresh = { threadId: res.data.threadId, threadKey: res.data.threadKey };
  write(THREAD_KEY, fresh);
  return fresh;
}

const isThreadError = (err) => /permission-denied|PERMISSION_DENIED|403/.test(String(err?.code || err?.status || err?.message));

function merge(list, incoming) {
  const map = new Map(list.map((m) => [m.id, m]));
  incoming.forEach((m) => map.set(m.id, { ...map.get(m.id), ...m }));
  return [...map.values()].sort((a, b) => a.ts - b.ts);
}

export async function syncSupport() {
  const t = thread();
  try {
    const res = await callable('supportFetch')({ since, media: true, ...(t || {}) });
    const data = res.data || {};
    // Notices sent from the Telegram bot also exist as web notices; skip their broadcast copy.
    const broadcasts = (data.broadcasts || []).filter((b) => !String(b.id).startsWith('broadcast_tgadmin_'));
    since = data.now || since;
    set({
      messages: merge(state.messages, data.messages || []),
      broadcasts: merge(state.broadcasts, broadcasts).slice(-50),
    });
  } catch (err) {
    if (t && isThreadError(err)) localStorage.removeItem(THREAD_KEY);
  }
}

export async function sendSupportText(text) {
  const clean = String(text || '').trim().slice(0, 2000);
  if (!clean) return;
  const tempId = `tmp_${Date.now()}`;
  set({ messages: [...state.messages, { id: tempId, sender: 'user', text: clean, ts: Date.now(), pending: true }] });
  try {
    let t = await ensureThread();
    let res;
    try {
      res = await callable('supportSend')({ ...t, text: clean });
    } catch (err) {
      if (!isThreadError(err)) throw err;
      localStorage.removeItem(THREAD_KEY);
      t = await ensureThread();
      res = await callable('supportSend')({ ...t, text: clean });
    }
    set({ messages: state.messages.map((m) => (m.id === tempId ? { ...m, id: `srv_${res.data.id}`, pending: false } : m)) });
  } catch (err) {
    set({ messages: state.messages.map((m) => (m.id === tempId ? { ...m, pending: false, failed: true } : m)) });
    throw new Error(err?.message || 'মেসেজ পাঠানো যায়নি।');
  }
}

async function compressImage(file) {
  const bitmap = await createImageBitmap(file);
  const scale = Math.min(1, 1600 / Math.max(bitmap.width, bitmap.height));
  const canvas = document.createElement('canvas');
  canvas.width = Math.round(bitmap.width * scale);
  canvas.height = Math.round(bitmap.height * scale);
  canvas.getContext('2d').drawImage(bitmap, 0, 0, canvas.width, canvas.height);
  return new Promise((resolve) => canvas.toBlob(resolve, 'image/jpeg', 0.85));
}

export async function sendSupportMedia(file, caption = '') {
  const isVideo = file.type.startsWith('video/');
  if (!isVideo && !file.type.startsWith('image/')) throw new Error('শুধু ছবি বা ভিডিও পাঠানো যাবে।');
  const blob = isVideo ? file : await compressImage(file);
  if (blob.size > MAX_UPLOAD) throw new Error('ফাইল ১০ MB এর বেশি। ছোট ভিডিও পাঠান।');
  const tempId = `tmp_${Date.now()}`;
  const localUrl = URL.createObjectURL(blob);
  set({ messages: [...state.messages, { id: tempId, sender: 'user', text: caption, media: isVideo ? 'video' : 'photo', localUrl, ts: Date.now(), pending: true }] });
  try {
    const t = await ensureThread();
    const res = await fetch(`${API_BASE}/supportUpload`, {
      method: 'POST',
      headers: { 'X-Thread-Id': t.threadId, 'X-Thread-Key': t.threadKey, 'X-Caption': encodeURIComponent(caption) },
      body: blob,
    });
    const body = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(body?.error?.message || 'ফাইল পাঠানো যায়নি।');
    set({ messages: state.messages.map((m) => (m.id === tempId ? { ...m, id: `srv_${body.result.id}`, pending: false } : m)) });
  } catch (err) {
    set({ messages: state.messages.map((m) => (m.id === tempId ? { ...m, pending: false, failed: true } : m)) });
    throw err;
  }
}

const mediaUrls = new Map();
/** Object URL for a photo/video message (downloaded once through the server). */
export async function loadSupportMedia(messageId) {
  if (mediaUrls.has(messageId)) return mediaUrls.get(messageId);
  const t = thread() || {};
  const res = await fetch(`${API_BASE}/supportMedia`, {
    headers: { 'X-Message-Id': messageId, ...(t.threadId ? { 'X-Thread-Id': t.threadId, 'X-Thread-Key': t.threadKey } : {}) },
  });
  if (!res.ok) throw new Error('ফাইল লোড হয়নি');
  const url = URL.createObjectURL(await res.blob());
  mediaUrls.set(messageId, url);
  return url;
}

export function markSupportRead() {
  const now = Date.now();
  write(READ_KEY, now);
  set({ readAt: now });
}

let timer = null;
let chatOpen = false;
export function setSupportChatOpen(open) {
  chatOpen = open;
  if (open) markSupportRead();
  startSupportPolling();
}

/** Polls every minute (every 5 seconds while the chat is open). */
export function startSupportPolling() {
  clearTimeout(timer);
  const tick = async () => {
    await syncSupport();
    if (chatOpen) markSupportRead();
    timer = setTimeout(tick, chatOpen ? POLL_OPEN : POLL_IDLE);
  };
  tick();
}

export function stopSupportPolling() {
  clearTimeout(timer);
}
