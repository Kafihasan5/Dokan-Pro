import React, { Suspense, lazy, useState, useEffect, useRef } from 'react';
import { useShop } from '../context/ShopContext';
import {
  Search,
  Barcode,
  Plus,
  Minus,
  Trash2,
  CheckCircle,
  CreditCard,
  Banknote,
  Smartphone,
  BookOpen,
  User,
  ShoppingBag,
  Sparkles,
  List,
  LayoutGrid,
  Package,
  X,
  Pause,
  Play,
  RotateCcw,
  Check,
  ScanLine,
} from 'lucide-react';
import { formatCurrency, playSuccessBeep, playErrorBeep } from '../utils/formatters';
import { notify } from '../components/Feedback';

// Loaded only when the camera opens (the barcode library is large).
const CameraScanner = lazy(() => import('../components/CameraScanner'));

export default function PosPage({ onCompleteSale, scanRequest = 0, onScanHandled }) {
  const {
    products,
    categories,
    customers,
    shopInfo,
    createSale,
    permissions,
    auth,
    heldCarts,
    holdCart,
    restoreHeldCart,
    deleteHeldCart,
    processProductReturn,
  } = useShop();
  const isOwner = auth?.role === 'owner';
  const canDiscount = isOwner || permissions?.allowStaffDiscount !== false;
  const maxDiscountPct = isOwner ? 100 : Number(permissions?.maxStaffDiscountPercent || 10);
  const isMaintenanceMode = Boolean(permissions?.posMaintenanceMode) && !isOwner;

  // Modals for Held Carts & Return
  const [showHeldCartsModal, setShowHeldCartsModal] = useState(false);
  const [showCameraScanner, setShowCameraScanner] = useState(false);
  // The floating bar's "স্ক্যান" button opens this page with the camera already running.
  useEffect(() => {
    if (scanRequest > 0) {
      setShowCameraScanner(true);
      onScanHandled?.();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [scanRequest]);
  const [showReturnModal, setShowReturnModal] = useState(false);
  const [returnProductId, setReturnProductId] = useState('');
  const [returnQty, setReturnQty] = useState('1');
  const [returnRefundAmount, setReturnRefundAmount] = useState('');
  const [returnCustName, setReturnCustName] = useState('');
  const [returnReason, setReturnReason] = useState('');
  const [isReturnProcessing, setIsReturnProcessing] = useState(false);

  // Search & Filters
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('all');
  const [viewMode, setViewMode] = useState('list'); // Default to 'list' (লিস্ট ভিউ)
  const [scannerStatus, setScannerStatus] = useState('ready');
  const [scannerMessage, setScannerMessage] = useState('স্ক্যানার রেডি');

  // Cart State
  const [cart, setCart] = useState([]);
  const [qtyModalProduct, setQtyModalProduct] = useState(null);
  const [modalQtyInput, setModalQtyInput] = useState('1');
  const [discountType, setDiscountType] = useState('fixed'); // 'fixed' | 'percent'
  const [discountVal, setDiscountVal] = useState(0);
  const [selectedCustomerId, setSelectedCustomerId] = useState('');
  const [customerName, setCustomerName] = useState('');
  const [customerPhone, setCustomerPhone] = useState('');
  const [paymentType, setPaymentType] = useState('নগদ');
  const [paidAmount, setPaidAmount] = useState('');
  // Cash handed over by the customer (for the change calculation).
  const [cashReceived, setCashReceived] = useState('');
  const [isProcessing, setIsProcessing] = useState(false);

  // Search input ref for quick keyboard focus
  const searchInputRef = useRef(null);

  // Phones and tablets: never focus fields on our own, or the on-screen keyboard keeps popping up.
  const isTouchDevice = window.matchMedia('(hover: none), (pointer: coarse)').matches;

  // Keep the POS barcode field armed on desktop so a keyboard-wedge scanner can scan immediately.
  useEffect(() => {
    if (!isTouchDevice && window.matchMedia('(min-width: 1025px)').matches) {
      searchInputRef.current?.focus({ preventScroll: true });
    }
  }, []);

  // Auto Barcode Scanner Detection: Listen for scanner keyboard inputs
  useEffect(() => {
    let buffer = '';
    let firstKeyTime = 0;
    let lastKeyTime = 0;
    let maxGap = 0;
    let resetStatusTimer;

    const showScanStatus = (status, message) => {
      setScannerStatus(status);
      setScannerMessage(message);
      window.clearTimeout(resetStatusTimer);
      resetStatusTimer = window.setTimeout(() => {
        setScannerStatus('ready');
        setScannerMessage('স্ক্যানার রেডি');
      }, 2600);
    };

    const handleKeyDown = (e) => {
      const target = e.target;
      const isEditable = target instanceof HTMLElement && (
        target.isContentEditable || /^(TEXTAREA|SELECT)$/.test(target.tagName) ||
        (target.tagName === 'INPUT' && target !== searchInputRef.current)
      );
      // Do not interrupt typing in payment, customer or other form fields.
      if (isEditable) {
        return;
      }

      const currentTime = Date.now();
      const gap = lastKeyTime ? currentTime - lastKeyTime : 0;
      if (lastKeyTime && gap > 110) {
        buffer = '';
        firstKeyTime = currentTime;
        maxGap = 0;
      } else if (!buffer) {
        firstKeyTime = currentTime;
      }
      if (gap) maxGap = Math.max(maxGap, gap);
      lastKeyTime = currentTime;

      if (e.key === 'Enter' || e.key === 'Tab') {
        // Keyboard-wedge scanners send a fast character burst, usually followed by Enter or Tab.
        // A normal person typing into search is left alone unless it matches that pattern.
        const duration = currentTime - firstKeyTime;
        const looksLikeScanner = buffer.length >= 4 && duration <= 1200 && maxGap <= 110;
        if (looksLikeScanner) {
          e.preventDefault();
          const matched = products.find(
            (p) => p.barcode && p.barcode.toLowerCase() === buffer.toLowerCase()
          );
          if (matched) {
            addToCart(matched);
            setSearchTerm('');
            showScanStatus('success', `${matched.nameBn || matched.nameEn || 'পণ্য'} কার্টে যোগ হয়েছে`);
          } else {
            playErrorBeep();
            setSearchTerm('');
            showScanStatus('error', 'বারকোড মেলেনি');
          }
        }
        buffer = '';
        firstKeyTime = 0;
        lastKeyTime = 0;
        maxGap = 0;
      } else if (e.key.length === 1) {
        buffer += e.key;
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
      window.clearTimeout(resetStatusTimer);
    };
  }, [products]);

  // Filter Products
  const filteredProducts = products.filter((p) => {
    if (!p.isActive) return false;
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

  // Add to Cart
  const addToCart = (product, quantity = 1) => {
    const qty = Math.max(1, Number(quantity) || 1);
    setCart((prev) => {
      const existing = prev.find((item) => String(item.id) === String(product.id));
      if (existing) {
        return prev.map((item) =>
          String(item.id) === String(product.id)
            ? { ...item, quantity: item.quantity + qty }
            : item
        );
      } else {
        return [...prev, { ...product, quantity: qty }];
      }
    });
    playSuccessBeep();
  };

  // Camera scan (barcode or QR): exact barcode match goes straight into the cart.
  const handleCameraCode = (code) => {
    const c = String(code).trim().toLowerCase();
    // Also tolerate spaces/dashes and the extra leading 0 that UPC/EAN readers sometimes add.
    const loose = (v) => String(v ?? '').toLowerCase().replace(/[^a-z0-9]/g, '').replace(/^0+/, '');
    const lc = loose(c);
    const matched =
      products.find((p) => p.barcode && String(p.barcode).trim().toLowerCase() === c) ||
      (lc && products.find((p) => p.barcode && loose(p.barcode) === lc)) ||
      products.find((p) => String(p.id) === c || [p.nameBn, p.nameEn].some((n) => n && String(n).trim().toLowerCase() === c));
    if (!matched) {
      playErrorBeep();
      return false;
    }
    addToCart(matched);
    return true;
  };

  // Select Product: Auto-clear search query and open quantity modal
  const handleSelectProduct = (product) => {
    setSearchTerm('');
    setQtyModalProduct(product);
    setModalQtyInput('1');
  };

  // Adjust Quantity
  const updateQuantity = (productId, delta) => {
    setCart((prev) =>
      prev
        .map((item) => {
          if (String(item.id) === String(productId)) {
            const newQty = item.quantity + delta;
            return newQty > 0 ? { ...item, quantity: newQty } : null;
          }
          return item;
        })
        .filter(Boolean)
    );
  };

  // Remove Item
  const removeFromCart = (productId) => {
    setCart((prev) => prev.filter((item) => String(item.id) !== String(productId)));
  };

  // Cart Calculations
  const subtotal = cart.reduce((sum, it) => sum + it.salePrice * it.quantity, 0);

  let discountAmount = 0;
  if (discountType === 'percent') {
    discountAmount = (subtotal * Number(discountVal || 0)) / 100;
  } else {
    discountAmount = Number(discountVal || 0);
  }
  discountAmount = Math.min(discountAmount, subtotal);

  const taxableAmount = Math.max(0, subtotal - discountAmount);
  const vatRate = shopInfo.vatEnabled ? Number(shopInfo.vatPercentage || 0) : 0;
  const vatAmount = (taxableAmount * vatRate) / 100;
  const grandTotal = Math.round(taxableAmount + vatAmount);

  // Set paid amount default when grand total changes
  const received = cashReceived === '' ? null : Number(cashReceived);
  const effectivePaid =
    paidAmount !== '' ? Number(paidAmount) : received !== null ? Math.min(received, grandTotal) : grandTotal;
  const dueAmount = Math.max(0, grandTotal - effectivePaid);
  const changeAmount = received !== null ? Math.max(0, received - grandTotal) : 0;
  // One-tap amounts: exact bill, then the next round notes above it.
  const cashSuggestions = [...new Set([grandTotal, ...[50, 100, 500, 1000].map((n) => Math.ceil(grandTotal / n) * n)])]
    .filter((v) => v >= grandTotal && v > 0)
    .slice(0, 4);

  // Auto fill customer phone on customer select
  const handleCustomerChange = (custId) => {
    setSelectedCustomerId(custId);
    if (!custId) {
      setCustomerName('');
      setCustomerPhone('');
      return;
    }
    const cust = customers.find((c) => String(c.id) === String(custId));
    if (cust) {
      setCustomerName(cust.name);
      setCustomerPhone(cust.phone || '');
    }
  };

  // Submit Sale & Trigger Realtime Sync
  const handleCheckout = async () => {
    if (cart.length === 0) {
      notify('অনুগ্রহ করে কার্টে পণ্য যোগ করুন।');
      return;
    }

    if (dueAmount > 0 && !customerName.trim()) {
      notify('বাকি বিক্রয়ের ক্ষেত্রে ক্রেতার নাম বা কাস্টমার নির্বাচন করা আবশ্যক।');
      return;
    }

    try {
      setIsProcessing(true);

      const normalizedCartItems = cart.map((it) => {
        const pName = it.nameBn || it.nameEn || it.name || it.productName || 'পণ্য';
        const pQty = Number(it.quantity || 1);
        const pPrice = Number(it.salePrice || 0);
        return {
          ...it,
          id: it.id,
          productId: Number(it.id),
          name: pName,
          productName: pName,
          unitName: it.unitName || '',
          quantity: pQty,
          qty: pQty,
          salePrice: pPrice,
          unitPrice: pPrice,
          subtotal: pPrice * pQty,
          pricePoisha: Math.round(pPrice * 100),
          unitPricePoisha: Math.round(pPrice * 100),
          subtotalPoisha: Math.round(pPrice * pQty * 100),
          lineTotalPoisha: Math.round(pPrice * pQty * 100),
        };
      });

      const saleData = {
        subtotal,
        discount: discountAmount,
        vat: vatAmount,
        total: grandTotal,
        paidAmount: effectivePaid,
        dueAmount,
        paymentType,
        customerId: selectedCustomerId || null,
        customerName: customerName.trim() || 'নগদ ক্রেতা',
        customerPhone: customerPhone.trim(),
        items: normalizedCartItems,
      };

      const result = await createSale(saleData);

      // Reset Form
      setCart([]);
      setDiscountVal(0);
      setSelectedCustomerId('');
      setCustomerName('');
      setCustomerPhone('');
      setPaidAmount('');
      setCashReceived('');
      setPaymentType('নগদ');

      // Pop up Receipt Modal
      if (onCompleteSale) {
        onCompleteSale({
          ...saleData,
          id: result.saleId,
          invoiceNumber: result.invoiceNumber,
          createdAt: Date.now(),
        });
      }
    } catch (err) {
      notify('বিক্রয় সম্পন্ন করতে সমস্যা হয়েছে: ' + err.message);
    } finally {
      setIsProcessing(false);
    }
  };

  // Hold Current Cart
  const handleHoldCurrentCart = () => {
    if (cart.length === 0) {
      notify('কার্ট খালি! কোনো পণ্য কার্টে যোগ করে হোল্ড করুন।');
      return;
    }
    holdCart(cart, {
      customerId: selectedCustomerId,
      customerName: customerName.trim(),
      customerPhone: customerPhone.trim(),
    });
    setCart([]);
    setSelectedCustomerId('');
    setCustomerName('');
    setCustomerPhone('');
    setPaidAmount('');
    setCashReceived('');
    notify('বিল সফলভাবে হোল্ড করা হয়েছে! পরবর্তী ক্রেতাকে সার্ভিস দিতে পারেন।');
  };

  // Restore a Held Cart
  const handleRestoreHeld = (index) => {
    const restored = restoreHeldCart(index);
    if (restored) {
      setCart(restored.cart || []);
      if (restored.customer?.customerId) {
        setSelectedCustomerId(restored.customer.customerId);
        setCustomerName(restored.customer.customerName || '');
        setCustomerPhone(restored.customer.customerPhone || '');
      }
      setShowHeldCartsModal(false);
    }
  };

  // Process Product Return & Refund
  const handleProductReturnSubmit = async (e) => {
    e.preventDefault();
    if (!returnProductId) {
      notify('ফেরতকৃত পণ্য নির্বাচন করুন।');
      return;
    }
    try {
      setIsReturnProcessing(true);
      await processProductReturn({
        productId: returnProductId,
        quantity: Number(returnQty) || 1,
        refundAmount: Number(returnRefundAmount) || 0,
        customerName: returnCustName.trim(),
        reason: returnReason.trim(),
      });
      setShowReturnModal(false);
      setReturnProductId('');
      setReturnQty('1');
      setReturnRefundAmount('');
      setReturnCustName('');
      setReturnReason('');
      notify('পণ্য ফেরত সফলভাবে সম্পন্ন হয়েছে এবং স্টক বৃদ্ধি করা হয়েছে!');
    } catch (err) {
      notify('পণ্য ফেরত প্রক্রিয়ায় সমস্যা: ' + err.message);
    } finally {
      setIsReturnProcessing(false);
    }
  };

  return (
    <div className="pos-layout">
      {showCameraScanner && (
        <Suspense fallback={null}>
          <CameraScanner onDetected={handleCameraCode} onClose={() => setShowCameraScanner(false)} />
        </Suspense>
      )}
      {/* LEFT: Product Catalog & Search */}
      <div className="pos-catalog-panel">
        {/* Top Search & Filter Bar */}
        <div style={{ display: 'flex', gap: '12px' }}>
          <div style={{ position: 'relative', flex: 1 }}>
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
              ref={searchInputRef}
              type="text"
              className="input-field"
              style={{ paddingLeft: '40px' }}
              placeholder="পণ্য বা বারকোড খুঁজুন (নাম, কোড লিখুন বা স্ক্যানার ব্যবহার করুন)..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
            />
          </div>

          <button
            type="button"
            className="pos-camera-btn"
            onClick={() => setShowCameraScanner(true)}
            title="ক্যামেরা দিয়ে বারকোড / QR স্ক্যান"
            aria-label="ক্যামেরা দিয়ে বারকোড / QR স্ক্যান"
          >
            <ScanLine size={20} />
            <span>স্ক্যান</span>
          </button>

          <div
            className={`scanner-status hide-mobile ${scannerStatus}`}
            title="কিবোর্ড-HID বারকোড স্ক্যানারের ইনপুটের জন্য প্রস্তুত"
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              padding: '0 12px',
              background: 'var(--bg-surface)',
              borderRadius: 'var(--radius-md)',
              border: '1px solid var(--border-subtle)',
              color: 'var(--text-secondary)',
              fontSize: '0.8rem',
            }}
          >
            <span className="scanner-status-dot" />
            <Barcode size={18} color={scannerStatus === 'error' ? 'var(--danger)' : 'var(--primary)'} />
            <span>{scannerMessage}</span>
          </div>
        </div>

        {/* Category Pills & View Switcher */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: '12px',
          }}
        >
          <div
            style={{
              display: 'flex',
              gap: '8px',
              overflowX: 'auto',
              paddingBottom: '4px',
              flex: 1,
            }}
          >
            <button
              onClick={() => setSelectedCategory('all')}
              className={`btn btn-sm ${
                selectedCategory === 'all' ? 'btn-primary' : 'btn-secondary'
              }`}
            >
              সব ক্যাটাগরি
            </button>
            {categories.map((cat) => (
              <button
                key={cat.id}
                onClick={() => setSelectedCategory(String(cat.id))}
                className={`btn btn-sm ${
                  String(selectedCategory) === String(cat.id) ? 'btn-primary' : 'btn-secondary'
                }`}
              >
                {cat.nameBn || cat.nameEn}
              </button>
            ))}
          </div>

          {/* View Switcher: List vs Grid */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              background: 'var(--bg-card)',
              border: '1px solid var(--border-subtle)',
              borderRadius: 'var(--radius-md)',
              padding: '2px',
              gap: '2px',
              flexShrink: 0,
            }}
          >
            <button
              type="button"
              onClick={() => setViewMode('list')}
              title="লিস্ট ভিউ (তালিকা)"
              className={`btn btn-sm ${viewMode === 'list' ? 'btn-primary' : 'btn-ghost'}`}
              style={{
                padding: '4px 10px',
                fontSize: '0.8rem',
                borderRadius: 'var(--radius-sm)',
                gap: '5px',
              }}
            >
              <List size={15} />
              <span>লিস্ট</span>
            </button>
            <button
              type="button"
              onClick={() => setViewMode('grid')}
              title="গ্রিড ভিউ (কার্ড)"
              className={`btn btn-sm ${viewMode === 'grid' ? 'btn-primary' : 'btn-ghost'}`}
              style={{
                padding: '4px 10px',
                fontSize: '0.8rem',
                borderRadius: 'var(--radius-sm)',
                gap: '5px',
              }}
            >
              <LayoutGrid size={15} />
              <span>গ্রিড</span>
            </button>
          </div>
        </div>

        {/* Product Listing (List View by default, or Grid View) */}
        {filteredProducts.length === 0 ? (
          <div
            style={{
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center',
              padding: '60px 20px',
              color: 'var(--text-muted)',
              background: 'var(--bg-surface)',
              borderRadius: 'var(--radius-md)',
              border: '1px dashed var(--border-subtle)',
            }}
          >
            <ShoppingBag size={48} style={{ opacity: 0.3, marginBottom: '12px' }} />
            <p style={{ fontSize: '0.95rem' }}>কোনো পণ্য পাওয়া যায়নি</p>
          </div>
        ) : viewMode === 'list' ? (
          <div className="pos-products-list">
            {filteredProducts.map((p) => {
              const isOutOfStock = p.stockQty <= 0;
              const isLowStock = !isOutOfStock && p.stockQty <= (p.alertQty || 5);
              const catObj = categories.find((c) => String(c.id) === String(p.categoryId));
              return (
                <div
                  key={p.id}
                  onClick={() => !isOutOfStock && handleSelectProduct(p)}
                  className={`pos-list-item ${isOutOfStock ? 'out-of-stock' : ''}`}
                >
                  {/* Left: Product Name, Barcode, Category */}
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
                      <span
                        style={{
                          fontSize: '0.96rem',
                          fontWeight: 700,
                          color: 'var(--text-primary)',
                        }}
                      >
                        {p.nameBn || p.nameEn}
                      </span>
                      {p.nameBn && p.nameEn && (
                        <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                          ({p.nameEn})
                        </span>
                      )}
                      {catObj && (
                        <span
                          style={{
                            fontSize: '0.7rem',
                            padding: '2px 8px',
                            borderRadius: 'var(--radius-pill)',
                            background: 'var(--bg-card)',
                            color: 'var(--text-secondary)',
                            border: '1px solid var(--border-subtle)',
                          }}
                        >
                          {catObj.nameBn || catObj.nameEn}
                        </span>
                      )}
                    </div>
                    {p.barcode && (
                      <div
                        style={{
                          fontSize: '0.74rem',
                          color: 'var(--text-muted)',
                          fontFamily: 'var(--font-mono)',
                          marginTop: '3px',
                          display: 'flex',
                          alignItems: 'center',
                          gap: '4px',
                        }}
                      >
                        <Barcode size={13} color="var(--primary)" /> #{p.barcode}
                      </div>
                    )}
                  </div>

                  {/* Center: Stock Status Badge */}
                  <div style={{ padding: '0 10px', flexShrink: 0 }}>
                    <span
                      style={{
                        fontSize: '0.78rem',
                        fontWeight: 600,
                        padding: '4px 10px',
                        borderRadius: 'var(--radius-pill)',
                        background: isOutOfStock
                          ? 'rgba(239, 68, 68, 0.15)'
                          : isLowStock
                          ? 'rgba(245, 158, 11, 0.15)'
                          : 'rgba(16, 185, 129, 0.12)',
                        color: isOutOfStock
                          ? 'var(--danger)'
                          : isLowStock
                          ? 'var(--warning)'
                          : 'var(--primary)',
                        border: `1px solid ${
                          isOutOfStock
                            ? 'rgba(239, 68, 68, 0.3)'
                            : isLowStock
                            ? 'rgba(245, 158, 11, 0.3)'
                            : 'rgba(16, 185, 129, 0.25)'
                        }`,
                      }}
                    >
                      {isOutOfStock ? 'স্টক শেষ' : `স্টক: ${p.stockQty} ${p.unitName || ''}`}
                    </span>
                  </div>

                  {/* Right: Price & Quick Add Button */}
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: '12px',
                      flexShrink: 0,
                    }}
                  >
                    <div
                      style={{
                        fontSize: '1.12rem',
                        fontWeight: 800,
                        color: 'var(--primary)',
                        fontFamily: 'var(--font-mono)',
                        minWidth: '80px',
                        textAlign: 'right',
                      }}
                    >
                      {formatCurrency(p.salePrice)}
                    </div>
                    <button
                      type="button"
                      disabled={isOutOfStock}
                      onClick={(e) => {
                        e.stopPropagation();
                        if (!isOutOfStock) handleSelectProduct(p);
                      }}
                      className="btn btn-primary btn-sm"
                      style={{
                        padding: '6px 14px',
                        borderRadius: 'var(--radius-md)',
                        fontSize: '0.82rem',
                        gap: '5px',
                      }}
                    >
                      <Plus size={14} />
                      <span>যোগ</span>
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        ) : (
          <div className="pos-products-grid">
            {filteredProducts.map((p) => {
              const isOutOfStock = p.stockQty <= 0;
              return (
                <div
                  key={p.id}
                  onClick={() => !isOutOfStock && handleSelectProduct(p)}
                  className="pos-product-card"
                  style={{
                    opacity: isOutOfStock ? 0.5 : 1,
                    cursor: isOutOfStock ? 'not-allowed' : 'pointer',
                  }}
                >
                  <div>
                    <div
                      style={{
                        fontSize: '0.92rem',
                        fontWeight: 700,
                        color: 'var(--text-primary)',
                        marginBottom: '4px',
                      }}
                    >
                      {p.nameBn || p.nameEn}
                    </div>
                    {p.barcode && (
                      <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)', fontFamily: 'var(--font-mono)' }}>
                        #{p.barcode}
                      </div>
                    )}
                  </div>

                  <div style={{ marginTop: '14px', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                    <div style={{ fontSize: '1.05rem', fontWeight: 800, color: 'var(--primary)' }}>
                      {formatCurrency(p.salePrice)}
                    </div>
                    <span
                      style={{
                        fontSize: '0.72rem',
                        fontWeight: 600,
                        padding: '2px 6px',
                        borderRadius: 'var(--radius-pill)',
                        background: isOutOfStock ? 'rgba(239, 68, 68, 0.15)' : 'var(--bg-card)',
                        color: isOutOfStock ? 'var(--danger)' : 'var(--text-secondary)',
                      }}
                    >
                      স্টক: {p.stockQty} {p.unitName}
                    </span>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* RIGHT: Live POS Cart & Cashier Panel */}
      <div className="pos-cart-panel">
        <div className="pos-cart-header" style={{ flexWrap: 'wrap', gap: '8px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <ShoppingBag size={20} color="var(--primary)" />
            <h3 style={{ fontSize: '1.1rem', fontWeight: 700 }}>বিক্রয় রশিদ (Cart)</h3>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            {/* Hold Cart Button */}
            <button
              type="button"
              disabled={cart.length === 0}
              onClick={handleHoldCurrentCart}
              className="btn btn-secondary btn-sm"
              title="বর্তমান কার্ট হোল্ড করুন"
              style={{ padding: '4px 8px', fontSize: '0.78rem', gap: '4px' }}
            >
              <Pause size={13} />
              <span>হোল্ড</span>
            </button>

            {/* View Held Carts Button */}
            {heldCarts?.length > 0 && (
              <button
                type="button"
                onClick={() => setShowHeldCartsModal(true)}
                className="btn btn-warning btn-sm"
                title="হোল্ড করা বিল দেখুন"
                style={{
                  padding: '4px 8px',
                  fontSize: '0.78rem',
                  gap: '4px',
                  background: 'rgba(245, 158, 11, 0.15)',
                  color: 'var(--warning)',
                  border: '1px solid rgba(245, 158, 11, 0.3)',
                }}
              >
                <Play size={13} />
                <span>হোল্ড ({heldCarts.length})</span>
              </button>
            )}

            {/* Product Return Button */}
            <button
              type="button"
              onClick={() => setShowReturnModal(true)}
              className="btn btn-ghost btn-sm"
              title="পণ্য ফেরত ও রিফান্ড"
              style={{ padding: '4px 8px', fontSize: '0.78rem', gap: '4px' }}
            >
              <RotateCcw size={13} />
              <span>ফেরত</span>
            </button>
          </div>
        </div>

        {/* Customer Selector Bar */}
        <div style={{ padding: '12px 16px', borderBottom: '1px solid var(--border-subtle)', background: 'var(--bg-card)' }}>
          <div style={{ display: 'flex', gap: '8px' }}>
            <select
              className="input-field"
              style={{ fontSize: '0.84rem', padding: '6px 10px' }}
              value={selectedCustomerId}
              onChange={(e) => handleCustomerChange(e.target.value)}
            >
              <option value="">-- নগদ ক্রেতা --</option>
              {customers.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name} {c.totalDue > 0 ? `(বাকি: ৳${c.totalDue})` : ''}
                </option>
              ))}
            </select>
          </div>
        </div>

        {/* Cart Item Rows */}
        <div className="pos-cart-items">
          {cart.length === 0 ? (
            <div style={{ textAlign: 'center', margin: 'auto 0', color: 'var(--text-muted)' }}>
              <ShoppingBag size={48} style={{ margin: '0 auto 12px auto', opacity: 0.3 }} />
              <p style={{ fontSize: '0.9rem' }}>কার্ট খালি। বাম পাশ থেকে পণ্য নির্বাচন করুন।</p>
            </div>
          ) : (
            cart.map((item) => (
              <div key={item.id} className="pos-cart-item">
                <div style={{ flex: 1, minWidth: 0, paddingRight: '10px' }}>
                  <div style={{ fontSize: '0.88rem', fontWeight: 600, color: 'var(--text-primary)' }}>
                    {item.nameBn || item.nameEn}
                  </div>
                  <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>
                    {formatCurrency(item.salePrice)} × {item.quantity} = <strong>{formatCurrency(item.salePrice * item.quantity)}</strong>
                  </div>
                </div>

                <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                  <button
                    onClick={() => updateQuantity(item.id, -1)}
                    className="btn btn-secondary btn-sm"
                    style={{ padding: '4px 8px' }}
                  >
                    <Minus size={13} />
                  </button>
                  <span style={{ fontSize: '0.9rem', fontWeight: 700, minWidth: '24px', textAlign: 'center' }}>
                    {item.quantity}
                  </span>
                  <button
                    onClick={() => updateQuantity(item.id, 1)}
                    className="btn btn-secondary btn-sm"
                    style={{ padding: '4px 8px' }}
                  >
                    <Plus size={13} />
                  </button>
                  <button
                    onClick={() => removeFromCart(item.id)}
                    className="btn btn-danger btn-sm"
                    style={{ padding: '4px 8px', marginLeft: '4px' }}
                  >
                    <Trash2 size={13} />
                  </button>
                </div>
              </div>
            ))
          )}
        </div>

        {/* Footer Billing Section */}
        <div className="pos-cart-footer">
          {/* Subtotal */}
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.88rem', marginBottom: '8px' }}>
            <span style={{ color: 'var(--text-secondary)' }}>সাবটোটাল:</span>
            <span style={{ fontWeight: 600 }}>{formatCurrency(subtotal)}</span>
          </div>

          {/* Discount Inputs */}
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '8px', marginBottom: '8px' }}>
            <span style={{ fontSize: '0.84rem', color: 'var(--text-secondary)' }}>ডিসকাউন্ট:</span>
            {canDiscount ? (
              <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                <input
                  type="number"
                  min="0"
                  className="input-field"
                  style={{ width: '80px', padding: '4px 8px', fontSize: '0.84rem', textAlign: 'right' }}
                  value={discountVal || ''}
                  placeholder="0"
                  onChange={(e) => {
                    let val = Math.max(0, Number(e.target.value));
                    if (discountType === 'percent' && val > maxDiscountPct) {
                      val = maxDiscountPct;
                    }
                    setDiscountVal(val);
                  }}
                />
                <select
                  className="input-field"
                  style={{ width: '56px', padding: '4px 4px', fontSize: '0.8rem' }}
                  value={discountType}
                  onChange={(e) => setDiscountType(e.target.value)}
                >
                  <option value="fixed">৳</option>
                  <option value="percent">%</option>
                </select>
              </div>
            ) : (
              <span style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>অনুমোদন নেই</span>
            )}
          </div>

          {/* VAT */}
          {shopInfo.vatEnabled && (
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.84rem', marginBottom: '8px' }}>
              <span style={{ color: 'var(--text-secondary)' }}>ভ্যাট ({shopInfo.vatPercentage}%):</span>
              <span style={{ fontWeight: 600 }}>+{formatCurrency(vatAmount)}</span>
            </div>
          )}

          {/* Grand Total */}
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              borderTop: '1px solid var(--border-subtle)',
              paddingTop: '10px',
              marginTop: '4px',
              marginBottom: '14px',
            }}
          >
            <span style={{ fontSize: '1.05rem', fontWeight: 800, color: 'var(--text-primary)' }}>
              সর্বমোট বিল:
            </span>
            <span style={{ fontSize: '1.45rem', fontWeight: 800, color: 'var(--primary)', fontFamily: 'var(--font-mono)' }}>
              {formatCurrency(grandTotal)}
            </span>
          </div>

          {/* Payment Type Buttons */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '6px', marginBottom: '14px' }}>
            {[
              { id: 'নগদ', label: 'নগদ', icon: Banknote },
              { id: 'বিকাশ', label: 'বিকাশ/নগদ', icon: Smartphone },
              { id: 'কার্ড', label: 'কার্ড', icon: CreditCard },
              { id: 'বাকি', label: 'বাকি (Due)', icon: BookOpen },
            ].map((p) => {
              const IconComp = p.icon;
              const isSelected = paymentType === p.id;
              return (
                <button
                  key={p.id}
                  onClick={() => {
                    setPaymentType(p.id);
                    if (p.id === 'বাকি') setPaidAmount('0');
                    else setPaidAmount('');
                    setCashReceived('');
                  }}
                  className={`btn btn-sm ${isSelected ? 'btn-primary' : 'btn-secondary'}`}
                  style={{ padding: '8px 4px', fontSize: '0.78rem', flexDirection: 'column', gap: '3px' }}
                >
                  <IconComp size={15} />
                  <span>{p.label}</span>
                </button>
              );
            })}
          </div>

          {/* Cash received & change */}
          {cart.length > 0 && paymentType !== 'বাকি' && (
            <div className="cash-box">
              <label className="cash-label" htmlFor="cash-received">ক্রেতা কত টাকা দিলেন?</label>
              <div className="cash-input-row">
                <span className="cash-currency">৳</span>
                <input
                  id="cash-received"
                  className="cash-input"
                  inputMode="decimal"
                  autoComplete="off"
                  placeholder={String(grandTotal)}
                  value={cashReceived}
                  onChange={(e) => {
                    const v = e.target.value
                      .replace(/[০-৯]/g, (d) => String('০১২৩৪৫৬৭৮৯'.indexOf(d)))
                      .replace(/[^0-9.]/g, '')
                      .replace(/(\..*)\./g, '$1');
                    setCashReceived(v);
                  }}
                />
                {cashReceived !== '' && (
                  <button type="button" className="cash-clear" onClick={() => setCashReceived('')} aria-label="মুছুন">
                    <X size={16} />
                  </button>
                )}
              </div>
              <div className="cash-chips">
                {cashSuggestions.map((v, i) => (
                  <button
                    key={v}
                    type="button"
                    className={`cash-chip ${Number(cashReceived) === v ? 'active' : ''}`}
                    onClick={() => setCashReceived(String(v))}
                  >
                    {i === 0 ? 'ঠিক ঠিক' : formatCurrency(v)}
                  </button>
                ))}
              </div>
              {received !== null && received >= grandTotal && (
                <div className="cash-result ok">
                  <span>{changeAmount > 0 ? 'ফেরত দিন' : 'ফেরত নেই — হুবহু পরিশোধ'}</span>
                  {changeAmount > 0 && <strong>{formatCurrency(changeAmount)}</strong>}
                </div>
              )}
              {received !== null && received < grandTotal && (
                <div className="cash-result short">
                  <span>{formatCurrency(grandTotal - received)} কম — বাকি থাকবে</span>
                  <small>বাকির জন্য ক্রেতার নাম দিন</small>
                </div>
              )}
            </div>
          )}

          {/* Maintenance Notice if active */}
          {isMaintenanceMode && (
            <div
              style={{
                background: 'rgba(245, 158, 11, 0.15)',
                border: '1px solid rgba(245, 158, 11, 0.35)',
                borderRadius: 'var(--radius-md)',
                padding: '8px 12px',
                color: 'var(--warning)',
                fontSize: '0.82rem',
                textAlign: 'center',
                marginBottom: '10px',
                fontWeight: 600,
              }}
            >
              ⚠️ পিওএস কাউন্টার সাময়িক স্থগিত (মালিক কর্তৃক লক)
            </div>
          )}

          {/* Checkout Button */}
          <button
            onClick={handleCheckout}
            disabled={cart.length === 0 || isProcessing || isMaintenanceMode}
            className="btn btn-primary btn-lg"
            style={{ width: '100%' }}
          >
            {isProcessing ? (
              <span>প্রসেসিং ও সিঙ্ক হচ্ছে...</span>
            ) : isMaintenanceMode ? (
              <span>কাউন্টার স্থগিত রয়েছে</span>
            ) : (
              <>
                <CheckCircle size={20} />
                <span>বিল সম্পন্ন করুন ({formatCurrency(grandTotal)})</span>
              </>
            )}
          </button>
        </div>
      </div>

      {/* Product Quantity Modal */}
      {qtyModalProduct && (
        <div className="modal-overlay" onClick={() => setQtyModalProduct(null)}>
          <div className="modal-card" onClick={(e) => e.stopPropagation()} style={{ maxWidth: '440px' }}>
            <div className="modal-header">
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <Package size={20} color="var(--primary)" />
                <h3 style={{ margin: 0, fontSize: '1.1rem', fontWeight: 700 }}>
                  {qtyModalProduct.nameBn || qtyModalProduct.nameEn}
                </h3>
              </div>
              <button
                onClick={() => setQtyModalProduct(null)}
                className="btn btn-secondary btn-sm"
                style={{ padding: '6px' }}
              >
                <X size={18} />
              </button>
            </div>
            <div className="modal-body" style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
              <div
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  background: 'var(--bg-card)',
                  padding: '10px 14px',
                  borderRadius: 'var(--radius-md)',
                  border: '1px solid var(--border-subtle)',
                }}
              >
                <span style={{ fontSize: '0.88rem', color: 'var(--text-secondary)' }}>
                  মূল্য: <strong style={{ color: 'var(--text-primary)' }}>{formatCurrency(qtyModalProduct.salePrice)}</strong> / {qtyModalProduct.unit || 'পিস'}
                </span>
                <span style={{ fontSize: '0.88rem', color: 'var(--primary)', fontWeight: 600 }}>
                  স্টক: {qtyModalProduct.stockQty} {qtyModalProduct.unit || 'পিস'}
                </span>
              </div>

              <div>
                <label className="input-label" style={{ marginBottom: '8px', display: 'block' }}>
                  পরিমাণ লিখুন ({qtyModalProduct.unit || 'পিস'}):
                </label>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <button
                    type="button"
                    className="btn btn-secondary"
                    style={{ width: '44px', height: '44px', padding: 0 }}
                    onClick={() => {
                      const cur = Number(modalQtyInput) || 1;
                      setModalQtyInput(String(Math.max(1, cur - 1)));
                    }}
                  >
                    <Minus size={18} />
                  </button>
                  <input
                    type="number"
                    min="1"
                    className="input-field"
                    style={{ textAlign: 'center', fontSize: '1.2rem', fontWeight: 700 }}
                    value={modalQtyInput}
                    onChange={(e) => setModalQtyInput(e.target.value)}
                    autoFocus={!isTouchDevice}
                    inputMode="numeric"
                  />
                  <button
                    type="button"
                    className="btn btn-secondary"
                    style={{ width: '44px', height: '44px', padding: 0 }}
                    onClick={() => {
                      const cur = Number(modalQtyInput) || 0;
                      setModalQtyInput(String(cur + 1));
                    }}
                  >
                    <Plus size={18} />
                  </button>
                </div>
              </div>

              {/* Quick Chips */}
              <div>
                <span style={{ fontSize: '0.78rem', color: 'var(--text-secondary)', display: 'block', marginBottom: '6px' }}>
                  দ্রুত বাছাই:
                </span>
                <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
                  {[1, 2, 3, 4, 5, 6, 10, 12, 24].map((q) => (
                    <button
                      key={q}
                      type="button"
                      onClick={() => setModalQtyInput(String(q))}
                      className="btn btn-sm"
                      style={{
                        background: Number(modalQtyInput) === q ? 'var(--primary)' : 'var(--bg-card)',
                        color: Number(modalQtyInput) === q ? '#fff' : 'var(--text-primary)',
                        border: '1px solid var(--border-subtle)',
                        borderRadius: 'var(--radius-pill)',
                        padding: '4px 10px',
                        fontSize: '0.8rem',
                      }}
                    >
                      {q}টি
                    </button>
                  ))}
                </div>
              </div>

              {/* Summary total */}
              <div
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  background: 'var(--bg-input)',
                  padding: '12px 16px',
                  borderRadius: 'var(--radius-md)',
                  border: '1px solid var(--border-subtle)',
                }}
              >
                <span style={{ fontSize: '0.9rem', color: 'var(--text-secondary)' }}>মোট মূল্য:</span>
                <span style={{ fontSize: '1.25rem', fontWeight: 800, color: 'var(--primary)' }}>
                  {formatCurrency(qtyModalProduct.salePrice * (Math.max(1, Number(modalQtyInput) || 1)))}
                </span>
              </div>

              {/* Actions */}
              <div style={{ display: 'flex', gap: '10px', marginTop: '4px' }}>
                <button
                  type="button"
                  onClick={() => setQtyModalProduct(null)}
                  className="btn btn-secondary"
                  style={{ flex: 1 }}
                >
                  বাতিল
                </button>
                <button
                  type="button"
                  onClick={() => {
                    const finalQty = Math.max(1, Number(modalQtyInput) || 1);
                    addToCart(qtyModalProduct, finalQty);
                    setQtyModalProduct(null);
                  }}
                  className="btn btn-primary"
                  style={{ flex: 2 }}
                >
                  কার্টে যোগ করুন
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* MODAL: HELD CARTS */}
      {showHeldCartsModal && (
        <div className="modal-overlay">
          <div className="modal-card" style={{ maxWidth: '560px' }}>
            <div className="modal-header">
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <Play size={20} color="var(--warning)" />
                <h3 style={{ fontSize: '1.2rem', fontWeight: 700 }}>হোল্ড করা কার্ট তালিকা</h3>
              </div>
              <button onClick={() => setShowHeldCartsModal(false)} className="btn btn-ghost btn-sm">
                <X size={18} />
              </button>
            </div>

            <div className="modal-body" style={{ maxHeight: '60vh', overflowY: 'auto' }}>
              {heldCarts?.length === 0 ? (
                <div style={{ textAlign: 'center', padding: '30px', color: 'var(--text-muted)' }}>
                  বর্তমানে কোনো হোল্ড করা বিল নেই।
                </div>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                  {heldCarts?.map((entry, idx) => (
                    <div
                      key={entry.id || idx}
                      style={{
                        background: 'var(--bg-card)',
                        padding: '12px 16px',
                        borderRadius: 'var(--radius-md)',
                        border: '1px solid var(--border-subtle)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                      }}
                    >
                      <div>
                        <div style={{ fontWeight: 700, fontSize: '0.92rem', color: 'var(--text-primary)' }}>
                          {entry.customer?.customerName || 'নগদ ক্রেতা'} ({entry.totalQty || entry.cart?.length} টি আইটেম)
                        </div>
                        <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)', marginTop: '2px' }}>
                          সময়: {new Date(entry.timestamp).toLocaleTimeString()} • মোট: <strong style={{ color: 'var(--primary)' }}>{formatCurrency(entry.grandTotal)}</strong>
                        </div>
                      </div>

                      <div style={{ display: 'flex', gap: '8px' }}>
                        <button
                          type="button"
                          onClick={() => handleRestoreHeld(idx)}
                          className="btn btn-primary btn-sm"
                          style={{ padding: '5px 12px', fontSize: '0.8rem' }}
                        >
                          রিস্টোর
                        </button>
                        <button
                          type="button"
                          onClick={() => deleteHeldCart(idx)}
                          className="btn btn-ghost btn-sm"
                          style={{ padding: '5px 8px', color: 'var(--danger)' }}
                        >
                          <Trash2 size={15} />
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <div className="modal-footer" style={{ display: 'flex', justifyContent: 'flex-end', padding: '14px 20px', borderTop: '1px solid var(--border-subtle)' }}>
              <button type="button" onClick={() => setShowHeldCartsModal(false)} className="btn btn-secondary">
                বন্ধ করুন
              </button>
            </div>
          </div>
        </div>
      )}

      {/* MODAL: PRODUCT RETURN & REFUND */}
      {showReturnModal && (
        <div className="modal-overlay">
          <div className="modal-card" style={{ maxWidth: '520px' }}>
            <div className="modal-header">
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <RotateCcw size={20} color="var(--primary)" />
                <h3 style={{ fontSize: '1.2rem', fontWeight: 700 }}>পণ্য ফেরত ও রিফান্ড</h3>
              </div>
              <button onClick={() => setShowReturnModal(false)} className="btn btn-ghost btn-sm">
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handleProductReturnSubmit}>
              <div className="modal-body">
                <div className="input-group">
                  <label className="input-label">ফেরতকৃত পণ্য*</label>
                  <select
                    className="input-field"
                    value={returnProductId}
                    onChange={(e) => {
                      setReturnProductId(e.target.value);
                      const prod = products.find((p) => String(p.id) === String(e.target.value));
                      if (prod) {
                        setReturnRefundAmount(String(prod.salePrice * (Number(returnQty) || 1)));
                      }
                    }}
                    required
                  >
                    <option value="">-- পণ্য নির্বাচন করুন --</option>
                    {products.map((p) => (
                      <option key={p.id} value={p.id}>
                        {p.nameBn || p.nameEn || p.name} (বিক্রয় দর: ৳{p.salePrice})
                      </option>
                    ))}
                  </select>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                  <div className="input-group">
                    <label className="input-label">ফেরতের পরিমাণ (Qty)*</label>
                    <input
                      type="number"
                      min="1"
                      className="input-field"
                      value={returnQty}
                      onChange={(e) => {
                        const q = e.target.value;
                        setReturnQty(q);
                        const prod = products.find((p) => String(p.id) === String(returnProductId));
                        if (prod) {
                          setReturnRefundAmount(String(prod.salePrice * (Math.max(1, Number(q) || 1))));
                        }
                      }}
                      required
                    />
                  </div>

                  <div className="input-group">
                    <label className="input-label">রিফান্ড পরিমাণ (৳)*</label>
                    <input
                      type="number"
                      min="0"
                      step="any"
                      className="input-field"
                      value={returnRefundAmount}
                      onChange={(e) => setReturnRefundAmount(e.target.value)}
                      required
                    />
                  </div>
                </div>

                <div className="input-group">
                  <label className="input-label">ক্রেতার নাম (ঐচ্ছিক)</label>
                  <input
                    type="text"
                    className="input-field"
                    placeholder="ক্রেতার নাম"
                    value={returnCustName}
                    onChange={(e) => setReturnCustName(e.target.value)}
                  />
                </div>

                <div className="input-group">
                  <label className="input-label">ফেরতের কারণ</label>
                  <input
                    type="text"
                    className="input-field"
                    placeholder="যেমন: মেয়াদোত্তীর্ণ / ডিফেক্টিভ / পরিবর্তন"
                    value={returnReason}
                    onChange={(e) => setReturnReason(e.target.value)}
                  />
                </div>
              </div>

              <div className="modal-footer" style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', padding: '14px 20px', borderTop: '1px solid var(--border-subtle)' }}>
                <button type="button" onClick={() => setShowReturnModal(false)} className="btn btn-secondary">
                  বাতিল
                </button>
                <button type="submit" disabled={isReturnProcessing} className="btn btn-primary">
                  <Check size={16} />
                  <span>{isReturnProcessing ? 'প্রক্রিয়াধীন...' : 'ফেরত সম্পন্ন করুন'}</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
