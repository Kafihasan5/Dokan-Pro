/**
 * Live support proxy between the mobile app and the Telegram support group.
 *
 * The bot token lives only here (Secret Manager: TELEGRAM_BOT_TOKEN). Phones talk to these
 * callables instead of Telegram, and each support thread is protected by a random key that only
 * the phone that opened it holds, so one shop can never read another shop's conversation.
 * Replies from the support team arrive through `telegramWebhook` (Telegram → this function).
 *
 * Data (server-only, no client rules):
 *   _support/threads/{threadId}   { keyHash, topicId, deviceId, shopName, createdAt }
 *   _support/topics/{topicId}     threadId
 *   _support/messages/{threadId}/{id} { sender: 'user'|'support', text, ts, media? }
 *   _support/broadcasts/{id}      { text, ts, media? }
 *
 * Photos and videos are stored by Telegram itself (media = { kind: 'photo'|'video', fileId, mime }).
 * Phones upload through `supportUpload` and download through `supportMedia`; Telegram file
 * URLs contain the bot token, so they never leave the server.
 */
import crypto from 'node:crypto';
import { getDatabase, ServerValue } from 'firebase-admin/database';
import { HttpsError } from 'firebase-functions/v2/https';
import { handleAdminMessage, handleAdminCallback } from './telegramAdmin.js';

// Read from the environment at call time (Firebase secrets and Netlify env vars both land here).
const botToken = () => process.env.TELEGRAM_BOT_TOKEN || '';
const webhookSecret = () => process.env.TELEGRAM_WEBHOOK_SECRET || '';
const supportChatId = () => process.env.SUPPORT_CHAT_ID || '-1003954086612';

const MAX_TEXT = 2000;
const OPEN_PER_IP_HOUR = 5;
const SEND_PER_THREAD_10MIN = 30;
const MEDIA_PER_THREAD_HOUR = 20;
export const MAX_UPLOAD_BYTES = 10 * 1024 * 1024;

const BROADCAST_PREFIXES = [
  '/all', '#all', '@all', '!all', 'all:', '/broadcast', '#broadcast', '/notice', '#notice', 'notice:',
  '/সব', '#সব', 'সব:', 'নোটিশ', 'ঘোষণা',
];

function db() {
  return getDatabase();
}

const sha256 = (s) => crypto.createHash('sha256').update(String(s)).digest('hex');

function safeEqualHex(a, b) {
  const ba = Buffer.from(String(a), 'hex');
  const bb = Buffer.from(String(b), 'hex');
  return ba.length === bb.length && ba.length > 0 && crypto.timingSafeEqual(ba, bb);
}

