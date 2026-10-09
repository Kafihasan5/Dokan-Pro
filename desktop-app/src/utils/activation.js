import { callable } from '../config/firebase';

const SUPABASE_URL = import.meta.env.VITE_SUPABASE_URL || 'https://fbkyfxghhzjonihnntip.supabase.co';
const SUPABASE_ANON_KEY = import.meta.env.VITE_SUPABASE_ANON_KEY || 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImZia3lmeGdoaHpqb25paG5udGlwIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODk0NDEyMTYsImV4cCI6MjEwNTAxNzIxNn0.Sn_4D24ZyVRbqa34WyV9lXZ0EBlHmqwVoSU0csRSPbo';

export function getDesktopDeviceId() {
  const key = 'dokan_desktop_device_id';
  let id = localStorage.getItem(key);
  if (!id) {
    id = globalThis.crypto?.randomUUID?.() || `DESKTOP-${Date.now()}-${Math.random().toString(36).slice(2)}`;
    localStorage.setItem(key, id);
  }
  return id;
}

const DEMO_USED_MESSAGE = 'এই ডিভাইসে ফ্রি ডেমো আগেই ব্যবহার করা হয়েছে। Dokan-Pro নিয়মিত ব্যবহার করতে লাইসেন্স সক্রিয় করুন।';

/** Prices are never shown in the desktop app, even if a server message mentions one. */
function withoutPrice(message) {
  return String(message || '')
    .replace(/\s*[(（]?\s*(৳|টাকা|tk\.?|bdt)\s*[০-৯0-9][০-৯0-9,.]*\s*[)）]?/gi, ' ')
    .replace(/\s*[(（]?\s*[০-৯0-9][০-৯0-9,.]*\s*(৳|টাকা|tk\.?|bdt)\s*[)）]?/gi, ' ')
    .replace(/\s{2,}/g, ' ')
    .replace(/\s+([।.,])/g, '$1')
    .trim();
}

async function callLicenseRpc(name, payload) {
  const response = await fetch(`${SUPABASE_URL}/rest/v1/rpc/${name}`, {
    method: 'POST',
    headers: {
      apikey: SUPABASE_ANON_KEY,
      Authorization: `Bearer ${SUPABASE_ANON_KEY}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(withoutPrice(body.message || body.error) || `সার্ভার সংযোগ ব্যর্থ হয়েছে (${response.status})`);
  return body && typeof body === 'object' && 'message' in body ? { ...body, message: withoutPrice(body.message) } : body;
}

export async function activateDesktopLicense(email) {
  const normalizedEmail = email.trim().toLowerCase();
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(normalizedEmail)) {
    throw new Error('সঠিক লাইসেন্স ইমেইল লিখুন।');
  }
  const result = await callLicenseRpc('activate_app_license', {
    p_email: normalizedEmail,
    p_device_id: getDesktopDeviceId(),
    p_device_model: 'Dokan Pro Desktop',
  });
  if (!result?.success) throw new Error(result?.message || 'লাইসেন্স সক্রিয় করা যায়নি। ইমেইল যাচাই করে আবার চেষ্টা করুন।');
  localStorage.removeItem('dokan_desktop_trial_expires_at');
  localStorage.setItem('dokan_desktop_license_email', normalizedEmail);
  return result;
}

/** Returns true/false for a verified shop lookup, or null if the cloud check is unavailable. */
export async function findExistingOwnerShop(email) {
  const result = await callable('findExistingOwnerShop')({
    email: email.trim().toLowerCase(),
    deviceId: getDesktopDeviceId(),
  });
  return typeof result.data?.hasExistingShop === 'boolean' ? result.data.hasExistingShop : null;
}

export async function startDesktopTrial() {
  const result = await callLicenseRpc('start_or_verify_device_demo', {
    p_device_id: getDesktopDeviceId(),
    p_device_model: 'Dokan Pro Desktop',
  });
  const seconds = Number(result?.remaining_seconds || 0);
  if (!result?.success || seconds <= 0) throw new Error(DEMO_USED_MESSAGE);
  const expiresAt = Date.now() + seconds * 1000;
  localStorage.setItem('dokan_desktop_trial_expires_at', String(expiresAt));
  return { expiresAt, remainingSeconds: seconds };
}
