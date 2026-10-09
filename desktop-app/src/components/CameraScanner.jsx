import React, { useEffect, useRef, useState } from 'react';
import { BrowserMultiFormatReader } from '@zxing/browser';
import { Camera, Keyboard, Repeat, X } from 'lucide-react';

/**
 * Camera barcode / QR scanner for the POS (works in iPhone Safari, Android Chrome and desktop).
 * onDetected(code) returns true when the code matched a product. In continuous mode the camera
 * stays open for the next item, like the mobile app.
 */
export default function CameraScanner({ onDetected, onClose }) {
  const videoRef = useRef(null);
  const controlsRef = useRef(null);
  const lastRef = useRef({ code: '', at: 0 });
  const continuousRef = useRef(true);
  const [continuous, setContinuous] = useState(true);
  const [status, setStatus] = useState({ tone: 'info', text: 'ক্যামেরা চালু হচ্ছে…' });
  const [manual, setManual] = useState('');
  const [cameraError, setCameraError] = useState('');

  continuousRef.current = continuous;

  useEffect(() => {
    let cancelled = false;
    const reader = new BrowserMultiFormatReader();
    reader
      .decodeFromConstraints({ audio: false, video: { facingMode: { ideal: 'environment' } } }, videoRef.current, (result) => {
        if (!result || cancelled) return;
        const code = result.getText().trim();
        const now = Date.now();
        // The same label stays in view for a while; ignore repeats for 1.5s.
        if (!code || (code === lastRef.current.code && now - lastRef.current.at < 1500)) return;
        lastRef.current = { code, at: now };
        const ok = onDetected(code);
        setStatus(ok ? { tone: 'ok', text: `✓ ${code} — কার্টে যোগ হয়েছে` } : { tone: 'err', text: `${code} — এই কোডের পণ্য নেই` });
        if (ok && !continuousRef.current) onClose();
      })
      .then((controls) => {
        if (cancelled) controls.stop();
        else {
          controlsRef.current = controls;
          setStatus({ tone: 'info', text: 'বারকোড বা QR কোডটি ফ্রেমের মাঝে ধরুন' });
        }
      })
      .catch((err) => {
        if (cancelled) return;
        const denied = err?.name === 'NotAllowedError' || err?.name === 'SecurityError';
        setCameraError(
          denied
            ? 'ক্যামেরার অনুমতি দেওয়া হয়নি। ব্রাউজারের সেটিংসে এই সাইটকে ক্যামেরা ব্যবহারের অনুমতি দিন, অথবা নিচে কোড লিখুন।'
            : 'ক্যামেরা চালু করা যায়নি। নিচে কোড লিখে যোগ করতে পারেন।'
        );
      });
    return () => {
      cancelled = true;
      controlsRef.current?.stop();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const submitManual = (e) => {
    e.preventDefault();
    const code = manual.trim();
    if (!code) return;
    const ok = onDetected(code);
    setStatus(ok ? { tone: 'ok', text: `✓ ${code} — কার্টে যোগ হয়েছে` } : { tone: 'err', text: `${code} — এই কোডের পণ্য নেই` });
    if (ok) setManual('');
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
            </>
          )}
        </div>

        <div className={`scanner-status-line ${status.tone}`}>{status.text}</div>

        <label className="scanner-toggle">
          <span><Repeat size={16} /> একটানা স্ক্যান <small>(পরপর অনেক পণ্য)</small></span>
          <input type="checkbox" checked={continuous} onChange={(e) => setContinuous(e.target.checked)} />
        </label>

        <form className="scanner-manual" onSubmit={submitManual}>
          <Keyboard size={17} />
          <input
            value={manual}
            onChange={(e) => setManual(e.target.value)}
            placeholder="অথবা কোড লিখুন"
            inputMode="text"
            autoComplete="off"
          />
          <button type="submit">যোগ</button>
        </form>

        <button type="button" className="scanner-done" onClick={onClose}>শেষ — কার্টে ফিরে যান</button>
      </div>
    </div>
  );
}
