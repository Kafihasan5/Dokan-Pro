import React, { useMemo } from 'react';
import { useShop } from '../context/ShopContext';
import {
  AlertTriangle, ArrowDownRight, ArrowRight, ArrowUpRight, Banknote,
  Boxes, CalendarDays, ChartNoAxesCombined, CircleDollarSign, ClipboardList,
  CreditCard, Package, Plus, Receipt, ShoppingBag, ShoppingCart, Truck, Users,
} from 'lucide-react';
import { formatCurrency, formatDateTime } from '../utils/formatters';

const dayStart = (date) => new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();

export default function Dashboard({ onNavigate, onSelectSale }) {
  const { auth, products, sales, customers, expenses, suppliers } = useShop();
  const isOwner = auth?.role === 'owner';
  const now = new Date();
  const startToday = dayStart(now);
  const startTomorrow = startToday + 86400000;
  const todaySales = sales.filter((sale) => sale.createdAt >= startToday && sale.createdAt < startTomorrow && !sale.isReturned);
  const todayTotal = todaySales.reduce((sum, sale) => sum + Number(sale.total || 0), 0);
  const todayExpenses = expenses.filter((expense) => expense.expenseDate >= startToday && expense.expenseDate < startTomorrow);
  const expenseTotal = todayExpenses.reduce((sum, expense) => sum + Number(expense.amount || 0), 0);
  const customerDue = customers.reduce((sum, customer) => sum + Number(customer.totalDue || 0), 0);
  const supplierDue = suppliers.reduce((sum, supplier) => sum + Number(supplier.totalDue || 0), 0);
  const activeProducts = products.filter((product) => product.isActive);
  const stockSaleValue = activeProducts.reduce((sum, product) => sum + Number(product.stockQty || 0) * Number(product.salePrice || 0), 0);
  const stockPurchaseValue = activeProducts.reduce((sum, product) => sum + Number(product.stockQty || 0) * Number(product.purchasePrice || 0), 0);
  const stockEstimatedProfit = stockSaleValue - stockPurchaseValue;
  const dueCustomers = customers.filter((customer) => Number(customer.totalDue || 0) > 0).length;
  const lowStock = products.filter((product) => product.isActive && product.stockQty <= product.minStock);
  const totalProfit = useMemo(() => todaySales.reduce((sum, sale) => {
    const cost = (sale.items || []).reduce((itemSum, item) => {
      const product = products.find((p) => String(p.id) === String(item.productId));
      return itemSum + Number(item.purchasePrice ?? product?.purchasePrice ?? 0) * Number(item.quantity || 0);
    }, 0);
    return sum + Number(sale.subtotal ?? sale.total ?? 0) - cost - Number(sale.discount || 0);
  }, 0) - expenseTotal, [todaySales, products, expenseTotal]);

  const chart = useMemo(() => Array.from({ length: 7 }, (_, index) => {
    const date = new Date(startToday - (6 - index) * 86400000);
    const start = dayStart(date);
    const amount = sales.filter((sale) => sale.createdAt >= start && sale.createdAt < start + 86400000 && !sale.isReturned)
      .reduce((sum, sale) => sum + Number(sale.total || 0), 0);
    return { date, amount };
  }), [sales, startToday]);
  const maxChart = Math.max(...chart.map((item) => item.amount), 1);
  const points = chart.map((item, index) => `${36 + index * 112},${145 - (item.amount / maxChart) * 112}`).join(' ');
  const chartArea = `36,145 ${points} 708,145`;

  const paymentGroups = [
    { key: 'নগদ', color: '#10b981', amount: todaySales.filter((sale) => !/বাকি|বিকাশ|রকেট|উপায়|upay|mfs/i.test(sale.paymentType || '')).reduce((sum, s) => sum + Number(s.paidAmount ?? s.total ?? 0), 0) },
    { key: 'বিকাশ / MFS', color: '#3b82f6', amount: todaySales.filter((sale) => /বিকাশ|রকেট|উপায়|upay|mfs/i.test(sale.paymentType || '')).reduce((sum, s) => sum + Number(s.paidAmount ?? s.total ?? 0), 0) },
    { key: 'বাকি', color: '#f59e0b', amount: todaySales.reduce((sum, s) => sum + Number(s.dueAmount || (s.paymentType === 'বাকি' ? s.total : 0)), 0) },
  ];
  const paymentSum = paymentGroups.reduce((sum, group) => sum + group.amount, 0) || 1;
  let pieOffset = 0;
  const pieGradient = paymentGroups.map((group) => {
    const start = pieOffset;
    pieOffset += (group.amount / paymentSum) * 100;
    return `${group.color} ${start}% ${pieOffset}%`;
  }).join(', ');

  const Stat = ({ icon: Icon, tone, title, value, note, trend }) => (
    <article className="desk-stat-card">
      <div className={`desk-stat-icon ${tone}`}><Icon size={22} /></div>
      <div className="desk-stat-copy">
        <span className="desk-stat-title">{title}</span>
        <strong className="desk-stat-value">{value}</strong>
        <span className={`desk-stat-note ${trend === 'down' ? 'negative' : 'positive'}`}>
          {trend === 'down' ? <ArrowDownRight size={13} /> : <ArrowUpRight size={13} />}{note}
        </span>
      </div>
    </article>
  );

  return (
    <div className="page-wrapper desk-dashboard">
      <header className="desk-welcome">
        <div>
          <h1>স্বাগতম, {auth?.userName || 'দোকানদার'} <span aria-hidden="true">👋</span></h1>
          <p>আজ আপনার দোকানের সারাংশ দেখে নিন</p>
        </div>
        <div className="desk-date-chip"><CalendarDays size={17} />{new Intl.DateTimeFormat('bn-BD', { dateStyle: 'long' }).format(now)}</div>
      </header>

      <section className="desk-quick-actions card">
        <h2>দ্রুত অর্ডার</h2>
        <button className="quick-action blue" onClick={() => onNavigate('products')}><Plus size={17} />নতুন পণ্য</button>
        <button className="quick-action green" onClick={() => onNavigate('pos')}><ShoppingCart size={17} />নতুন বিক্রয়</button>
        {isOwner ? <button className="quick-action purple" onClick={() => onNavigate('expenses')}><Banknote size={17} />খরচ যোগ</button> : <button className="quick-action purple" onClick={() => onNavigate('reports')}><ChartNoAxesCombined size={17} />রিপোর্ট</button>}
        <button className="quick-action amber" onClick={() => onNavigate('due')}><ClipboardList size={17} />বাকি খাতা</button>
      </section>

      <section className="desk-stats-grid" aria-label="আজকের হিসাব ও স্টক সারাংশ">
        <Stat icon={ShoppingBag} tone="green" title="আজকের মোট বিক্রয়" value={formatCurrency(todayTotal)} note={`${todaySales.length} টি বিক্রয়`} />
        {isOwner && <Stat icon={CircleDollarSign} tone="blue" title="আজকের মোট লাভ" value={formatCurrency(totalProfit)} note="খরচ বাদে লাভ" />}
        <Stat icon={Receipt} tone="purple" title="আজকের বিক্রয়" value={`${todaySales.length} টি`} note="সম্পন্ন বিক্রয়" />
        {isOwner && <Stat icon={Banknote} tone="orange" title="আজকের খরচ" value={formatCurrency(expenseTotal)} note={`${todayExpenses.length} টি এন্ট্রি`} />}
        <Stat icon={AlertTriangle} tone="orange" title="কম স্টক পণ্য" value={`${lowStock.length} টি`} note="স্টক দেখে পুনরায় অর্ডার করুন" trend="down" />
        <Stat icon={Package} tone="blue" title="মোট স্টক পণ্য" value={`${activeProducts.length} টি`} note="সক্রিয় পণ্য" />
        {isOwner && <Stat icon={Boxes} tone="green" title="দোকানের মোট স্টক মূল্য" value={formatCurrency(stockSaleValue)} note="বিক্রয়মূল্য অনুযায়ী" />}
        {isOwner && <Stat icon={ChartNoAxesCombined} tone="purple" title="আনুমানিক স্টক লাভ" value={formatCurrency(stockEstimatedProfit)} note="বিক্রয়মূল্য − ক্রয়মূল্য" />}
        {isOwner && <Stat icon={Users} tone="blue" title="কাস্টমারের মোট পাওনা" value={formatCurrency(customerDue)} note={`${dueCustomers} জন কাস্টমারের কাছে`} />}
        {isOwner && <Stat icon={Truck} tone="orange" title="সাপ্লায়ারের মোট দেনা" value={formatCurrency(supplierDue)} note="ক্রয়ের বকেয়া" />}
      </section>

      <section className="desk-chart-grid">
        <article className="card desk-panel">
          <div className="desk-panel-heading"><div><h2>বিক্রয়ের গ্রাফ</h2><p>গত ৭ দিনের বিক্রয়</p></div><button className="desk-select-chip" onClick={() => onNavigate('reports')}>রিপোর্ট দেখুন <ArrowRight size={14} /></button></div>
          <div className="sales-chart-wrap">
            <div className="chart-y-labels"><span>{formatCurrency(maxChart)}</span><span>{formatCurrency(maxChart / 2)}</span><span>৳ ০</span></div>
            <svg className="sales-chart" viewBox="0 0 744 176" role="img" aria-label="গত সাত দিনের বিক্রয় চার্ট" preserveAspectRatio="none">
              <defs><linearGradient id="salesFill" x1="0" x2="0" y1="0" y2="1"><stop offset="0%" stopColor="#10b981" stopOpacity=".28"/><stop offset="100%" stopColor="#10b981" stopOpacity="0"/></linearGradient></defs>
              {[33, 89, 145].map((y) => <line key={y} x1="36" x2="708" y1={y} y2={y} className="chart-grid-line" />)}
              <polygon points={chartArea} fill="url(#salesFill)" />
              <polyline points={points} fill="none" stroke="#10b981" strokeWidth="3.5" strokeLinecap="round" strokeLinejoin="round" />
              {chart.map((item, index) => <circle key={item.date.toISOString()} cx={36 + index * 112} cy={145 - (item.amount / maxChart) * 112} r="4.5" className="chart-dot" />)}
            </svg>
          </div>
          <div className="chart-x-labels">{chart.map((item) => <span key={item.date.toISOString()}>{new Intl.DateTimeFormat('en-GB', { day: '2-digit', month: 'short' }).format(item.date)}</span>)}</div>
        </article>

        <article className="card desk-panel payment-panel">
          <div className="desk-panel-heading"><div><h2>পেমেন্টের ধরন</h2><p>আজকের বিক্রয়ের ভিত্তিতে</p></div><CreditCard size={19} className="panel-heading-icon" /></div>
          <div className="payment-content">
            <div className="payment-donut" style={{ background: `conic-gradient(${pieGradient})` }}><div><strong>{formatCurrency(todayTotal)}</strong><span>মোট বিক্রয়</span></div></div>
            <div className="payment-legend">{paymentGroups.map((group) => <div className="payment-legend-row" key={group.key}><i style={{ background: group.color }} /><span>{group.key}</span><strong>{Math.round((group.amount / paymentSum) * 100)}%</strong></div>)}</div>
          </div>
        </article>
      </section>

      <section className="desk-lower-grid">
        <article className="card desk-panel recent-sales-panel">
          <div className="desk-panel-heading"><div><h2>সাম্প্রতিক বিক্রয়</h2><p>সর্বশেষ লেনদেনগুলো</p></div><button className="desk-link-button" onClick={() => onNavigate('sales')}>সব বিক্রয় <ArrowRight size={14} /></button></div>
          {sales.length === 0 ? <div className="desk-empty"><Receipt size={26} /><span>এখনো কোনো বিক্রয় রেকর্ড হয়নি</span></div> : (
            <div className="desk-table-scroll"><table className="desk-table"><thead><tr><th>ইনভয়েস</th><th>কাস্টমার</th><th>পণ্য</th><th>তারিখ</th><th>পেমেন্ট</th><th className="align-right">মোট</th></tr></thead><tbody>
              {sales.slice(0, 6).map((sale) => <tr key={sale.id} onClick={() => onSelectSale(sale)} className="desk-sale-row" title="রসিদ দেখুন"><td className="invoice-cell">{sale.invoiceNumber}</td><td>{sale.customerName || 'সাধারণ ক্রেতা'}</td><td>{sale.items?.[0]?.nameBn || sale.items?.[0]?.productName || 'পণ্য'}{sale.items?.length > 1 ? ` +${sale.items.length - 1}` : ''}</td><td>{formatDateTime(sale.createdAt)}</td><td><span className={`payment-badge ${sale.paymentType === 'বাকি' ? 'due' : 'paid'}`}>{sale.paymentType || 'নগদ'}</span></td><td className="align-right amount-cell">{formatCurrency(sale.total)}</td></tr>)}
            </tbody></table></div>
          )}
        </article>

        <article className="card desk-panel low-stock-panel">
          <div className="desk-panel-heading"><div><h2>কম স্টক পণ্য</h2><p>রি-অর্ডার লেভেলের নিচে</p></div><button className="desk-link-button" onClick={() => onNavigate('products')}>স্টক দেখুন <ArrowRight size={14} /></button></div>
          {lowStock.length === 0 ? <div className="desk-empty"><Package size={25} /><span>সব পণ্যের স্টক পর্যাপ্ত আছে</span></div> : <div className="low-stock-list">{lowStock.slice(0, 5).map((product) => <div className="low-stock-row" key={product.id}><div className="low-stock-product-icon"><Package size={18} /></div><div className="low-stock-product-name"><strong>{product.nameBn || product.nameEn}</strong><span>{product.nameEn || product.unitName}</span></div><span className="stock-count">{product.stockQty} {product.unitName}</span></div>)}</div>}
        </article>
      </section>

    </div>
  );
}
