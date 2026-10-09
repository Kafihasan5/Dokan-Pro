import React, { useState } from 'react';
import { useShop } from '../context/ShopContext';
import { TrendingDown, Plus, X, Check, Calendar } from 'lucide-react';
import { formatCurrency, formatDate } from '../utils/formatters';
import { notify } from '../components/Feedback';

export default function ExpensesPage() {
  const { expenses, saveExpense } = useShop();

  const [showModal, setShowModal] = useState(false);
  const [title, setTitle] = useState('');
  const [amount, setAmount] = useState('');
  const [category, setCategory] = useState('দোকান ভাড়া');
  const [note, setNote] = useState('');
  const [isProcessing, setIsProcessing] = useState(false);

  const totalExpense = expenses.reduce((sum, e) => sum + e.amount, 0);

  const handleAddExpense = async (e) => {
    e.preventDefault();
    if (!title.trim() || !amount || Number(amount) <= 0) {
      notify('খরচের বিবরণ ও সঠিক টাকার পরিমাণ লিখুন।');
      return;
    }

    try {
      setIsProcessing(true);
      await saveExpense({
        title: title.trim(),
        amount: Number(amount),
        category,
        note: note.trim(),
        expenseDate: Date.now(),
      });
      setShowModal(false);
      setTitle('');
      setAmount('');
      setNote('');
    } catch (err) {
      notify('খরচ সংরক্ষণে সমস্যা: ' + err.message);
    } finally {
      setIsProcessing(false);
    }
  };

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
            খরচের খাতা (Expenses)
          </h1>
          <p style={{ fontSize: '0.9rem', color: 'var(--text-secondary)' }}>
            দোকান ভাড়া, বিদ্যুৎ বিল, কর্মচারীর বেতন ও অন্যান্য আনুষঙ্গিক খরচ
          </p>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <div style={{ textAlign: 'right' }}>
            <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>মোট খরচ</div>
            <div style={{ fontSize: '1.4rem', fontWeight: 800, color: 'var(--warning)', fontFamily: 'var(--font-mono)' }}>
              {formatCurrency(totalExpense)}
            </div>
          </div>
          <button onClick={() => setShowModal(true)} className="btn btn-primary">
            <Plus size={18} />
            <span>নতুন খরচ লিখুন</span>
          </button>
        </div>
      </div>

      {/* Expenses Table */}
      <div className="table-responsive">
        <table className="data-table">
          <thead>
            <tr>
              <th>খরচের বিবরণ</th>
              <th>ক্যাটাগরি</th>
              <th>তারিখ</th>
              <th>মন্তব্য</th>
              <th style={{ textAlign: 'right' }}>পরিমাণ (টাকা)</th>
            </tr>
          </thead>
          <tbody>
            {expenses.length === 0 ? (
              <tr>
                <td colSpan={5} style={{ textAlign: 'center', padding: '40px', color: 'var(--text-muted)' }}>
                  কোনো খরচের রেকর্ড নেই।
                </td>
              </tr>
            ) : (
              expenses.map((e) => (
                <tr key={e.id}>
                  <td style={{ fontWeight: 700, color: 'var(--text-primary)' }}>
                    {e.title}
                  </td>
                  <td>
                    <span
                      style={{
                        fontSize: '0.76rem',
                        fontWeight: 600,
                        padding: '3px 10px',
                        borderRadius: 'var(--radius-pill)',
                        background: 'var(--bg-card)',
                        color: 'var(--text-secondary)',
                        border: '1px solid var(--border-subtle)',
                      }}
                    >
                      {e.category}
                    </span>
                  </td>
                  <td style={{ fontSize: '0.82rem', color: 'var(--text-secondary)' }}>
                    {formatDate(e.expenseDate)}
                  </td>
                  <td style={{ color: 'var(--text-muted)', fontSize: '0.84rem' }}>
                    {e.note || '-'}
                  </td>
                  <td style={{ textAlign: 'right', fontWeight: 800, color: 'var(--warning)', fontFamily: 'var(--font-mono)' }}>
                    {formatCurrency(e.amount)}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {/* Add Expense Modal */}
      {showModal && (
        <div className="modal-overlay" onClick={() => setShowModal(false)}>
          <div className="modal-card" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <TrendingDown size={20} color="var(--warning)" />
                <h3 style={{ fontSize: '1.15rem', fontWeight: 700 }}>নতুন খরচ যুক্ত করুন</h3>
              </div>
              <button onClick={() => setShowModal(false)} className="btn btn-secondary btn-sm" style={{ padding: '6px' }}>
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handleAddExpense}>
              <div className="modal-body">
                <div className="input-group">
                  <label className="input-label">খরচের শিরোনাম / বিবরণ*</label>
                  <input
                    type="text"
                    className="input-field"
                    placeholder="যেমন: দোকান ভাড়া / বিদ্যুৎ বিল"
                    value={title}
                    onChange={(e) => setTitle(e.target.value)}
                    required
                  />
                </div>

                <div className="input-group">
                  <label className="input-label">ক্যাটাগরি</label>
                  <select
                    className="input-field"
                    value={category}
                    onChange={(e) => setCategory(e.target.value)}
                  >
                    <option value="দোকান ভাড়া">দোকান ভাড়া</option>
                    <option value="বিদ্যুৎ বিল">বিদ্যুৎ বিল</option>
                    <option value="কর্মচারীর বেতন">কর্মচারীর বেতন</option>
                    <option value="যাতায়াত খরচ">যাতায়াত খরচ</option>
                    <option value="নাস্তা ও বিনোদন">নাস্তা ও বিনোদন</option>
                    <option value="মেরামত ও রক্ষণাবেক্ষণ">মেরামত ও রক্ষণাবেক্ষণ</option>
                    <option value="অন্যান্য">অন্যান্য</option>
                  </select>
                </div>

                <div className="input-group">
                  <label className="input-label">টাকার পরিমাণ*</label>
                  <input
                    type="number"
                    step="0.01"
                    min="1"
                    className="input-field"
                    style={{ fontSize: '1.2rem', fontWeight: 700 }}
                    placeholder="৳ 0.00"
                    value={amount}
                    onChange={(e) => setAmount(e.target.value)}
                    required
                  />
                </div>

                <div className="input-group">
                  <label className="input-label">মন্তব্য (ঐচ্ছিক)</label>
                  <input
                    type="text"
                    className="input-field"
                    placeholder="অতিরিক্ত কোনো তথ্য"
                    value={note}
                    onChange={(e) => setNote(e.target.value)}
                  />
                </div>
              </div>

              <div
                style={{
                  padding: '16px 24px',
                  borderTop: '1px solid var(--border-subtle)',
                  display: 'flex',
                  justifyContent: 'flex-end',
                  gap: '12px',
                }}
              >
                <button type="button" onClick={() => setShowModal(false)} className="btn btn-secondary">
                  বাতিল
                </button>
                <button type="submit" disabled={isProcessing} className="btn btn-primary">
                  <Check size={16} />
                  <span>{isProcessing ? 'যুক্ত হচ্ছে...' : 'খরচ সংরক্ষণ করুন'}</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
