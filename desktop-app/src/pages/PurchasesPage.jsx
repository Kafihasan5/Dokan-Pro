import React, { useState } from 'react';
import { useShop } from '../context/ShopContext';
import {
  Truck,
  Plus,
  Search,
  FileText,
  DollarSign,
  Calendar,
  Check,
  X,
  Phone,
  Building,
  Printer,
  Trash2,
  AlertCircle,
  Eye,
  MessageSquare,
} from 'lucide-react';
import { formatCurrency, formatDate } from '../utils/formatters';
import { notify, confirmDialog } from '../components/Feedback';

export default function PurchasesPage() {
  const {
    purchases,
    suppliers,
    products,
    savePurchase,
    deletePurchase,
    saveSupplier,
    deleteSupplier,
    paySupplierDue,
    shopInfo,
  } = useShop();

  const [activeTab, setActiveTab] = useState('invoices'); // 'invoices' | 'suppliers'
  const [searchTerm, setSearchTerm] = useState('');

  // Modals
  const [showPurchaseModal, setShowPurchaseModal] = useState(false);
  const [showSupplierModal, setShowSupplierModal] = useState(false);
  const [showPayDueModal, setShowPayDueModal] = useState(false);
  const [selectedInvoice, setSelectedInvoice] = useState(null);
  const [activeSupplier, setActiveSupplier] = useState(null);
  const [isProcessing, setIsProcessing] = useState(false);

  // New Purchase Form State
  const [invoiceNo, setInvoiceNo] = useState(`PUR-${Date.now().toString().slice(-6)}`);
  const [selectedSupplierId, setSelectedSupplierId] = useState('');
  const [purchaseDate, setPurchaseDate] = useState(new Date().toISOString().split('T')[0]);
  const [purchaseItems, setPurchaseItems] = useState([
    { productId: '', productName: '', quantity: 1, purchasePrice: '', lineTotal: 0 },
  ]);
  const [purchaseDiscount, setPurchaseDiscount] = useState('');
  const [purchasePaid, setPurchasePaid] = useState('');
  const [purchasePaymentMethod, setPurchasePaymentMethod] = useState('নগদ');
  const [purchaseNote, setPurchaseNote] = useState('');

  // Supplier Form State
  const [supName, setSupName] = useState('');
  const [supCompany, setSupCompany] = useState('');
  const [supPhone, setSupPhone] = useState('');
  const [supAddress, setSupAddress] = useState('');
  const [supDue, setSupDue] = useState('');

  // Pay Supplier Due Form State
  const [payDueAmount, setPayDueAmount] = useState('');
  const [payDueMethod, setPayDueMethod] = useState('নগদ');
  const [payDueNote, setPayDueNote] = useState('');

  // Calculations for Invoices
  const totalPurchasesAmount = purchases.reduce((sum, p) => sum + (Number(p.grandTotal) || 0), 0);
  const totalPurchasesPaid = purchases.reduce((sum, p) => sum + (Number(p.paidAmount) || 0), 0);
  const totalSupplierDue = suppliers.reduce((sum, s) => sum + (Number(s.totalDue) || 0), 0);

  // Dynamic Item row handlers
  const handleItemProductSelect = (index, prodId) => {
    const prod = products.find((p) => String(p.id) === String(prodId));
    const updated = [...purchaseItems];
    if (prod) {
      updated[index].productId = prod.id;
      updated[index].productName = prod.nameBn || prod.nameEn || prod.name;
      updated[index].purchasePrice = prod.buyPrice || '';
      updated[index].lineTotal = (Number(prod.buyPrice || 0)) * (Number(updated[index].quantity) || 1);
    } else {
      updated[index].productId = '';
      updated[index].productName = '';
    }
    setPurchaseItems(updated);
  };

  const handleItemChange = (index, field, value) => {
    const updated = [...purchaseItems];
    updated[index][field] = value;
    const qty = Number(updated[index].quantity) || 0;
    const price = Number(updated[index].purchasePrice) || 0;
    updated[index].lineTotal = Math.round(qty * price * 100) / 100;
    setPurchaseItems(updated);
  };

  const addItemRow = () => {
    setPurchaseItems([
      ...purchaseItems,
      { productId: '', productName: '', quantity: 1, purchasePrice: '', lineTotal: 0 },
    ]);
  };

  const removeItemRow = (index) => {
    if (purchaseItems.length === 1) return;
    setPurchaseItems(purchaseItems.filter((_, i) => i !== index));
  };

  const itemsSubtotal = purchaseItems.reduce((sum, it) => sum + (Number(it.lineTotal) || 0), 0);
  const discountVal = Number(purchaseDiscount) || 0;
  const grandTotal = Math.max(0, itemsSubtotal - discountVal);
  const paidVal = Number(purchasePaid) || 0;
  const dueVal = Math.max(0, grandTotal - paidVal);

  const resetPurchaseForm = () => {
    setInvoiceNo(`PUR-${Date.now().toString().slice(-6)}`);
    setSelectedSupplierId('');
    setPurchaseItems([{ productId: '', productName: '', quantity: 1, purchasePrice: '', lineTotal: 0 }]);
    setPurchaseDiscount('');
    setPurchasePaid('');
    setPurchasePaymentMethod('নগদ');
    setPurchaseNote('');
  };

  // Submit New Purchase
  const handleSavePurchase = async (e) => {
    e.preventDefault();
    const validItems = purchaseItems.filter((it) => it.productName && Number(it.quantity) > 0 && Number(it.purchasePrice) >= 0);
    if (validItems.length === 0) {
      notify('কমপক্ষে একটি বৈধ পণ্য, পরিমাণ ও ক্রয়মূল্য যোগ করুন।');
      return;
    }

    try {
      setIsProcessing(true);
      const supplierObj = suppliers.find((s) => String(s.id) === String(selectedSupplierId));
      await savePurchase({
        invoiceNo: invoiceNo.trim() || `PUR-${Date.now().toString().slice(-6)}`,
        supplierId: selectedSupplierId || null,
        supplierName: supplierObj ? (supplierObj.company ? `${supplierObj.name} (${supplierObj.company})` : supplierObj.name) : 'সাধারণ সাপ্লায়ার',
        purchaseDate: new Date(purchaseDate).getTime() || Date.now(),
        subtotal: itemsSubtotal,
        discount: discountVal,
        grandTotal,
        paidAmount: Math.min(paidVal, grandTotal),
        dueAmount: dueVal,
        paymentMethod: purchasePaymentMethod,
        note: purchaseNote.trim(),
        items: validItems.map((it) => ({
          productId: it.productId,
          productName: it.productName,
          quantity: Number(it.quantity),
          purchasePrice: Number(it.purchasePrice),
          lineTotal: Number(it.lineTotal),
        })),
      });

      setShowPurchaseModal(false);
      resetPurchaseForm();
      notify('ক্রয় চালান সফলভাবে সংরক্ষিত হয়েছে এবং পণ্যের স্টক আপডেট হয়েছে!');
    } catch (err) {
      notify('ক্রয় চালান সংরক্ষণে সমস্যা: ' + err.message);
    } finally {
      setIsProcessing(false);
    }
  };

  // Submit New Supplier
  const handleSaveSupplier = async (e) => {
    e.preventDefault();
    if (!supName.trim()) {
      notify('মহাজন / সাপ্লায়ারের নাম লিখুন।');
      return;
    }
    try {
      setIsProcessing(true);
      await saveSupplier({
        name: supName.trim(),
        company: supCompany.trim(),
        phone: supPhone.trim(),
        address: supAddress.trim(),
        totalDue: Number(supDue || 0),
      });
      setShowSupplierModal(false);
      setSupName('');
      setSupCompany('');
      setSupPhone('');
      setSupAddress('');
      setSupDue('');
      notify('সাপ্লায়ার সফলভাবে যুক্ত হয়েছে!');
    } catch (err) {
      notify('সাপ্লায়ার সংরক্ষণে সমস্যা: ' + err.message);
    } finally {
      setIsProcessing(false);
    }
  };

  // Submit Pay Due
  const handlePayDue = async (e) => {
    e.preventDefault();
    if (!activeSupplier || !payDueAmount || Number(payDueAmount) <= 0) {
      notify('সঠিক পরিশোধের পরিমাণ লিখুন।');
      return;
    }
    try {
      setIsProcessing(true);
      await paySupplierDue(activeSupplier.id, Number(payDueAmount), payDueMethod, payDueNote.trim());
      setShowPayDueModal(false);
      setPayDueAmount('');
      setPayDueNote('');
      setActiveSupplier(null);
      notify('মহাজন বাকি পরিশোধ সফলভাবে সংরক্ষিত হয়েছে এবং খরচের খাতায় অন্তর্ভুক্ত হয়েছে!');
    } catch (err) {
      notify('বাকি পরিশোধে সমস্যা: ' + err.message);
    } finally {
      setIsProcessing(false);
    }
  };

  // Delete Purchase Handler
  const handleDeletePurchase = async (purchase) => {
    if (!await confirmDialog(`আপনি কি নিশ্চিত যে চালান ${purchase.invoiceNo} মুছে ফেলতে চান? এতে যোগ করা স্টক ও বাকি সমন্বয় হবে।`)) {
      return;
    }
    try {
      await deletePurchase(purchase.id);
      if (selectedInvoice?.id === purchase.id) setSelectedInvoice(null);
      notify('চালান মুছে ফেলা হয়েছে ও স্টক সমন্বয় হয়েছে!');
    } catch (err) {
      notify('চালান মুছতে সমস্যা: ' + err.message);
    }
  };

  // Filtered lists
  const filteredPurchases = purchases.filter((p) => {
    const term = searchTerm.toLowerCase().trim();
    if (!term) return true;
    return (
      (p.invoiceNo && p.invoiceNo.toLowerCase().includes(term)) ||
      (p.supplierName && p.supplierName.toLowerCase().includes(term))
    );
  });

  const filteredSuppliers = suppliers.filter((s) => {
    const term = searchTerm.toLowerCase().trim();
    if (!term) return true;
    return (
      s.name.toLowerCase().includes(term) ||
      (s.company && s.company.toLowerCase().includes(term)) ||
      (s.phone && s.phone.includes(term))
    );
  });

  return (
    <div className="page-wrapper">
      {/* Top Header */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '24px', flexWrap: 'wrap', gap: '16px' }}>
        <div>
          <h1 style={{ fontSize: '1.65rem', fontWeight: 800, color: 'var(--text-primary)', display: 'flex', alignItems: 'center', gap: '10px' }}>
            <Truck size={28} color="var(--primary)" />
            <span>ক্রয় ও মহাজন খাতা (Purchases & Suppliers)</span>
          </h1>
          <p style={{ fontSize: '0.9rem', color: 'var(--text-secondary)' }}>
            পণ্য ক্রয় চালান, মহাজন দেনা ও স্টক বৃদ্ধিকরণ খাতা
          </p>
        </div>

        <div style={{ display: 'flex', gap: '10px' }}>
          <button
            onClick={() => {
              resetPurchaseForm();
              setShowPurchaseModal(true);
            }}
            className="btn btn-primary"
          >
            <Plus size={18} />
            <span>নতুন ক্রয় চালান</span>
          </button>
          <button
            onClick={() => setShowSupplierModal(true)}
            className="btn btn-secondary"
          >
            <Building size={18} />
            <span>নতুন মহাজন</span>
          </button>
        </div>
      </div>

      {/* KPI Cards */}
      <div className="stats-grid" style={{ marginBottom: '24px' }}>
        <div className="stat-card">
          <div className="stat-icon" style={{ background: 'rgba(5, 150, 105, 0.15)', color: 'var(--primary)' }}>
            <FileText size={22} />
          </div>
          <div>
            <div className="stat-label">মোট পণ্য ক্রয়</div>
            <div className="stat-value">{formatCurrency(totalPurchasesAmount)}</div>
            <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)', marginTop: '4px' }}>
              মোট {purchases.length} টি চালান
            </div>
          </div>
        </div>

        <div className="stat-card">
          <div className="stat-icon" style={{ background: 'rgba(239, 68, 68, 0.15)', color: 'var(--danger)' }}>
            <DollarSign size={22} />
          </div>
          <div>
            <div className="stat-label">মহাজন বকেয়া দেনা</div>
            <div className="stat-value" style={{ color: 'var(--danger)' }}>{formatCurrency(totalSupplierDue)}</div>
            <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)', marginTop: '4px' }}>
              মোট বকেয়া পাওনাদার
            </div>
          </div>
        </div>

        <div className="stat-card">
          <div className="stat-icon" style={{ background: 'rgba(59, 130, 246, 0.15)', color: 'var(--info)' }}>
            <Check size={22} />
          </div>
          <div>
            <div className="stat-label">ক্রয়ে মোট পরিশোধ</div>
            <div className="stat-value" style={{ color: 'var(--info)' }}>{formatCurrency(totalPurchasesPaid)}</div>
            <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)', marginTop: '4px' }}>
              নগদ/ব্যাংক পেইড
            </div>
          </div>
        </div>

        <div className="stat-card">
          <div className="stat-icon" style={{ background: 'rgba(168, 85, 247, 0.15)', color: '#a855f7' }}>
            <Building size={22} />
          </div>
          <div>
            <div className="stat-label">সক্রিয় মহাজন / সাপ্লায়ার</div>
            <div className="stat-value">{suppliers.length} জন</div>
            <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)', marginTop: '4px' }}>
              তালিকাভুক্ত ভেন্ডর
            </div>
          </div>
        </div>
      </div>

      {/* Tabs and Search Bar */}
      <div className="card" style={{ padding: '16px 20px', marginBottom: '20px' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '14px' }}>
          <div style={{ display: 'flex', gap: '8px' }}>
            <button
              onClick={() => setActiveTab('invoices')}
              className={`btn btn-sm ${activeTab === 'invoices' ? 'btn-primary' : 'btn-secondary'}`}
              style={{ padding: '8px 18px', fontSize: '0.9rem' }}
            >
              <FileText size={16} />
              <span>ক্রয় চালান সমূহ ({purchases.length})</span>
            </button>
            <button
              onClick={() => setActiveTab('suppliers')}
              className={`btn btn-sm ${activeTab === 'suppliers' ? 'btn-primary' : 'btn-secondary'}`}
              style={{ padding: '8px 18px', fontSize: '0.9rem' }}
            >
              <Building size={16} />
              <span>মহাজন ও সাপ্লায়ার তালিকা ({suppliers.length})</span>
            </button>
          </div>

          <div style={{ position: 'relative', minWidth: '280px', flex: 1, maxWidth: '400px' }}>
            <Search
              size={18}
              style={{
                position: 'absolute',
                left: '12px',
                top: '50%',
                transform: 'translateY(-50%)',
                color: 'var(--text-muted)',
              }}
            />
            <input
              type="text"
              className="input-field"
              style={{ paddingLeft: '38px', height: '38px', fontSize: '0.88rem' }}
              placeholder={activeTab === 'invoices' ? 'চালান নম্বর বা সাপ্লায়ার খুঁজুন...' : 'মহাজন নাম বা ফোন নম্বর খুঁজুন...'}
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
            />
          </div>
        </div>
      </div>

      {/* TAB 1: PURCHASE INVOICES */}
      {activeTab === 'invoices' && (
        <div className="table-responsive">
          <table className="data-table">
            <thead>
              <tr>
                <th>চালান নং</th>
                <th>তারিখ</th>
                <th>মহাজন / সাপ্লায়ার</th>
                <th style={{ textAlign: 'center' }}>আইটেম সংখ্যা</th>
                <th style={{ textAlign: 'right' }}>মোট টাকা</th>
                <th style={{ textAlign: 'right' }}>পরিশোধ</th>
                <th style={{ textAlign: 'right' }}>বাকি (Due)</th>
                <th style={{ textAlign: 'center' }}>পেমেন্ট মাধ্যম</th>
                <th style={{ textAlign: 'center' }}>অ্যাকশন</th>
              </tr>
            </thead>
            <tbody>
              {filteredPurchases.length === 0 ? (
                <tr>
                  <td colSpan={9} style={{ textAlign: 'center', padding: '40px', color: 'var(--text-muted)' }}>
                    কোনো ক্রয় চালান পাওয়া যায়নি। "নতুন ক্রয় চালান" বাটনে ক্লিক করে চালান তৈরি করুন।
                  </td>
                </tr>
              ) : (
                filteredPurchases.map((p) => (
                  <tr key={p.id}>
                    <td style={{ fontWeight: 700, fontFamily: 'var(--font-mono)', color: 'var(--primary)' }}>
                      {p.invoiceNo}
                    </td>
                    <td style={{ color: 'var(--text-secondary)', fontSize: '0.85rem' }}>
                      {formatDate(p.purchaseDate || p.createdAt)}
                    </td>
                    <td style={{ fontWeight: 600, color: 'var(--text-primary)' }}>
                      {p.supplierName}
                    </td>
                    <td style={{ textAlign: 'center' }}>
                      <span className="badge" style={{ background: 'var(--bg-card)', color: 'var(--text-secondary)' }}>
                        {p.items?.length || 0} টি
                      </span>
                    </td>
                    <td style={{ textAlign: 'right', fontWeight: 700, color: 'var(--text-primary)' }}>
                      {formatCurrency(p.grandTotal)}
                    </td>
                    <td style={{ textAlign: 'right', color: 'var(--success)', fontWeight: 600 }}>
                      {formatCurrency(p.paidAmount)}
                    </td>
                    <td style={{ textAlign: 'right', fontWeight: 800, color: p.dueAmount > 0 ? 'var(--danger)' : 'var(--text-muted)' }}>
                      {formatCurrency(p.dueAmount)}
                    </td>
                    <td style={{ textAlign: 'center' }}>
                      <span style={{ fontSize: '0.8rem', padding: '3px 8px', borderRadius: '4px', background: 'var(--bg-card)', color: 'var(--text-secondary)' }}>
                        {p.paymentMethod || 'নগদ'}
                      </span>
                    </td>
                    <td style={{ textAlign: 'center' }}>
                      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px' }}>
                        <button
                          onClick={() => setSelectedInvoice(p)}
                          className="btn btn-secondary btn-sm"
                          title="চালান দেখুন ও প্রিন্ট করুন"
                          style={{ padding: '5px 8px' }}
                        >
                          <Eye size={14} />
                        </button>
                        <button
                          onClick={() => handleDeletePurchase(p)}
                          className="btn btn-ghost btn-sm"
                          title="চালান মুছে ফেলুন"
                          style={{ padding: '5px 8px', color: 'var(--danger)' }}
                        >
                          <Trash2 size={14} />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}

      {/* TAB 2: SUPPLIERS LIST */}
      {activeTab === 'suppliers' && (
        <div className="table-responsive">
          <table className="data-table">
            <thead>
              <tr>
                <th>মহাজনের নাম</th>
                <th>কোম্পানি / প্রতিষ্ঠান</th>
                <th>মোবাইল নম্বর</th>
                <th>ঠিকানা</th>
                <th style={{ textAlign: 'right' }}>মোট দেনা (Due)</th>
                <th style={{ textAlign: 'center' }}>অ্যাকশন</th>
              </tr>
            </thead>
            <tbody>
              {filteredSuppliers.length === 0 ? (
                <tr>
                  <td colSpan={6} style={{ textAlign: 'center', padding: '40px', color: 'var(--text-muted)' }}>
                    কোনো সাপ্লায়ার বা মহাজন পাওয়া যায়নি।
                  </td>
                </tr>
              ) : (
                filteredSuppliers.map((s) => (
                  <tr key={s.id}>
                    <td style={{ fontWeight: 700, color: 'var(--text-primary)' }}>
                      {s.name}
                    </td>
                    <td style={{ color: 'var(--text-secondary)' }}>
                      {s.company || '-'}
                    </td>
                    <td style={{ color: 'var(--text-secondary)' }}>
                      {s.phone ? (
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                          <span>{s.phone}</span>
                          <a
                            href={`https://wa.me/880${s.phone.replace(/[^0-9]/g, '').slice(-10)}`}
                            target="_blank"
                            rel="noreferrer"
                            className="btn btn-ghost btn-sm"
                            style={{ padding: '3px 6px', color: '#25D366' }}
                            title="WhatsApp মেসেজ দিন"
                          >
                            <MessageSquare size={14} />
                          </a>
                        </div>
                      ) : (
                        '-'
                      )}
                    </td>
                    <td style={{ color: 'var(--text-secondary)' }}>
                      {s.address || '-'}
                    </td>
                    <td style={{ textAlign: 'right', fontWeight: 800, fontSize: '1.05rem', color: s.totalDue > 0 ? 'var(--danger)' : 'var(--success)' }}>
                      {formatCurrency(s.totalDue)}
                    </td>
                    <td style={{ textAlign: 'center' }}>
                      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px' }}>
                        <button
                          onClick={() => {
                            setActiveSupplier(s);
                            setPayDueAmount('');
                            setPayDueNote('');
                            setShowPayDueModal(true);
                          }}
                          className="btn btn-primary btn-sm"
                          style={{ padding: '5px 12px' }}
                        >
                          <DollarSign size={14} />
                          <span>বাকি পরিশোধ</span>
                        </button>
                        <button
                          onClick={async () => {
                            if (await confirmDialog(`আপনি কি নিশ্চিত যে ${s.name} এর তথ্য মুছে ফেলতে চান?`)) {
                              try {
                                await deleteSupplier(s.id);
                                notify('সাপ্লায়ারের তথ্য মুছে ফেলা হয়েছে।');
                              } catch (err) {
                                notify('মুছতে সমস্যা: ' + err.message);
                              }
                            }
                          }}
                          className="btn btn-ghost btn-sm"
                          style={{ padding: '5px 8px', color: 'var(--danger)' }}
                        >
                          <Trash2 size={14} />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}

      {/* MODAL 1: NEW PURCHASE INVOICE */}
      {showPurchaseModal && (
        <div className="modal-overlay">
          <div className="modal-card" style={{ maxWidth: '850px', width: '95%' }}>
            <div className="modal-header">
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <Truck size={22} color="var(--primary)" />
                <h3 style={{ fontSize: '1.2rem', fontWeight: 700 }}>নতুন পণ্য ক্রয় চালান (Purchase Invoice)</h3>
              </div>
              <button onClick={() => setShowPurchaseModal(false)} className="btn btn-ghost btn-sm">
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handleSavePurchase}>
              <div className="modal-body" style={{ maxHeight: '72vh', overflowY: 'auto' }}>
                {/* Invoice Top Details */}
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '14px', marginBottom: '18px' }}>
                  <div className="input-group">
                    <label className="input-label">চালান নং*</label>
                    <input
                      type="text"
                      className="input-field"
                      value={invoiceNo}
                      onChange={(e) => setInvoiceNo(e.target.value)}
                      required
                    />
                  </div>

                  <div className="input-group">
                    <label className="input-label">মহাজন / সাপ্লায়ার নির্বাচন</label>
                    <select
                      className="input-field"
                      value={selectedSupplierId}
                      onChange={(e) => setSelectedSupplierId(e.target.value)}
                    >
                      <option value="">-- সাধারণ / অননুমোদিত সাপ্লায়ার --</option>
                      {suppliers.map((s) => (
                        <option key={s.id} value={s.id}>
                          {s.name} {s.company ? `(${s.company})` : ''} {s.totalDue > 0 ? `[দেনা: ৳${s.totalDue}]` : ''}
                        </option>
                      ))}
                    </select>
                  </div>

                  <div className="input-group">
                    <label className="input-label">ক্রয়ের তারিখ</label>
                    <input
                      type="date"
                      className="input-field"
                      value={purchaseDate}
                      onChange={(e) => setPurchaseDate(e.target.value)}
                    />
                  </div>
                </div>

                {/* Items Section */}
                <div style={{ borderTop: '1px solid var(--border-subtle)', paddingTop: '16px', marginBottom: '16px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '12px' }}>
                    <h4 style={{ fontSize: '0.95rem', fontWeight: 700, color: 'var(--text-primary)' }}>ক্রয়কৃত পণ্যের তালিকা</h4>
                    <button
                      type="button"
                      onClick={addItemRow}
                      className="btn btn-secondary btn-sm"
                      style={{ fontSize: '0.8rem', padding: '4px 10px' }}
                    >
                      <Plus size={14} />
                      <span>আরেকটি পণ্য যোগ করুন</span>
                    </button>
                  </div>

                  <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                    {purchaseItems.map((item, idx) => (
                      <div
                        key={idx}
                        style={{
                          display: 'grid',
                          gridTemplateColumns: 'minmax(180px, 2fr) minmax(80px, 1fr) minmax(100px, 1fr) minmax(100px, 1fr) 40px',
                          gap: '10px',
                          alignItems: 'center',
                          background: 'var(--bg-card)',
                          padding: '10px',
                          borderRadius: 'var(--radius-md)',
                        }}
                      >
                        {/* Product Picker */}
                        <div>
                          <label style={{ fontSize: '0.72rem', color: 'var(--text-muted)', display: 'block', marginBottom: '3px' }}>পণ্য</label>
                          <select
                            className="input-field"
                            style={{ height: '34px', fontSize: '0.82rem', padding: '4px 8px' }}
                            value={item.productId}
                            onChange={(e) => handleItemProductSelect(idx, e.target.value)}
                          >
                            <option value="">-- পণ্য বাছুন অথবা নিচে নাম লিখুন --</option>
                            {products.map((p) => (
                              <option key={p.id} value={p.id}>
                                {p.nameBn || p.nameEn || p.name} (বর্তমান স্টক: {p.stockQty})
                              </option>
                            ))}
                          </select>
                          {!item.productId && (
                            <input
                              type="text"
                              className="input-field"
                              style={{ height: '30px', fontSize: '0.8rem', marginTop: '4px', padding: '3px 8px' }}
                              placeholder="অথবা পণ্যের নাম লিখুন"
                              value={item.productName}
                              onChange={(e) => handleItemChange(idx, 'productName', e.target.value)}
                            />
                          )}
                        </div>

                        {/* Quantity */}
                        <div>
                          <label style={{ fontSize: '0.72rem', color: 'var(--text-muted)', display: 'block', marginBottom: '3px' }}>পরিমাণ</label>
                          <input
                            type="number"
                            min="1"
                            step="any"
                            className="input-field"
                            style={{ height: '34px', fontSize: '0.85rem', padding: '4px 8px' }}
                            value={item.quantity}
                            onChange={(e) => handleItemChange(idx, 'quantity', e.target.value)}
                            required
                          />
                        </div>

                        {/* Purchase Price */}
                        <div>
                          <label style={{ fontSize: '0.72rem', color: 'var(--text-muted)', display: 'block', marginBottom: '3px' }}>ক্রয় দর (৳)</label>
                          <input
                            type="number"
                            min="0"
                            step="any"
                            className="input-field"
                            style={{ height: '34px', fontSize: '0.85rem', padding: '4px 8px' }}
                            placeholder="0.00"
                            value={item.purchasePrice}
                            onChange={(e) => handleItemChange(idx, 'purchasePrice', e.target.value)}
                            required
                          />
                        </div>

                        {/* Line Total */}
                        <div>
                          <label style={{ fontSize: '0.72rem', color: 'var(--text-muted)', display: 'block', marginBottom: '3px' }}>মোট টাকা</label>
                          <div style={{ height: '34px', display: 'flex', alignItems: 'center', fontWeight: 700, fontFamily: 'var(--font-mono)', color: 'var(--primary)' }}>
                            {formatCurrency(item.lineTotal)}
                          </div>
                        </div>

                        {/* Remove */}
                        <div style={{ paddingTop: '16px' }}>
                          <button
                            type="button"
                            disabled={purchaseItems.length === 1}
                            onClick={() => removeItemRow(idx)}
                            className="btn btn-ghost btn-sm"
                            style={{ padding: '4px', color: 'var(--danger)', opacity: purchaseItems.length === 1 ? 0.3 : 1 }}
                          >
                            <Trash2 size={16} />
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>

                {/* Totals & Payments */}
                <div style={{ background: 'var(--bg-surface)', padding: '16px', borderRadius: 'var(--radius-md)', border: '1px solid var(--border-subtle)' }}>
                  <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '14px' }}>
                    <div className="input-group">
                      <label className="input-label">মোট উপমোট (Subtotal)</label>
                      <div style={{ fontSize: '1.1rem', fontWeight: 800, fontFamily: 'var(--font-mono)' }}>
                        {formatCurrency(itemsSubtotal)}
                      </div>
                    </div>

                    <div className="input-group">
                      <label className="input-label">মহাজন ছাড় / ডিসকাউন্ট (৳)</label>
                      <input
                        type="number"
                        min="0"
                        step="any"
                        className="input-field"
                        placeholder="0.00"
                        value={purchaseDiscount}
                        onChange={(e) => setPurchaseDiscount(e.target.value)}
                      />
                    </div>

                    <div className="input-group">
                      <label className="input-label">সর্বমোট বিল (Net Bill)</label>
                      <div style={{ fontSize: '1.25rem', fontWeight: 800, color: 'var(--primary)', fontFamily: 'var(--font-mono)' }}>
                        {formatCurrency(grandTotal)}
                      </div>
                    </div>
                  </div>

                  <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '14px', marginTop: '14px' }}>
                    <div className="input-group">
                      <label className="input-label">নগদ প্রদান / পরিশোধ (৳)</label>
                      <input
                        type="number"
                        min="0"
                        step="any"
                        className="input-field"
                        style={{ fontSize: '1.05rem', fontWeight: 700 }}
                        placeholder="৳ 0.00"
                        value={purchasePaid}
                        onChange={(e) => setPurchasePaid(e.target.value)}
                      />
                    </div>

                    <div className="input-group">
                      <label className="input-label">মহাজন বাকি (Due)</label>
                      <div style={{ fontSize: '1.15rem', fontWeight: 800, color: dueVal > 0 ? 'var(--danger)' : 'var(--success)', fontFamily: 'var(--font-mono)' }}>
                        {formatCurrency(dueVal)}
                      </div>
                    </div>

                    <div className="input-group">
                      <label className="input-label">পেমেন্ট মাধ্যম</label>
                      <select
                        className="input-field"
                        value={purchasePaymentMethod}
                        onChange={(e) => setPurchasePaymentMethod(e.target.value)}
                      >
                        <option value="নগদ">নগদ (Cash)</option>
                        <option value="ব্যাংক">ব্যাংক ট্রান্সফার (Bank)</option>
                        <option value="বিকাশ">বিকাশ (bKash)</option>
                        <option value="নগদ/এমএফএস">নগদ / রকেট</option>
                      </select>
                    </div>
                  </div>

                  <div className="input-group" style={{ marginTop: '14px' }}>
                    <label className="input-label">চালানের নোট / মন্তব্য (ঐচ্ছিক)</label>
                    <input
                      type="text"
                      className="input-field"
                      placeholder="যেমন: গেট পাস নম্বর, ডেলিভারি রসিদ বা ট্রাক নম্বর..."
                      value={purchaseNote}
                      onChange={(e) => setPurchaseNote(e.target.value)}
                    />
                  </div>
                </div>
              </div>

              <div className="modal-footer" style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px', padding: '16px 24px', borderTop: '1px solid var(--border-subtle)' }}>
                <button type="button" onClick={() => setShowPurchaseModal(false)} className="btn btn-secondary">
                  বাতিল
                </button>
                <button type="submit" disabled={isProcessing} className="btn btn-primary">
                  <Check size={16} />
                  <span>{isProcessing ? 'সংরক্ষণ হচ্ছে...' : 'ক্রয় চালান নিশ্চিত করুন'}</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* MODAL 2: NEW SUPPLIER */}
      {showSupplierModal && (
        <div className="modal-overlay">
          <div className="modal-card" style={{ maxWidth: '520px' }}>
            <div className="modal-header">
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <Building size={22} color="var(--primary)" />
                <h3 style={{ fontSize: '1.2rem', fontWeight: 700 }}>নতুন মহাজন / সাপ্লায়ার যোগ</h3>
              </div>
              <button onClick={() => setShowSupplierModal(false)} className="btn btn-ghost btn-sm">
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handleSaveSupplier}>
              <div className="modal-body">
                <div className="input-group">
                  <label className="input-label">মহাজনের নাম*</label>
                  <input
                    type="text"
                    className="input-field"
                    placeholder="যেমন: মো: রফিক উদ্দিন"
                    value={supName}
                    onChange={(e) => setSupName(e.target.value)}
                    required
                  />
                </div>

                <div className="input-group">
                  <label className="input-label">প্রতিষ্ঠান / এজেন্সির নাম</label>
                  <input
                    type="text"
                    className="input-field"
                    placeholder="যেমন: বেঙ্গল ডিস্ট্রিবিউটরস"
                    value={supCompany}
                    onChange={(e) => setSupCompany(e.target.value)}
                  />
                </div>

                <div className="input-group">
                  <label className="input-label">মোবাইল নম্বর</label>
                  <input
                    type="tel"
                    className="input-field"
                    placeholder="017XXXXXXXX"
                    value={supPhone}
                    onChange={(e) => setSupPhone(e.target.value)}
                  />
                </div>

                <div className="input-group">
                  <label className="input-label">ঠিকানা / এলাকা</label>
                  <input
                    type="text"
                    className="input-field"
                    placeholder="যেমন: পাইকারি বাজার, ঢাকা"
                    value={supAddress}
                    onChange={(e) => setSupAddress(e.target.value)}
                  />
                </div>

                <div className="input-group">
                  <label className="input-label">পূর্বের বকেয়া দেনা (যদি থাকে)</label>
                  <input
                    type="number"
                    min="0"
                    step="any"
                    className="input-field"
                    placeholder="৳ 0.00"
                    value={supDue}
                    onChange={(e) => setSupDue(e.target.value)}
                  />
                </div>
              </div>

              <div className="modal-footer" style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px', padding: '16px 24px', borderTop: '1px solid var(--border-subtle)' }}>
                <button type="button" onClick={() => setShowSupplierModal(false)} className="btn btn-secondary">
                  বাতিল
                </button>
                <button type="submit" disabled={isProcessing} className="btn btn-primary">
                  <Check size={16} />
                  <span>{isProcessing ? 'যুক্ত হচ্ছে...' : 'মহাজন সংরক্ষণ করুন'}</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* MODAL 3: PAY SUPPLIER DUE */}
      {showPayDueModal && activeSupplier && (
        <div className="modal-overlay">
          <div className="modal-card" style={{ maxWidth: '480px' }}>
            <div className="modal-header">
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <DollarSign size={22} color="var(--danger)" />
                <h3 style={{ fontSize: '1.2rem', fontWeight: 700 }}>মহাজন বাকি পরিশোধ</h3>
              </div>
              <button onClick={() => setShowPayDueModal(false)} className="btn btn-ghost btn-sm">
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handlePayDue}>
              <div className="modal-body">
                <div style={{ background: 'var(--bg-surface)', padding: '14px', borderRadius: 'var(--radius-md)', marginBottom: '16px' }}>
                  <div style={{ fontSize: '0.9rem', fontWeight: 700, color: 'var(--text-primary)' }}>
                    {activeSupplier.name} {activeSupplier.company ? `(${activeSupplier.company})` : ''}
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '6px' }}>
                    <span style={{ fontSize: '0.82rem', color: 'var(--text-muted)' }}>বর্তমান মোট বকেয়া দেনা:</span>
                    <span style={{ fontSize: '1.1rem', fontWeight: 800, color: 'var(--danger)', fontFamily: 'var(--font-mono)' }}>
                      {formatCurrency(activeSupplier.totalDue)}
                    </span>
                  </div>
                </div>

                <div className="input-group">
                  <label className="input-label">পরিশোধের পরিমাণ (টাকা)*</label>
                  <input
                    type="number"
                    min="1"
                    step="any"
                    max={activeSupplier.totalDue}
                    className="input-field"
                    style={{ fontSize: '1.2rem', fontWeight: 800 }}
                    placeholder="৳ 0.00"
                    value={payDueAmount}
                    onChange={(e) => setPayDueAmount(e.target.value)}
                    required
                  />
                </div>

                <div className="input-group">
                  <label className="input-label">পরিশোধ মাধ্যম</label>
                  <select
                    className="input-field"
                    value={payDueMethod}
                    onChange={(e) => setPayDueMethod(e.target.value)}
                  >
                    <option value="নগদ">নগদ (Cash)</option>
                    <option value="ব্যাংক">ব্যাংক ট্রান্সফার (Bank)</option>
                    <option value="বিকাশ">বিকাশ (bKash)</option>
                    <option value="নগদ/এমএফএস">নগদ / রকেট</option>
                  </select>
                </div>

                <div className="input-group">
                  <label className="input-label">নোট / রেফারেন্স (ঐচ্ছিক)</label>
                  <input
                    type="text"
                    className="input-field"
                    placeholder="যেমন: চেক নং, স্লিপ নং..."
                    value={payDueNote}
                    onChange={(e) => setPayDueNote(e.target.value)}
                  />
                </div>
              </div>

              <div className="modal-footer" style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px', padding: '16px 24px', borderTop: '1px solid var(--border-subtle)' }}>
                <button type="button" onClick={() => setShowPayDueModal(false)} className="btn btn-secondary">
                  বাতিল
                </button>
                <button type="submit" disabled={isProcessing} className="btn btn-primary">
                  <Check size={16} />
                  <span>{isProcessing ? 'পরিশোধ হচ্ছে...' : 'বাকি পরিশোধ নিশ্চিত করুন'}</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* MODAL 4: INVOICE DETAILS & PRINT */}
      {selectedInvoice && (
        <div className="modal-overlay">
          <div className="modal-card" style={{ maxWidth: '650px', width: '95%' }}>
            <div className="modal-header">
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <FileText size={20} color="var(--primary)" />
                <h3 style={{ fontSize: '1.15rem', fontWeight: 700 }}>ক্রয় চালান বিস্তারিত</h3>
              </div>
              <button onClick={() => setSelectedInvoice(null)} className="btn btn-ghost btn-sm">
                <X size={18} />
              </button>
            </div>

            <div className="modal-body" id="printable-purchase-invoice">
              <div style={{ textAlign: 'center', marginBottom: '16px' }}>
                <h2 style={{ fontSize: '1.3rem', fontWeight: 800 }}>{shopInfo?.shopName || 'দোকান'}</h2>
                <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>পণ্য ক্রয় রসিদ / চালান</p>
              </div>

              <div style={{ display: 'flex', justifyContent: 'space-between', padding: '12px', background: 'var(--bg-surface)', borderRadius: 'var(--radius-md)', marginBottom: '16px', fontSize: '0.86rem' }}>
                <div>
                  <div><strong>চালান নং:</strong> {selectedInvoice.invoiceNo}</div>
                  <div><strong>মহাজন:</strong> {selectedInvoice.supplierName}</div>
                </div>
                <div style={{ textAlign: 'right' }}>
                  <div><strong>তারিখ:</strong> {formatDate(selectedInvoice.purchaseDate || selectedInvoice.createdAt)}</div>
                  <div><strong>মাধ্যম:</strong> {selectedInvoice.paymentMethod || 'নগদ'}</div>
                </div>
              </div>

              <table className="data-table" style={{ marginBottom: '16px' }}>
                <thead>
                  <tr>
                    <th>পণ্য</th>
                    <th style={{ textAlign: 'center' }}>পরিমাণ</th>
                    <th style={{ textAlign: 'right' }}>দর</th>
                    <th style={{ textAlign: 'right' }}>মোট</th>
                  </tr>
                </thead>
                <tbody>
                  {selectedInvoice.items?.map((it, i) => (
                    <tr key={i}>
                      <td>{it.productName}</td>
                      <td style={{ textAlign: 'center' }}>{it.quantity}</td>
                      <td style={{ textAlign: 'right' }}>{formatCurrency(it.purchasePrice)}</td>
                      <td style={{ textAlign: 'right', fontWeight: 700 }}>{formatCurrency(it.lineTotal)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>

              <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', alignItems: 'flex-end', fontSize: '0.9rem', borderTop: '1px solid var(--border-subtle)', paddingTop: '10px' }}>
                <div>উপমোট: <strong>{formatCurrency(selectedInvoice.subtotal)}</strong></div>
                {selectedInvoice.discount > 0 && <div>মহাজন ছাড়: -{formatCurrency(selectedInvoice.discount)}</div>}
                <div style={{ fontSize: '1.1rem', fontWeight: 800, color: 'var(--primary)' }}>
                  সর্বমোট: {formatCurrency(selectedInvoice.grandTotal)}
                </div>
                <div style={{ color: 'var(--success)' }}>পরিশোধ: {formatCurrency(selectedInvoice.paidAmount)}</div>
                {selectedInvoice.dueAmount > 0 && (
                  <div style={{ color: 'var(--danger)', fontWeight: 800 }}>
                    বাকি দেনা: {formatCurrency(selectedInvoice.dueAmount)}
                  </div>
                )}
              </div>
            </div>

            <div className="modal-footer" style={{ display: 'flex', justifyContent: 'space-between', padding: '16px 24px', borderTop: '1px solid var(--border-subtle)' }}>
              <button
                type="button"
                onClick={() => window.print()}
                className="btn btn-secondary"
              >
                <Printer size={16} />
                <span>প্রিন্ট করুন</span>
              </button>
              <button type="button" onClick={() => setSelectedInvoice(null)} className="btn btn-primary">
                বন্ধ করুন
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
