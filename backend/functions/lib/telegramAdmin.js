/**
 * Button-only admin panel inside Telegram (private chat with the bot).
 *
 * - A persistent keyboard at the bottom of the chat opens the main sections.
 * - Everything else is inline buttons; screens update in place (editMessageText).
 * - When text is needed (notice, search, custom reason) the bot asks for it and the next
 *   message is used; a "বাতিল" button cancels.
 * Only Telegram user IDs in TELEGRAM_ADMIN_IDS can use it; others only see their ID.
 */
import crypto from 'node:crypto';
import { getDatabase, ServerValue } from 'firebase-admin/database';
import { getAuth } from 'firebase-admin/auth';
import { hashPin, isWeakPin } from './pin.js';

const db = () => getDatabase();
const botToken = () => process.env.TELEGRAM_BOT_TOKEN || '';
const adminIds = () =>
  new Set((process.env.TELEGRAM_ADMIN_IDS || '').split(',').map((s) => s.trim()).filter(Boolean));

const esc = (s) => String(s ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
const DAY = 24 * 60 * 60 * 1000;
const SHOP_RE = /^SHOP-[A-Z0-9]{4,16}$/;
const PAGE = 8;
const STATE_TTL = 10 * 60 * 1000;

// Bottom keyboard labels (they arrive as plain text messages).
const KB = {
  stats: '📊 সারসংক্ষেপ',
  shops: '🏪 সব দোকান',
  search: '🔍 দোকান খুঁজুন',
  notice: '📢 সবাইকে নোটিশ',
  maint: '🛠 রক্ষণাবেক্ষণ',
};
const MAIN_KEYBOARD = {
  keyboard: [[{ text: KB.stats }, { text: KB.shops }], [{ text: KB.search }, { text: KB.notice }], [{ text: KB.maint }]],
  resize_keyboard: true,
  is_persistent: true,
};

const LICENSES = {
  lifetime: { label: 'আজীবন', ms: null, type: 'lifetime', plan: 'lifetime' },
  '365d': { label: '১ বছর', ms: 365 * DAY, type: 'subscription', plan: 'pro' },
  '30d': { label: '৩০ দিন', ms: 30 * DAY, type: 'subscription', plan: 'pro' },
  '7d': { label: '৭ দিন ট্রায়াল', ms: 7 * DAY, type: 'trial', plan: 'trial' },
  '24h': { label: '২৪ ঘণ্টা ট্রায়াল', ms: DAY, type: 'trial', plan: 'trial' },
  expired: { label: 'মেয়াদ শেষ করে দিন', ms: -1000, type: 'trial', plan: 'trial' },
};

const FEATURES = {
  pos: 'পিওএস',
  inventoryEdit: 'পণ্য সম্পাদনা',
  dueKhata: 'বাকি খাতা',
  reports: 'রিপোর্ট',
  expenses: 'খরচ',
  staffManagement: 'কর্মচারী',
  receiptPrint: 'রসিদ প্রিন্ট',
};

const REASONS = ['বিল বকেয়া', 'নীতিমালা লঙ্ঘন', 'মালিকের অনুরোধে'];

const FILTERS = { all: 'সব', act: 'সক্রিয়', sus: 'স্থগিত', exp: 'মেয়াদ শেষ', tri: 'ট্রায়াল' };

// ---------- Telegram helpers ----------

async function tg(method, payload) {
  const res = await fetch(`https://api.telegram.org/bot${botToken()}/${method}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  return res.json().catch(() => ({}));
}

const markup = (rows) => (rows ? { inline_keyboard: rows } : undefined);

function send(chatId, text, rows, extra = {}) {
  return tg('sendMessage', {
    chat_id: chatId,
    text,
    parse_mode: 'HTML',
    disable_web_page_preview: true,
    reply_markup: rows ? markup(rows) : extra.reply_markup,
  });
}

/** Shows a screen: edits the message the button belonged to, or sends a new one. */
async function show(ctx, text, rows) {
  if (ctx.messageId) {
    const r = await tg('editMessageText', {
      chat_id: ctx.chatId,
      message_id: ctx.messageId,
      text,
      parse_mode: 'HTML',
      disable_web_page_preview: true,
      reply_markup: markup(rows),
    });
    if (r.ok || /not modified/i.test(r.description || '')) return r;
  }
  return send(ctx.chatId, text, rows);
}

const btn = (text, data) => ({ text, callback_data: data });
const NAV = (back) => [btn('⬅️ পেছনে', back), btn('🏠 মেনু', 'm')];

// ---------- data ----------

const val = async (path) => (await db().ref(path).get()).val();

function shopState(shop) {
  const st = shop?.status || {};
  const suspended = Boolean(st.isSuspended || st.status === 'suspended' || st.status === 'banned');
  const expired = Boolean(st.expiresAt && Date.now() > st.expiresAt);
  const license = st.licenseType || (st.plan === 'trial' ? 'trial' : 'lifetime');
  return { suspended, expired, license, expiresAt: st.expiresAt || null, reason: st.reason || '' };
}

const icon = (s) => (s.suspended ? '⛔' : s.expired ? '⌛' : '✅');

function licenseText(s) {
  if (!s.expiresAt) return 'আজীবন';
  const d = new Date(s.expiresAt).toLocaleDateString('en-GB', { timeZone: 'Asia/Dhaka' });
  const kind = s.license === 'trial' ? 'ট্রায়াল' : 'সাবস্ক্রিপশন';
  return `${kind} — ${s.expired ? 'মেয়াদ শেষ হয়েছে' : 'মেয়াদ'} ${d}`;
}

async function audit(code, action, details, actor) {
  const id = `${Date.now()}_${Math.random().toString(36).slice(2, 7)}`;
  await db().ref(`shops/${code}/auditLogs/${id}`).set({
    timestamp: ServerValue.TIMESTAMP,
    actor,
    role: 'admin',
    action,
    details,
    source: 'telegram',
  });
}

// Pending text input per admin (server-only node).
const stateRef = (userId) => db().ref(`_security/tg_state/${userId}`);
const setState = (userId, s) => stateRef(userId).set({ ...s, at: Date.now() });
const clearState = (userId) => stateRef(userId).remove();
async function getState(userId) {
  const s = (await stateRef(userId).get()).val();
  return s && Date.now() - s.at < STATE_TTL ? s : null;
}

// ---------- screens ----------

async function screenMenu(ctx) {
  const settings = (await val('global_settings')) || {};
  return show(
    ctx,
    `<b>🏠 Dokan Pro অ্যাডমিন</b>\n${settings.maintenanceMode ? '🛠 রক্ষণাবেক্ষণ মোড চালু আছে\n' : ''}\nনিচের বাটন থেকে বেছে নিন।`,
    [
      [btn(KB.stats, 'st'), btn(KB.shops, 'ls:all:0')],
      [btn(KB.search, 'srch'), btn(KB.notice, 'ntall')],
      [btn(KB.maint, 'mt')],
    ]
  );
}

async function screenStats(ctx) {
  const shops = (await val('shops')) || {};
  const list = Object.values(shops).map(shopState);
  const settings = (await val('global_settings')) || {};
  const count = (f) => list.filter(f).length;
  return show(
    ctx,
    [
      '<b>📊 সারসংক্ষেপ</b>',
      '',
      `🏪 মোট দোকান: <b>${list.length}</b>`,
      `✅ সক্রিয়: ${count((s) => !s.suspended && !s.expired)}`,
      `⛔ স্থগিত: ${count((s) => s.suspended)}`,
      `⌛ মেয়াদ শেষ: ${count((s) => s.expired)}`,
      `🧪 ট্রায়াল: ${count((s) => s.license === 'trial')}`,
      `🛠 রক্ষণাবেক্ষণ: ${settings.maintenanceMode ? 'চালু' : 'বন্ধ'}`,
    ].join('\n'),
    [
      [btn('✅ সক্রিয়', 'ls:act:0'), btn('⛔ স্থগিত', 'ls:sus:0')],
      [btn('⌛ মেয়াদ শেষ', 'ls:exp:0'), btn('🧪 ট্রায়াল', 'ls:tri:0')],
      [btn('🔄 রিফ্রেশ', 'st'), btn('🏠 মেনু', 'm')],
    ]
  );
}

function matchesFilter(s, f) {
  if (f === 'act') return !s.suspended && !s.expired;
  if (f === 'sus') return s.suspended;
  if (f === 'exp') return s.expired;
  if (f === 'tri') return s.license === 'trial';
  return true;
}

async function screenList(ctx, filter = 'all', page = 0, query = '') {
  const shops = (await val('shops')) || {};
  const q = query.trim().toLowerCase();
  const codes = Object.keys(shops)
    .sort()
    .filter((c) => matchesFilter(shopState(shops[c]), filter))
    .filter((c) => !q || c.toLowerCase().includes(q) || String(shops[c]?.info?.shopName || '').toLowerCase().includes(q) || String(shops[c]?.info?.shopPhone || '').includes(q) || String(shops[c]?.info?.ownerEmail || '').toLowerCase().includes(q));
  const pages = Math.max(1, Math.ceil(codes.length / PAGE));
  const p = Math.min(Math.max(0, page), pages - 1);
  const rows = codes.slice(p * PAGE, p * PAGE + PAGE).map((c) => {
    const name = shops[c]?.info?.shopName || '';
    return [btn(`${icon(shopState(shops[c]))} ${c}${name ? ` · ${name}` : ''}`.slice(0, 60), `sh:${c}`)];
  });
  if (!q) {
    rows.push(Object.entries(FILTERS).map(([k, l]) => btn(k === filter ? `• ${l}` : l, `ls:${k}:0`)));
  }
  const nav = [];
  if (p > 0) nav.push(btn('◀️ আগের', `ls:${filter}:${p - 1}`));
  if (p < pages - 1) nav.push(btn('পরের ▶️', `ls:${filter}:${p + 1}`));
  if (nav.length) rows.push(nav);
  rows.push([btn('🔍 খুঁজুন', 'srch'), btn('🏠 মেনু', 'm')]);
  const title = q ? `🔍 "${esc(query)}" এর ফলাফল` : `🏪 দোকান — ${FILTERS[filter] || 'সব'}`;
  return show(ctx, `<b>${title}</b> (${codes.length}টি)${pages > 1 ? ` · পাতা ${p + 1}/${pages}` : ''}\n\n${codes.length ? 'বিস্তারিত দেখতে দোকানে চাপুন।' : 'কোনো দোকান পাওয়া যায়নি।'}`, rows);
}

async function screenShop(ctx, code, note = '') {
  if (!SHOP_RE.test(code)) return show(ctx, 'দোকান কোড সঠিক নয়।', [NAV('ls:all:0')]);
  const shop = await val(`shops/${code}`);
  if (!shop) return show(ctx, `${esc(code)} পাওয়া যায়নি।`, [NAV('ls:all:0')]);
  const s = shopState(shop);
  const info = shop.info || {};
  const n = (k) => Object.keys(shop[k] || {}).length;
  const off = Object.entries(FEATURES).filter(([k]) => shop.features?.[k] === false).map(([, l]) => l);
  const text = [
    note ? `${note}\n` : null,
    `<b>🏪 ${esc(info.shopName || code)}</b>`,
    `কোড: <code>${esc(code)}</code>`,
    info.ownerEmail ? `📧 ${esc(info.ownerEmail)}` : null,
    info.shopPhone ? `📞 ${esc(info.shopPhone)}` : null,
    '',
    `অবস্থা: ${s.suspended ? `⛔ স্থগিত${s.reason ? ` — ${esc(s.reason)}` : ''}` : s.expired ? '⌛ মেয়াদ শেষ' : '✅ সক্রিয়'}`,
    `লাইসেন্স: ${esc(licenseText(s))}`,
    `বন্ধ ফিচার: ${off.length ? esc(off.join(', ')) : 'নেই'}`,
    `📦 পণ্য ${n('products')} · 🧾 বিক্রি ${n('sales')} · 👥 কাস্টমার ${n('customers')} · 👤 কর্মচারী ${n('staff')}`,
  ]
    .filter((x) => x !== null)
    .join('\n');
  return show(ctx, text, [
    [s.suspended ? btn('✅ চালু করুন', `act:${code}`) : btn('⛔ স্থগিত করুন', `sus?:${code}`)],
    [btn('🎫 লাইসেন্স', `li:${code}`), btn('⚙️ ফিচার', `fe:${code}`)],
    [btn('🔑 পিন রিসেট', `pin?:${code}`), btn('📢 নোটিশ পাঠান', `nt:${code}`)],
    [btn('🔄 রিফ্রেশ', `sh:${code}`)],
    NAV('ls:all:0'),
  ]);
}

function screenSuspendReason(ctx, code) {
  return show(ctx, `<b>⛔ <code>${esc(code)}</code> স্থগিত করবেন?</b>\nকারণ বেছে নিন। দোকানের অ্যাপে এই কারণ দেখাবে।`, [
    ...REASONS.map((r, i) => [btn(r, `susr:${code}:${i}`)]),
    [btn('✍️ নিজে কারণ লিখুন', `susw:${code}`)],
    [btn('❌ বাতিল', `sh:${code}`)],
  ]);
}

function screenLicense(ctx, code) {
  return show(ctx, `<b>🎫 <code>${esc(code)}</code> — লাইসেন্স বেছে নিন</b>\nসময় আজ থেকে গোনা হবে।`, [
    [btn('♾️ আজীবন', `lic:${code}:lifetime`), btn('📅 ১ বছর', `lic:${code}:365d`)],
    [btn('📅 ৩০ দিন', `lic:${code}:30d`), btn('🧪 ৭ দিন ট্রায়াল', `lic:${code}:7d`)],
    [btn('🧪 ২৪ ঘণ্টা ট্রায়াল', `lic:${code}:24h`)],
    [btn('⌛ মেয়াদ শেষ করে দিন', `lic?:${code}:expired`)],
    NAV(`sh:${code}`),
  ]);
}

async function screenFeatures(ctx, code, note = '') {
  const feats = (await val(`shops/${code}/features`)) || {};
  return show(ctx, `${note ? note + '\n\n' : ''}<b>⚙️ <code>${esc(code)}</code> — ফিচার</b>\nচাপলে চালু/বন্ধ হবে।`, [
    ...Object.entries(FEATURES).map(([k, l]) => [btn(`${feats[k] === false ? '🔴 বন্ধ' : '🟢 চালু'} — ${l}`, `ft:${code}:${k}`)]),
    NAV(`sh:${code}`),
  ]);
}

async function screenMaintenance(ctx, note = '') {
  const settings = (await val('global_settings')) || {};
  const on = Boolean(settings.maintenanceMode);
  return show(
    ctx,
    `${note ? note + '\n\n' : ''}<b>🛠 রক্ষণাবেক্ষণ মোড: ${on ? 'চালু' : 'বন্ধ'}</b>\nচালু থাকলে সব দোকানের অ্যাপ সাময়িকভাবে বন্ধ থাকে।${settings.maintenanceMessage ? `\n\nবার্তা: ${esc(settings.maintenanceMessage)}` : ''}`,
    [on ? [btn('✅ বন্ধ করুন (সব অ্যাপ সচল)', 'mtoff')] : [btn('🛠 চালু করুন', 'mt?')], [btn('🏠 মেনু', 'm')]]
  );
}

const confirm = (ctx, text, yes, no) => show(ctx, text, [[btn('✅ হ্যাঁ, নিশ্চিত', yes), btn('❌ না', no)]]);

// ---------- actions ----------

async function suspend(ctx, code, reason, actor) {
  await db().ref(`shops/${code}/status`).update({
    status: 'suspended',
    isSuspended: true,
    reason: reason || 'অ্যাডমিন কর্তৃক স্থগিত',
    updatedAt: ServerValue.TIMESTAMP,
  });
  await audit(code, 'ADMIN_SUSPENDED', reason || '', actor);
  return screenShop(ctx, code, '⛔ দোকান স্থগিত করা হয়েছে।');
}

async function activate(ctx, code, actor) {
  await db().ref(`shops/${code}/status`).update({ status: 'active', isSuspended: false, reason: '', updatedAt: ServerValue.TIMESTAMP });
  await audit(code, 'ADMIN_ACTIVATED', '', actor);
  return screenShop(ctx, code, '✅ দোকান চালু করা হয়েছে।');
}

async function setLicense(ctx, code, key, actor) {
  const l = LICENSES[key];
  if (!l) return screenLicense(ctx, code);
  await db().ref(`shops/${code}/status`).update({
    licenseType: l.type,
    plan: l.plan,
    expiresAt: l.ms === null ? null : Date.now() + l.ms,
    updatedAt: ServerValue.TIMESTAMP,
  });
  await audit(code, 'ADMIN_LICENSE', l.label, actor);
  return screenShop(ctx, code, `🎫 লাইসেন্স সেট হয়েছে: ${l.label}`);
}

async function toggleFeature(ctx, code, feature, actor) {
  if (!FEATURES[feature]) return screenFeatures(ctx, code);
  const next = (await val(`shops/${code}/features/${feature}`)) === false;
  await db().ref(`shops/${code}/features`).update({ [feature]: next, updatedAt: ServerValue.TIMESTAMP });
  await audit(code, 'ADMIN_FEATURE', `${FEATURES[feature]}: ${next ? 'চালু' : 'বন্ধ'}`, actor);
  return screenFeatures(ctx, code, `${next ? '🟢' : '🔴'} ${FEATURES[feature]} ${next ? 'চালু' : 'বন্ধ'} করা হয়েছে।`);
}

function newPin() {
  for (;;) {
    const pin = String(crypto.randomInt(100000, 1000000));
    if (!isWeakPin(pin)) return pin;
  }
}

async function resetPin(ctx, code, actor) {
  const pin = newPin();
  await db().ref().update({
    [`shop_secrets/${code}/masterPinHash`]: hashPin(pin),
    [`shop_secrets/${code}/legacy`]: null,
    [`shops/${code}/security/updatedAt`]: ServerValue.TIMESTAMP,
  });
  await db().ref(`_security/shop_locks/${code}__owner`).remove();
  await getAuth().revokeRefreshTokens(`owner_${code}`).catch(() => {});
  await audit(code, 'ADMIN_PIN_RESET', 'Telegram থেকে মাস্টার পিন রিসেট', actor);
  return show(
    ctx,
    `🔑 <b><code>${esc(code)}</code> এর নতুন মাস্টার পিন:</b>\n\n<code>${pin}</code>\n\nশুধু দোকানের মালিককে জানান। জানানো হয়ে গেলে নিচের বাটনে চেপে এই পিনটি চ্যাট থেকে মুছে দিন।`,
    [[btn('🧹 পিন জানানো হয়েছে, মুছে দিন', `sh:${code}`)]]
  );
}

async function setMaintenance(ctx, on) {
  await db().ref('global_settings').update({ maintenanceMode: on, updatedAt: ServerValue.TIMESTAMP });
  return screenMaintenance(ctx, on ? '🛠 রক্ষণাবেক্ষণ মোড চালু হয়েছে।' : '✅ রক্ষণাবেক্ষণ বন্ধ, সব অ্যাপ সচল।');
}

async function postNotice(target, message, actor) {
  const id = `${Date.now()}_${Math.random().toString(36).slice(2, 6)}`;
  const payload = {
    id,
    title: 'নোটিশ',
    message: message.slice(0, 500),
    type: 'info',
    target,
    isActive: true,
    createdAt: ServerValue.TIMESTAMP,
    createdBy: actor,
  };
  await db().ref(target === 'all' ? `global_notices/${id}` : `shops/${target}/notices/${id}`).set(payload);

  // The Android app reads notices through supportFetch: broadcasts for everyone, support
  // messages for a single shop (its support threads are matched by shop name/phone).
  const text = `📢 ${payload.message}`;
  const ts = Date.now();
  if (target === 'all') {
    await db().ref(`_support/broadcasts/tgadmin_${id}`).set({ text: payload.message, ts });
    return 1;
  }
  const info = (await val(`shops/${target}/info`)) || {};
  const threads = (await val('_support/threads')) || {};
  const ids = Object.keys(threads).filter((t) => {
    const th = threads[t] || {};
    return (th.shopCode && th.shopCode === target) || (info.shopPhone && th.shopPhone === info.shopPhone) || (info.shopName && th.shopName === info.shopName);
  });
  const updates = {};
  for (const t of ids) updates[`_support/messages/${t}/notice_${id}`] = { sender: 'support', text, ts };
  if (ids.length) await db().ref().update(updates);
  return ids.length;
}

async function askText(ctx, userId, state, prompt) {
  await setState(userId, state);
  return show(ctx, `${prompt}\n\n<i>নিচে লিখে পাঠান।</i>`, [[btn('❌ বাতিল', 'cx')]]);
}

// ---------- entry points ----------

function deny(chatId) {
  return send(chatId, '⛔ এই বট শুধু অনুমোদিত অ্যাডমিন ব্যবহার করতে পারবেন।', [[btn('🆔 আমার ID দেখুন', 'myid')]]);
}

/** Private text message: bottom keyboard taps, /start, or answers to a pending question. */
export async function handleAdminMessage(msg) {
  const chatId = msg.chat.id;
  const userId = String(msg.from?.id || '');
  const text = String(msg.text || '').trim();
  const ctx = { chatId };

  if (!adminIds().has(userId)) {
    if (/^\/(myid|start)/i.test(text)) {
      return send(chatId, `আপনার Telegram ID: <code>${esc(userId)}</code>\nঅ্যাডমিন হতে এটি সার্ভারে যোগ করতে হবে।`);
    }
    return deny(chatId);
  }
  const actor = `Telegram ${msg.from?.username ? '@' + msg.from.username : userId}`;

  // Bottom keyboard and /start take priority over a pending question.
  const kbAction = Object.entries(KB).find(([, label]) => label === text)?.[0];
  if (kbAction || /^\/start/i.test(text)) await clearState(userId);
  if (/^\/start/i.test(text) || /^\/menu/i.test(text)) {
    await send(chatId, 'স্বাগতম! নিচের বাটনগুলো সবসময় থাকবে।', null, { reply_markup: MAIN_KEYBOARD });
    return screenMenu(ctx);
  }
  if (kbAction === 'stats') return screenStats(ctx);
  if (kbAction === 'shops') return screenList(ctx, 'all', 0);
  if (kbAction === 'search') return askText(ctx, userId, { action: 'search' }, '🔍 দোকানের কোড, নাম, ফোন বা ইমেইলের অংশ লিখুন:');
  if (kbAction === 'notice') return askText(ctx, userId, { action: 'notice', target: 'all' }, '📢 সব দোকানের অ্যাপে যে নোটিশ দেখাবে, সেটি লিখুন:');
  if (kbAction === 'maint') return screenMaintenance(ctx);

  const state = await getState(userId);
  if (!state) {
    await send(chatId, 'নিচের বাটন ব্যবহার করুন।', null, { reply_markup: MAIN_KEYBOARD });
    return screenMenu(ctx);
  }
  await clearState(userId);
  const input = text.slice(0, 500);
  if (!input) return screenMenu(ctx);

  switch (state.action) {
    case 'search':
      return screenList(ctx, 'all', 0, input);
    case 'notice':
      return confirm(
        ctx,
        `<b>এই নোটিশ পাঠাবেন?</b>\nপ্রাপক: ${state.target === 'all' ? 'সব দোকান' : `<code>${esc(state.target)}</code>`}\n\n${esc(input)}`,
        `ntok:${state.target}`,
        'm'
      ).then(() => setState(userId, { action: 'noticeConfirm', target: state.target, message: input }));
    case 'reason':
      return suspend(ctx, state.code, input, actor);
    default:
      return screenMenu(ctx);
  }
}

/** Inline button press. */
export async function handleAdminCallback(cb) {
  const chatId = cb.message?.chat?.id;
  const userId = String(cb.from?.id || '');
  await tg('answerCallbackQuery', { callback_query_id: cb.id });
  if (!chatId || cb.message?.chat?.type !== 'private') return undefined;
  if (cb.data === 'myid') return send(chatId, `আপনার Telegram ID: <code>${esc(userId)}</code>`);
  if (!adminIds().has(userId)) return deny(chatId);

  const ctx = { chatId, messageId: cb.message.message_id };
  const actor = `Telegram ${cb.from?.username ? '@' + cb.from.username : userId}`;
  const [action, a, b] = String(cb.data || '').split(':');
  const code = a && SHOP_RE.test(a) ? a : null;
  const needShop = async (fn) => (code && (await val(`shops/${code}/info`)) ? fn() : show(ctx, 'দোকানটি পাওয়া যায়নি।', [NAV('ls:all:0')]));

  switch (action) {
    case 'm':
      await clearState(userId);
      return screenMenu(ctx);
    case 'cx':
      await clearState(userId);
      return screenMenu(ctx);
    case 'st':
      return screenStats(ctx);
    case 'ls':
      return screenList(ctx, FILTERS[a] ? a : 'all', Number(b) || 0);
    case 'srch':
      return askText(ctx, userId, { action: 'search' }, '🔍 দোকানের কোড, নাম, ফোন বা ইমেইলের অংশ লিখুন:');
    case 'sh':
      return screenShop(ctx, a);
    case 'sus?':
      return needShop(() => screenSuspendReason(ctx, code));
    case 'susr':
      return needShop(() => suspend(ctx, code, REASONS[Number(b)] || '', actor));
    case 'susw':
      return needShop(() => askText(ctx, userId, { action: 'reason', code }, `✍️ <code>${esc(code)}</code> স্থগিত করার কারণ লিখুন:`));
    case 'act':
      return needShop(() => activate(ctx, code, actor));
    case 'li':
      return needShop(() => screenLicense(ctx, code));
    case 'lic?':
      return needShop(() =>
        confirm(ctx, `⌛ <b><code>${esc(code)}</code> এর লাইসেন্সের মেয়াদ এখনই শেষ করবেন?</b>\nদোকানের অ্যাপ সাথে সাথে বন্ধ হয়ে যাবে।`, `lic:${code}:expired`, `li:${code}`)
      );
    case 'lic':
      return needShop(() => setLicense(ctx, code, b, actor));
    case 'fe':
      return needShop(() => screenFeatures(ctx, code));
    case 'ft':
      return needShop(() => toggleFeature(ctx, code, b, actor));
    case 'pin?':
      return needShop(() =>
        confirm(ctx, `🔑 <b><code>${esc(code)}</code> এর মাস্টার পিন রিসেট করবেন?</b>\nপুরনো পিন আর কাজ করবে না, মালিকের ফোন লগআউট হয়ে যাবে।`, `pin!:${code}`, `sh:${code}`)
      );
    case 'pin!':
      return needShop(() => resetPin(ctx, code, actor));
    case 'nt':
      return needShop(() => askText(ctx, userId, { action: 'notice', target: code }, `📢 <code>${esc(code)}</code> এর অ্যাপে যে নোটিশ দেখাবে, সেটি লিখুন:`));
    case 'ntall':
      return askText(ctx, userId, { action: 'notice', target: 'all' }, '📢 সব দোকানের অ্যাপে যে নোটিশ দেখাবে, সেটি লিখুন:');
    case 'ntok': {
      const state = await getState(userId);
      await clearState(userId);
      if (state?.action !== 'noticeConfirm' || !state.message) return screenMenu(ctx);
      const reached = await postNotice(state.target, state.message, actor);
      const where =
        state.target === 'all'
          ? 'সব দোকানের অ্যাপে (অ্যাপ খোলা থাকলে ১-২ মিনিটের মধ্যে নোটিফিকেশন আসবে)'
          : reached
            ? `<code>${esc(state.target)}</code> এর ${reached}টি ফোনের সাপোর্ট চ্যাটে`
            : `<code>${esc(state.target)}</code> এর ওয়েব অ্যাপে (এই দোকানের কোনো ফোন এখনো সাপোর্ট চ্যাট চালু করেনি)`;
      return show(ctx, `📢 নোটিশ পাঠানো হয়েছে: ${where}।`, [[btn('📢 আরেকটি নোটিশ', state.target === 'all' ? 'ntall' : `nt:${state.target}`), btn('🏠 মেনু', 'm')]]);
    }
    case 'mt':
      return screenMaintenance(ctx);
    case 'mt?':
      return confirm(ctx, '🛠 <b>রক্ষণাবেক্ষণ মোড চালু করবেন?</b>\nসব দোকানের অ্যাপ সাথে সাথে বন্ধ হয়ে যাবে।', 'mton', 'mt');
    case 'mton':
      return setMaintenance(ctx, true);
    case 'mtoff':
      return setMaintenance(ctx, false);
    default:
      return screenMenu(ctx);
  }
}
