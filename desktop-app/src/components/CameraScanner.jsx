import React, { useEffect, useRef, useState } from 'react';
import { BrowserMultiFormatReader } from '@zxing/browser';
import { BarcodeFormat, DecodeHintType } from '@zxing/library';
import { Camera, ImageUp, Keyboard, Repeat, X } from 'lucide-react';

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
 * - Photo: takes a still picture with the phone's own camera app (best on iPhone) and reads it.
 * onDetected(code) returns true when the code matched a product.
 */
export default function CameraScanner({ onDetected, onClose }) {
  const videoRef = useRef(null);
  const photoRef = useRef(null);
  const lastRef = useRef({ code: '', at: 0 });
  const continuousRef = useRef(true);
  const onDetectedRef = useRef(onDetected);
  const [continuous, setContinuous] = useState(true);
  const [status, setStatus] = useState({ tone: 'info', text: 'ক্যামেরা চালু হচ্ছে…' });
  const [scanning, setScanning] = useState(false);
  const [manual, setManual] = useState('');
  const [cameraError, setCameraError] = useState('');
  const [reading, setReading] = useState(false);

  continuousRef.current = continuous;
  onDetectedRef.current = onDetected;

  const handleCode = (raw) => {
    const code = String(raw || '').trim();
    const now = Date.now();
    // The same label stays in view for a while; ignore repeats for 2s.
    if (!code || (code === lastRef.current.code && now - lastRef.current.at < 2000)) return;
    lastRef.current = { code, at: now };
    if (navigator.vibrate) navigator.vibrate(60);
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
            ? 'ক্যামেরার অনুমতি দেওয়া হয়নি। Safari/ব্রাউজারের সেটিংসে এই সাইটকে ক্যামেরার অনুমতি দিন, অথবা নিচের "ছবি তুলে স্ক্যান" ব্যবহার করুন।'
            : 'ক্যামেরা চালু করা যায়নি। নিচের "ছবি তুলে স্ক্যান" বা কোড লিখে যোগ করুন।'
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

  // Still photo from the phone camera: autofocus + full resolution, very reliable on iPhone.
  const readPhoto = async (e) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    setReading(true);
    setStatus({ tone: 'info', text: 'ছবি থেকে কোড পড়া হচ্ছে…' });
    const url = URL.createObjectURL(file);
    try {
      const native = await makeNativeDetector();
      let code = '';
      if (native) {
        const bitmap = await createImageBitmap(file);
        const found = await native.detect(bitmap);
        code = found?.[0]?.rawValue || '';
      }
      if (!code) code = (await makeReader().decodeFromImageUrl(url)).getText();
      lastRef.current = { code: '', at: 0 };
      handleCode(code);
    } catch {
      setStatus({ tone: 'err', text: 'ছবিতে কোনো কোড পাওয়া যায়নি। কোডটি কাছে ও স্পষ্ট করে আবার তুলুন।' });
    } finally {
      URL.revokeObjectURL(url);
      setReading(false);
    }
  };

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

        <input ref={photoRef} type="file" accept="image/*" capture="environment" hidden onChange={readPhoto} />
        <button type="button" className="scanner-photo" onClick={() => photoRef.current?.click()} disabled={reading}>
          <ImageUp size={18} /> {reading ? 'পড়া হচ্ছে…' : 'ছবি তুলে স্ক্যান করুন'}
        </button>

        <label className="scanner-toggle">
          <span><Repeat size={16} /> একটানা স্ক্যান <small>(পরপর অনেক পণ্য)</small></span>
          <input type="checkbox" checked={continuous} onChange={(e) => setContinuous(e.target.checked)} />
        </label>

        <form className="scanner-manual" onSubmit={submitManual}>
          <Keyboard size={17} />
          <input value={manual} onChange={(e) => setManual(e.target.value)} placeholder="অথবা কোড লিখুন" inputMode="text" autoComplete="off" />
          <button type="submit">যোগ</button>
        </form>

        <button type="button" className="scanner-done" onClick={onClose}>শেষ — কার্টে ফিরে যান</button>
      </div>
    </div>
  );
}
