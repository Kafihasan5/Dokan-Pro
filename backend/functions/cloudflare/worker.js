/**
 * Dokan Pro server on Cloudflare Workers (free plan: 100,000 requests/day, no card).
 * Runs the same handlers as before (../lib); firebase-admin and firebase-functions are replaced
 * by the REST-based shims in ./shim (see wrangler.toml [alias]).
 *
 * Speaks the Firebase "callable" protocol at /api/<name>, so the apps call it with
 * httpsCallableFromURL / getHttpsCallableFromUrl exactly like a Cloud Function.
 *
 * Secrets (wrangler secret put): FIREBASE_PROJECT_ID, FIREBASE_CLIENT_EMAIL, FIREBASE_PRIVATE_KEY,
 * FIREBASE_DATABASE_URL, TELEGRAM_BOT_TOKEN, TELEGRAM_WEBHOOK_SECRET, SUPPORT_CHAT_ID, TELEGRAM_ADMIN_IDS.
 */
import { getAuth } from 'firebase-admin/auth';
import { HttpsError } from 'firebase-functions/v2/https';
import * as auth from '../lib/handlers.js';
import * as support from '../lib/support.js';

const CALLABLES = {
  shopLogin: auth.shopLogin,
  findExistingOwnerShop: auth.findExistingOwnerShop,
  registerShop: auth.registerShop,
  verifyOwnerPin: auth.verifyOwnerPin,
  changeOwnerPin: auth.changeOwnerPin,
  setStaffPin: auth.setStaffPin,
  setSharedStaffPin: auth.setSharedStaffPin,
  removeStaff: auth.removeStaff,
  adminSetShopPin: auth.adminSetShopPin,
  adminCreateShop: auth.adminCreateShop,
  adminDeleteShop: auth.adminDeleteShop,
  supportOpen: support.supportOpen,
  supportSend: support.supportSend,
  supportFetch: support.supportFetch,
};

function corsHeaders(req) {
  const allowed = (process.env.ALLOWED_ORIGINS || '').split(',').map((s) => s.trim()).filter(Boolean);
  const origin = req.headers.get('origin') || '';
  const allow = !allowed.length || allowed.includes(origin) || /^http:\/\/localhost:\d+$/.test(origin);
  return {
    'Access-Control-Allow-Origin': allow ? origin || '*' : 'null',
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
    'Access-Control-Allow-Headers':
      'Content-Type, Authorization, X-Firebase-AppCheck, X-Firebase-Instance-ID-Token, Firebase-Instance-ID-Token, X-Firebase-GMPID, X-Client-Version, X-Thread-Id, X-Thread-Key, X-Caption, X-Message-Id',
    'Access-Control-Max-Age': '3600',
    Vary: 'Origin',
  };
}

/**
 * Old app versions still call the Netlify URL, which now only proxies here. Netlify adds a secret
 * header; only then is its forwarded client IP trusted (otherwise anyone could fake their IP and
 * dodge the wrong-PIN limits).
 */
function clientIp(req) {
  const secret = process.env.PROXY_SECRET || '';
  const given = req.headers.get('x-proxy-secret') || '';
  if (secret && given.length === secret.length && given === secret) {
    const fwd = req.headers.get('x-nf-client-connection-ip') || (req.headers.get('x-forwarded-for') || '').split(',')[0];
    if (fwd?.trim()) return fwd.trim();
  }
  return req.headers.get('cf-connecting-ip') || 'unknown';
}

const json = (status, body, headers = {}) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json', ...headers } });

function errorResponse(err, headers) {
  if (err instanceof HttpsError) {
    const { canonicalName, status } = err.httpErrorCode;
    return json(status, { error: { status: canonicalName, message: err.message } }, headers);
  }
  console.error(err?.stack || String(err));
  return json(500, { error: { status: 'INTERNAL', message: 'INTERNAL' } }, headers);
}

async function handleMedia(name, req) {
  // The web version calls these cross-origin, so they need CORS like the callables.
  const cors = corsHeaders(req);
  if (req.method === 'OPTIONS') return new Response(null, { status: 204, headers: cors });
  const thread = { threadId: req.headers.get('x-thread-id') || '', threadKey: req.headers.get('x-thread-key') || '' };
  try {
    if (name === 'supportUpload') {
      if (req.method !== 'POST') throw new HttpsError('invalid-argument', 'POST only');
      if (Number(req.headers.get('content-length') || 0) > support.MAX_UPLOAD_BYTES) {
        throw new HttpsError('invalid-argument', 'ফাইল ১০ MB এর বেশি হতে পারবে না।');
      }
      const bytes = new Uint8Array(await req.arrayBuffer());
      let caption = '';
      try {
        caption = decodeURIComponent(req.headers.get('x-caption') || '');
      } catch {
        /* ignore bad caption */
      }
      return json(200, { result: await support.supportUpload({ ...thread, caption, bytes }) }, cors);
    }
    const file = await support.supportMedia({ ...thread, messageId: req.headers.get('x-message-id') || '' });
    return new Response(file.stream, {
      headers: {
        'Content-Type': file.mime,
        'Cache-Control': 'private, no-store',
        ...cors,
        ...(file.size ? { 'Content-Length': String(file.size) } : {}),
      },
    });
  } catch (err) {
    return errorResponse(err, cors);
  }
}

export default {
  async fetch(req) {
    const url = new URL(req.url);
    const name = /^\/api\/([A-Za-z]+)$/.exec(url.pathname)?.[1] || '';

    if (name === 'telegramWebhook') {
      const body = await req.json().catch(() => null);
      const r = await support.telegramWebhook({
        method: req.method,
        secretHeader: req.headers.get('x-telegram-bot-api-secret-token'),
        body,
      });
      return new Response(r.body, { status: r.status });
    }

    if (name === 'supportUpload' || name === 'supportMedia') return handleMedia(name, req);

    const headers = corsHeaders(req);
    if (req.method === 'OPTIONS') return new Response(null, { status: 204, headers });
    const handler = Object.hasOwn(CALLABLES, name) ? CALLABLES[name] : null;
    if (!handler) return json(404, { error: { status: 'NOT_FOUND', message: 'NOT_FOUND' } }, headers);
    if (req.method !== 'POST') return json(405, { error: { status: 'INVALID_ARGUMENT', message: 'POST only' } }, headers);

    try {
      const body = await req.json().catch(() => {
        throw new HttpsError('invalid-argument', 'Bad request');
      });

      // Same verification Cloud Functions does: a valid, non-revoked Firebase ID token.
      let authCtx;
      const bearer = (req.headers.get('authorization') || '').match(/^Bearer (.+)$/i)?.[1];
      if (bearer) {
        try {
          const decoded = await getAuth().verifyIdToken(bearer, true);
          authCtx = { uid: decoded.uid, token: decoded };
        } catch {
          throw new HttpsError('unauthenticated', 'সেশনের মেয়াদ শেষ। আবার লগইন করুন।');
        }
      }

      const ip = clientIp(req);
      const result = await handler({
        data: body?.data ?? null,
        auth: authCtx,
        rawRequest: { ip, headers: { 'x-forwarded-for': ip } },
      });
      return json(200, { result: result ?? null }, headers);
    } catch (err) {
      return errorResponse(err, headers);
    }
  },
};
