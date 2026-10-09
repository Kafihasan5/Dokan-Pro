/**
 * Dokan Web - Utilities and Formatters
 */

// Convert Poisha (stored as Long/Int in Firebase, e.g. 50000 = 500.00 Tk) to Taka
export function poishaToTaka(poisha) {
  if (!poisha || isNaN(poisha)) return 0;
  return Number((poisha / 100).toFixed(2));
}

// Convert Taka (UI input) to Poisha for Firebase storage
export function takaToPoisha(taka) {
  if (!taka || isNaN(taka)) return 0;
  return Math.round(Number(taka) * 100);
}

// Format Taka with Symbol (e.g., ৳ ৫০০.০০)
export function formatCurrency(amount, currency = '৳') {
  if (amount === null || amount === undefined || isNaN(amount)) return `${currency} 0.00`;
  const val = Number(amount).toLocaleString('en-US', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
  return `${currency} ${val}`;
}

// Format Date & Time for Receipts & Tables
export function formatDateTime(timestamp) {
  if (!timestamp) return '';
  const date = new Date(timestamp);
  return date.toLocaleString('en-GB', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: true,
  });
}

// Format Date Only (e.g. 19 Sep, 2026)
export function formatDate(timestamp) {
  if (!timestamp) return '';
  const date = new Date(timestamp);
  return date.toLocaleDateString('en-GB', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  });
}

// Generate Invoice Number (e.g. INV-260919-8472)
export function generateInvoiceNumber() {
  const now = new Date();
  const yr = String(now.getFullYear()).slice(-2);
  const mo = String(now.getMonth() + 1).padStart(2, '0');
  const da = String(now.getDate()).padStart(2, '0');
  const rnd = Math.floor(1000 + Math.random() * 9000);
  return `INV-${yr}${mo}${da}-${rnd}`;
}

// Clean and Sanitize Firebase Keys
export function sanitizeFirebaseKey(input) {
  if (!input) return '';
  return input.trim().replace(/[.#$\[\]\/]/g, '_');
}

// Encode email for Firebase indexing (matching Android app)
export function encodeEmailKey(email) {
  if (!email) return '';
  return email.trim().toLowerCase().replace(/\./g, '_dot_').replace(/@/g, '_at_');
}

// Web Audio Beep Feedback for Barcode Scan and Checkout
export function playSuccessBeep() {
  try {
    const ctx = new (window.AudioContext || window.webkitAudioContext)();
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.type = 'sine';
    osc.frequency.setValueAtTime(880, ctx.currentTime); // A5 note
    gain.gain.setValueAtTime(0.15, ctx.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 0.15);
    osc.connect(gain);
    gain.connect(ctx.destination);
    osc.start();
    osc.stop(ctx.currentTime + 0.15);
  } catch (_) {}
}

export function playErrorBeep() {
  try {
    const ctx = new (window.AudioContext || window.webkitAudioContext)();
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.type = 'sawtooth';
    osc.frequency.setValueAtTime(220, ctx.currentTime);
    gain.gain.setValueAtTime(0.2, ctx.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 0.25);
    osc.connect(gain);
    gain.connect(ctx.destination);
    osc.start();
    osc.stop(ctx.currentTime + 0.25);
  } catch (_) {}
}
