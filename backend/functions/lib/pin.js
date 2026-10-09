import crypto from 'node:crypto';

const BN_DIGITS = '০১২৩৪৫৬৭৮৯';
const SCRYPT_PARAMS = { N: 16384, r: 8, p: 1 };
const KEY_LEN = 32;

// Bengali numerals -> ASCII digits, trimmed. Keyboards on Android often emit ০-৯.
export function normalizePin(pin) {
  return String(pin ?? '')
    .trim()
    .replace(/[০-৯]/g, (d) => String(BN_DIGITS.indexOf(d)));
}

export function isValidPinFormat(pin) {
  return /^[0-9]{4,12}$/.test(pin);
}

// Weak PINs that we refuse to accept as new PINs.
const WEAK = new Set(['0000', '1111', '1234', '4321', '1122', '2222', '9999', '000000', '123456', '111111', '654321']);
export function isWeakPin(pin) {
  return WEAK.has(pin) || /^(\d)\1+$/.test(pin);
}

// PBKDF2 keeps each check around 2 ms, inside the Cloudflare Workers free-plan CPU budget
// (scrypt took ~40 ms). Hashes live only under /shop_secrets, which no client can read.
const PBKDF2_ITER = 10000;

export function hashPin(pin) {
  const salt = crypto.randomBytes(16);
  const key = crypto.pbkdf2Sync(pin, salt, PBKDF2_ITER, KEY_LEN, 'sha256');
  return `pbkdf2$${PBKDF2_ITER}$${salt.toString('base64')}$${key.toString('base64')}`;
}

/** True for hashes in an older format; they are replaced after the next successful login. */
export function needsRehash(stored) {
  return typeof stored === 'string' && !stored.startsWith(`pbkdf2$${PBKDF2_ITER}$`);
}

export function verifyPinHash(pin, stored) {
  if (typeof stored !== 'string') return false;
  const [kind, n, saltB64, keyB64] = stored.split('$');
  if (!saltB64 || !keyB64) return false;
  const expected = Buffer.from(keyB64, 'base64');
  const salt = Buffer.from(saltB64, 'base64');
  let actual;
  if (kind === 'pbkdf2') {
    actual = crypto.pbkdf2Sync(pin, salt, Number(n), expected.length, 'sha256');
  } else if (kind === 'scrypt') {
    actual = crypto.scryptSync(pin, salt, expected.length, { ...SCRYPT_PARAMS, N: Number(n), maxmem: 64 * 1024 * 1024 });
  } else {
    return false;
  }
  return actual.length === expected.length && crypto.timingSafeEqual(actual, expected);
}

function sha256Hex(s) {
  return crypto.createHash('sha256').update(s, 'utf8').digest('hex');
}

function safeEqual(a, b) {
  const ba = Buffer.from(String(a));
  const bb = Buffer.from(String(b));
  return ba.length === bb.length && crypto.timingSafeEqual(ba, bb);
}

// Legacy check against the plaintext / SHA-256 values the Android app and old web stored
// under shops/{code}/security and shops/{code}/info. Used only until a shop is migrated.
export function matchesLegacyMasterPin(pin, sec = {}, info = {}) {
  const plains = [sec.masterPin, info.pinCode, info.pin, info.masterPin]
    .filter((v) => v !== undefined && v !== null && String(v).trim() !== '')
    .map((v) => normalizePin(v));
  if (plains.some((p) => safeEqual(p, pin))) return true;
  const hashes = [sec.masterPinHash].filter(Boolean).map((h) => String(h).trim().toLowerCase());
  return hashes.some((h) => safeEqual(h, sha256Hex(pin)));
}
