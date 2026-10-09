import React, { useState, useMemo } from 'react';
import { useShop } from '../context/ShopContext';
import { X, Printer, QrCode, Layers, Check } from 'lucide-react';
import { encodeCode128, getBarcodeBars } from '../utils/barcode';
import { formatCurrency } from '../utils/formatters';

export default function BarcodePrintModal({ product, onClose }) {
  const { shopInfo } = useShop();

  const [paperFormat, setPaperFormat] = useState('50x30'); // '50x30' | '40x30' | 'a4'
  const [copies, setCopies] = useState(1);

  const barcodeValue = product?.barcode || `${product?.id || '100001'}`;
  const barcodePattern = useMemo(() => encodeCode128(barcodeValue), [barcodeValue]);
  const { rects, totalWidth, totalHeight } = useMemo(
    () => getBarcodeBars(barcodePattern, 1.8, 38),
    [barcodePattern]
  );

  const shopName = shopInfo?.shopName || 'Dokan Pro';
  const prodName = product?.nameBn || product?.nameEn || product?.name || 'পণ্য';
  const price = product?.salePrice || 0;

  const handlePrint = () => {
    window.print();
  };

  const renderSingleSticker = (key = 0) => (
    <div
      key={key}
      className="barcode-sticker-item"
      style={{
        width: paperFormat === '40x30' ? '150px' : '190px',
        padding: '6px 8px',
        background: '#ffffff',
        color: '#000000',
        borderRadius: '4px',
        textAlign: 'center',
        border: '1px dashed #ccc',
        display: 'inline-flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        boxSizing: 'border-box',
        margin: '4px',
      }}
    >
      {/* Shop Name */}
      <div style={{ fontSize: '11px', fontWeight: 800, textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: '2px', color: '#111' }}>
        {shopName}
      </div>

      {/* Product Name */}
      <div style={{ fontSize: '12px', fontWeight: 700, color: '#000', maxWidth: '100%', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', marginBottom: '4px' }}>
        {prodName}
      </div>

      {/* Barcode SVG */}
      <svg
        viewBox={`0 0 ${totalWidth} ${totalHeight}`}
        style={{ width: '92%', height: '36px', display: 'block', margin: '0 auto' }}
      >
        {rects.map((r, i) => (
          <rect key={i} x={r.x} y={r.y} width={r.width} height={r.height} fill="#000000" />
        ))}
      </svg>

      {/* Barcode Numeric Text */}
      <div style={{ fontSize: '11px', fontFamily: 'monospace', fontWeight: 700, letterSpacing: '2px', marginTop: '2px', color: '#222' }}>
        {barcodeValue}
      </div>

      {/* Price */}
      <div style={{ fontSize: '13px', fontWeight: 900, marginTop: '2px', color: '#000' }}>
        MRP: {formatCurrency(price)}
      </div>
    </div>
  );

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-card" style={{ maxWidth: '640px', width: '95%' }} onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Printer size={20} color="var(--primary)" />
            <h3 style={{ fontSize: '1.2rem', fontWeight: 700 }}>বারকোড স্টিকার প্রিন্টার</h3>
          </div>
          <button onClick={onClose} className="btn btn-ghost btn-sm">
            <X size={18} />
          </button>
        </div>

        <div className="modal-body">
          {/* Controls */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '14px', marginBottom: '18px' }}>
            <div className="input-group">
              <label className="input-label">লেবেল সাইজ / ফরম্যাট</label>
              <select
                className="input-field"
                value={paperFormat}
                onChange={(e) => setPaperFormat(e.target.value)}
              >
                <option value="50x30">থার্মাল স্টিকার (50mm x 30mm)</option>
                <option value="40x30">মিনি থার্মাল স্টিকার (40mm x 30mm)</option>
                <option value="a4">A4 স্টিকার শিট (24 স্টিকার প্রতি পেজ)</option>
              </select>
            </div>

            <div className="input-group">
              <label className="input-label">কপির সংখ্যা (Stickers)</label>
              <input
                type="number"
                min="1"
                max="100"
                className="input-field"
                value={copies}
                onChange={(e) => setCopies(Math.max(1, parseInt(e.target.value, 10) || 1))}
              />
            </div>
          </div>

          {/* Live Preview Area */}
          <div style={{ marginBottom: '12px' }}>
            <span style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', fontWeight: 600 }}>
              প্রিন্ট প্রিভিউ:
            </span>
          </div>

          <div
            id="printable-barcode-area"
            style={{
              background: '#f1f5f9',
              padding: '16px',
              borderRadius: 'var(--radius-md)',
              maxHeight: '340px',
              overflowY: 'auto',
              display: 'flex',
              flexWrap: 'wrap',
              justifyContent: 'center',
              alignItems: 'center',
              gap: '8px',
            }}
          >
            {Array.from({ length: copies }).map((_, idx) => renderSingleSticker(idx))}
          </div>

          <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)', marginTop: '10px', textAlign: 'center' }}>
            প্রিন্ট উইন্ডো খুললে 'Destination' হিসেবে আপনার থার্মাল বারকোড প্রিন্টার নির্বাচন করুন।
          </div>
        </div>

        <div className="modal-footer" style={{ display: 'flex', justifyContent: 'space-between', padding: '16px 24px', borderTop: '1px solid var(--border-subtle)' }}>
          <button type="button" onClick={onClose} className="btn btn-secondary">
            বন্ধ করুন
          </button>
          <button type="button" onClick={handlePrint} className="btn btn-primary" style={{ gap: '8px' }}>
            <Printer size={16} />
            <span>{copies} টি স্টিকার প্রিন্ট করুন</span>
          </button>
        </div>
      </div>
    </div>
  );
}
