// Input hygiene helpers shared by the context and pages.

const BN_DIGITS = '০১২৩৪৫৬৭৮৯';

// Bengali numerals -> ASCII, so a PIN typed on a Bangla keyboard matches the stored one.
export function normalizePin(pin) {
  return String(pin ?? '')
    .trim()
    .replace(/[০-৯]/g, (d) => String(BN_DIGITS.indexOf(d)));
}

export function isValidPin(pin) {
  return /^[0-9]{4,12}$/.test(pin);
}

const WEAK_PINS = new Set(['0000', '1111', '1234', '4321', '1122', '2222', '9999', '000000', '123456', '111111', '654321']);
export function isWeakPin(pin) {
  return WEAK_PINS.has(pin) || /^(\d)\1+$/.test(pin);
}

const MAX_AMOUNT = 1_000_000_000; // 100 crore taka; anything above is a typo or tampering.

// Returns a finite, non-negative number or throws a user-facing error.
export function cleanAmount(value, label = 'পরিমাণ') {
  const n = Number(value ?? 0);
  if (!Number.isFinite(n) || n < 0 || n > MAX_AMOUNT) {
    throw new Error(`${label} সঠিক নয়।`);
  }
  return n;
}

export function cleanText(value, max = 200) {
  return String(value ?? '')
    .replace(/[\u0000-\u001f\u007f]/g, ' ')
    .trim()
    .slice(0, max);
}

// Prevent CSV/formula injection when a spreadsheet opens an exported file.
export function csvCell(value) {
  let s = String(value ?? '');
  // Negative numbers stay numeric; anything else starting with a formula trigger is neutralised.
  if (/^[=+@\t\r]/.test(s) || /^-[^0-9.]/.test(s)) s = `'${s}`;
  return `"${s.replace(/"/g, '""')}"`;
}

export function toCsv(rows) {
  return rows.map((r) => r.map(csvCell).join(',')).join('\r\n');
}

export function downloadCsv(rows, filename) {
  const blob = new Blob(['\uFEFF' + toCsv(rows)], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

// Only allow opening links we build ourselves (WhatsApp / tel).
export function openExternal(url) {
  const u = new URL(url);
  if (!['https://wa.me', 'https://api.whatsapp.com'].includes(u.origin)) return;
  window.open(u.toString(), '_blank', 'noopener,noreferrer');
}