// Everything users type is shown with parse_mode=HTML, so it must be escaped.
const esc = (s) => String(s ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

function str(v, max) {
  return typeof v === 'string' ? v.replace(/[\u0000-\u0008\u000b\u000c\u000e-\u001f]/g, '').trim().slice(0, max) : '';
}

function clientIp(request) {
  const fwd = request.rawRequest?.headers?.['x-forwarded-for'];
  return String((typeof fwd === 'string' ? fwd.split(',')[0] : request.rawRequest?.ip) || 'unknown')
    .trim()
    .replace(/[.#$\[\]\/:]/g, '_')
    .slice(0, 60);
}

async function telegram(method, payload) {
  if (!botToken()) throw new HttpsError('unavailable', 'সাপোর্ট সার্ভিস এখনো চালু হয়নি।');
  const res = await fetch(`https://api.telegram.org/bot${botToken()}/${method}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  const json = await res.json().catch(() => ({}));
  if (!json.ok) throw new HttpsError('unavailable', 'সাপোর্ট সার্ভিস সাময়িকভাবে অনুপলব্ধ।');
  return json.result;
}

// Fixed-window counter under _security/support_limits.
async function hitLimit(key, max, windowMs) {
  let allowed = true;
  await db().ref(`_security/support_limits/${key}`).transaction((rec) => {
    const now = Date.now();
    if (!rec || rec.start < now - windowMs) return { start: now, count: 1 };
    if (rec.count >= max) {
      allowed = false;
      return; // abort, leave unchanged
    }
    rec.count += 1;
    return rec;
  });
  if (!allowed) throw new HttpsError('resource-exhausted', 'অনেক বেশি অনুরোধ। কিছুক্ষণ পর চেষ্টা করুন।');
}

async function requireThread(data) {
  const threadId = str(data?.threadId, 40);
  const threadKey = str(data?.threadKey, 128);
  if (!/^[a-f0-9]{24}$/.test(threadId) || !/^[a-f0-9]{64}$/.test(threadKey)) {
    throw new HttpsError('permission-denied', 'সাপোর্ট সেশন সঠিক নয়।');
  }
  const snap = await db().ref(`_support/threads/${threadId}`).get();
  const thread = snap.val();
  if (!thread || !safeEqualHex(thread.keyHash, sha256(threadKey))) {
    throw new HttpsError('permission-denied', 'সাপোর্ট সেশন সঠিক নয়।');
  }
  return { threadId, thread };
}

function profileCard(p, title) {
  const date = new Date().toLocaleString('en-GB', { timeZone: 'Asia/Dhaka' });
  return [
    `${title}`,
    '━━━━━━━━━━━━━━━━━━━━',
    `🏪 <b>দোকান:</b> ${esc(p.shopName || 'নাম দেওয়া হয়নি')}`,
    `📞 <b>মোবাইল:</b> ${esc(p.shopPhone || 'ফোন দেওয়া হয়নি')}`,
    `📍 <b>ঠিকানা:</b> ${esc(p.shopAddress || 'ঠিকানা দেওয়া হয়নি')}`,
    `🔑 <b>ডিভাইস আইডি:</b> <code>${esc(p.deviceId)}</code>`,
    `📱 <b>অ্যাপ সংস্করণ:</b> v${esc(p.appVersion)}`,
    `🛡️ <b>লাইসেন্স:</b> ${esc(p.licenseStatus)}`,
    `⏰ <b>সময়:</b> ${date}`,
  ].join('\n');
}

/** Opens (or re-validates) this phone's support thread and returns its credentials. */
export async function supportOpen(request) {
  const d = request.data || {};
  if (d.threadId && d.threadKey) {
    const { threadId } = await requireThread(d);
    return { threadId, threadKey: d.threadKey };
  }
  const p = {
    deviceId: str(d.deviceId, 64) || 'unknown',
    shopName: str(d.shopName, 80),
    shopPhone: str(d.shopPhone, 30),
    shopAddress: str(d.shopAddress, 120),
    appVersion: str(d.appVersion, 20),
    licenseStatus: str(d.licenseStatus, 40),
  };
  await hitLimit(`open_${clientIp(request)}`, OPEN_PER_IP_HOUR, 60 * 60 * 1000);

  const chatId = supportChatId();
  const topic = await telegram('createForumTopic', {
    chat_id: chatId,
    name: `🏪 ${p.shopName || 'দোকান'} [${p.deviceId.slice(-6)}]`.slice(0, 120),
  });
  const topicId = topic.message_thread_id;
  await telegram('sendMessage', {
    chat_id: chatId,
    message_thread_id: topicId,
    parse_mode: 'HTML',
    text: `${profileCard(p, '🛍️ <b>নতুন গ্রাহক সাপোর্ট রিকোয়েস্ট</b>')}\n━━━━━━━━━━━━━━━━━━━━\n<i>এই টপিকে রিপ্লাই দিলে তা সরাসরি গ্রাহকের অ্যাপে চলে যাবে।</i>`,
  });

  if (d.announce === true) {
    const cleanChat = String(chatId).replace(/^-100/, '');
    await telegram('sendMessage', {
      chat_id: chatId,
      parse_mode: 'HTML',
      text: profileCard(p, '🎉 <b>নতুন গ্রাহক অ্যাপ সেটআপ সম্পন্ন করেছেন!</b>'),
      reply_markup: { inline_keyboard: [[{ text: '💬 চ্যাট টপিক ওপেন করুন', url: `https://t.me/c/${cleanChat}/${topicId}` }]] },
    }).catch(() => {});
  }

  const threadId = crypto.randomBytes(12).toString('hex');
  const threadKey = crypto.randomBytes(32).toString('hex');
  await db().ref().update({
    [`_support/threads/${threadId}`]: {
      keyHash: sha256(threadKey),
      topicId,
      deviceId: p.deviceId,
      shopName: p.shopName,
      shopPhone: p.shopPhone,
      createdAt: ServerValue.TIMESTAMP,
    },
    [`_support/topics/${topicId}`]: threadId,
  });
  return { threadId, threadKey };
}

/** Sends a customer message into their own Telegram topic. */
export async function supportSend(request) {
  const { threadId, thread } = await requireThread(request.data);
  const text = str(request.data?.text, MAX_TEXT);
  if (!text) throw new HttpsError('invalid-argument', 'মেসেজ খালি হতে পারে না।');
  await hitLimit(`send_${threadId}`, SEND_PER_THREAD_10MIN, 10 * 60 * 1000);

  await telegram('sendMessage', {
    chat_id: supportChatId(),
    message_thread_id: thread.topicId,
    parse_mode: 'HTML',
    text: `👤 <b>গ্রাহক:</b>\n${esc(text)}`,
  });
  const ref = db().ref(`_support/messages/${threadId}`).push();
  await ref.set({ sender: 'user', text, ts: ServerValue.TIMESTAMP });
  return { id: ref.key };
}

/** Returns this thread's messages and platform broadcasts newer than `since` (ms). */
export async function supportFetch(request) {
  const since = Math.max(0, Number(request.data?.since) || 0);
  const result = { messages: [], broadcasts: [], now: Date.now() };
  // Older app versions can't show media; give them a text line instead of an empty bubble.
  const canMedia = request.data?.media === true;
  const withMedia = (v) => {
    if (!v.media) return { text: v.text || '' };
    if (canMedia) return { text: v.text || '', media: v.media.kind };
    const label = v.media.kind === 'photo' ? '📷 একটি ছবি' : '🎬 একটি ভিডিও';
    return { text: `${label} পাঠানো হয়েছে। দেখতে অ্যাপ আপডেট করুন।${v.text ? `\n${v.text}` : ''}` };
  };

  const bSnap = await db().ref('_support/broadcasts').orderByChild('ts').startAt(since + 1).limitToLast(20).get();
  bSnap.forEach((c) => {
    const v = c.val();
    result.broadcasts.push({ id: `broadcast_${c.key}`, ts: v.ts, ...withMedia(v) });
  });

  if (request.data?.threadId) {
    const { threadId } = await requireThread(request.data);
    const mSnap = await db().ref(`_support/messages/${threadId}`).orderByChild('ts').startAt(since + 1).limitToLast(200).get();
    mSnap.forEach((c) => {
      const v = c.val();
      result.messages.push({ id: `srv_${c.key}`, sender: v.sender, ts: v.ts, ...withMedia(v) });
    });
  }
  return result;
}

// ---------- photos & videos ----------

function sniffMedia(bytes) {
  const b = bytes.subarray(0, 12);
  const hex = Buffer.from(b).toString('hex');
  if (hex.startsWith('ffd8ff')) return { kind: 'photo', mime: 'image/jpeg', ext: 'jpg' };
  if (hex.startsWith('89504e47')) return { kind: 'photo', mime: 'image/png', ext: 'png' };
  if (hex.startsWith('52494646') && Buffer.from(b.subarray(8, 12)).toString() === 'WEBP') return { kind: 'photo', mime: 'image/webp', ext: 'webp' };
  if (Buffer.from(b.subarray(4, 8)).toString() === 'ftyp') return { kind: 'video', mime: 'video/mp4', ext: 'mp4' };
  if (hex.startsWith('1a45dfa3')) return { kind: 'video', mime: 'video/webm', ext: 'webm' };
  return null;
}

async function telegramUpload(method, field, fields, bytes, filename, mime) {
  if (!botToken()) throw new HttpsError('unavailable', 'সাপোর্ট সার্ভিস এখনো চালু হয়নি।');
  const form = new FormData();
  for (const [k, v] of Object.entries(fields)) if (v !== undefined && v !== '') form.append(k, String(v));
  form.append(field, new Blob([bytes], { type: mime }), filename);
  const res = await fetch(`https://api.telegram.org/bot${botToken()}/${method}`, { method: 'POST', body: form });
  const json = await res.json().catch(() => ({}));
  if (!json.ok) throw new HttpsError('unavailable', 'ফাইল পাঠানো যায়নি। আবার চেষ্টা করুন।');
  return json.result;
}

/** Picks the photo/video out of a Telegram message, or null. */
function mediaOf(msg) {
  if (Array.isArray(msg.photo) && msg.photo.length) return { kind: 'photo', fileId: msg.photo[msg.photo.length - 1].file_id, mime: 'image/jpeg' };
  const v = msg.video || msg.animation || msg.video_note;
  if (v?.file_id) return { kind: 'video', fileId: v.file_id, mime: v.mime_type || 'video/mp4' };
  const d = msg.document;
  if (d?.file_id && /^image\//.test(d.mime_type || '')) return { kind: 'photo', fileId: d.file_id, mime: d.mime_type };
  if (d?.file_id && /^video\//.test(d.mime_type || '')) return { kind: 'video', fileId: d.file_id, mime: d.mime_type };
  return null;
}

/** Customer sends a photo or video (raw bytes) into their own Telegram topic. */
export async function supportUpload({ threadId, threadKey, caption, bytes }) {
  const { threadId: id, thread } = await requireThread({ threadId, threadKey });
  if (!bytes?.length) throw new HttpsError('invalid-argument', 'ফাইল খালি।');
  if (bytes.length > MAX_UPLOAD_BYTES) throw new HttpsError('invalid-argument', 'ফাইল ১০ MB এর বেশি হতে পারবে না।');
  const type = sniffMedia(bytes);
  if (!type) throw new HttpsError('invalid-argument', 'শুধু ছবি (JPG/PNG) বা ভিডিও (MP4) পাঠানো যাবে।');
  await hitLimit(`media_${id}`, MEDIA_PER_THREAD_HOUR, 60 * 60 * 1000);

  const text = str(caption, 1000);
  const fields = {
    chat_id: supportChatId(),
    message_thread_id: thread.topicId,
    parse_mode: 'HTML',
    caption: `👤 <b>গ্রাহক:</b>${text ? `\n${esc(text)}` : ''}`,
  };
  const sent =
    type.kind === 'photo'
      ? await telegramUpload('sendPhoto', 'photo', fields, bytes, `photo.${type.ext}`, type.mime)
      : await telegramUpload('sendVideo', 'video', { ...fields, supports_streaming: true }, bytes, `video.${type.ext}`, type.mime);
  const media = mediaOf(sent) || { kind: type.kind, fileId: sent?.document?.file_id, mime: type.mime };
  if (!media.fileId) throw new HttpsError('unavailable', 'ফাইল পাঠানো যায়নি। আবার চেষ্টা করুন।');

  const ref = db().ref(`_support/messages/${id}`).push();
  await ref.set({ sender: 'user', text, media, ts: ServerValue.TIMESTAMP });
  return { id: ref.key, media: media.kind };
}

/**
 * Streams one photo/video to the phone. Thread messages need that thread's key; broadcast
 * media is public to every app. Returns a fetch Response from Telegram's file server.
 */
export async function supportMedia({ threadId, threadKey, messageId }) {
  const mid = str(messageId, 80);
  let rec;
  if (mid.startsWith('broadcast_')) {
    const key = mid.slice('broadcast_'.length);
    if (!/^[A-Za-z0-9_-]{1,60}$/.test(key)) throw new HttpsError('not-found', 'ফাইল পাওয়া যায়নি।');
    rec = (await db().ref(`_support/broadcasts/${key}`).get()).val();
  } else {
    const { threadId: id } = await requireThread({ threadId, threadKey });
    const key = mid.replace(/^srv_/, '');
    if (!/^[A-Za-z0-9_-]{1,60}$/.test(key)) throw new HttpsError('not-found', 'ফাইল পাওয়া যায়নি।');
    rec = (await db().ref(`_support/messages/${id}/${key}`).get()).val();
  }
  if (!rec?.media?.fileId) throw new HttpsError('not-found', 'ফাইল পাওয়া যায়নি।');
  const file = await telegram('getFile', { file_id: rec.media.fileId }).catch(() => null);
  if (!file?.file_path) throw new HttpsError('failed-precondition', 'ফাইলটি অনেক বড় (২০ MB এর বেশি) অথবা আর পাওয়া যাচ্ছে না।');
  const res = await fetch(`https://api.telegram.org/file/bot${botToken()}/${file.file_path}`);
  if (!res.ok || !res.body) throw new HttpsError('unavailable', 'ফাইল ডাউনলোড করা যায়নি।');
  return { stream: res.body, mime: rec.media.mime || (rec.media.kind === 'photo' ? 'image/jpeg' : 'video/mp4'), size: file.file_size };
}

/**
 * Telegram webhook. Register once:
 *   curl "https://api.telegram.org/bot<TOKEN>/setWebhook" \
 *     -d url=<telegramWebhook URL> -d secret_token=<TELEGRAM_WEBHOOK_SECRET> -d allowed_updates='["message"]'
 */
export async function telegramWebhook({ method, secretHeader, body }) {
  const given = String(secretHeader || '');
  const expected = webhookSecret();
  if (method !== 'POST' || !expected || given.length !== expected.length || !crypto.timingSafeEqual(Buffer.from(given), Buffer.from(expected))) {
    return { status: 403, body: 'forbidden' };
  }
  try {
    // Admin panel: button presses and private chats with the bot.
    if (body?.callback_query) {
      await handleAdminCallback(body.callback_query);
      return { status: 200, body: 'ok' };
    }
    if (body?.message?.chat?.type === 'private') {
      await handleAdminMessage(body.message);
      return { status: 200, body: 'ok' };
    }

    const msg = body?.message;
    const text = str(msg?.text ?? msg?.caption, MAX_TEXT);
    const media = msg ? mediaOf(msg) : null;
    if (!msg || msg.from?.is_bot || (!text && !media) || String(msg.chat?.id) !== String(supportChatId())) {
      return { status: 200, body: 'ignored' };
    }
    const ts = (Number(msg.date) || Math.floor(Date.now() / 1000)) * 1000;
    const threadIdTg = Number(msg.message_thread_id || msg.reply_to_message?.message_thread_id || 0);
    const lower = text.toLowerCase();
    const prefix = BROADCAST_PREFIXES.find((p) => lower.startsWith(p));

    if (prefix || threadIdTg <= 1) {
      const clean = (prefix ? text.slice(prefix.length) : text).replace(/^\s*:\s*/, '').trim() || text;
      await db().ref(`_support/broadcasts/${msg.message_id}`).set({ text: clean, ts, ...(media ? { media } : {}) });
    } else {
      const threadId = (await db().ref(`_support/topics/${threadIdTg}`).get()).val();
      if (threadId) {
        await db().ref(`_support/messages/${threadId}/tg_${msg.message_id}`).set({ sender: 'support', text, ts, ...(media ? { media } : {}) });
      }
    }
    return { status: 200, body: 'ok' };
  } catch (err) {
    console.error('telegramWebhook', err);
    return { status: 200, body: 'error' }; // 200 so Telegram doesn't retry forever
  }
}
