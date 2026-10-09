import React, { useState } from 'react';
import { useShop } from '../context/ShopContext';
import {
  Package,
  Plus,
  Search,
  Edit2,
  Trash2,
  X,
  Check,
  AlertCircle,
  Barcode,
  Printer,
} from 'lucide-react';
import { formatCurrency } from '../utils/formatters';
import BarcodePrintModal from '../components/BarcodePrintModal';
import { generateRandomBarcode } from '../utils/barcode';
import { notify, confirmDialog } from '../components/Feedback';

export default function ProductsPage() {
  const { products, categories, saveProduct, deleteProduct, auth } = useShop();
  const isOwner = auth?.role === 'owner';

  const [searchTerm, setSearchTerm] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('all');
  const [showModal, setShowModal] = useState(false);
  const [editingProduct, setEditingProduct] = useState(null);
  const [printingProduct, setPrintingProduct] = useState(null);

  // Form State
  const [formData, setFormData] = useState({
    nameBn: '',
    nameEn: '',
    barcode: '',
    purchasePrice: '',
    salePrice: '',
    wholesalePrice: '',
    stockQty: '',
    minStock: '5',
    unitName: 'কেজি',
    categoryId: '1',
  });
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [formError, setFormError] = useState(null);

  const openAddModal = () => {
    setEditingProduct(null);
    setFormData({
      nameBn: '',
      nameEn: '',
      barcode: '',
      purchasePrice: '',
      salePrice: '',
      wholesalePrice: '',
      stockQty: '0',
      minStock: '5',
      unitName: 'কেজি',
      categoryId: categories[0]?.id || '1',
    });
    setFormError(null);
    setShowModal(true);
  };

  const openEditModal = (p) => {
    setEditingProduct(p);
    setFormData({
      nameBn: p.nameBn || '',
      nameEn: p.nameEn || '',
      barcode: p.barcode || '',
      purchasePrice: String(p.purchasePrice || ''),
      salePrice: String(p.salePrice || ''),
      wholesalePrice: String(p.wholesalePrice || ''),
      stockQty: String(p.stockQty || '0'),
      minStock: String(p.minStock || '5'),
      unitName: p.unitName || 'কেজি',
      categoryId: String(p.categoryId || '1'),
    });
    setFormError(null);
    setShowModal(true);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setFormError(null);

    if (!formData.nameBn.trim() && !formData.nameEn.trim()) {
      setFormError('পণ্যের নাম লিখুন।');
      return;
    }
    if (!formData.salePrice || Number(formData.salePrice) <= 0) {
      setFormError('সঠিক বিক্রয়মূল্য লিখুন।');
      return;
    }

    try {
      setIsSubmitting(true);
      await saveProduct({
        ...formData,
        id: editingProduct?.id,
        purchasePrice: Number(formData.purchasePrice || 0),
        salePrice: Number(formData.salePrice || 0),
        wholesalePrice: Number(formData.wholesalePrice || 0),
        stockQty: Number(formData.stockQty || 0),
        minStock: Number(formData.minStock || 5),
      });
      setShowModal(false);
    } catch (err) {
      setFormError('পণ্য সংরক্ষণ করতে সমস্যা: ' + err.message);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleDelete = async (id, name) => {
    if (await confirmDialog(`আপনি কি নিশ্চিতভাবে "${name}" পণ্যটি মুছে ফেলতে চান? এটি ফোনের অ্যাপ থেকেও মুছে যাবে।`)) {
      try {
        await deleteProduct(id);
      } catch (err) {
        notify('পণ্য মুছতে সমস্যা হয়েছে: ' + err.message);
      }
    }
  };

  // Filter
  const filteredProducts = products.filter((p) => {
    const matchesCat =
      selectedCategory === 'all' || String(p.categoryId) === String(selectedCategory);
    const s = searchTerm.toLowerCase().trim();
    const matchesSearch =
      !s ||
      (p.nameBn && p.nameBn.toLowerCase().includes(s)) ||
      (p.nameEn && p.nameEn.toLowerCase().includes(s)) ||
      (p.barcode && p.barcode.toLowerCase().includes(s));
    return matchesCat && matchesSearch;
  });

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
            পণ্য ও ইনভেন্টরি
          </h1>
          <p style={{ fontSize: '0.9rem', color: 'var(--text-secondary)' }}>
            সকল পণ্যের তালিকা, স্টক কাউন্ট ও মূল্য ব্যবস্থাপনা (মোবাইল অ্যাপে লাইভ আপডেট হবে)
          </p>
        </div>

        {isOwner && (
          <button onClick={openAddModal} className="btn btn-primary">
            <Plus size={18} />
            <span>নতুন পণ্য যোগ করুন</span>
          </button>
        )}
      </div>

      {/* Filter & Search Bar */}
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
            placeholder="পণ্য বা বারকোড খুঁজুন..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
        </div>

        <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
          <span style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>ক্যাটাগরি:</span>
          <select
            className="input-field"
            style={{ width: 'auto', padding: '8px 14px' }}
            value={selectedCategory}
            onChange={(e) => setSelectedCategory(e.target.value)}
          >
            <option value="all">সকল ক্যাটাগরি</option>
            {categories.map((c) => (
              <option key={c.id} value={c.id}>
                {c.nameBn || c.nameEn}
              </option>
            ))}
          </select>
        </div>

        <div style={{ marginLeft: 'auto', fontSize: '0.85rem', color: 'var(--text-muted)' }}>
          মোট পণ্য: <strong>{filteredProducts.length}</strong> টি
        </div>
      </div>

      {/* Products Table */}
      <div className="table-responsive">
        <table className="data-table">
          <thead>
            <tr>
              <th>পণ্যের নাম</th>
              <th>বারকোড</th>
              <th style={{ textAlign: 'right' }}>ক্রয়মূল্য</th>
              <th style={{ textAlign: 'right' }}>বিক্রয়মূল্য</th>
              <th style={{ textAlign: 'center' }}>বর্তমান স্টক</th>
              <th style={{ textAlign: 'center' }}>একক</th>
              {isOwner && <th style={{ textAlign: 'center' }}>অ্যাকশন</th>}
            </tr>
          </thead>
          <tbody>
            {filteredProducts.length === 0 ? (
              <tr>
                <td colSpan={isOwner ? 7 : 6} style={{ textAlign: 'center', padding: '40px', color: 'var(--text-muted)' }}>
                  কোনো পণ্য পাওয়া যায়নি।
                </td>
              </tr>
            ) : (
              filteredProducts.map((p) => {
                const isLowStock = p.stockQty <= p.minStock;
                return (
                  <tr key={p.id}>
                    <td>
                      <div style={{ fontWeight: 700, color: 'var(--text-primary)' }}>
                        {p.nameBn || p.nameEn}
                      </div>
                      {p.nameEn && p.nameBn && (
                        <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>
                          {p.nameEn}
                        </div>
                      )}
                    </td>
                    <td style={{ fontFamily: 'var(--font-mono)', fontSize: '0.82rem', color: 'var(--text-secondary)' }}>
                      {p.barcode || '-'}
                    </td>
                    <td style={{ textAlign: 'right', color: 'var(--text-secondary)' }}>
                      {isOwner ? formatCurrency(p.purchasePrice) : '🔒 গোপনীয়'}
                    </td>
                    <td style={{ textAlign: 'right', fontWeight: 700, color: 'var(--primary)' }}>
                      {formatCurrency(p.salePrice)}
                    </td>
                    <td style={{ textAlign: 'center' }}>
                      <span
                        style={{
                          fontSize: '0.82rem',
                          fontWeight: 700,
                          padding: '3px 10px',
                          borderRadius: 'var(--radius-pill)',
                          background: isLowStock
                            ? 'rgba(239, 68, 68, 0.15)'
                            : 'rgba(16, 185, 129, 0.15)',
                          color: isLowStock ? 'var(--danger)' : 'var(--success)',
                        }}
                      >
                        {p.stockQty}
                      </span>
                    </td>
                    <td style={{ textAlign: 'center', color: 'var(--text-secondary)' }}>
                      {p.unitName}
                    </td>
                    {isOwner && (
                      <td style={{ textAlign: 'center' }}>
                        <div style={{ display: 'inline-flex', gap: '8px' }}>
                          <button
                            onClick={() => setPrintingProduct(p)}
                            className="btn btn-secondary btn-sm"
                            title="বারকোড প্রিন্ট করুন"
                            style={{ padding: '6px' }}
                          >
                            <Printer size={14} />
                          </button>
                          <button
                            onClick={() => openEditModal(p)}
                            className="btn btn-secondary btn-sm"
                            title="এডিট করুন"
                            style={{ padding: '6px' }}
                          >
                            <Edit2 size={14} />
                          </button>
                          <button
                            onClick={() => handleDelete(p.id, p.nameBn || p.nameEn)}
                            className="btn btn-danger btn-sm"
                            title="মুছে ফেলুন"
                            style={{ padding: '6px' }}
                          >
                            <Trash2 size={14} />
                          </button>
                        </div>
                      </td>
                    )}
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>

      {/* Add / Edit Product Modal */}
      {showModal && (
        <div className="modal-overlay" onClick={() => setShowModal(false)}>
          <div className="modal-card" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <Package size={20} color="var(--primary)" />
                <h3 style={{ fontSize: '1.15rem', fontWeight: 700 }}>
                  {editingProduct ? 'পণ্য সংশোধন (Edit Product)' : 'নতুন পণ্য যোগ করুন (New Product)'}
                </h3>
              </div>
              <button onClick={() => setShowModal(false)} className="btn btn-secondary btn-sm" style={{ padding: '6px' }}>
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handleSubmit}>
              <div className="modal-body">
                {formError && (
                  <div
                    style={{
                      background: 'rgba(239, 68, 68, 0.12)',
                      color: 'var(--danger)',
                      padding: '10px 14px',
                      borderRadius: 'var(--radius-sm)',
                      marginBottom: '16px',
                      fontSize: '0.85rem',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '8px',
                    }}
                  >
                    <AlertCircle size={16} />
                    <span>{formError}</span>
                  </div>
                )}

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '14px' }}>
                  <div className="input-group">
                    <label className="input-label">পণ্যের নাম (বাংলা)*</label>
                    <input
                      type="text"
                      className="input-field"
                      placeholder="যেমন: মিনিকেট চাল"
                      value={formData.nameBn}
                      onChange={(e) => setFormData({ ...formData, nameBn: e.target.value })}
                      required
                    />
                  </div>

                  <div className="input-group">
                    <label className="input-label">পণ্যের নাম (ইংরেজি)</label>
                    <input
                      type="text"
                      className="input-field"
                      placeholder="যেমন: Miniket Rice"
                      value={formData.nameEn}
                      onChange={(e) => setFormData({ ...formData, nameEn: e.target.value })}
                    />
                  </div>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '14px' }}>
                  <div className="input-group">
                    <label className="input-label">বারকোড (Barcode)</label>
                    <div style={{ display: 'flex', gap: '6px' }}>
                      <div style={{ position: 'relative', flex: 1 }}>
                        <Barcode
                          size={16}
                          style={{ position: 'absolute', left: '12px', top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted)' }}
                        />
                        <input
                          type="text"
                          className="input-field"
                          style={{ paddingLeft: '36px' }}
                          placeholder="বারকোড স্ক্যান করুন বা লিখুন"
                          value={formData.barcode}
                          onChange={(e) => setFormData({ ...formData, barcode: e.target.value })}
                        />
                      </div>
                      <button
                        type="button"
                        onClick={() => setFormData({ ...formData, barcode: generateRandomBarcode() })}
                        className="btn btn-secondary btn-sm"
                        style={{ whiteSpace: 'nowrap', fontSize: '0.78rem' }}
                        title="নতুন কোড জেনারেট করুন"
                      >
                        জেনারেট
                      </button>
                    </div>
                  </div>

                  <div className="input-group">
                    <label className="input-label">ক্যাটাগরি</label>
                    <select
                      className="input-field"
                      value={formData.categoryId}
                      onChange={(e) => setFormData({ ...formData, categoryId: e.target.value })}
                    >
                      {categories.map((c) => (
                        <option key={c.id} value={c.id}>
                          {c.nameBn || c.nameEn}
                        </option>
                      ))}
                    </select>
                  </div>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '14px' }}>
                  <div className="input-group">
                    <label className="input-label">ক্রয়মূল্য (Purchase Price)</label>
                    <input
                      type="number"
                      step="0.01"
                      className="input-field"
                      placeholder="৳ 0.00"
                      value={formData.purchasePrice}
                      onChange={(e) => setFormData({ ...formData, purchasePrice: e.target.value })}
                    />
                  </div>

                  <div className="input-group">
                    <label className="input-label">বিক্রয়মূল্য (Selling Price)*</label>
                    <input
                      type="number"
                      step="0.01"
                      className="input-field"
                      placeholder="৳ 0.00"
                      value={formData.salePrice}
                      onChange={(e) => setFormData({ ...formData, salePrice: e.target.value })}
                      required
                    />
                  </div>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '12px' }}>
                  <div className="input-group">
                    <label className="input-label">বর্তমান স্টক</label>
                    <input
                      type="number"
                      step="0.01"
                      className="input-field"
                      placeholder="0"
                      value={formData.stockQty}
                      onChange={(e) => setFormData({ ...formData, stockQty: e.target.value })}
                    />
                  </div>

                  <div className="input-group">
                    <label className="input-label">সতর্ক স্টক লেভেল</label>
                    <input
                      type="number"
                      step="0.01"
                      className="input-field"
                      placeholder="5"
                      value={formData.minStock}
                      onChange={(e) => setFormData({ ...formData, minStock: e.target.value })}
                    />
                  </div>

                  <div className="input-group">
                    <label className="input-label">একক (Unit)</label>
                    <select
                      className="input-field"
                      value={formData.unitName}
                      onChange={(e) => setFormData({ ...formData, unitName: e.target.value })}
                    >
                      <option value="কেজি">কেজি</option>
                      <option value="পিস">পিস</option>
                      <option value="লিটার">লিটার</option>
                      <option value="গ্রাম">গ্রাম</option>
                      <option value="প্যাকেট">প্যাকেট</option>
                      <option value="ডজন">ডজন</option>
                      <option value="বক্স">বক্স</option>
                      <option value="মিটার">মিটার</option>
                    </select>
                  </div>
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

      {/* Barcode Print Modal */}
      {printingProduct && (
        <BarcodePrintModal
          product={printingProduct}
          onClose={() => setPrintingProduct(null)}
        />
      )}
    </div>
  );
}
