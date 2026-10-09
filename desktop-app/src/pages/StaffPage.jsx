import React, { useState } from 'react';
import { useShop } from '../context/ShopContext';
import {
  Users,
  UserPlus,
  Edit2,
  Trash2,
  X,
  Check,
  KeyRound,
  Shield,
  Phone,
  Mail,
  TrendingUp,
  Receipt,
} from 'lucide-react';
import { formatCurrency, formatDate } from '../utils/formatters';
import { notify, confirmDialog } from '../components/Feedback';

export default function StaffPage() {
  const { staffMembers, saveStaffMember, deleteStaffMember, sales } = useShop();

  const [showModal, setShowModal] = useState(false);
  const [editingStaff, setEditingStaff] = useState(null);

  // Form State
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [pin, setPin] = useState('');
  const [role, setRole] = useState('ক্যাশিয়ার');
  const [isActive, setIsActive] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Sales per staff summary
  const now = new Date();
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  const todaySales = sales.filter((s) => s.createdAt >= startOfToday && !s.isReturned);

  const staffPerformance = staffMembers.map((staff) => {
    const matchedSales = todaySales.filter(
      (s) =>
        (s.staffName && s.staffName.toLowerCase() === staff.name.toLowerCase()) ||
        (s.items && s.items.some((it) => it.staffName === staff.name))
    );
    const totalAmount = matchedSales.reduce((sum, s) => sum + s.total, 0);
    return {
      ...staff,
      todayOrders: matchedSales.length,
      todayTotal: totalAmount,
    };
  });

  const openAddModal = () => {
    setEditingStaff(null);
    setName('');
    setEmail('');
    setPhone('');
    setPin('');
    setRole('ক্যাশিয়ার');
    setIsActive(true);
    setShowModal(true);
  };

  const openEditModal = (staff) => {
    setEditingStaff(staff);
    setName(staff.name || '');
    setEmail(staff.email || '');
    setPhone(staff.phone || '');
    setPin('');
    setRole(staff.role || 'ক্যাশিয়ার');
    setIsActive(staff.isActive !== false);
    setShowModal(true);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!name.trim()) {
      notify('কর্মচারীর নাম লিখুন।');
      return;
    }
    if (!editingStaff && pin.trim().length < 4) {
      notify('নতুন কর্মচারীর লগইনের জন্য কমপক্ষে ৪ সংখ্যার পিন দিন।');
      return;
    }

    try {
      setIsSubmitting(true);
      await saveStaffMember({
        id: editingStaff?.id,
        name: name.trim(),
        email: email.trim().toLowerCase(),
        phone: phone.trim(),
        pin: pin.trim(),
        role: role.trim(),
        isActive,
      });
      setShowModal(false);
      notify(editingStaff ? 'কর্মচারীর তথ্য আপডেট হয়েছে।' : 'নতুন কর্মচারী যোগ হয়েছে।');
    } catch (err) {
      notify('কর্মচারী তথ্য সংরক্ষণে সমস্যা: ' + err.message);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleDelete = async (staffId, staffName) => {
    if (
      await confirmDialog(
        `আপনি কি নিশ্চিতভাবে "${staffName}" কে তালিকা থেকে মুছে ফেলতে চান? সে আর মোবাইল অ্যাপ বা ওয়েবে লগইন করতে পারবে না।`
      )
    ) {
      try {
        await deleteStaffMember(staffId);
      } catch (err) {
        notify('মুছতে সমস্যা হয়েছে: ' + err.message);
      }
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
          flexWrap: 'wrap',
          gap: '16px',
        }}
      >
        <div>
          <h1 style={{ fontSize: '1.65rem', fontWeight: 800, color: 'var(--text-primary)' }}>
            কর্মচারী ব্যবস্থাপনা (Staff Management)
          </h1>
          <p style={{ fontSize: '0.9rem', color: 'var(--text-secondary)' }}>
            দোকানের কর্মচারীদের তালিকা, পিন কোড এবং মোবাইল অ্যাপ অ্যাক্সেস নিয়ন্ত্রণ
          </p>
        </div>

        <button onClick={openAddModal} className="btn btn-primary">
          <UserPlus size={18} />
          <span>নতুন কর্মচারী যোগ করুন</span>
        </button>
      </div>

      {/* Stats Cards */}
      <div className="stats-grid">
        <div className="stat-card">
          <div className="stat-icon-wrap" style={{ background: 'rgba(6, 182, 212, 0.15)', color: 'var(--accent-cyan)' }}>
            <Users size={24} />
          </div>
          <div>
            <div className="stat-title">মোট কর্মচারী</div>
            <div className="stat-val" style={{ color: 'var(--accent-cyan)' }}>
              {staffMembers.length} জন
            </div>
            <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>
              সক্রিয়: {staffMembers.filter((s) => s.isActive).length} জন
            </div>
          </div>
        </div>

        <div className="stat-card">
          <div className="stat-icon-wrap" style={{ background: 'rgba(16, 185, 129, 0.15)', color: 'var(--success)' }}>
            <TrendingUp size={24} />
          </div>
          <div>
            <div className="stat-title">আজকের কর্মচারী বিক্রয়</div>
            <div className="stat-val" style={{ color: 'var(--success)' }}>
              {formatCurrency(
                staffPerformance.reduce((sum, s) => sum + s.todayTotal, 0)
              )}
            </div>
            <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>
              মোট {staffPerformance.reduce((sum, s) => sum + s.todayOrders, 0)} টি বিক্রয় সম্পন্ন
            </div>
          </div>
        </div>
      </div>

      {/* Staff Table */}
      <div className="table-responsive">
        <table className="data-table">
          <thead>
            <tr>
              <th>কর্মচারীর নাম</th>
              <th>পদবি / রোল</th>
              <th>মোবাইল নম্বর</th>
              <th>ইমেইল</th>
              <th>লগইন পিন (PIN)</th>
              <th style={{ textAlign: 'right' }}>আজকের বিক্রি</th>
              <th style={{ textAlign: 'center' }}>স্ট্যাটাস</th>
              <th style={{ textAlign: 'center' }}>অ্যাকশন</th>
            </tr>
          </thead>
          <tbody>
            {staffMembers.length === 0 ? (
              <tr>
                <td colSpan={8} style={{ textAlign: 'center', padding: '40px', color: 'var(--text-muted)' }}>
                  কোনো কর্মচারী যুক্ত করা হয়নি। উপরে "নতুন কর্মচারী যোগ করুন" বাটনে ক্লিক করে যুক্ত করুন।
                </td>
              </tr>
            ) : (
              staffPerformance.map((staff) => (
                <tr key={staff.id}>
                  <td>
                    <div style={{ fontWeight: 700, color: 'var(--text-primary)' }}>
                      {staff.name}
                    </div>
                  </td>
                  <td>
                    <span
                      style={{
                        fontSize: '0.76rem',
                        fontWeight: 600,
                        padding: '3px 10px',
                        borderRadius: 'var(--radius-pill)',
                        background: 'var(--bg-card)',
                        border: '1px solid var(--border-subtle)',
                        color: 'var(--text-secondary)',
                      }}
                    >
                      {staff.role}
                    </span>
                  </td>
                  <td style={{ color: 'var(--text-secondary)', fontSize: '0.84rem' }}>
                    {staff.phone || '-'}
                  </td>
                  <td style={{ color: 'var(--text-secondary)', fontSize: '0.84rem' }}>
                    {staff.email || '-'}
                  </td>
                  <td>
                    {staff.hasPin ? (
                      <span className="badge badge-success">
                        <KeyRound size={11} /> সেট করা
                      </span>
                    ) : (
                      <span className="badge badge-warning">সেট করা নেই</span>
                    )}
                  </td>
                  <td style={{ textAlign: 'right', fontWeight: 700, color: 'var(--primary)' }}>
                    {formatCurrency(staff.todayTotal)}
                    <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>
                      {staff.todayOrders} অর্ডার
                    </div>
                  </td>
                  <td style={{ textAlign: 'center' }}>
                    <span
                      style={{
                        fontSize: '0.74rem',
                        fontWeight: 700,
                        padding: '2px 8px',
                        borderRadius: 'var(--radius-pill)',
                        background: staff.isActive
                          ? 'rgba(16, 185, 129, 0.15)'
                          : 'rgba(239, 68, 68, 0.15)',
                        color: staff.isActive ? 'var(--success)' : 'var(--danger)',
                      }}
                    >
                      {staff.isActive ? 'সক্রিয়' : 'নিষ্ক্রিয়'}
                    </span>
                  </td>
                  <td style={{ textAlign: 'center' }}>
                    <div style={{ display: 'inline-flex', gap: '8px' }}>
                      <button
                        onClick={() => openEditModal(staff)}
                        className="btn btn-secondary btn-sm"
                        style={{ padding: '6px' }}
                        title="সংশোধন করুন"
                      >
                        <Edit2 size={14} />
                      </button>
                      <button
                        onClick={() => handleDelete(staff.id, staff.name)}
                        className="btn btn-danger btn-sm"
                        style={{ padding: '6px' }}
                        title="মুছে ফেলুন"
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

      {/* Add / Edit Staff Modal */}
      {showModal && (
        <div className="modal-overlay" onClick={() => setShowModal(false)}>
          <div className="modal-card" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <Users size={20} color="var(--primary)" />
                <h3 style={{ fontSize: '1.15rem', fontWeight: 700 }}>
                  {editingStaff ? 'কর্মচারীর তথ্য সংশোধন' : 'নতুন কর্মচারী যোগ করুন'}
                </h3>
              </div>
              <button onClick={() => setShowModal(false)} className="btn btn-secondary btn-sm" style={{ padding: '6px' }}>
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handleSubmit}>
              <div className="modal-body">
                <div className="input-group">
                  <label className="input-label">কর্মচারীর পুরো নাম*</label>
                  <input
                    type="text"
                    className="input-field"
                    placeholder="যেমন: মোঃ রফিকুল ইসলাম"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    required
                  />
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '14px' }}>
                  <div className="input-group">
                    <label className="input-label">পদবি / রোল</label>
                    <select
                      className="input-field"
                      value={role}
                      onChange={(e) => setRole(e.target.value)}
                    >
                      <option value="ক্যাশিয়ার">ক্যাশিয়ার (Cashier)</option>
                      <option value="সেলসম্যান">সেলসম্যান (Salesman)</option>
                      <option value="ম্যানেজার">ম্যানেজার (Manager)</option>
                      <option value="সহকারী">সহকারী</option>
                    </select>
                  </div>

                  <div className="input-group">
                    <label className="input-label">
                      {editingStaff ? 'নতুন লগইন পিন (ঐচ্ছিক)' : 'লগইন পিন*'}
                    </label>
                    <input
                      type="password"
                      inputMode="numeric"
                      autoComplete="new-password"
                      maxLength={12}
                      className="input-field"
                      style={{ fontFamily: 'var(--font-mono)', letterSpacing: '0.2em' }}
                      placeholder={editingStaff ? 'খালি রাখলে আগের পিন থাকবে' : '৪-১২ সংখ্যা'}
                      value={pin}
                      onChange={(e) => setPin(e.target.value.replace(/[^0-9০-৯]/g, ''))}
                      required={!editingStaff}
                    />
                    <span className="input-hint">নিরাপত্তার জন্য পিন কখনো দেখানো হয় না। ০০০০ বা ১২৩৪ এর মতো সহজ পিন গ্রহণযোগ্য নয়।</span>
                  </div>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '14px' }}>
                  <div className="input-group">
                    <label className="input-label">মোবাইল নম্বর</label>
                    <input
                      type="tel"
                      className="input-field"
                      placeholder="যেমন: 017XXXXXXXX"
                      value={phone}
                      onChange={(e) => setPhone(e.target.value)}
                    />
                  </div>

                  <div className="input-group">
                    <label className="input-label">ইমেইল এড্রেস (ঐচ্ছিক)</label>
                    <input
                      type="email"
                      className="input-field"
                      placeholder="staff@email.com"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                    />
                  </div>
                </div>

                <div style={{ marginTop: '8px' }}>
                  <label style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer' }}>
                    <input
                      type="checkbox"
                      checked={isActive}
                      onChange={(e) => setIsActive(e.target.checked)}
                      style={{ width: '18px', height: '18px', accentColor: 'var(--primary)' }}
                    />
                    <span style={{ fontSize: '0.9rem', fontWeight: 600 }}>
                      সক্রিয় কর্মচারী (লগইন অনুমোদিত)
                    </span>
                  </label>
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
                <button type="submit" disabled={isSubmitting} className="btn btn-primary">
                  <Check size={16} />
                  <span>{isSubmitting ? 'সংরক্ষণ হচ্ছে...' : 'সংরক্ষণ করুন'}</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
