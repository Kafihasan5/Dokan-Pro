import React, { useEffect, useRef, useState } from 'react';
import { BrowserMultiFormatReader } from '@zxing/browser';
import { BarcodeFormat, DecodeHintType } from '@zxing/library';
import { Camera, Keyboard, Repeat, X } from 'lucide-react';

const FORMATS = [
  BarcodeFormat.QR_CODE, BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.UPC_E,
  BarcodeFormat.CODE_128, BarcodeFormat.CODE_39, BarcodeFormat.CODE_93, BarcodeFormat.ITF, BarcodeFormat.DATA_MATRIX,
];
const NATIVE_FORMATS = ['qr_code', 'ean_13', 'ean_8', 'upc_a', 'upc_e', 'code_128', 'code_39', 'code_93', 'itf', 'data_matrix'];

function makeReader() {
  const hints = new Map();
  hints.set(DecodeHintType.POSSIBLE_FORMATS, FORMATS);
  hints.set(DecodeHintType.TRY_HARDER, true);
  return new BrowserMultiFormatReader(hints);
}

async function makeNativeDetector() {
  try {
    if (!('BarcodeDetector' in window)) return null;
    const supported = await window.BarcodeDetector.getSupportedFormats();
    const formats = NATIVE_FORMATS.filter((f) => supported.includes(f));
    return formats.length ? new window.BarcodeDetector({ formats }) : null;
  } catch {
    return null;
  }
}

/**
 * Camera barcode / QR scanner for the POS (iPhone Safari, Android Chrome, desktop).
 * - Live: scans the framed centre of the camera picture several times a second
 *   (native BarcodeDetector where available, ZXing otherwise).
 * onDetected(code) returns true when the code matched a product.
 */
