// Map raw Firebase records (shared with the Android app) to the shapes the web UI uses.
import { poishaToTaka, takaToPoisha } from '../utils/formatters';

// Older Android versions stored isActive as 1/0; 0 means deleted/inactive.
const isActive = (v) => v !== false && v !== 0 && v !== '0';

const toList = (data, mapFn) => Object.keys(data || {}).map((key) => mapFn(data[key] || {}, key));

const asArray = (items) =>
  items && !Array.isArray(items) && typeof items === 'object' ? Object.values(items) : items || [];

export function normalizeCategories(data) {
  return toList(data, (c, key) => ({
    id: c.id || key,
    nameBn: c.nameBn || c.name || '',
    nameEn: c.nameEn || '',
    icon: c.icon || 'Package',
  }));
}

export function normalizeProducts(data) {
  return toList(data, (item, key) => ({
    id: String(item.id || key),
    firebaseKey: key,
    nameBn: item.nameBn || '',
    nameEn: item.nameEn || '',
    name: item.nameBn || item.nameEn || item.name || item.productName || '',
    barcode: item.barcode || '',
    purchasePrice: poishaToTaka(item.purchasePricePoisha ?? 0),
    salePrice: poishaToTaka(item.salePricePoisha ?? item.sellingPricePoisha ?? 0),
    wholesalePrice: poishaToTaka(item.wholesalePricePoisha ?? 0),
    stockQty: Number(item.stockQty ?? 0),
    minStock: Number(item.minStock ?? 5),
    unitName: item.unitName || 'কেজি',
    categoryId: String(item.categoryId || 1),
    isActive: isActive(item.isActive),
    updatedAt: item.updatedAt || Date.now(),
  }));
}

function normalizeSaleItem(it, idx) {
  const unitPrice = Number(
    it.salePrice ?? (it.unitPricePoisha ? it.unitPricePoisha / 100 : it.pricePoisha ? it.pricePoisha / 100 : 0)
  );
  const quantity = Number(it.qty ?? it.quantity ?? 1);
  const lineTotal = Number(
    it.lineTotalPoisha ? it.lineTotalPoisha / 100 : it.subtotalPoisha ? it.subtotalPoisha / 100 : unitPrice * quantity
  );
  const name = it.productName || it.name || it.nameBn || it.nameEn || it.title || `পণ্য #${idx + 1}`;
  return {
    ...it,
    id: it.id || it.productId || idx,
    productId: it.productId || it.id,
    name,
    productName: name,
    unitName: it.unitName || '',
    quantity,
    qty: quantity,
    salePrice: unitPrice,
    unitPrice,
    pricePoisha: it.pricePoisha || (it.unitPricePoisha ?? takaToPoisha(unitPrice)),
    unitPricePoisha: it.unitPricePoisha || (it.pricePoisha ?? takaToPoisha(unitPrice)),
    subtotalPoisha: it.subtotalPoisha || (it.lineTotalPoisha ?? takaToPoisha(lineTotal)),
    lineTotalPoisha: it.lineTotalPoisha || (it.subtotalPoisha ?? takaToPoisha(lineTotal)),
    lineTotal,
    subtotal: lineTotal,
  };
}

export function normalizeSales(data) {
  const list = toList(data, (item, key) => ({
    id: String(item.id || key),
    firebaseKey: key,
    invoiceNumber: item.invoiceNumber || item.invoiceNo || key,
    customerId: item.customerId ? String(item.customerId) : '',
    customerName: item.customerName || 'নগদ ক্রেতা',
    customerPhone: item.customerPhone || '',
    subtotal: poishaToTaka(item.subtotalPoisha || item.totalPoisha || 0),
    discount: poishaToTaka(item.discountPoisha || 0),
    vat: poishaToTaka(item.vatPoisha || 0),
    total: poishaToTaka(item.totalPoisha || 0),
    paidAmount: poishaToTaka(item.paidPoisha || item.paidAmountPoisha || item.totalPoisha || 0),
    dueAmount: poishaToTaka(item.duePoisha || item.dueAmountPoisha || 0),
    paymentType: item.paymentType || item.paymentMethod || 'নগদ',
    createdAt: item.createdAt || item.saleDate || Date.now(),
    staffName: item.staffName || 'মালিক',
    isReturned: Boolean(item.isReturned),
    items: asArray(item.items).map(normalizeSaleItem),
  }));
  return list.sort((a, b) => b.createdAt - a.createdAt);
}

