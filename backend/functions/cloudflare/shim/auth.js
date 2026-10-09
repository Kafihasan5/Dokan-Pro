/**
 * The subset of firebase-admin/auth the server code uses: custom tokens, ID-token verification
 * (including the revocation check) and refresh-token revocation, over Google's REST APIs.
 */
import { accessToken, env, signJwt } from './google.js';

const TOOLKIT_AUD = 'https://identitytoolkit.googleapis.com/google.identity.identitytoolkit.v1.IdentityToolkit';
const JWKS_URL = 'https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com';

class AuthError extends Error {
  constructor(code, message) {
    super(message);
    this.code = `auth/${code}`;
  }
}

async function toolkit(method, body) {
  const res = await fetch(`https://identitytoolkit.googleapis.com/v1/projects/${env().projectId}/${method}`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${await accessToken()}`, 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  const json = await res.json().catch(() => ({}));
  if (!res.ok) throw new AuthError('internal-error', json.error?.message || `Identity Toolkit ${method} failed`);
  return json;
}

let jwks = { keys: {}, exp: 0 };
async function publicKey(kid) {
  if (!jwks.keys[kid] || Date.now() > jwks.exp) {
    const res = await fetch(JWKS_URL);
    const maxAge = Number(/max-age=(\d+)/.exec(res.headers.get('cache-control') || '')?.[1] || 3600);
    const { keys = [] } = await res.json();
    const imported = {};
    for (const k of keys) {
      imported[k.kid] = await crypto.subtle.importKey('jwk', k, { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['verify']);
    }
    jwks = { keys: imported, exp: Date.now() + maxAge * 1000 };
  }
  return jwks.keys[kid];
}

const fromB64url = (s) => Uint8Array.from(atob(s.replace(/-/g, '+').replace(/_/g, '/') + '==='.slice((s.length + 3) % 4)), (c) => c.charCodeAt(0));
const decodePart = (s) => JSON.parse(new TextDecoder().decode(fromB64url(s)));

const auth = {
  /** Same token firebase-admin produces: signed by the service account, exchanged by the client SDK. */
  async createCustomToken(uid, claims) {
    const now = Math.floor(Date.now() / 1000);
    const { clientEmail } = env();
    return signJwt({ iss: clientEmail, sub: clientEmail, aud: TOOLKIT_AUD, iat: now, exp: now + 3600, uid, ...(claims ? { claims } : {}) });
  },

  async verifyIdToken(token, checkRevoked = false) {
    const parts = String(token || '').split('.');
    if (parts.length !== 3) throw new AuthError('argument-error', 'Malformed ID token');
    const header = decodePart(parts[0]);
    const payload = decodePart(parts[1]);
    const { projectId } = env();
    const now = Math.floor(Date.now() / 1000);
    if (header.alg !== 'RS256') throw new AuthError('argument-error', 'Bad algorithm');
    if (payload.aud !== projectId || payload.iss !== `https://securetoken.google.com/${projectId}`) {
      throw new AuthError('argument-error', 'Token is for another project');
    }
    if (!payload.sub || typeof payload.sub !== 'string' || payload.sub.length > 128) throw new AuthError('argument-error', 'Bad subject');
    if (payload.exp <= now || payload.iat > now + 300 || payload.auth_time > now + 300) throw new AuthError('id-token-expired', 'Token expired');
    const key = await publicKey(header.kid);
    if (!key) throw new AuthError('argument-error', 'Unknown signing key');
    const ok = await crypto.subtle.verify('RSASSA-PKCS1-v1_5', key, fromB64url(parts[2]), new TextEncoder().encode(`${parts[0]}.${parts[1]}`));
    if (!ok) throw new AuthError('argument-error', 'Bad signature');

    if (checkRevoked) {
      const { users = [] } = await toolkit('accounts:lookup', { localId: [payload.sub] });
      const user = users[0];
      if (!user) throw new AuthError('user-not-found', 'User not found');
      if (user.disabled) throw new AuthError('user-disabled', 'User disabled');
      if (user.validSince && payload.auth_time < Number(user.validSince)) throw new AuthError('id-token-revoked', 'Token revoked');
    }
    return { ...payload, uid: payload.sub };
  },

  async revokeRefreshTokens(uid) {
    await toolkit('accounts:update', { localId: uid, validSince: String(Math.floor(Date.now() / 1000)) });
  },
};

export const getAuth = () => auth;
