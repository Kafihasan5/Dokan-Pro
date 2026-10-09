import React, { useState } from 'react';
import { useShop } from '../context/ShopContext';
import {
  BookOpen,
  UserPlus,
  Search,
  DollarSign,
  Check,
  X,
  Phone,
  MapPin,
  Building,
  MessageSquare,
  Truck,
  Plus,
} from 'lucide-react';
import { formatCurrency } from '../utils/formatters';
import { notify } from '../components/Feedback';
import { openExternal } from '../utils/security';

export default function DueKhataPage() {
  const {
    customers,
    saveCustomer,
    collectDuePayment,
    suppliers,
    saveSupplier,
    paySupplierDue,
    shopInfo,
  } = useShop();

  const [activeTab, setActiveTab] = useState('customers'); // 'customers' | 'suppliers'
  const [searchTerm, setSearchTerm] = useState('');

  // Modals
  const [showAddCustomerModal, setShowAddCustomerModal] = useState(false);
  const [showAddSupplierModal, setShowAddSupplierModal] = useState(false);
  const [showCollectModal, setShowCollectModal] = useState(false);
  const [showPaySupplierModal, setShowPaySupplierModal] = useState(false);
  const [activeCustomer, setActiveCustomer] = useState(null);
  const [activeSupplier, setActiveSupplier] = useState(null);

  // Add Customer Form
  const [newCustName, setNewCustName] = useState('');
  const [newCustPhone, setNewCustPhone] = useState('');
  const [newCustAddress, setNewCustAddress] = useState('');
  const [newCustDue, setNewCustDue] = useState('');

  // Add Supplier Form
  const [newSupName, setNewSupName] = useState('');
  const [newSupCompany, setNewSupCompany] = useState('');
  const [newSupPhone, setNewSupPhone] = useState('');
  const [newSupAddress, setNewSupAddress] = useState('');
  const [newSupDue, setNewSupDue] = useState('');

  // Collect Payment Form
  const [collectAmount, setCollectAmount] = useState('');
  const [collectNote, setCollectNote] = useState('');

  // Pay Supplier Form
  const [payAmount, setPayAmount] = useState('');
  const [payMethod, setPayMethod] = useState('নগদ');
  const [payNote, setPayNote] = useState('');

  const [isProcessing, setIsProcessing] = useState(false);

  // Filtered lists
  const filteredCustomers = customers.filter((c) => {
    const term = searchTerm.toLowerCase().trim();
    return !term || c.name.toLowerCase().includes(term) || (c.phone && c.phone.includes(term));
  });

  const filteredSuppliers = suppliers.filter((s) => {
    const term = searchTerm.toLowerCase().trim();
    return !term || s.name.toLowerCase().includes(term) || (s.company && s.company.toLowerCase().includes(term)) || (s.phone && s.phone.includes(term));
  });

  const totalCustomerDue = customers.reduce((sum, c) => sum + (Number(c.totalDue) || 0), 0);
  const totalSupplierDue = suppliers.reduce((sum, s) => sum + (Number(s.totalDue) || 0), 0);

  // Handlers
  const handleAddCustomer = async (e) => {
    e.preventDefault();
    if (!newCustName.trim()) {
      notify('কাস্টমারের নাম লিখুন।');
      return;
    }

    try {
      setIsProcessing(true);
      await saveCustomer({
        name: newCustName.trim(),
        phone: newCustPhone.trim(),
        address: newCustAddress.trim(),
        totalDue: Number(newCustDue || 0),
      });
      setShowAddCustomerModal(false);
      setNewCustName('');
      setNewCustPhone('');
      setNewCustAddress('');
      setNewCustDue('');
    } catch (err) {
      notify('কাস্টমার সংরক্ষণে সমস্যা: ' + err.message);
    } finally {
      setIsProcessing(false);
    }
  };

  const handleAddSupplier = async (e) => {
    e.preventDefault();
    if (!newSupName.trim()) {
      notify('মহাজনের নাম লিখুন।');
      return;
    }
    try {
      setIsProcessing(true);
      await saveSupplier({
        name: newSupName.trim(),
        company: newSupCompany.trim(),
        phone: newSupPhone.trim(),
        address: newSupAddress.trim(),
        totalDue: Number(newSupDue || 0),
      });
      setShowAddSupplierModal(false);
      setNewSupName('');
      setNewSupCompany('');
      setNewSupPhone('');
      setNewSupAddress('');
      setNewSupDue('');
    } catch (err) {
      notify('মহাজন সংরক্ষণে সমস্যা: ' + err.message);
    } finally {
      setIsProcessing(false);
    }
  };

  const handleCollectPayment = async (e) => {
    e.preventDefault();
    if (!activeCustomer || !collectAmount || Number(collectAmount) <= 0) {
      notify('সঠিক জমার পরিমাণ লিখুন।');
      return;
    }

    try {
      setIsProcessing(true);
      await collectDuePayment(activeCustomer.id, Number(collectAmount), collectNote.trim());
      setShowCollectModal(false);
      setCollectAmount('');
      setCollectNote('');
      setActiveCustomer(null);
      notify('বাকি জমা সফলভাবে সম্পন্ন হয়েছে এবং অ্যাপে সিঙ্ক হয়েছে!');
    } catch (err) {
      notify('জমা গ্রহণে সমস্যা: ' + err.message);
    } finally {
      setIsProcessing(false);
    }
  };

  const handlePaySupplier = async (e) => {
    e.preventDefault();
    if (!activeSupplier || !payAmount || Number(payAmount) <= 0) {
      notify('সঠিক পরিশোধের পরিমাণ লিখুন।');
      return;
    }
    try {
      setIsProcessing(true);
      await paySupplierDue(activeSupplier.id, Number(payAmount), payMethod, payNote.trim());
      setShowPaySupplierModal(false);
      setPayAmount('');
      setPayNote('');
      setActiveSupplier(null);
      notify('মহাজন দেনা পরিশোধ সম্পন্ন হয়েছে!');
    } catch (err) {
      notify('পরিশোধে সমস্যা: ' + err.message);
    } finally {
      setIsProcessing(false);
    }
  };

  const sendWhatsAppReminder = (name, phone, amount) => {
    if (!phone) {
      notify('কাস্টমারের কোনো মোবাইল নম্বর সংরক্ষণ করা নেই।');
      return;
    }
    const cleanPhone = phone.replace(/[^0-9]/g, '').slice(-10);
    const shop = shopInfo?.shopName || 'আমাদের দোকান';
    const msg = `আসসালামু আলাইকুম ${name}, ${shop}-এ আপনার পূর্বের বকেয়া পাওনা রয়েছে ৳${amount}। অনুগ্রহ করে দ্রুত বকেয়া পরিশোধ করার অনুরোধ করা হলো। ধন্যবাদ।`;
    const url = `https://wa.me/880${cleanPhone}?text=${encodeURIComponent(msg)}`;
    openExternal(url);
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
          flexWrap: 'wrap',
          gap: '16px',
        }}
      >
        <div>
          <h1 style={{ fontSize: '1.65rem', fontWeight: 800, color: 'var(--text-primary)', display: 'flex', alignItems: 'center', gap: '10px' }}>
            <BookOpen size={28} color="var(--primary)" />
            <span>বাকির খাতা ও লেজার (Due Khata)</span>
          </h1>
          <p style={{ fontSize: '0.9rem', color: 'var(--text-secondary)' }}>
            কাস্টমার বকেয়া পাওনা ও মহাজন বকেয়া দেনা ব্যবস্থাপনা
          </p>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '16px', flexWrap: 'wrap' }}>
          <div style={{ textAlign: 'right' }}>
            <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>কাস্টমার মোট পাওনা</div>
            <div style={{ fontSize: '1.35rem', fontWeight: 800, color: 'var(--danger)', fontFamily: 'var(--font-mono)' }}>
              {formatCurrency(totalCustomerDue)}
            </div>
          </div>

          <div style={{ textAlign: 'right', borderLeft: '1px solid var(--border-subtle)', paddingLeft: '16px' }}>
            <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>মহাজন মোট দেনা</div>
            <div style={{ fontSize: '1.35rem', fontWeight: 800, color: 'var(--warning)', fontFamily: 'var(--font-mono)' }}>
              {formatCurrency(totalSupplierDue)}
            </div>
          </div>

          {activeTab === 'customers' ? (
            <button onClick={() => setShowAddCustomerModal(true)} className="btn btn-primary">
              <UserPlus size={18} />
              <span>নতুন কাস্টমার</span>
            </button>
          ) : (
            <button onClick={() => setShowAddSupplierModal(true)} className="btn btn-primary">
              <Building size={18} />
              <span>নতুন মহাজন</span>
            </button>
          )}
        </div>
      </div>

      {/* Tabs & Search Bar */}
      <div className="card" style={{ padding: '16px 20px', marginBottom: '20px' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '14px' }}>
          <div style={{ display: 'flex', gap: '8px' }}>
            <button
              onClick={() => setActiveTab('customers')}
              className={`btn btn-sm ${activeTab === 'customers' ? 'btn-primary' : 'btn-secondary'}`}
              style={{ padding: '8px 18px', fontSize: '0.9rem' }}
            >
              <UserPlus size={16} />
              <span>কাস্টমার বাকি ({customers.length})</span>
            </button>
            <button
              onClick={() => setActiveTab('suppliers')}
              className={`btn btn-sm ${activeTab === 'suppliers' ? 'btn-primary' : 'btn-secondary'}`}
              style={{ padding: '8px 18px', fontSize: '0.9rem' }}
            >
              <Truck size={16} />
              <span>মহাজন দেনা ({suppliers.length})</span>
            </button>
          </div>

          <div style={{ position: 'relative', minWidth: '280px', flex: 1, maxWidth: '400px' }}>
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
              style={{ paddingLeft: '40px', height: '38px' }}
              placeholder={activeTab === 'customers' ? 'কাস্টমারের নাম বা ফোন নাম্বার খুঁজুন...' : 'মহাজন বা কোম্পানির নাম খুঁজুন...'}
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
            />
          </div>
        </div>
      </div>

      {/* TAB 1: CUSTOMERS TABLE */}
      {activeTab === 'customers' && (
        <div className="table-responsive">
          <table className="data-table">
            <thead>
              <tr>
                <th>কাস্টমারের নাম</th>
                <th>মোবাইল নম্বর</th>
                <th>ঠিকানা</th>
                <th style={{ textAlign: 'right' }}>মোট বকেয়া (Due)</th>
                <th style={{ textAlign: 'center' }}>অ্যাকশন</th>
              </tr>
            </thead>
            <tbody>
              {filteredCustomers.length === 0 ? (
                <tr>
                  <td colSpan={5} style={{ textAlign: 'center', padding: '40px', color: 'var(--text-muted)' }}>
                    কোনো কাস্টমার পাওয়া যায়নি।
                  </td>
                </tr>
              ) : (
                filteredCustomers.map((c) => (
                  <tr key={c.id}>
                    <td style={{ fontWeight: 700, color: 'var(--text-primary)' }}>
                      {c.name}
                    </td>
                    <td style={{ color: 'var(--text-secondary)' }}>
                      {c.phone ? (
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                          <Phone size={13} />
                          <span>{c.phone}</span>
                        </div>
                      ) : (
                        '-'
                      )}
                    </td>
                    <td style={{ color: 'var(--text-secondary)' }}>
                      {c.address ? (
                        <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
                          <MapPin size={13} />
                          <span>{c.address}</span>
                        </span>
                      ) : (
                        '-'
                      )}
                    </td>
                    <td style={{ textAlign: 'right', fontWeight: 800, fontSize: '1.05rem', color: c.totalDue > 0 ? 'var(--danger)' : 'var(--success)' }}>
                      {formatCurrency(c.totalDue)}
                    </td>
                    <td style={{ textAlign: 'center' }}>
                      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px' }}>
                        <button
                          onClick={() => {
                            setActiveCustomer(c);
                            setCollectAmount('');
                            setShowCollectModal(true);
                          }}
                          className="btn btn-primary btn-sm"
                          style={{ padding: '6px 14px' }}
                        >
                          <DollarSign size={14} />
                          <span>জমা গ্রহণ</span>
                        </button>

                        {c.totalDue > 0 && c.phone && (
                          <button
                            onClick={() => sendWhatsAppReminder(c.name, c.phone, c.totalDue)}
                            className="btn btn-secondary btn-sm"
                            title="হোয়াটসঅ্যাপে তাগাদা মেসেজ পাঠান"
                            style={{ padding: '6px 10px', color: '#25D366' }}
                          >
                            <MessageSquare size={15} />
                            <span>তাগাদা</span>
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}

      {/* TAB 2: SUPPLIERS TABLE */}
      {activeTab === 'suppliers' && (
        <div className="table-responsive">
          <table className="data-table">
            <thead>
              <tr>
                <th>মহাজনের নাম</th>
                <th>প্রতিষ্ঠান</th>
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
                    কোনো মহাজন বা সাপ্লায়ার পাওয়া যায়নি।
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
                        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                          <Phone size={13} />
                          <span>{s.phone}</span>
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
                            setPayAmount('');
                            setShowPaySupplierModal(true);
                          }}
                          className="btn btn-primary btn-sm"
                          style={{ padding: '6px 14px' }}
                        >
                          <DollarSign size={14} />
                          <span>বাকি পরিশোধ</span>
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

      {/* MODAL 1: Add Customer Modal */}
      {showAddCustomerModal && (
        <div className="modal-overlay" onClick={() => setShowAddCustomerModal(false)}>
          <div className="modal-card" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <UserPlus size={20} color="var(--primary)" />
                <h3 style={{ fontSize: '1.15rem', fontWeight: 700 }}>নতুন কাস্টমার যোগ করুন</h3>
              </div>
              <button onClick={() => setShowAddCustomerModal(false)} className="btn btn-secondary btn-sm" style={{ padding: '6px' }}>
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handleAddCustomer}>
              <div className="modal-body">
                <div className="input-group">
                  <label className="input-label">কাস্টমারের নাম*</label>
                  <input
                    type="text"
                    className="input-field"
                    placeholder="যেমন: মোঃ রফিকুল ইসলাম"
                    value={newCustName}
                    onChange={(e) => setNewCustName(e.target.value)}
                    required
                  />
                </div>

                <div className="input-group">
                  <label className="input-label">মোবাইল নম্বর</label>
                  <input
                    type="tel"
                    className="input-field"
                    placeholder="যেমন: 017XXXXXXXX"
                    value={newCustPhone}
                    onChange={(e) => setNewCustPhone(e.target.value)}
                  />
                </div>

                <div className="input-group">
                  <label className="input-label">ঠিকানা</label>
                  <input
                    type="text"
                    className="input-field"
                    placeholder="যেমন: বাজার রোড, ঢাকা"
                    value={newCustAddress}
                    onChange={(e) => setNewCustAddress(e.target.value)}
                  />
                </div>

                <div className="input-group">
                  <label className="input-label">পূর্বের বকেয়া (যদি থাকে)</label>
                  <input
                    type="number"
                    step="0.01"
                    className="input-field"
                    placeholder="৳ 0.00"
                    value={newCustDue}
                    onChange={(e) => setNewCustDue(e.target.value)}
                  />
                </div>
              </div>

              <div className="modal-footer" style={{ padding: '16px 24px', borderTop: '1px solid var(--border-subtle)', display: 'flex', justifyContent: 'flex-end', gap: '12px' }}>
                <button type="button" onClick={() => setShowAddCustomerModal(false)} className="btn btn-secondary">
                  বাতিল
                </button>
                <button type="submit" disabled={isProcessing} className="btn btn-primary">
                  <Check size={16} />
                  <span>{isProcessing ? 'যোগ হচ্ছে...' : 'কাস্টমার সংরক্ষণ করুন'}</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* MODAL 2: Add Supplier Modal */}
      {showAddSupplierModal && (
        <div className="modal-overlay" onClick={() => setShowAddSupplierModal(false)}>
          <div className="modal-card" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <Building size={20} color="var(--primary)" />
                <h3 style={{ fontSize: '1.15rem', fontWeight: 700 }}>নতুন মহাজন যোগ করুন</h3>
              </div>
              <button onClick={() => setShowAddSupplierModal(false)} className="btn btn-secondary btn-sm" style={{ padding: '6px' }}>
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handleAddSupplier}>
              <div className="modal-body">
                <div className="input-group">
                  <label className="input-label">মহাজনের নাম*</label>
                  <input
                    type="text"
                    className="input-field"
                    placeholder="যেমন: মোঃ আনিসুর রহমান"
                    value={newSupName}
                    onChange={(e) => setNewSupName(e.target.value)}
                    required
                  />
                </div>

                <div className="input-group">
                  <label className="input-label">প্রতিষ্ঠান / এজেন্সি</label>
                  <input
                    type="text"
                    className="input-field"
                    placeholder="যেমন: রূপালী ট্রেডার্স"
                    value={newSupCompany}
                    onChange={(e) => setNewSupCompany(e.target.value)}
                  />
                </div>

                <div className="input-group">
                  <label className="input-label">মোবাইল নম্বর</label>
                  <input
                    type="tel"
                    className="input-field"
                    placeholder="017XXXXXXXX"
                    value={newSupPhone}
                    onChange={(e) => setNewSupPhone(e.target.value)}
                  />
                </div>

                <div className="input-group">
                  <label className="input-label">ঠিকানা</label>
                  <input
                    type="text"
                    className="input-field"
                    placeholder="যেমন: চকবাজার, ঢাকা"
                    value={newSupAddress}
                    onChange={(e) => setNewSupAddress(e.target.value)}
                  />
                </div>

                <div className="input-group">
                  <label className="input-label">পূর্বের বকেয়া দেনা (৳)</label>
                  <input
                    type="number"
                    step="0.01"
                    className="input-field"
                    placeholder="৳ 0.00"
                    value={newSupDue}
                    onChange={(e) => setNewSupDue(e.target.value)}
                  />
                </div>
              </div>

              <div className="modal-footer" style={{ padding: '16px 24px', borderTop: '1px solid var(--border-subtle)', display: 'flex', justifyContent: 'flex-end', gap: '12px' }}>
                <button type="button" onClick={() => setShowAddSupplierModal(false)} className="btn btn-secondary">
                  বাতিল
                </button>
                <button type="submit" disabled={isProcessing} className="btn btn-primary">
                  <Check size={16} />
                  <span>{isProcessing ? 'যোগ হচ্ছে...' : 'মহাজন সংরক্ষণ করুন'}</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* MODAL 3: Collect Customer Due Modal */}
      {showCollectModal && activeCustomer && (
        <div className="modal-overlay" onClick={() => setShowCollectModal(false)}>
          <div className="modal-card" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <DollarSign size={20} color="var(--success)" />
                <h3 style={{ fontSize: '1.15rem', fontWeight: 700 }}>
                  বাকি জমা গ্রহণ: {activeCustomer.name}
                </h3>
              </div>
              <button onClick={() => setShowCollectModal(false)} className="btn btn-secondary btn-sm" style={{ padding: '6px' }}>
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handleCollectPayment}>
              <div className="modal-body">
                <div
                  style={{
                    background: 'var(--bg-card)',
                    padding: '14px 18px',
                    borderRadius: 'var(--radius-md)',
                    marginBottom: '18px',
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                  }}
                >
                  <span style={{ fontSize: '0.9rem', color: 'var(--text-secondary)' }}>বর্তমান বকেয়া:</span>
                  <span style={{ fontSize: '1.3rem', fontWeight: 800, color: 'var(--danger)', fontFamily: 'var(--font-mono)' }}>
                    {formatCurrency(activeCustomer.totalDue)}
                  </span>
                </div>

                <div className="input-group">
                  <label className="input-label">জমার পরিমাণ (টাকা)*</label>
                  <input
                    type="number"
                    step="0.01"
                    min="1"
                    className="input-field"
                    style={{ fontSize: '1.2rem', fontWeight: 700 }}
                    placeholder="৳ 0.00"
                    value={collectAmount}
                    onChange={(e) => setCollectAmount(e.target.value)}
                    autoFocus
                    required
                  />
                </div>

                <div className="input-group">
                  <label className="input-label">মন্তব্য / রেফারেন্স</label>
                  <input
                    type="text"
                    className="input-field"
                    placeholder="যেমন: নগদ পরিশোধ / বিকাশ জমা"
                    value={collectNote}
                    onChange={(e) => setCollectNote(e.target.value)}
                  />
                </div>
              </div>

              <div className="modal-footer" style={{ padding: '16px 24px', borderTop: '1px solid var(--border-subtle)', display: 'flex', justifyContent: 'flex-end', gap: '12px' }}>
                <button type="button" onClick={() => setShowCollectModal(false)} className="btn btn-secondary">
                  বাতিল
                </button>
                <button type="submit" disabled={isProcessing} className="btn btn-primary">
                  <Check size={16} />
                  <span>{isProcessing ? 'জমা হচ্ছে...' : 'জমা সম্পন্ন করুন'}</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* MODAL 4: Pay Supplier Due Modal */}
      {showPaySupplierModal && activeSupplier && (
        <div className="modal-overlay" onClick={() => setShowPaySupplierModal(false)}>
          <div className="modal-card" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <DollarSign size={20} color="var(--danger)" />
                <h3 style={{ fontSize: '1.15rem', fontWeight: 700 }}>
                  মহাজন দেনা পরিশোধ: {activeSupplier.name}
                </h3>
              </div>
              <button onClick={() => setShowPaySupplierModal(false)} className="btn btn-secondary btn-sm" style={{ padding: '6px' }}>
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handlePaySupplier}>
              <div className="modal-body">
                <div
                  style={{
                    background: 'var(--bg-card)',
                    padding: '14px 18px',
                    borderRadius: 'var(--radius-md)',
                    marginBottom: '18px',
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                  }}
                >
                  <span style={{ fontSize: '0.9rem', color: 'var(--text-secondary)' }}>মোট দেনা:</span>
                  <span style={{ fontSize: '1.3rem', fontWeight: 800, color: 'var(--danger)', fontFamily: 'var(--font-mono)' }}>
                    {formatCurrency(activeSupplier.totalDue)}
                  </span>
                </div>

                <div className="input-group">
                  <label className="input-label">পরিশোধের পরিমাণ (টাকা)*</label>
                  <input
                    type="number"
                    step="0.01"
                    min="1"
                    className="input-field"
                    style={{ fontSize: '1.2rem', fontWeight: 700 }}
                    placeholder="৳ 0.00"
                    value={payAmount}
                    onChange={(e) => setPayAmount(e.target.value)}
                    autoFocus
                    required
                  />
                </div>

                <div className="input-group">
                  <label className="input-label">পরিশোধ মাধ্যম</label>
                  <select
                    className="input-field"
                    value={payMethod}
                    onChange={(e) => setPayMethod(e.target.value)}
                  >
                    <option value="নগদ">নগদ</option>
                    <option value="ব্যাংক">ব্যাংক ট্রান্সফার</option>
                    <option value="বিকাশ">বিকাশ</option>
                    <option value="নগদ/এমএফএস">নগদ/এমএফএস</option>
                  </select>
                </div>

                <div className="input-group">
                  <label className="input-label">মন্তব্য / রেফারেন্স</label>
                  <input
                    type="text"
                    className="input-field"
                    placeholder="যেমন: চেক নং বা ভাউচার..."
                    value={payNote}
                    onChange={(e) => setPayNote(e.target.value)}
                  />
                </div>
              </div>

              <div className="modal-footer" style={{ padding: '16px 24px', borderTop: '1px solid var(--border-subtle)', display: 'flex', justifyContent: 'flex-end', gap: '12px' }}>
                <button type="button" onClick={() => setShowPaySupplierModal(false)} className="btn btn-secondary">
                  বাতিল
                </button>
                <button type="submit" disabled={isProcessing} className="btn btn-primary">
                  <Check size={16} />
                  <span>{isProcessing ? 'পরিশোধ হচ্ছে...' : 'পরিশোধ সম্পন্ন করুন'}</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