export function normalizeCustomers(data) {
  return toList(data, (item, key) => ({
    id: String(item.id || key),
    firebaseKey: key,
    name: item.name || '',
    phone: item.phone || '',
    address: item.address || '',
    totalDue: poishaToTaka(item.totalDuePoisha || item.duePoisha || 0),
    creditLimit: poishaToTaka(item.creditLimitPoisha || 500000),
    isActive: isActive(item.isActive),
    createdAt: item.createdAt || Date.now(),
  }));
}

export function normalizeExpenses(data) {
  const list = toList(data, (item, key) => ({
    id: String(item.id || key),
    firebaseKey: key,
    title: item.title || '',
    amount: poishaToTaka(item.amountPoisha || 0),
    category: item.category || 'অন্যান্য',
    expenseDate: item.expenseDate || Date.now(),
    note: item.note || '',
  }));
  return list.sort((a, b) => b.expenseDate - a.expenseDate);
}

export function normalizeStaff(data) {
  return toList(data, (item, key) => ({
    id: key,
    firebaseKey: key,
    name: item.name || '',
    email: item.email || '',
    // Legacy records still carry a plaintext pin; migrated ones only have hasPin.
    hasPin: Boolean(item.hasPin || item.pin),
    phone: item.phone || '',
    role: item.role || 'staff',
    isActive: isActive(item.isActive),
    createdAt: item.createdAt || Date.now(),
  }));
}

export function normalizeSuppliers(data) {
  return toList(data, (item, key) => ({
    id: String(item.id || key),
    firebaseKey: key,
    name: item.name || '',
    company: item.company || '',
    phone: item.phone || '',
    address: item.address || '',
    totalDue: poishaToTaka(item.totalDuePoisha || item.duePoisha || item.totalDue || 0),
    isActive: isActive(item.isActive),
    createdAt: item.createdAt || Date.now(),
  }));
}

export function normalizePurchases(data) {
  const list = toList(data, (item, key) => ({
    id: String(item.id || key),
    firebaseKey: key,
    invoiceNo: item.invoiceNo || item.invoiceNumber || key,
    supplierId: String(item.supplierId || ''),
    supplierName: item.supplierName || 'সাধারণ মহাজন',
    purchaseDate: item.purchaseDate || item.date || item.createdAt || Date.now(),
    totalAmount: poishaToTaka(item.totalAmountPoisha || item.totalPoisha || item.total || 0),
    paidAmount: poishaToTaka(item.paidAmountPoisha || item.paidPoisha || item.paid || 0),
    dueAmount: poishaToTaka(item.dueAmountPoisha || item.duePoisha || item.due || 0),
    paymentMethod: item.paymentMethod || 'নগদ',
    notes: item.notes || item.note || '',
    items: asArray(item.items).map((it, idx) => ({
      id: it.id || idx,
      productId: it.productId ? String(it.productId) : '',
      productName: it.productName || it.name || '',
      unitName: it.unitName || it.unit || 'কেজি',
      purchasePrice: poishaToTaka(it.purchasePricePoisha || takaToPoisha(it.purchasePrice || it.price || 0)),
      quantity: Number(it.quantity || it.qty || 1),
      lineTotal: poishaToTaka(
        it.lineTotalPoisha ||
          takaToPoisha(it.lineTotal || Number(it.purchasePrice || 0) * Number(it.quantity || it.qty || 1))
      ),
    })),
  }));
  return list.sort((a, b) => (b.purchaseDate || 0) - (a.purchaseDate || 0));
}

export function normalizeShopStatus(val) {
  if (!val) {
    return { isSuspended: false, status: 'active', reason: '', licenseType: 'lifetime', plan: 'lifetime', expiresAt: null };
  }
  return {
    isSuspended: Boolean(val.isSuspended || val.status === 'suspended' || val.status === 'banned'),
    status: val.status || 'active',
    reason: val.reason || '',
    licenseType: val.licenseType || (val.plan === 'trial' ? 'trial' : 'lifetime'),
    plan: val.plan || (val.licenseType === 'trial' ? 'trial' : 'lifetime'),
    expiresAt: val.expiresAt || null,
  };
}
