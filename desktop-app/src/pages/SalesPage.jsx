import React, { useState } from 'react';
import { useShop } from '../context/ShopContext';
import { Receipt, Search, Printer, Calendar, ArrowUpDown } from 'lucide-react';
import { formatCurrency, formatDateTime } from '../utils/formatters';

export default function SalesPage({ onSelectSale }) {
  const { sales } = useShop();
  const [searchTerm, setSearchTerm] = useState('');
  const [paymentFilter, setPaymentFilter] = useState('all');

  const filteredSales = sales.filter((s) => {
    const matchesPay = paymentFilter === 'all' || s.paymentType === paymentFilter;
    const term = searchTerm.toLowerCase().trim();
    const matchesTerm =
      !term ||
      (s.invoiceNumber && s.invoiceNumber.toLowerCase().includes(term)) ||
      (s.customerName && s.customerName.toLowerCase().includes(term)) ||
      (s.customerPhone && s.customerPhone.includes(term));
    return matchesPay && matchesTerm;
  });

  const totalFilteredSales = filteredSales.reduce((sum, s) => sum + s.total, 0);

  return (
    <div className="page-wrapper">
      {/* Header */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          marginBottom: '24px',
        }}
      >
        <div>
          <h1 style={{ fontSize: '1.65rem', fontWeight: 800, color: 'var(--text-primary)' }}>
            বিক্রয় খাতা (Sales History)
          </h1>
          <p style={{ fontSize: '0.9rem', color: 'var(--text-secondary)' }}>
            মোবাইল অ্যাপ ও কম্পিউটার থেকে সম্পন্ন হওয়া সকল বিক্রয়ের লাইভ খতিয়ান
          </p>
        </div>

        <div style={{ textAlign: 'right' }}>
          <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>মোট বিক্রিত মূল্য</div>
          <div style={{ fontSize: '1.4rem', fontWeight: 800, color: 'var(--primary)', fontFamily: 'var(--font-mono)' }}>
            {formatCurrency(totalFilteredSales)}
          </div>
        </div>
      </div>

      {/* Filter Bar */}
      <div
        className="card"
        style={{
          padding: '16px 20px',
          marginBottom: '20px',
          display: 'flex',
          gap: '16px',
          alignItems: 'center',
          flexWrap: 'wrap',
        }}
      >
        <div style={{ position: 'relative', flex: 1, minWidth: '240px' }}>
          <Search
            size={18}
            style={{
              position: 'absolute',
              left: '14px',
              top: '50%',
              transform: 'translateY(-50%)',
              color: 'var(--text-muted)',
            }}
          />
          <input
            type="text"
            className="input-field"
            style={{ paddingLeft: '40px' }}
            placeholder="ইনভয়েস নাম্বার, ক্রেতার নাম বা ফোন নম্বর খুঁজুন..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
        </div>

        <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
          <span style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>পেমেন্ট ধরন:</span>
          <select
            className="input-field"
            style={{ width: 'auto', padding: '8px 14px' }}
            value={paymentFilter}
            onChange={(e) => setPaymentFilter(e.target.value)}
          >
            <option value="all">সকল পেমেন্ট</option>
            <option value="নগদ">নগদ (Cash)</option>
            <option value="বিকাশ">বিকাশ/নগদ</option>
            <option value="কার্ড">কার্ড</option>
            <option value="বাকি">বাকি (Due)</option>
          </select>
        </div>
      </div>

      {/* Sales Table */}
      <div className="table-responsive">
        <table className="data-table">
          <thead>
            <tr>
              <th>ইনভয়েস নম্বর</th>
              <th>তারিখ ও সময়</th>
              <th>ক্রেতার বিবরণ</th>
              <th>কাউন্টার / স্টাফ</th>
              <th>পেমেন্ট মাধ্যম</th>
              <th style={{ textAlign: 'right' }}>মোট বিল</th>
              <th style={{ textAlign: 'right' }}>পরিশোধিত</th>
              <th style={{ textAlign: 'right' }}>বকেয়া</th>
              <th style={{ textAlign: 'center' }}>রসিদ প্রিন্ট</th>
            </tr>
          </thead>
          <tbody>
            {filteredSales.length === 0 ? (
              <tr>
                <td colSpan={9} style={{ textAlign: 'center', padding: '40px', color: 'var(--text-muted)' }}>
                  কোনো বিক্রয় হিস্ট্রি পাওয়া যায়নি।
                </td>
              </tr>
            ) : (
              filteredSales.map((s) => (
                <tr key={s.id}>
                  <td style={{ fontWeight: 600, fontFamily: 'var(--font-mono)' }}>
                    {s.invoiceNumber}
                  </td>
                  <td style={{ fontSize: '0.82rem', color: 'var(--text-secondary)' }}>
                    {formatDateTime(s.createdAt)}
                  </td>
                  <td>
                    <div style={{ fontWeight: 600 }}>{s.customerName}</div>
                    {s.customerPhone && (
                      <div style={{ fontSize: '0.76rem', color: 'var(--text-muted)' }}>
                        {s.customerPhone}
                      </div>
                    )}
                  </td>
                  <td style={{ color: 'var(--text-secondary)', fontSize: '0.84rem' }}>
                    {s.staffName || 'মালিক'}
                  </td>
                  <td>
                    <span
                      style={{
                        fontSize: '0.76rem',
                        fontWeight: 600,
                        padding: '3px 8px',
                        borderRadius: 'var(--radius-pill)',
                        background:
                          s.paymentType === 'বাকি'
                            ? 'rgba(239, 68, 68, 0.15)'
                            : 'rgba(16, 185, 129, 0.15)',
                        color: s.paymentType === 'বাকি' ? 'var(--danger)' : 'var(--success)',
                      }}
                    >
                      {s.paymentType}
                    </span>
                  </td>
                  <td style={{ textAlign: 'right', fontWeight: 700, color: 'var(--text-primary)' }}>
                    {formatCurrency(s.total)}
                  </td>
                  <td style={{ textAlign: 'right', color: 'var(--success)', fontWeight: 600 }}>
                    {formatCurrency(s.paidAmount || s.total)}
                  </td>
                  <td style={{ textAlign: 'right', color: s.dueAmount > 0 ? 'var(--danger)' : 'var(--text-muted)', fontWeight: 600 }}>
                    {formatCurrency(s.dueAmount)}
                  </td>
                  <td style={{ textAlign: 'center' }}>
                    <button
                      onClick={() => onSelectSale(s)}
                      className="btn btn-secondary btn-sm"
                      style={{ padding: '5px 10px' }}
                    >
                      <Printer size={14} />
                      <span>রসিদ</span>
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
