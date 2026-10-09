import React, { useState, useEffect } from 'react';
import { useShop } from '../context/ShopContext';
import {
  Printer,
  X,
  CheckCircle2,
  FileText,
  Receipt,
  Building2,
  Phone,
  MapPin,
  Calendar,
  User,
  AlertCircle
} from 'lucide-react';
import { formatCurrency, formatDateTime } from '../utils/formatters';
import { notify } from './Feedback';

export default function ReceiptModal({ sale, onClose, isPrintDisabled = false }) {
  const { shopInfo } = useShop();
  // Default to full A4 Cash Memo on PC, with instant toggle to Thermal Roll
  const [viewMode, setViewMode] = useState('a4'); // 'a4' or 'thermal'

  // Keyboard shortcut listener for Ctrl+P and Esc
  useEffect(() => {
    const handleKeyDown = (e) => {
      if ((e.ctrlKey || e.metaKey) && e.key === 'p') {
        e.preventDefault();
        handlePrint();
      } else if (e.key === 'Escape') {
        onClose();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [onClose, isPrintDisabled]);

  if (!sale) return null;

  const handlePrint = () => {
    if (isPrintDisabled) {
      notify('⚠️ রসিদ প্রিন্ট করার সুবিধা ডেভেলপার কর্তৃক আপনার প্যাকেজে বন্ধ রাখা হয়েছে। ফিচারটি সক্রিয় করতে ডেভেলপারের সাথে যোগাযোগ করুন।', 'warning');
      return;
    }
    window.print();
  };

  // Safe item normalization
  let rawItems = sale.items || [];
  if (rawItems && !Array.isArray(rawItems) && typeof rawItems === 'object') {
    rawItems = Object.values(rawItems);
  }

  const items = rawItems.map((it, idx) => {
    const name = it.productName || it.name || it.nameBn || it.nameEn || it.title || `পণ্য #${idx + 1}`;
    const qty = Number(it.quantity ?? it.qty ?? 1);
    const unitPrice = Number(
      it.salePrice ??
      (it.unitPricePoisha ? it.unitPricePoisha / 100 : (it.pricePoisha ? it.pricePoisha / 100 : 0))
    );
    const lineTotal = Number(
      it.lineTotal ??
      it.subtotal ??
      (it.lineTotalPoisha ? it.lineTotalPoisha / 100 : (it.subtotalPoisha ? it.subtotalPoisha / 100 : unitPrice * qty))
    );
    const unitName = it.unitName || '';

    return {
      ...it,
      name,
      qty,
      unitPrice,
      lineTotal,
      unitName,
    };
  });

  const totalQtySum = items.reduce((sum, item) => sum + item.qty, 0);

  // Financial values
  const subtotal = Number(sale.subtotal || 0);
  const discount = Number(sale.discount || 0);
  const vat = Number(sale.vat || 0);
  const grandTotal = Number(sale.total || (subtotal - discount + vat));
  const paidAmount = Number(sale.paidAmount !== undefined ? sale.paidAmount : grandTotal);
  const dueAmount = Number(sale.dueAmount || 0);
  const changeAmount = paidAmount > grandTotal ? paidAmount - grandTotal : 0;

  const invoiceNo = sale.invoiceNumber || sale.invoiceNo || sale.id || 'INV-0000';
  const paymentType = sale.paymentType || sale.paymentMethod || 'নগদ';
  const staffName = sale.staffName || 'মালিক';
  const customerName = sale.customerName || 'নগদ ক্রেতা';
  const customerPhone = sale.customerPhone || '';
  const saleDate = sale.createdAt || sale.saleDate || Date.now();


  return (
    <div className="modal-overlay receipt-modal-overlay" onClick={onClose}>
      <div
        className="modal-card receipt-modal-card"
        style={{
          maxWidth: viewMode === 'a4' ? '800px' : '480px',
          width: '95%',
          transition: 'all 0.2s ease-in-out',
          display: 'flex',
          flexDirection: 'column',
          maxHeight: '92vh',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Modal Header */}
        <div
          className="modal-header no-print"
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            padding: '14px 20px',
            borderBottom: '1px solid var(--border-subtle)',
            background: 'var(--bg-surface)',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <CheckCircle2 size={20} color="var(--primary)" />
            <span style={{ fontSize: '1.05rem', fontWeight: 700, color: 'var(--text-primary)' }}>
              বিক্রয় রসিদ (Receipt Preview)
            </span>
          </div>

          {/* View Mode Toggle Switch */}
          <div
            style={{
              display: 'flex',
              background: 'var(--bg-card)',
              padding: '3px',
              borderRadius: 'var(--radius-md)',
              border: '1px solid var(--border-subtle)',
              gap: '4px',
            }}
          >
            <button
              type="button"
              onClick={() => setViewMode('a4')}
              className={`btn btn-sm ${viewMode === 'a4' ? 'btn-primary' : 'btn-secondary'}`}
              style={{
                fontSize: '0.78rem',
                padding: '4px 10px',
                display: 'flex',
                alignItems: 'center',
                gap: '5px',
              }}
              title="পূর্ণাঙ্গ এ৪ ক্যাশ মেমো / ইনভয়েস"
            >
              <FileText size={14} />
              <span>পূর্ণাঙ্গ ইনভয়েস (A4)</span>
            </button>
            <button
              type="button"
              onClick={() => setViewMode('thermal')}
              className={`btn btn-sm ${viewMode === 'thermal' ? 'btn-primary' : 'btn-secondary'}`}
              style={{
                fontSize: '0.78rem',
                padding: '4px 10px',
                display: 'flex',
                alignItems: 'center',
                gap: '5px',
              }}
              title="থার্মাল স্লিপ / পিওএস রসিদ"
            >
              <Receipt size={14} />
              <span>থার্মাল স্লিপ (POS)</span>
            </button>
          </div>

          <button
            onClick={onClose}
            className="btn btn-secondary btn-sm"
            style={{ padding: '6px', borderRadius: '50%' }}
            title="বন্ধ করুন (Esc)"
          >
            <X size={18} />
          </button>
        </div>

        {/* Modal Scrollable Body */}
        <div
          className="modal-body receipt-modal-body"
          style={{
            background: '#eceff1',
            padding: '20px',
            overflowY: 'auto',
            display: 'flex',
            justifyContent: 'center',
          }}
        >
          {/* =========================================================
              VIEW MODE 1: FULL A4 CASH MEMO / INVOICE (পিসির জন্য আদর্শ)
              ========================================================= */}
          {viewMode === 'a4' && (
            <div
              id="printable-receipt"
              className="receipt-a4"
              style={{
                width: '100%',
                maxWidth: '740px',
                background: '#ffffff',
                color: '#1e293b',
                padding: '30px 32px',
                borderRadius: '8px',
                boxShadow: '0 4px 20px rgba(0,0,0,0.08)',
                fontFamily: "'Hind Siliguri', sans-serif",
                lineHeight: 1.5,
              }}
            >
              {/* Top Header with Shop Details and Invoice Badge */}
              <div
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'flex-start',
                  borderBottom: '2px solid #0f172a',
                  paddingBottom: '16px',
                }}
              >
                <div>
                  <h1
                    style={{
                      fontSize: '24px',
                      fontWeight: 800,
                      color: '#0f172a',
                      margin: '0 0 4px 0',
                    }}
                  >
                    {shopInfo.shopName || 'Dokan Pro'}
                  </h1>
                  {shopInfo.tagline && (
                    <div style={{ fontSize: '13px', color: '#64748b', marginBottom: '4px' }}>
                      {shopInfo.tagline}
                    </div>
                  )}
                  {shopInfo.shopAddress && (
                    <div style={{ fontSize: '12px', color: '#475569', display: 'flex', alignItems: 'center', gap: '4px' }}>
                      <MapPin size={13} /> {shopInfo.shopAddress}
                    </div>
                  )}
                  {shopInfo.shopPhone && (
                    <div style={{ fontSize: '12px', color: '#475569', display: 'flex', alignItems: 'center', gap: '4px', marginTop: '2px' }}>
                      <Phone size={13} /> ফোন: {shopInfo.shopPhone}
                    </div>
                  )}
                </div>

                <div style={{ textAlign: 'right' }}>
                  <div
                    style={{
                      display: 'inline-block',
                      background: '#0f172a',
                      color: '#ffffff',
                      padding: '4px 14px',
                      borderRadius: '4px',
                      fontSize: '13px',
                      fontWeight: 700,
                      letterSpacing: '0.5px',
                    }}
                  >
                    ক্যাশ মেমো / ইনভয়েস
                  </div>
                  <div style={{ marginTop: '8px', fontSize: '13px', color: '#334155' }}>
                    ইনভয়েস নং: <strong style={{ color: '#0f172a' }}>{invoiceNo}</strong>
                  </div>
                  <div style={{ fontSize: '12px', color: '#64748b', marginTop: '2px' }}>
                    তারিখ: {formatDateTime(saleDate)}
                  </div>
                  <div style={{ fontSize: '12px', color: '#64748b', marginTop: '2px' }}>
                    কাউন্টার: <strong>{staffName}</strong>
                  </div>
                </div>
              </div>

              {/* Customer & Payment Information Card */}
              <div
                style={{
                  background: '#f8fafc',
                  border: '1px solid #e2e8f0',
                  borderRadius: '6px',
                  padding: '12px 16px',
                  margin: '16px 0',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                }}
              >
                <div>
                  <span style={{ fontSize: '11px', color: '#64748b', textTransform: 'uppercase', fontWeight: 600 }}>
                    ক্রেতার তথ্য
                  </span>
                  <div style={{ fontSize: '14px', fontWeight: 700, color: '#0f172a', marginTop: '2px' }}>
                    {customerName}
                  </div>
                  {customerPhone && (
                    <div style={{ fontSize: '12px', color: '#475569' }}>
                      মোবাইল: {customerPhone}
                    </div>
                  )}
                </div>

                <div style={{ textAlign: 'right' }}>
                  <span style={{ fontSize: '11px', color: '#64748b', textTransform: 'uppercase', fontWeight: 600 }}>
                    পেমেন্ট মাধ্যম
                  </span>
                  <div style={{ marginTop: '3px' }}>
                    <span
                      style={{
                        fontSize: '12px',
                        fontWeight: 700,
                        padding: '3px 10px',
                        borderRadius: '20px',
                        background: paymentType === 'বাকি' ? '#fee2e2' : '#dcfce7',
                        color: paymentType === 'বাকি' ? '#b91c1c' : '#15803d',
                      }}
                    >
                      {paymentType}
                    </span>
                  </div>
                </div>
              </div>

              {/* Items Table */}
              <table
                style={{
                  width: '100%',
                  borderCollapse: 'collapse',
                  fontSize: '13px',
                  marginTop: '12px',
                }}
              >
                <thead>
                  <tr
                    style={{
                      background: '#f1f5f9',
                      color: '#0f172a',
                      textAlign: 'left',
                      borderBottom: '2px solid #cbd5e1',
                    }}
                  >
                    <th style={{ padding: '8px 10px', width: '6%', textAlign: 'center' }}>#</th>
                    <th style={{ padding: '8px 10px', width: '48%' }}>পণ্যের নাম ও বিবরণ</th>
                    <th style={{ padding: '8px 10px', width: '12%', textAlign: 'center' }}>পরিমাণ</th>
                    <th style={{ padding: '8px 10px', width: '16%', textAlign: 'right' }}>দর (টাকা)</th>
                    <th style={{ padding: '8px 10px', width: '18%', textAlign: 'right' }}>মোট (টাকা)</th>
                  </tr>
                </thead>
                <tbody>
                  {items.map((it, idx) => (
                    <tr
                      key={idx}
                      style={{
                        borderBottom: '1px solid #e2e8f0',
                        background: idx % 2 === 0 ? '#ffffff' : '#fcfdfd',
                      }}
                    >
                      <td style={{ padding: '9px 10px', textAlign: 'center', color: '#64748b' }}>
                        {idx + 1}
                      </td>
                      <td style={{ padding: '9px 10px', fontWeight: 600, color: '#0f172a' }}>
                        {it.name}
                        {it.unitName && (
                          <span style={{ fontSize: '11px', color: '#64748b', marginLeft: '6px' }}>
                            ({it.unitName})
                          </span>
                        )}
                      </td>
                      <td style={{ padding: '9px 10px', textAlign: 'center', color: '#334155' }}>
                        {it.qty} {it.unitName ? it.unitName : ''}
                      </td>
                      <td style={{ padding: '9px 10px', textAlign: 'right', color: '#334155' }}>
                        {formatCurrency(it.unitPrice, '')}
                      </td>
                      <td style={{ padding: '9px 10px', textAlign: 'right', fontWeight: 700, color: '#0f172a' }}>
                        {formatCurrency(it.lineTotal, '')}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>

              {/* Bottom Summary Section */}
              <div
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  marginTop: '16px',
                  paddingTop: '12px',
                  borderTop: '2px solid #e2e8f0',
                }}
              >
                {/* Left: Notes & Signatures */}
                <div style={{ width: '50%', display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
                  <div>
                    <div style={{ fontSize: '12px', color: '#64748b' }}>
                      মোট আইটেম: <strong>{items.length} টি</strong> | মোট কোয়ান্টিটি: <strong>{totalQtySum}</strong>
                    </div>
                    <div
                      style={{
                        marginTop: '12px',
                        padding: '10px',
                        background: '#f8fafc',
                        borderLeft: '3px solid #0f172a',
                        borderRadius: '4px',
                        fontSize: '11px',
                        color: '#475569',
                      }}
                    >
                      <div>• বিক্রিত পণ্য ৩ দিনের মধ্যে ক্যাশ মেমো সহ ফেরতযোগ্য।</div>
                      <div style={{ marginTop: '2px' }}>• যে কোনো অভিযোগ বা তথ্যের জন্য কল করুন: {shopInfo.shopPhone || 'কর্তৃপক্ষ'}</div>
                    </div>
                  </div>

                  {/* Signatures Area */}
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: '36px', paddingRight: '20px' }}>
                    <div style={{ textAlign: 'center' }}>
                      <div style={{ borderTop: '1px dashed #94a3b8', width: '120px', marginBottom: '4px' }}></div>
                      <span style={{ fontSize: '11px', color: '#64748b' }}>ক্রেতার স্বাক্ষর</span>
                    </div>
                    <div style={{ textAlign: 'center' }}>
                      <div style={{ borderTop: '1px dashed #94a3b8', width: '120px', marginBottom: '4px' }}></div>
                      <span style={{ fontSize: '11px', color: '#64748b' }}>বিক্রেতার স্বাক্ষর</span>
                    </div>
                  </div>
                </div>

                {/* Right: Calculations Breakdown */}
                <div style={{ width: '45%' }}>
                  <div
                    style={{
                      background: '#f8fafc',
                      border: '1px solid #e2e8f0',
                      borderRadius: '6px',
                      padding: '12px 16px',
                      fontSize: '13px',
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '3px 0' }}>
                      <span style={{ color: '#475569' }}>সাবটোটাল:</span>
                      <strong style={{ color: '#0f172a' }}>{formatCurrency(subtotal)}</strong>
                    </div>

                    {discount > 0 && (
                      <div style={{ display: 'flex', justifyContent: 'space-between', padding: '3px 0', color: '#b91c1c' }}>
                        <span>ছাড় / ডিসকাউন্ট:</span>
                        <strong>- {formatCurrency(discount)}</strong>
                      </div>
                    )}

                    {vat > 0 && (
                      <div style={{ display: 'flex', justifyContent: 'space-between', padding: '3px 0', color: '#0f172a' }}>
                        <span style={{ color: '#475569' }}>ভ্যাট ({shopInfo.vatPercentage}%):</span>
                        <strong>+ {formatCurrency(vat)}</strong>
                      </div>
                    )}

                    <div
                      style={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        padding: '8px 0',
                        marginTop: '6px',
                        borderTop: '2px solid #cbd5e1',
                        borderBottom: '2px solid #cbd5e1',
                        fontSize: '16px',
                        fontWeight: 800,
                        color: '#0f172a',
                      }}
                    >
                      <span>সর্বমোট বিল:</span>
                      <span style={{ color: '#166534' }}>{formatCurrency(grandTotal)}</span>
                    </div>

                    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '4px 0', marginTop: '6px' }}>
                      <span style={{ color: '#475569' }}>পরিশোধিত টাকা:</span>
                      <strong style={{ color: '#166534' }}>{formatCurrency(paidAmount)}</strong>
                    </div>

                    {dueAmount > 0 && (
                      <div
                        style={{
                          display: 'flex',
                          justifyContent: 'space-between',
                          padding: '6px 8px',
                          marginTop: '4px',
                          background: '#fee2e2',
                          color: '#b91c1c',
                          borderRadius: '4px',
                          fontWeight: 700,
                        }}
                      >
                        <span>বকেয়া (Due):</span>
                        <span>{formatCurrency(dueAmount)}</span>
                      </div>
                    )}

                    {changeAmount > 0 && (
                      <div
                        style={{
                          display: 'flex',
                          justifyContent: 'space-between',
                          padding: '4px 0',
                          color: '#0284c7',
                          fontWeight: 700,
                        }}
                      >
                        <span>ফেরত টাকা (Change):</span>
                        <span>{formatCurrency(changeAmount)}</span>
                      </div>
                    )}
                  </div>
                </div>
              </div>

              {/* Bottom Footer Watermark */}
              <div
                style={{
                  textAlign: 'center',
                  marginTop: '24px',
                  paddingTop: '12px',
                  borderTop: '1px solid #e2e8f0',
                  fontSize: '11px',
                  color: '#94a3b8',
                }}
              >
                ধন্যবাদ, আবার আসবেন! • Dokan Pro ম্যানেজমেন্ট সিস্টেম দ্বারা প্রস্তুতকৃত
              </div>
            </div>
          )}

          {/* =========================================================
              VIEW MODE 2: THERMAL POS ROLL SLIP (58mm / 80mm পিওএস প্রিন্টার)
              ========================================================= */}
          {viewMode === 'thermal' && (
            <div
              id="printable-receipt"
              className="receipt-thermal"
              style={{
                width: '100%',
                maxWidth: '420px',
                background: '#ffffff',
                color: '#000000',
                padding: '20px 18px',
                borderRadius: '6px',
                boxShadow: '0 4px 15px rgba(0,0,0,0.1)',
                fontFamily: "'Hind Siliguri', monospace",
                fontSize: '13px',
                lineHeight: 1.4,
              }}
            >
              {/* Thermal Header */}
              <div style={{ textAlign: 'center', borderBottom: '1px dashed #000', paddingBottom: '10px' }}>
                <h2 style={{ fontSize: '18px', fontWeight: 800, margin: '0 0 2px 0' }}>
                  {shopInfo.shopName || 'Dokan Pro'}
                </h2>
                {shopInfo.tagline && (
                  <div style={{ fontSize: '11px', fontStyle: 'italic', marginBottom: '2px' }}>
                    {shopInfo.tagline}
                  </div>
                )}
                {shopInfo.shopAddress && (
                  <div style={{ fontSize: '11px' }}>{shopInfo.shopAddress}</div>
                )}
                {shopInfo.shopPhone && (
                  <div style={{ fontSize: '11px' }}>ফোন: {shopInfo.shopPhone}</div>
                )}
              </div>

              {/* Thermal Meta */}
              <div style={{ padding: '8px 0', borderBottom: '1px dashed #000', fontSize: '11px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span>ইনভয়েস: <strong>{invoiceNo}</strong></span>
                  <span style={{ fontWeight: 700 }}>{paymentType}</span>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: '3px' }}>
                  <span>তারিখ: {formatDateTime(saleDate)}</span>
                  <span>কাউন্টার: {staffName}</span>
                </div>
                {customerName && customerName !== 'নগদ ক্রেতা' && (
                  <div style={{ marginTop: '3px' }}>
                    ক্রেতা: <strong>{customerName}</strong> {customerPhone ? `(${customerPhone})` : ''}
                  </div>
                )}
              </div>

              {/* Thermal Items Table */}
              <table style={{ width: '100%', borderCollapse: 'collapse', margin: '8px 0', fontSize: '12px' }}>
                <thead>
                  <tr style={{ borderBottom: '1px solid #000', textAlign: 'left' }}>
                    <th style={{ padding: '4px 0', width: '48%' }}>পণ্য</th>
                    <th style={{ textAlign: 'center', padding: '4px 0', width: '16%' }}>পরিমাণ</th>
                    <th style={{ textAlign: 'right', padding: '4px 0', width: '18%' }}>দর</th>
                    <th style={{ textAlign: 'right', padding: '4px 0', width: '18%' }}>মোট</th>
                  </tr>
                </thead>
                <tbody>
                  {items.map((it, idx) => (
                    <tr key={idx} style={{ borderBottom: '1px dotted #ccc' }}>
                      <td style={{ padding: '4px 0', wordBreak: 'break-word', fontWeight: 600 }}>
                        {it.name}
                        {it.unitName ? ` (${it.unitName})` : ''}
                      </td>
                      <td style={{ textAlign: 'center', padding: '4px 0' }}>{it.qty}</td>
                      <td style={{ textAlign: 'right', padding: '4px 0' }}>
                        {formatCurrency(it.unitPrice, '')}
                      </td>
                      <td style={{ textAlign: 'right', padding: '4px 0', fontWeight: 'bold' }}>
                        {formatCurrency(it.lineTotal, '')}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>

              {/* Thermal Summary Breakdown */}
              <div style={{ borderTop: '1px dashed #000', paddingTop: '6px', fontSize: '12px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span>সাবটোটাল:</span>
                  <span>{formatCurrency(subtotal)}</span>
                </div>

                {discount > 0 && (
                  <div style={{ display: 'flex', justifyContent: 'space-between', color: '#b91c1c' }}>
                    <span>ডিসকাউন্ট:</span>
                    <span>- {formatCurrency(discount)}</span>
                  </div>
                )}

                {vat > 0 && (
                  <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <span>ভ্যাট ({shopInfo.vatPercentage}%):</span>
                    <span>+ {formatCurrency(vat)}</span>
                  </div>
                )}

                <div
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    fontSize: '15px',
                    fontWeight: 800,
                    borderTop: '1px solid #000',
                    borderBottom: '1px solid #000',
                    padding: '5px 0',
                    marginTop: '5px',
                  }}
                >
                  <span>সর্বমোট বিল:</span>
                  <span>{formatCurrency(grandTotal)}</span>
                </div>

                <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: '4px' }}>
                  <span>পরিশোধিত:</span>
                  <span>{formatCurrency(paidAmount)}</span>
                </div>

                {dueAmount > 0 && (
                  <div
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      fontWeight: 'bold',
                      color: '#b91c1c',
                      marginTop: '2px',
                    }}
                  >
                    <span>বকেয়া (Due):</span>
                    <span>{formatCurrency(dueAmount)}</span>
                  </div>
                )}

                {changeAmount > 0 && (
                  <div
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      fontWeight: 'bold',
                      marginTop: '2px',
                    }}
                  >
                    <span>ফেরত টাকা (Change):</span>
                    <span>{formatCurrency(changeAmount)}</span>
                  </div>
                )}
              </div>

              {/* Thermal Footer */}
              <div
                style={{
                  textAlign: 'center',
                  marginTop: '16px',
                  fontSize: '11px',
                  borderTop: '1px dashed #000',
                  paddingTop: '8px',
                }}
              >
                <div>ধন্যবাদ, আবার আসবেন!</div>
                <div style={{ fontSize: '9px', color: '#666', marginTop: '3px' }}>
                  Powered by Dokan Pro
                </div>
              </div>
            </div>
          )}
        </div>

        {/* Modal Bottom Actions */}
        <div
          className="modal-footer no-print"
          style={{
            padding: '12px 20px',
            borderTop: '1px solid var(--border-subtle)',
            background: 'var(--bg-surface)',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
          }}
        >
          <div style={{ fontSize: '0.82rem', color: 'var(--text-secondary)' }}>
            শর্টকাট: <kbd style={{ background: 'var(--bg-card)', padding: '2px 6px', borderRadius: '4px', border: '1px solid var(--border-subtle)' }}>Ctrl + P</kbd> প্রিন্ট | <kbd style={{ background: 'var(--bg-card)', padding: '2px 6px', borderRadius: '4px', border: '1px solid var(--border-subtle)' }}>Esc</kbd> বন্ধ
          </div>

          <div style={{ display: 'flex', gap: '10px' }}>
            <button onClick={onClose} className="btn btn-secondary">
              বন্ধ করুন
            </button>
            <button
              onClick={handlePrint}
              className={`btn ${isPrintDisabled ? 'btn-secondary' : 'btn-primary'}`}
              style={{ display: 'flex', alignItems: 'center', gap: '8px', opacity: isPrintDisabled ? 0.7 : 1 }}
            >
              <Printer size={16} />
              <span>
                {isPrintDisabled
                  ? '🔒 প্রিন্ট লক করা'
                  : `${viewMode === 'a4' ? 'পূর্ণাঙ্গ ইনভয়েস প্রিন্ট' : 'থার্মাল স্লিপ প্রিন্ট'} (Ctrl + P)`}
              </span>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
