import React, { useEffect, useState } from 'react';
import { CheckCircle2, AlertCircle, AlertTriangle, Info, X, Trash2, HelpCircle } from 'lucide-react';

// Imperative toast + confirm API so any handler can call notify()/confirmDialog()
// without threading hooks through every page. <FeedbackHost /> renders them.

const listeners = new Set();
let queue = { toasts: [], dialog: null };
const emit = () => listeners.forEach((l) => l(queue));

const ERROR_HINT = /(সমস্যা|ভুল|ব্যর্থ|ত্রুটি|পাওয়া যায়নি|অনুমতি নেই|সঠিক নয়|error|failed)/i;
const SUCCESS_HINT = /(সফল|সম্পন্ন|হয়েছে)/;

export function notify(message, type) {
  const text = String(message ?? '');
  const kind = type || (ERROR_HINT.test(text) ? 'error' : SUCCESS_HINT.test(text) ? 'success' : 'warning');
  const id = `${Date.now()}_${Math.random()}`;
  queue = { ...queue, toasts: [...queue.toasts.slice(-3), { id, text, kind }] };
  emit();
  setTimeout(() => dismissToast(id), kind === 'error' ? 7000 : 4000);
}

export function dismissToast(id) {
  queue = { ...queue, toasts: queue.toasts.filter((t) => t.id !== id) };
  emit();
}

export function confirmDialog(message, { title = 'আপনি কি নিশ্চিত?', confirmText = 'হ্যাঁ, নিশ্চিত', cancelText = 'বাতিল', danger = true } = {}) {
  return new Promise((resolve) => {
    queue = { ...queue, dialog: { message, title, confirmText, cancelText, danger, resolve } };
    emit();
  });
}

function closeDialog(result) {
  queue.dialog?.resolve(result);
  queue = { ...queue, dialog: null };
  emit();
}

const ICONS = { success: CheckCircle2, error: AlertCircle, warning: AlertTriangle, info: Info };

export default function FeedbackHost() {
  const [state, setState] = useState(queue);

  useEffect(() => {
    listeners.add(setState);
    return () => listeners.delete(setState);
  }, []);

  useEffect(() => {
    if (!state.dialog) return undefined;
    const onKey = (e) => {
      if (e.key === 'Escape') closeDialog(false);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [state.dialog]);

  const d = state.dialog;

  return (
    <>
      <div className="toast-stack no-print" role="status" aria-live="polite">
        {state.toasts.map((t) => {
          const Icon = ICONS[t.kind] || Info;
          return (
            <div key={t.id} className={`toast ${t.kind}`}>
              <Icon size={18} className="toast-icon" />
              <span>{t.text}</span>
              <button className="toast-close" onClick={() => dismissToast(t.id)} aria-label="বন্ধ করুন">
                <X size={15} />
              </button>
            </div>
          );
        })}
      </div>

      {d && (
        <div className="modal-overlay no-print" style={{ zIndex: 280 }} onClick={() => closeDialog(false)}>
          <div
            className="modal-card dialog-card"
            role="alertdialog"
            aria-modal="true"
            aria-labelledby="dialog-title"
            onClick={(e) => e.stopPropagation()}
          >
            <div className={`dialog-icon ${d.danger ? 'danger' : ''}`}>
              {d.danger ? <Trash2 size={22} /> : <HelpCircle size={22} />}
            </div>
            <div id="dialog-title" className="dialog-title">
              {d.title}
            </div>
            <div className="dialog-message">{d.message}</div>
            <div className="dialog-actions">
              <button className="btn btn-secondary" onClick={() => closeDialog(false)}>
                {d.cancelText}
              </button>
              <button
                className={`btn ${d.danger ? 'btn-danger' : 'btn-primary'}`}
                style={d.danger ? { background: 'var(--danger)', color: '#fff' } : undefined}
                onClick={() => closeDialog(true)}
                autoFocus
              >
                {d.confirmText}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
