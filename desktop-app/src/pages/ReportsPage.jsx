import React, { useState, useMemo } from 'react';
import { useShop } from '../context/ShopContext';
import {
  BarChart3,
  TrendingUp,
  TrendingDown,
  DollarSign,
  Calendar,
  Download,
  Printer,
  ShoppingBag,
  CreditCard,
  PieChart,
  ArrowUpRight,
  ArrowDownRight,
  Percent,
  Receipt,
  FileSpreadsheet,
} from 'lucide-react';
import { formatCurrency, formatDate } from '../utils/formatters';
import { downloadCsv } from '../utils/security';

export default function ReportsPage() {
  const { sales, expenses, products, shopInfo } = useShop();

  // Date Range Filter
  const [dateFilter, setDateFilter] = useState('month'); // 'today' | 'yesterday' | 'week' | 'month' | 'last30' | 'all' | 'custom'
  const [customStartDate, setCustomStartDate] = useState('');
  const [customEndDate, setCustomEndDate] = useState('');
  const [activeTab, setActiveTab] = useState('pnl'); // 'pnl' | 'products' | 'payments' | 'expenses'
  const [productSortBy, setProductSortBy] = useState('profit'); // 'profit' | 'revenue' | 'qty'

  // Date Filtering Logic
  const filteredData = useMemo(() => {
    const now = new Date();
    let startTimestamp = 0;
    let endTimestamp = Date.now();

    if (dateFilter === 'today') {
      const startOfDay = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
      startTimestamp = startOfDay;
    } else if (dateFilter === 'yesterday') {
      const yesterday = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1);
      startTimestamp = yesterday.getTime();
      endTimestamp = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime() - 1;
    } else if (dateFilter === 'week') {
      const lastWeek = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 7);
      startTimestamp = lastWeek.getTime();
    } else if (dateFilter === 'month') {
      const startOfMonth = new Date(now.getFullYear(), now.getMonth(), 1).getTime();
      startTimestamp = startOfMonth;
    } else if (dateFilter === 'last30') {
      const last30 = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 30);
      startTimestamp = last30.getTime();
    } else if (dateFilter === 'custom' && customStartDate) {
      startTimestamp = new Date(customStartDate).getTime();
      if (customEndDate) {
        endTimestamp = new Date(customEndDate).setHours(23, 59, 59, 999);
      }
    } else if (dateFilter === 'all') {
      startTimestamp = 0;
    }

    const filteredSales = sales.filter((s) => {
      const t = Number(s.createdAt || s.date || 0);
      return t >= startTimestamp && t <= endTimestamp;
    });

    const filteredExpenses = expenses.filter((e) => {
      const t = Number(e.expenseDate || e.createdAt || 0);
      return t >= startTimestamp && t <= endTimestamp;
    });

    return { filteredSales, filteredExpenses, startTimestamp, endTimestamp };
  }, [sales, expenses, dateFilter, customStartDate, customEndDate]);

  const { filteredSales, filteredExpenses } = filteredData;

  // Key Financial Metrics Calculation
  const metrics = useMemo(() => {
    let totalRevenue = 0;
    let totalCOGS = 0; // Cost of Goods Sold
    let totalDiscount = 0;
    let totalVat = 0;
    let totalDue = 0;
    let totalPaid = 0;

    // Payment methods map
    const paymentBreakdown = {
      'নগদ': 0,
      'বিকাশ': 0,
      'নগদ/এমএফএস': 0,
      'কার্ড': 0,
      'অন্যান্য': 0,
    };

    // Product performance map
    const productStats = {};

    filteredSales.forEach((sale) => {
      const rev = Number(sale.total) || 0;
      totalRevenue += rev;
      totalDiscount += Number(sale.discount) || 0;
      totalVat += Number(sale.vat) || 0;
      totalDue += Number(sale.dueAmount) || 0;
      totalPaid += Number(sale.paidAmount) || 0;

      // Payment Breakdown
      const method = sale.paymentType || 'নগদ';
      if (paymentBreakdown[method] !== undefined) {
        paymentBreakdown[method] += Number(sale.paidAmount) || rev;
      } else {
        paymentBreakdown['নগদ'] = (paymentBreakdown['নগদ'] || 0) + (Number(sale.paidAmount) || rev);
      }

      // Items COGS and performance
      if (Array.isArray(sale.items)) {
        sale.items.forEach((item) => {
          const qty = Number(item.quantity || item.qty || 1);
          const pId = String(item.productId || item.id);
          const matchedProd = products.find((p) => String(p.id) === pId);

          const buyPrice = Number(item.buyPrice || (matchedProd?.buyPrice) || 0);
          const salePrice = Number(item.salePrice || item.unitPrice || 0);
          const lineRevenue = salePrice * qty;
          const lineCost = buyPrice * qty;
          const lineProfit = lineRevenue - lineCost;

          totalCOGS += lineCost;

          if (!productStats[pId]) {
            productStats[pId] = {
              id: pId,
              name: item.nameBn || item.productName || item.name || matchedProd?.nameBn || 'অজ্ঞাত পণ্য',
              qty: 0,
              revenue: 0,
              cost: 0,
              profit: 0,
            };
          }

          productStats[pId].qty += qty;
          productStats[pId].revenue += lineRevenue;
          productStats[pId].cost += lineCost;
          productStats[pId].profit += lineProfit;
        });
      }
    });

    const totalExpense = filteredExpenses.reduce((sum, e) => sum + (Number(e.amount) || 0), 0);
    const grossProfit = totalRevenue - totalCOGS;
    const netProfit = grossProfit - totalExpense;
    const grossMarginPct = totalRevenue > 0 ? (grossProfit / totalRevenue) * 100 : 0;
    const netMarginPct = totalRevenue > 0 ? (netProfit / totalRevenue) * 100 : 0;
    const avgOrderValue = filteredSales.length > 0 ? totalRevenue / filteredSales.length : 0;

    // Expense breakdown by category
    const expenseCategories = {};
    filteredExpenses.forEach((e) => {
      const cat = e.category || 'অন্যান্য';
      expenseCategories[cat] = (expenseCategories[cat] || 0) + (Number(e.amount) || 0);
    });

    return {
      totalRevenue,
      totalCOGS,
      grossProfit,
      totalExpense,
      netProfit,
      grossMarginPct,
      netMarginPct,
      totalDiscount,
      totalVat,
      totalDue,
      totalPaid,
      avgOrderValue,
      invoiceCount: filteredSales.length,
      productStats: Object.values(productStats),
      paymentBreakdown,
      expenseCategories,
    };
  }, [filteredSales, filteredExpenses, products]);

  // Sorted Products
  const sortedProductStats = useMemo(() => {
    return [...metrics.productStats].sort((a, b) => {
      if (productSortBy === 'profit') return b.profit - a.profit;
      if (productSortBy === 'revenue') return b.revenue - a.revenue;
      return b.qty - a.qty;
    });
  }, [metrics.productStats, productSortBy]);

  // CSV Export Handler
  const handleExportCSV = () => {
    const rows = [
      ['Dokan Pro - Profit & Loss Financial Report'],
      ['Shop Name', shopInfo?.shopName || 'Dokan'],
      ['Filter Period', dateFilter],
      ['Generated At', new Date().toLocaleString()],
      [''],
      ['Financial Metric', 'Amount (BDT)'],
      ['Total Sales (Gross Revenue)', metrics.totalRevenue.toFixed(2)],
      ['Cost of Goods Sold (COGS)', metrics.totalCOGS.toFixed(2)],
      ['Gross Profit', metrics.grossProfit.toFixed(2)],
      ['Gross Profit Margin (%)', `${metrics.grossMarginPct.toFixed(2)}%`],
      ['Total Shop Operating Expenses', metrics.totalExpense.toFixed(2)],
      ['Net Operating Profit / Loss', metrics.netProfit.toFixed(2)],
      ['Net Profit Margin (%)', `${metrics.netMarginPct.toFixed(2)}%`],
      ['Total Due (Baki)', metrics.totalDue.toFixed(2)],
      ['Total Paid (Cash/MFS)', metrics.totalPaid.toFixed(2)],
      ['Total Invoices Count', metrics.invoiceCount],
      ['Average Order Value', metrics.avgOrderValue.toFixed(2)],
      [''],
      ['Product Performance Breakdown'],
      ['Product Name', 'Sold Qty', 'Total Revenue (BDT)', 'Total Cost (BDT)', 'Net Profit (BDT)', 'Margin (%)'],
      ...sortedProductStats.map((p) => [
        p.name,
        p.qty,
        p.revenue.toFixed(2),
        p.cost.toFixed(2),
        p.profit.toFixed(2),
        p.revenue > 0 ? `${((p.profit / p.revenue) * 100).toFixed(1)}%` : '0%',
      ]),
    ];

    downloadCsv(rows, `Dokan_Report_${dateFilter}_${Date.now()}.csv`);
  };

  return (
    <div className="page-wrapper">
      {/* Top Header */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '24px', flexWrap: 'wrap', gap: '16px' }}>
        <div>
          <h1 style={{ fontSize: '1.65rem', fontWeight: 800, color: 'var(--text-primary)', display: 'flex', alignItems: 'center', gap: '10px' }}>
            <BarChart3 size={28} color="var(--primary)" />
            <span>লাভ-ক্ষতি ও পূর্ণাঙ্গ রিপোর্ট (Profit & Analytics)</span>
          </h1>
          <p style={{ fontSize: '0.9rem', color: 'var(--text-secondary)' }}>
            বিক্রি, ক্রয় খরচ, দোকান পরিচালন ব্যয় এবং নিট ব্যবসায়িক লাভ-ক্ষতির বিশ্লেষণ
          </p>
        </div>

        <div style={{ display: 'flex', gap: '10px' }}>
          <button onClick={handleExportCSV} className="btn btn-secondary">
            <Download size={16} />
            <span>এক্সেল / CSV এক্সপোর্ট</span>
          </button>
          <button onClick={() => window.print()} className="btn btn-primary">
            <Printer size={16} />
            <span>রিপোর্ট প্রিন্ট করুন</span>
          </button>
        </div>
      </div>

      {/* Date Range Selector Pill Bar */}
      <div className="card" style={{ padding: '16px 20px', marginBottom: '24px' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '12px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
            <Calendar size={18} color="var(--text-muted)" />
            <span style={{ fontSize: '0.85rem', fontWeight: 600, color: 'var(--text-secondary)', marginRight: '6px' }}>
              সময়কাল:
            </span>
            {[
              { id: 'today', label: 'আজ' },
              { id: 'yesterday', label: 'গতকাল' },
              { id: 'week', label: 'বিগত ৭ দিন' },
              { id: 'month', label: 'চলতি মাস' },
              { id: 'last30', label: 'বিগত ৩০ দিন' },
              { id: 'all', label: 'সব সময়' },
              { id: 'custom', label: 'কাস্টম রেঞ্জ' },
            ].map((f) => (
              <button
                key={f.id}
                onClick={() => setDateFilter(f.id)}
                className={`btn btn-sm ${dateFilter === f.id ? 'btn-primary' : 'btn-ghost'}`}
                style={{ padding: '6px 14px', fontSize: '0.82rem', borderRadius: 'var(--radius-pill)' }}
              >
                {f.label}
              </button>
            ))}
          </div>

          {dateFilter === 'custom' && (
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <input
                type="date"
                className="input-field"
                style={{ height: '34px', fontSize: '0.82rem', padding: '4px 8px' }}
                value={customStartDate}
                onChange={(e) => setCustomStartDate(e.target.value)}
              />
              <span style={{ color: 'var(--text-muted)' }}>থেকে</span>
              <input
                type="date"
                className="input-field"
                style={{ height: '34px', fontSize: '0.82rem', padding: '4px 8px' }}
                value={customEndDate}
                onChange={(e) => setCustomEndDate(e.target.value)}
              />
            </div>
          )}
        </div>
      </div>

      {/* CORE FINANCIAL KPIS */}
      <div className="stats-grid" style={{ marginBottom: '24px' }}>
        {/* Total Sales */}
        <div className="stat-card">
          <div className="stat-icon" style={{ background: 'rgba(5, 150, 105, 0.15)', color: 'var(--primary)' }}>
            <TrendingUp size={22} />
          </div>
          <div>
            <div className="stat-label">মোট বিক্রয় (Revenue)</div>
            <div className="stat-value">{formatCurrency(metrics.totalRevenue)}</div>
            <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)', marginTop: '4px' }}>
              মোট {metrics.invoiceCount} টি বিক্রয় রশিদ
            </div>
          </div>
        </div>

        {/* COGS */}
        <div className="stat-card">
          <div className="stat-icon" style={{ background: 'rgba(239, 68, 68, 0.12)', color: '#f87171' }}>
            <ShoppingBag size={22} />
          </div>
          <div>
            <div className="stat-label">পণ্যের ক্রয়মূল্য (COGS)</div>
            <div className="stat-value" style={{ color: '#f87171' }}>{formatCurrency(metrics.totalCOGS)}</div>
            <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)', marginTop: '4px' }}>
              বিক্রিত পণ্যের আসল খরচ
            </div>
          </div>
        </div>

        {/* Shop Expenses */}
        <div className="stat-card">
          <div className="stat-icon" style={{ background: 'rgba(245, 158, 11, 0.15)', color: 'var(--warning)' }}>
            <TrendingDown size={22} />
          </div>
          <div>
            <div className="stat-label">দোকান পরিচালনা খরচ</div>
            <div className="stat-value" style={{ color: 'var(--warning)' }}>{formatCurrency(metrics.totalExpense)}</div>
            <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)', marginTop: '4px' }}>
              ভাড়া, বিল, বেতন ও বিবিধ খরচ
            </div>
          </div>
        </div>

        {/* Net Profit */}
        <div className="stat-card" style={{ border: `1px solid ${metrics.netProfit >= 0 ? 'rgba(16, 185, 129, 0.4)' : 'rgba(239, 68, 68, 0.4)'}` }}>
          <div
            className="stat-icon"
            style={{
              background: metrics.netProfit >= 0 ? 'rgba(16, 185, 129, 0.2)' : 'rgba(239, 68, 68, 0.2)',
              color: metrics.netProfit >= 0 ? 'var(--success)' : 'var(--danger)',
            }}
          >
            {metrics.netProfit >= 0 ? <ArrowUpRight size={24} /> : <ArrowDownRight size={24} />}
          </div>
          <div>
            <div className="stat-label">{metrics.netProfit >= 0 ? 'প্রকৃত নিট লাভ (Net Profit)' : 'ব্যবসায়িক নিট ক্ষতি (Loss)'}</div>
            <div
              className="stat-value"
              style={{
                color: metrics.netProfit >= 0 ? 'var(--success)' : 'var(--danger)',
                fontFamily: 'var(--font-mono)',
              }}
            >
              {formatCurrency(metrics.netProfit)}
            </div>
            <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)', marginTop: '4px' }}>
              নিট প্রফিট মার্জিন: <strong>{metrics.netMarginPct.toFixed(1)}%</strong>
            </div>
          </div>
        </div>
      </div>

      {/* Tabs Switcher */}
      <div className="card" style={{ padding: '12px 18px', marginBottom: '20px' }}>
        <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
          <button
            onClick={() => setActiveTab('pnl')}
            className={`btn btn-sm ${activeTab === 'pnl' ? 'btn-primary' : 'btn-secondary'}`}
            style={{ padding: '8px 16px' }}
          >
            <PieChart size={16} />
            <span>লাভ-ক্ষতি বিবরণী (P&L Summary)</span>
          </button>
          <button
            onClick={() => setActiveTab('products')}
            className={`btn btn-sm ${activeTab === 'products' ? 'btn-primary' : 'btn-secondary'}`}
            style={{ padding: '8px 16px' }}
          >
            <ShoppingBag size={16} />
            <span>পণ্যভিত্তিক লাভ ও বিক্রয় ({metrics.productStats.length})</span>
          </button>
          <button
            onClick={() => setActiveTab('payments')}
            className={`btn btn-sm ${activeTab === 'payments' ? 'btn-primary' : 'btn-secondary'}`}
            style={{ padding: '8px 16px' }}
          >
            <CreditCard size={16} />
            <span>পেমেন্ট মাধ্যম বিশ্লেষণ</span>
          </button>
          <button
            onClick={() => setActiveTab('expenses')}
            className={`btn btn-sm ${activeTab === 'expenses' ? 'btn-primary' : 'btn-secondary'}`}
            style={{ padding: '8px 16px' }}
          >
            <TrendingDown size={16} />
            <span>খরচের খাত বিশ্লেষণ</span>
          </button>
        </div>
      </div>

      {/* TAB 1: P&L SUMMARY STATEMENT */}
      {activeTab === 'pnl' && (
        <div className="card" style={{ padding: '24px' }}>
          <h3 style={{ fontSize: '1.2rem', fontWeight: 800, marginBottom: '16px', color: 'var(--text-primary)' }}>
            বিস্তারিত আয়-ব্যয় ও লাভ-ক্ষতির খতিয়ান
          </h3>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            {/* Sales Revenue */}
            <div style={{ display: 'flex', justifyContent: 'space-between', padding: '14px', background: 'var(--bg-surface)', borderRadius: 'var(--radius-md)' }}>
              <div>
                <strong style={{ fontSize: '1rem', color: 'var(--text-primary)' }}>(+) মোট বিক্রয় আয় (Gross Sales)</strong>
                <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>নির্ধারিত সময়ে মোট পণ্য বিক্রয়</div>
              </div>
              <div style={{ fontSize: '1.2rem', fontWeight: 800, color: 'var(--primary)', fontFamily: 'var(--font-mono)' }}>
                {formatCurrency(metrics.totalRevenue)}
              </div>
            </div>

            {/* COGS */}
            <div style={{ display: 'flex', justifyContent: 'space-between', padding: '14px', background: 'var(--bg-surface)', borderRadius: 'var(--radius-md)' }}>
              <div>
                <strong style={{ fontSize: '1rem', color: 'var(--danger)' }}>(-) বিক্রিত পণ্যের ক্রয়মূল্য (COGS)</strong>
                <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>বিক্রি করা পণ্যের মোট আসল ক্রয় খরচ</div>
              </div>
              <div style={{ fontSize: '1.2rem', fontWeight: 800, color: 'var(--danger)', fontFamily: 'var(--font-mono)' }}>
                - {formatCurrency(metrics.totalCOGS)}
              </div>
            </div>

            {/* Gross Profit */}
            <div style={{ display: 'flex', justifyContent: 'space-between', padding: '14px', background: 'rgba(5, 150, 105, 0.1)', border: '1px solid rgba(16, 185, 129, 0.3)', borderRadius: 'var(--radius-md)' }}>
              <div>
                <strong style={{ fontSize: '1.05rem', color: 'var(--success)' }}>(=) মোট স্থূল লাভ (Gross Profit)</strong>
                <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                  গ্রস মার্জিন: <strong>{metrics.grossMarginPct.toFixed(1)}%</strong>
                </div>
              </div>
              <div style={{ fontSize: '1.25rem', fontWeight: 800, color: 'var(--success)', fontFamily: 'var(--font-mono)' }}>
                {formatCurrency(metrics.grossProfit)}
              </div>
            </div>

            {/* Operating Expenses */}
            <div style={{ display: 'flex', justifyContent: 'space-between', padding: '14px', background: 'var(--bg-surface)', borderRadius: 'var(--radius-md)' }}>
              <div>
                <strong style={{ fontSize: '1rem', color: 'var(--warning)' }}>(-) দোকান পরিচালনা ব্যয় (Operating Expenses)</strong>
                <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>দোকান ভাড়া, বিল, কর্মচারীর বেতন, নাস্তা ইত্যাদি</div>
              </div>
              <div style={{ fontSize: '1.2rem', fontWeight: 800, color: 'var(--warning)', fontFamily: 'var(--font-mono)' }}>
                - {formatCurrency(metrics.totalExpense)}
              </div>
            </div>

            {/* NET OPERATING PROFIT */}
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                padding: '18px',
                background: metrics.netProfit >= 0 ? 'rgba(16, 185, 129, 0.18)' : 'rgba(239, 68, 68, 0.18)',
                border: `2px solid ${metrics.netProfit >= 0 ? 'var(--success)' : 'var(--danger)'}`,
                borderRadius: 'var(--radius-lg)',
                marginTop: '6px',
              }}
            >
              <div>
                <strong style={{ fontSize: '1.2rem', color: metrics.netProfit >= 0 ? 'var(--success)' : 'var(--danger)' }}>
                  (=) প্রকৃত নিট ব্যবসায়িক {metrics.netProfit >= 0 ? 'লাভ (Net Profit)' : 'ক্ষতি (Net Loss)'}
                </strong>
                <div style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>
                  নিট প্রফিট মার্জিন: <strong>{metrics.netMarginPct.toFixed(1)}%</strong>
                </div>
              </div>
              <div
                style={{
                  fontSize: '1.6rem',
                  fontWeight: 900,
                  color: metrics.netProfit >= 0 ? 'var(--success)' : 'var(--danger)',
                  fontFamily: 'var(--font-mono)',
                }}
              >
                {formatCurrency(metrics.netProfit)}
              </div>
            </div>

            {/* Additional Cashflow Info */}
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '14px', marginTop: '14px' }}>
              <div style={{ background: 'var(--bg-card)', padding: '14px', borderRadius: 'var(--radius-md)' }}>
                <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>মোট নগদ/এমএফএস আদায়</div>
                <div style={{ fontSize: '1.15rem', fontWeight: 700, color: 'var(--success)', marginTop: '4px' }}>
                  {formatCurrency(metrics.totalPaid)}
                </div>
              </div>
              <div style={{ background: 'var(--bg-card)', padding: '14px', borderRadius: 'var(--radius-md)' }}>
                <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>মোট বকেয়া পাওনা (বাকি বিক্রি)</div>
                <div style={{ fontSize: '1.15rem', fontWeight: 700, color: 'var(--danger)', marginTop: '4px' }}>
                  {formatCurrency(metrics.totalDue)}
                </div>
              </div>
              <div style={{ background: 'var(--bg-card)', padding: '14px', borderRadius: 'var(--radius-md)' }}>
                <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>গড় বিক্রয় রশিদ মূল্য (AOV)</div>
                <div style={{ fontSize: '1.15rem', fontWeight: 700, color: 'var(--text-primary)', marginTop: '4px' }}>
                  {formatCurrency(metrics.avgOrderValue)}
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* TAB 2: PRODUCT-WISE PROFIT & SALES */}
      {activeTab === 'products' && (
        <div className="card" style={{ padding: '20px' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '16px', flexWrap: 'wrap', gap: '12px' }}>
            <h3 style={{ fontSize: '1.15rem', fontWeight: 700 }}>পণ্যভিত্তিক লাভ ও বিক্রয় র‍্যাংকিং</h3>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <span style={{ fontSize: '0.82rem', color: 'var(--text-muted)' }}>সর্ট করুন:</span>
              <select
                className="input-field"
                style={{ height: '32px', fontSize: '0.82rem', padding: '2px 8px' }}
                value={productSortBy}
                onChange={(e) => setProductSortBy(e.target.value)}
              >
                <option value="profit">সর্বোচ্চ লাভ (Profit)</option>
                <option value="revenue">সর্বোচ্চ বিক্রয় (Revenue)</option>
                <option value="qty">সর্বোচ্চ বিক্রিত সংখ্যা (Qty)</option>
              </select>
            </div>
          </div>

          <div className="table-responsive">
            <table className="data-table">
              <thead>
                <tr>
                  <th>পণ্যের নাম</th>
                  <th style={{ textAlign: 'center' }}>বিক্রিত পরিমাণ</th>
                  <th style={{ textAlign: 'right' }}>মোট বিক্রয় (৳)</th>
                  <th style={{ textAlign: 'right' }}>ক্রয় খরচ (৳)</th>
                  <th style={{ textAlign: 'right' }}>অর্জিত লাভ (৳)</th>
                  <th style={{ textAlign: 'right' }}>লাভের হার (%)</th>
                </tr>
              </thead>
              <tbody>
                {sortedProductStats.length === 0 ? (
                  <tr>
                    <td colSpan={6} style={{ textAlign: 'center', padding: '36px', color: 'var(--text-muted)' }}>
                      কোনো বিক্রয় তথ্য পাওয়া যায়নি।
                    </td>
                  </tr>
                ) : (
                  sortedProductStats.map((p, idx) => {
                    const margin = p.revenue > 0 ? (p.profit / p.revenue) * 100 : 0;
                    return (
                      <tr key={idx}>
                        <td style={{ fontWeight: 600, color: 'var(--text-primary)' }}>
                          {p.name}
                        </td>
                        <td style={{ textAlign: 'center', fontWeight: 700 }}>
                          {p.qty}
                        </td>
                        <td style={{ textAlign: 'right', fontWeight: 600 }}>
                          {formatCurrency(p.revenue)}
                        </td>
                        <td style={{ textAlign: 'right', color: 'var(--text-muted)' }}>
                          {formatCurrency(p.cost)}
                        </td>
                        <td style={{ textAlign: 'right', fontWeight: 800, color: p.profit >= 0 ? 'var(--success)' : 'var(--danger)' }}>
                          {formatCurrency(p.profit)}
                        </td>
                        <td style={{ textAlign: 'right', fontWeight: 700, color: margin >= 0 ? 'var(--success)' : 'var(--danger)' }}>
                          {margin.toFixed(1)}%
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* TAB 3: PAYMENT METHOD BREAKDOWN */}
      {activeTab === 'payments' && (
        <div className="card" style={{ padding: '24px' }}>
          <h3 style={{ fontSize: '1.15rem', fontWeight: 700, marginBottom: '16px' }}>পেমেন্ট মাধ্যম বিশ্লেষণ</h3>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '16px' }}>
            {Object.entries(metrics.paymentBreakdown).map(([method, amount]) => {
              const pct = metrics.totalRevenue > 0 ? (amount / metrics.totalRevenue) * 100 : 0;
              return (
                <div key={method} className="card" style={{ padding: '16px', background: 'var(--bg-surface)' }}>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                    <span style={{ fontSize: '0.9rem', fontWeight: 700, color: 'var(--text-primary)' }}>{method}</span>
                    <span className="badge" style={{ background: 'var(--bg-card)' }}>{pct.toFixed(1)}%</span>
                  </div>
                  <div style={{ fontSize: '1.25rem', fontWeight: 800, color: 'var(--primary)', marginTop: '8px', fontFamily: 'var(--font-mono)' }}>
                    {formatCurrency(amount)}
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* TAB 4: EXPENSES BREAKDOWN */}
      {activeTab === 'expenses' && (
        <div className="card" style={{ padding: '24px' }}>
          <h3 style={{ fontSize: '1.15rem', fontWeight: 700, marginBottom: '16px' }}>দোকান খরচের খাতভিত্তিক বিশ্লেষণ</h3>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '16px' }}>
            {Object.entries(metrics.expenseCategories).length === 0 ? (
              <div style={{ padding: '20px', color: 'var(--text-muted)' }}>কোনো খরচের রেকর্ড নেই।</div>
            ) : (
              Object.entries(metrics.expenseCategories).map(([cat, amount]) => {
                const pct = metrics.totalExpense > 0 ? (amount / metrics.totalExpense) * 100 : 0;
                return (
                  <div key={cat} className="card" style={{ padding: '16px', background: 'var(--bg-surface)' }}>
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                      <span style={{ fontSize: '0.9rem', fontWeight: 700, color: 'var(--text-primary)' }}>{cat}</span>
                      <span className="badge" style={{ background: 'rgba(245, 158, 11, 0.15)', color: 'var(--warning)' }}>
                        {pct.toFixed(1)}%
                      </span>
                    </div>
                    <div style={{ fontSize: '1.25rem', fontWeight: 800, color: 'var(--warning)', marginTop: '8px', fontFamily: 'var(--font-mono)' }}>
                      {formatCurrency(amount)}
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>
      )}
    </div>
  );
}
