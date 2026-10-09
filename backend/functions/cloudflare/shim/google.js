/**
 * Service-account helpers for Cloudflare Workers (no firebase-admin there): RS256 signing with
 * WebCrypto and a cached OAuth access token for the Realtime Database and Identity Toolkit REST APIs.
 * Credentials come from the Worker secrets FIREBASE_PROJECT_ID / FIREBASE_CLIENT_EMAIL /
 * FIREBASE_PRIVATE_KEY / FIREBASE_DATABASE_URL (exposed on process.env by nodejs_compat).
 */
const SCOPES = [
  'https://www.googleapis.com/auth/firebase.database',
  'https://www.googleapis.com/auth/userinfo.email',
  'https://www.googleapis.com/auth/identitytoolkit',
  'https://www.googleapis.com/auth/cloud-platform',
].join(' ');

export const env = () => ({
  projectId: process.env.FIREBASE_PROJECT_ID,
  clientEmail: process.env.FIREBASE_CLIENT_EMAIL,
  databaseURL: String(process.env.FIREBASE_DATABASE_URL || '').replace(/\/+$/, ''),
});

// Env UIs store the PEM key with literal "\n" (sometimes double-escaped or quoted); restore real newlines.
function normalizePrivateKey(raw = '') {
  return String(raw)
    .replace(/\\\r?\n/g, '\n')
    .trim()
    .replace(/^"(.*)"$/s, '$1')
    .replace(/\\\\n/g, '\n')
    .replace(/\\n/g, '\n')
    .replace(/\\+$/, '');
}

const b64url = (bytes) =>
  btoa(String.fromCharCode(...new Uint8Array(bytes))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
const b64urlJson = (obj) => b64url(new TextEncoder().encode(JSON.stringify(obj)));

let signingKey;
async function privateKey() {
  if (signingKey) return signingKey;
  const pem = normalizePrivateKey(process.env.FIREBASE_PRIVATE_KEY);
  const body = pem.replace(/-----(BEGIN|END) PRIVATE KEY-----/g, '').replace(/\s+/g, '');
  const der = Uint8Array.from(atob(body), (c) => c.charCodeAt(0));
  signingKey = await crypto.subtle.importKey('pkcs8', der, { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['sign']);
  return signingKey;
}

/** Signs a JWT with the service account key. */
export async function signJwt(payload) {
  const unsigned = `${b64urlJson({ alg: 'RS256', typ: 'JWT' })}.${b64urlJson(payload)}`;
  const sig = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', await privateKey(), new TextEncoder().encode(unsigned));
  return `${unsigned}.${b64url(sig)}`;
}

let cached = { token: '', exp: 0 };
let pending;

/** OAuth access token for Google APIs, cached for the life of the isolate. */
export async function accessToken() {
  if (cached.token && Date.now() < cached.exp - 60_000) return cached.token;
  pending ??= (async () => {
    const now = Math.floor(Date.now() / 1000);
    const assertion = await signJwt({
      iss: env().clientEmail,
      scope: SCOPES,
      aud: 'https://oauth2.googleapis.com/token',
      iat: now,
      exp: now + 3600,
    });
    const res = await fetch('https://oauth2.googleapis.com/token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: `grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=${assertion}`,
    });
    const json = await res.json();
    if (!json.access_token) throw new Error(`Google auth failed: ${json.error || res.status}`);
    cached = { token: json.access_token, exp: Date.now() + json.expires_in * 1000 };
    return cached.token;
  })().finally(() => {
    pending = undefined;
  });
  return pending;
}
