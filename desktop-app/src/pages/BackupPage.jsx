import React, { useState } from 'react';
import { useShop } from '../context/ShopContext';
import {
  Database,
  Download,
  Upload,
  RefreshCw,
  FileSpreadsheet,
  ShieldAlert,
  CheckCircle,
  AlertTriangle,
  Lock,
  Layers,
  FileText,
  Trash2,
} from 'lucide-react';
import { formatDate } from '../utils/formatters';
import { notify } from '../components/Feedback';
import { downloadCsv } from '../utils/security';

export default function BackupPage() {
  const {
    auth,
    shopInfo,
    products,
    categories,
    sales,
    purchases,
    customers,
    suppliers,
    expenses,
    isFirebaseConnected,
    isSyncing,
    lastSyncTime,
    exportFullDatabase,
    restoreFullDatabase,
    clearTestSales,
  } = useShop();

  // Restore State
  const [showRestoreModal, setShowRestoreModal] = useState(false);
  const [restoreJson, setRestoreJson] = useState('');
  const [restorePin, setRestorePin] = useState('');
  const [isRestoring, setIsRestoring] = useState(false);

  // Danger Zone Clear Sales State
  const [showClearModal, setShowClearModal] = useState(false);
  const [clearPin, setClearPin] = useState('');
  const [isClearing, setIsClearing] = useState(false);

  // 1. JSON Full Backup Export
  const handleExportJSON = async () => {
    try {
      const data = await exportFullDatabase();
      const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `Dokan_Pro_Backup_${auth?.shopCode || 'shop'}_${Date.now()}.json`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(url);
    } catch (err) {
      notify('ব্যাকআপ এক্সপোর্টে সমস্যা: ' + err.message);
    }
  };

  // 2. CSV Exports
  const exportProductsCSV = () => {
    const rows = [
      ['ID', 'Name (Bengali)', 'Name (English)', 'Barcode', 'Category', 'Buy Price (BDT)', 'Sale Price (BDT)', 'Stock Qty', 'Unit'],
      ...products.map((p) => [
        p.id,
        (p.nameBn || p.name || ''),
        (p.nameEn || ''),
        p.barcode || '',
        categories.find((c) => String(c.id) === String(p.categoryId))?.nameBn || '',
        p.purchasePrice || 0,
        p.salePrice || 0,
        p.stockQty || 0,
        p.unitName || '',
      ]),
    ];
    downloadCSV(rows, `Products_${auth?.shopCode}_${Date.now()}.csv`);
  };

  const exportSalesCSV = () => {
    const rows = [
      ['Invoice No', 'Date', 'Customer Name', 'Customer Phone', 'Payment Method', 'Subtotal', 'Discount', 'Total (BDT)', 'Paid Amount', 'Due Amount'],
      ...sales.map((s) => [
        s.invoiceNumber || s.invoiceNo || s.id,
        formatDate(s.createdAt || s.date),
        (s.customerName || 'নগদ ক্রেতা'),
        s.customerPhone || '',
        s.paymentType || 'নগদ',
        s.subtotal || s.total,
        s.discount || 0,
        s.total || 0,
        s.paidAmount || 0,
        s.dueAmount || 0,
      ]),
    ];
    downloadCSV(rows, `Sales_${auth?.shopCode}_${Date.now()}.csv`);
  };

  const exportExpensesCSV = () => {
    const rows = [
      ['ID', 'Title', 'Category', 'Date', 'Amount (BDT)', 'Note'],
      ...expenses.map((e) => [
        e.id,
        (e.title || ''),
        (e.category || ''),
        formatDate(e.expenseDate || e.createdAt),
        e.amount || 0,
        (e.note || ''),
      ]),
    ];
    downloadCSV(rows, `Expenses_${auth?.shopCode}_${Date.now()}.csv`);
  };

  const exportCustomersCSV = () => {
    const rows = [
      ['ID', 'Name', 'Phone', 'Address', 'Total Due (BDT)'],
      ...customers.map((c) => [
        c.id,
        (c.name || ''),
        c.phone || '',
        (c.address || ''),
        c.totalDue || 0,
      ]),
    ];
    downloadCSV(rows, `Customers_Dues_${auth?.shopCode}_${Date.now()}.csv`);
  };

  const downloadCSV = (rows, filename) => downloadCsv(rows, filename);

  // 3. File upload handler for JSON restore
  const handleFileUpload = (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = (event) => {
      try {
        const text = event.target?.result;
        setRestoreJson(String(text));
      } catch (err) {
        notify('ফাইল পড়তে সমস্যা হয়েছে: ' + err.message);
      }
    };
    reader.readAsText(file);
  };

  // 4. Submit Restore
  const handleRestoreSubmit = async (e) => {
    e.preventDefault();
    if (!restoreJson.trim()) {
      notify('রিস্টোর করার জন্য JSON ডাটা দিন বা ফাইল আপলোড করুন।');
      return;
    }
    if (!restorePin) {
      notify('মাস্টার পিন আবশ্যক।');
      return;
    }

    try {
      setIsRestoring(true);
      await restoreFullDatabase(restoreJson, restorePin);
      setShowRestoreModal(false);
      setRestoreJson('');
      setRestorePin('');
      notify('সফলভাবে ডাটাবেজ রিস্টোর ও সিঙ্ক করা হয়েছে!');
    } catch (err) {
      notify('ডাটা রিস্টোর ব্যর্থ হয়েছে: ' + err.message);
    } finally {
      setIsRestoring(false);
    }
  };

  // 5. Submit Clear Sales
  const handleClearSalesSubmit = async (e) => {
    e.preventDefault();
    if (!clearPin) {
      notify('মাস্টার পিন আবশ্যক।');
      return;
    }

    try {
      setIsClearing(true);
      await clearTestSales(clearPin);
      setShowClearModal(false);
      setClearPin('');
      notify('সকল পরীক্ষামূলক বিক্রয় ডাটা সফলভাবে মুছে ফেলা হয়েছে!');
    } catch (err) {
      notify('ডাটা মুছতে ব্যর্থ হয়েছে: ' + err.message);
    } finally {
      setIsClearing(false);
    }
  };

  return (
    <div className="page-wrapper">
      {/* Top Header */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '24px', flexWrap: 'wrap', gap: '16px' }}>
        <div>
          <h1 style={{ fontSize: '1.65rem', fontWeight: 800, color: 'var(--text-primary)', display: 'flex', alignItems: 'center', gap: '10px' }}>
            <Database size={28} color="var(--primary)" />
            <span>ডাটা হাব ও ব্যাকআপ (Backup & Restore)</span>
          </h1>
          <p style={{ fontSize: '0.9rem', color: 'var(--text-secondary)' }}>
            ক্লাউড রিয়েল-টাইম ডাটা সিঙ্ক, সম্পূর্ণ ডাটাবেজ ব্যাকআপ ও রিস্টোর হাব
          </p>
        </div>

        <button onClick={handleExportJSON} className="btn btn-primary" style={{ gap: '8px' }}>
          <Download size={18} />
          <span>সম্পূর্ণ ব্যাকআপ ডাউনলোড (JSON)</span>
        </button>
      </div>

      {/* Cloud Live Sync Status Card */}
      <div className="card" style={{ padding: '24px', marginBottom: '24px', background: 'linear-gradient(135deg, rgba(5,150,105,0.08), rgba(6,182,212,0.08))', border: '1px solid rgba(16, 185, 129, 0.3)' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '16px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
            <div
              style={{
                width: '56px',
                height: '56px',
                borderRadius: '50%',
                background: isFirebaseConnected ? 'rgba(16, 185, 129, 0.2)' : 'rgba(239, 68, 68, 0.2)',
                color: isFirebaseConnected ? 'var(--success)' : 'var(--danger)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <CheckCircle size={30} />
            </div>
            <div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <h3 style={{ fontSize: '1.25rem', fontWeight: 800, color: 'var(--text-primary)' }}>
                  {shopInfo?.shopName || 'দোকান'}
                </h3>
                <span className="badge badge-success">লাইভ কানেক্টেড</span>
              </div>
              <div style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginTop: '4px' }}>
                দোকান কোড: <strong>{auth?.shopCode}</strong> • শেষ ক্লাউড আপডেট: {lastSyncTime ? new Date(lastSyncTime).toLocaleTimeString() : 'এখনই'}
              </div>
            </div>
          </div>

          <div style={{ display: 'flex', gap: '20px', flexWrap: 'wrap' }}>
            <div style={{ textAlign: 'center' }}>
              <div style={{ fontSize: '1.2rem', fontWeight: 800, color: 'var(--primary)' }}>{products.length}</div>
              <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>পণ্য</div>
            </div>
            <div style={{ textAlign: 'center' }}>
              <div style={{ fontSize: '1.2rem', fontWeight: 800, color: 'var(--primary)' }}>{sales.length}</div>
              <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>বিক্রি</div>
            </div>
            <div style={{ textAlign: 'center' }}>
              <div style={{ fontSize: '1.2rem', fontWeight: 800, color: 'var(--primary)' }}>{purchases.length}</div>
              <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>ক্রয় চালান</div>
            </div>
            <div style={{ textAlign: 'center' }}>
              <div style={{ fontSize: '1.2rem', fontWeight: 800, color: 'var(--primary)' }}>{customers.length}</div>
              <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>কাস্টমার</div>
            </div>
            <div style={{ textAlign: 'center' }}>
              <div style={{ fontSize: '1.2rem', fontWeight: 800, color: 'var(--primary)' }}>{suppliers.length}</div>
              <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>মহাজন</div>
            </div>
          </div>
        </div>
      </div>

      {/* CSV & Excel Exports Grid */}
      <h2 style={{ fontSize: '1.25rem', fontWeight: 800, marginBottom: '16px', color: 'var(--text-primary)' }}>
        এক্সেল / সিএসভি স্প্রেডশিট এক্সপোর্ট
      </h2>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: '16px', marginBottom: '32px' }}>
        {/* Products CSV */}
        <div className="card" style={{ padding: '20px', display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '8px' }}>
              <div style={{ padding: '8px', borderRadius: '8px', background: 'rgba(59, 130, 246, 0.15)', color: 'var(--info)' }}>
                <Layers size={20} />
              </div>
              <h4 style={{ fontSize: '1rem', fontWeight: 700 }}>পণ্য তালিকা (Products)</h4>
            </div>
            <p style={{ fontSize: '0.82rem', color: 'var(--text-secondary)', marginBottom: '16px' }}>
              সকল পণ্যের নাম, স্টক পরিমাণ, ক্রয়মূল্য, বিক্রয়মূল্য ও বারকোড তালিকা
            </p>
          </div>
          <button onClick={exportProductsCSV} className="btn btn-secondary btn-sm" style={{ width: '100%', justifyContent: 'center' }}>
            <FileSpreadsheet size={15} />
            <span>পণ্য CSV ডাউনলোড ({products.length})</span>
          </button>
        </div>

        {/* Sales CSV */}
        <div className="card" style={{ padding: '20px', display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '8px' }}>
              <div style={{ padding: '8px', borderRadius: '8px', background: 'rgba(16, 185, 129, 0.15)', color: 'var(--success)' }}>
                <FileText size={20} />
              </div>
              <h4 style={{ fontSize: '1rem', fontWeight: 700 }}>বিক্রয় খাতা (Sales)</h4>
            </div>
            <p style={{ fontSize: '0.82rem', color: 'var(--text-secondary)', marginBottom: '16px' }}>
              সকল বিক্রয় চালান, কাস্টমার নাম, পেমেন্ট মাধ্যম ও মোট টাকার হিসাব
            </p>
          </div>
          <button onClick={exportSalesCSV} className="btn btn-secondary btn-sm" style={{ width: '100%', justifyContent: 'center' }}>
            <FileSpreadsheet size={15} />
            <span>বিক্রয় CSV ডাউনলোড ({sales.length})</span>
          </button>
        </div>

        {/* Expenses CSV */}
        <div className="card" style={{ padding: '20px', display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '8px' }}>
              <div style={{ padding: '8px', borderRadius: '8px', background: 'rgba(245, 158, 11, 0.15)', color: 'var(--warning)' }}>
                <Database size={20} />
              </div>
              <h4 style={{ fontSize: '1rem', fontWeight: 700 }}>খরচের খাতা (Expenses)</h4>
            </div>
            <p style={{ fontSize: '0.82rem', color: 'var(--text-secondary)', marginBottom: '16px' }}>
              দোকান পরিচালনা খরচ, ভাড়া, বেতন ও অন্যান্য ব্যয়ের পূর্ণাঙ্গ তালিকা
            </p>
          </div>
          <button onClick={exportExpensesCSV} className="btn btn-secondary btn-sm" style={{ width: '100%', justifyContent: 'center' }}>
            <FileSpreadsheet size={15} />
            <span>খরচ CSV ডাউনলোড ({expenses.length})</span>
          </button>
        </div>

        {/* Customers CSV */}
        <div className="card" style={{ padding: '20px', display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '8px' }}>
              <div style={{ padding: '8px', borderRadius: '8px', background: 'rgba(239, 68, 68, 0.15)', color: 'var(--danger)' }}>
                <ShieldAlert size={20} />
              </div>
              <h4 style={{ fontSize: '1rem', fontWeight: 700 }}>বাকির খাতা (Customers)</h4>
            </div>
            <p style={{ fontSize: '0.82rem', color: 'var(--text-secondary)', marginBottom: '16px' }}>
              সকল কাস্টমারদের নাম, মোবাইল নাম্বার, ঠিকানা ও মোট বকেয়া পাওনা
            </p>
          </div>
          <button onClick={exportCustomersCSV} className="btn btn-secondary btn-sm" style={{ width: '100%', justifyContent: 'center' }}>
            <FileSpreadsheet size={15} />
            <span>কাস্টমার CSV ডাউনলোড ({customers.length})</span>
          </button>
        </div>
      </div>

      {/* Restore & Danger Zone */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '20px' }}>
        {/* Restore Section */}
        <div className="card" style={{ padding: '24px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '12px' }}>
            <Upload size={22} color="var(--primary)" />
            <h3 style={{ fontSize: '1.15rem', fontWeight: 700 }}>ডাটাবেজ রিস্টোর (JSON Import)</h3>
          </div>
          <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginBottom: '18px', lineHeight: 1.5 }}>
            পূর্বের ডাউনলোড করা JSON ব্যাকআপ ফাইল থেকে দোকান ডাটাবেজ পুনরায় প্রতিস্থাপন করুন। নিরাপত্তার স্বার্থে মাস্টার পিন যাচাই প্রয়োজন হবে।
          </p>
          <button onClick={() => setShowRestoreModal(true)} className="btn btn-secondary" style={{ gap: '8px' }}>
            <Upload size={16} />
            <span>ব্যাকআপ রিস্টোর করুন</span>
          </button>
        </div>

        {/* Danger Zone */}
        <div className="card" style={{ padding: '24px', border: '1px solid rgba(239, 68, 68, 0.3)', background: 'rgba(239, 68, 68, 0.03)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '12px' }}>
            <AlertTriangle size={22} color="var(--danger)" />
            <h3 style={{ fontSize: '1.15rem', fontWeight: 700, color: 'var(--danger)' }}>বিপদজনক এলাকা (Danger Zone)</h3>
          </div>
          <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginBottom: '18px', lineHeight: 1.5 }}>
            দোকান চালুর শুরুতে দেওয়া সকল টেস্ট বিক্রয় ও ট্রানজেকশন মুছে ফেলুন। পণ্য তালিকা মুছে যাবে না। এই কাজটি অপরিবর্তনীয়!
          </p>
          <button onClick={() => setShowClearModal(true)} className="btn btn-danger" style={{ gap: '8px' }}>
            <Trash2 size={16} />
            <span>টেস্ট বিক্রয় মুছুন</span>
          </button>
        </div>
      </div>

      {/* RESTORE MODAL */}
      {showRestoreModal && (
        <div className="modal-overlay">
          <div className="modal-card" style={{ maxWidth: '540px' }}>
            <div className="modal-header">
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <Upload size={22} color="var(--primary)" />
                <h3 style={{ fontSize: '1.2rem', fontWeight: 700 }}>ডাটাবেজ রিস্টোর নিশ্চিতকরণ</h3>
              </div>
              <button onClick={() => setShowRestoreModal(false)} className="btn btn-ghost btn-sm">
                ✕
              </button>
            </div>

            <form onSubmit={handleRestoreSubmit}>
              <div className="modal-body">
                <div className="input-group">
                  <label className="input-label">JSON ফাইল নির্বাচন করুন</label>
                  <input
                    type="file"
                    accept=".json"
                    className="input-field"
                    onChange={handleFileUpload}
                  />
                </div>

                <div className="input-group">
                  <label className="input-label">অথবা JSON ডাটা পেস্ট করুন</label>
                  <textarea
                    className="input-field"
                    rows={4}
                    style={{ fontFamily: 'var(--font-mono)', fontSize: '0.78rem' }}
                    placeholder='{"products": [...], "sales": [...]}'
                    value={restoreJson}
                    onChange={(e) => setRestoreJson(e.target.value)}
                  />
                </div>

                <div className="input-group">
                  <label className="input-label">মাস্টার পিন (PIN)*</label>
                  <input
                    type="password"
                    maxLength={6}
                    className="input-field"
                    placeholder="দোকান বা মাস্টার পিন লিখুন"
                    value={restorePin}
                    onChange={(e) => setRestorePin(e.target.value)}
                    required
                  />
                </div>
              </div>

              <div className="modal-footer" style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px', padding: '16px 24px', borderTop: '1px solid var(--border-subtle)' }}>
                <button type="button" onClick={() => setShowRestoreModal(false)} className="btn btn-secondary">
                  বাতিল
                </button>
                <button type="submit" disabled={isRestoring} className="btn btn-primary">
                  <span>{isRestoring ? 'রিস্টোর হচ্ছে...' : 'রিস্টোর নিশ্চিত করুন'}</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* CLEAR TEST SALES MODAL */}
      {showClearModal && (
        <div className="modal-overlay">
          <div className="modal-card" style={{ maxWidth: '480px' }}>
            <div className="modal-header">
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <AlertTriangle size={22} color="var(--danger)" />
                <h3 style={{ fontSize: '1.2rem', fontWeight: 700, color: 'var(--danger)' }}>টেস্ট বিক্রয় মুছুন</h3>
              </div>
              <button onClick={() => setShowClearModal(false)} className="btn btn-ghost btn-sm">
                ✕
              </button>
            </div>

            <form onSubmit={handleClearSalesSubmit}>
              <div className="modal-body">
                <p style={{ fontSize: '0.88rem', color: 'var(--text-secondary)', marginBottom: '16px', lineHeight: 1.5 }}>
                  এই অপশনটি ব্যবহারের ফলে আপনার দোকানের পূর্বের সকল পরীক্ষামূলক বিক্রয় রশিদ স্থায়ীভাবে মুছে যাবে। এটি নিশ্চিত করতে দোকানের মাস্টার পিন লিখুন।
                </p>

                <div className="input-group">
                  <label className="input-label">মাস্টার পিন (PIN)*</label>
                  <input
                    type="password"
                    maxLength={6}
                    className="input-field"
                    placeholder="মাস্টার পিন লিখুন"
                    value={clearPin}
                    onChange={(e) => setClearPin(e.target.value)}
                    required
                  />
                </div>
              </div>

              <div className="modal-footer" style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px', padding: '16px 24px', borderTop: '1px solid var(--border-subtle)' }}>
                <button type="button" onClick={() => setShowClearModal(false)} className="btn btn-secondary">
                  বাতিল
                </button>
                <button type="submit" disabled={isClearing} className="btn btn-danger">
                  <span>{isClearing ? 'মুছে ফেলা হচ্ছে...' : 'স্থায়ীভাবে মুছুন'}</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