export default function CameraScanner({ onDetected, onClose, mode = 'sale' }) {
  // mode "capture": read one code (e.g. for a new product's barcode field) and close.
  const capture = mode === 'capture';
  const videoRef = useRef(null);
  const lastRef = useRef({ code: '', at: 0 });
  const continuousRef = useRef(true);
  const onDetectedRef = useRef(onDetected);
  const [continuous, setContinuous] = useState(true);
  const [status, setStatus] = useState({ tone: 'info', text: 'ক্যামেরা চালু হচ্ছে…' });
  const [scanning, setScanning] = useState(false);
  const [manual, setManual] = useState('');
  const [cameraError, setCameraError] = useState('');

  continuousRef.current = continuous;
  onDetectedRef.current = onDetected;

  const handleCode = (raw) => {
    const code = String(raw || '').trim();
    const now = Date.now();
    if (!code) return;
    // While the same code stays in front of the camera it is counted once, however long it is held.
    // "at" is refreshed on every sighting, so it only counts again after it has left the view for 1.5s.
    if (code === lastRef.current.code && now - lastRef.current.at < 1500) {
      lastRef.current.at = now;
      return;
    }
    lastRef.current = { code, at: now };
    if (navigator.vibrate) navigator.vibrate(60);
    if (capture) {
      onDetectedRef.current(code);
      onClose();
      return;
    }
    const ok = onDetectedRef.current(code);
    setStatus(ok ? { tone: 'ok', text: `✓ ${code} — কার্টে যোগ হয়েছে` } : { tone: 'err', text: `কোড পাওয়া গেছে: ${code} — কিন্তু এই কোডের কোনো পণ্য নেই` });
    if (ok && !continuousRef.current) onClose();
  };

  useEffect(() => {
    let stream;
    let stopped = false;
    let timer;
    const reader = makeReader();
    const canvas = document.createElement('canvas');
    const ctx = canvas.getContext('2d', { willReadFrequently: true });

    (async () => {
      try {
        stream = await navigator.mediaDevices.getUserMedia({
          audio: false,
          video: { facingMode: { ideal: 'environment' }, width: { ideal: 1280 }, height: { ideal: 720 } },
        });
      } catch (err) {
        if (stopped) return;
        const denied = err?.name === 'NotAllowedError' || err?.name === 'SecurityError';
        setCameraError(
          denied
            ? 'ক্যামেরার অনুমতি দেওয়া হয়নি। Safari/ব্রাউজারের সেটিংসে এই সাইটকে ক্যামেরার অনুমতি দিন, অথবা নিচে কোড লিখুন।'
            : 'ক্যামেরা চালু করা যায়নি। নিচে কোড লিখে যোগ করতে পারেন।'
        );
        return;
      }
      if (stopped) {
        stream.getTracks().forEach((t) => t.stop());
        return;
      }
      const video = videoRef.current;
      video.srcObject = stream;
      video.setAttribute('playsinline', 'true');
      video.muted = true;
      await video.play().catch(() => {});
      const native = await makeNativeDetector();
      setScanning(true);
      setStatus({ tone: 'info', text: 'বারকোড বা QR কোডটি ফ্রেমের ভেতরে ধরুন' });

      const tick = async () => {
        if (stopped) return;
        try {
          const w = video.videoWidth;
          const h = video.videoHeight;
          if (w && h) {
            if (native) {
              const found = await native.detect(video);
              if (found?.length) handleCode(found[0].rawValue);
            } else {
              // Centre crop (where the frame is drawn): smaller image, faster and more reliable.
              const cw = Math.round(w * 0.8);
              const ch = Math.round(h * 0.7);
              canvas.width = cw;
              canvas.height = ch;
              ctx.drawImage(video, (w - cw) / 2, (h - ch) / 2, cw, ch, 0, 0, cw, ch);
              try {
                handleCode(reader.decodeFromCanvas(canvas).getText());
              } catch {
                /* nothing in this frame */
              }
            }
          }
        } catch {
          /* keep scanning */
        }
        timer = setTimeout(tick, native ? 150 : 220);
      };
      tick();
    })();

    return () => {
      stopped = true;
      clearTimeout(timer);
      stream?.getTracks().forEach((t) => t.stop());
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const submitManual = (e) => {
    e.preventDefault();
    const code = manual.trim();
    if (!code) return;
    lastRef.current = { code: '', at: 0 };
    handleCode(code);
    setManual('');
  };

  return (
    <div className="scanner-overlay" role="dialog" aria-modal="true" aria-label="বারকোড / QR স্ক্যানার">
      <div className="scanner-sheet">
        <div className="scanner-head">
          <span><Camera size={18} /> বারকোড / QR স্ক্যানার</span>
          <button type="button" className="scanner-close" onClick={onClose} aria-label="বন্ধ করুন"><X size={18} /></button>
        </div>

        <div className="scanner-view">
          {cameraError ? (
            <div className="scanner-error">{cameraError}</div>
          ) : (
            <>
              <video ref={videoRef} className="scanner-video" muted playsInline autoPlay />
              <div className="scanner-frame" aria-hidden="true"><i /></div>
              {scanning && <span className="scanner-live"><b /> স্ক্যান হচ্ছে…</span>}
            </>
          )}
        </div>

        <div className={`scanner-status-line ${status.tone}`}>{status.text}</div>


        {!capture && <label className="scanner-toggle">
          <span><Repeat size={16} /> একটানা স্ক্যান <small>(পরপর অনেক পণ্য)</small></span>
          <input type="checkbox" checked={continuous} onChange={(e) => setContinuous(e.target.checked)} />
        </label>}

        {!capture && <form className="scanner-manual" onSubmit={submitManual}>
          <Keyboard size={17} />
          <input value={manual} onChange={(e) => setManual(e.target.value)} placeholder="অথবা কোড লিখুন" inputMode="text" autoComplete="off" />
          <button type="submit">যোগ</button>
        </form>}

        <button type="button" className="scanner-done" onClick={onClose}>{capture ? 'বাতিল' : 'শেষ — কার্টে ফিরে যান'}</button>
      </div>
    </div>
  );
}
