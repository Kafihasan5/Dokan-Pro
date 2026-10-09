import React from 'react';
import { useShop } from '../context/ShopContext';
import {
  LayoutDashboard,
  ShoppingCart,
  Package,
  Receipt,
  BookOpen,
  TrendingDown,
  Settings,
  Users,
  Store,
  ChevronsLeft,
  ChevronsRight,
  Truck,
  BarChart3,
  Database,
  Lock, Headset } from 'lucide-react';

export default function Sidebar({ currentPage, setCurrentPage, isCollapsed, setIsCollapsed, isMobileOpen, onCloseMobile }) {
  const { auth, shopFeatures } = useShop();
  const isOwner = auth?.role === 'owner';

  const sections = [
    {
      label: 'প্রধান',
      items: [
        { id: 'dashboard', label: 'ড্যাশবোর্ড', icon: LayoutDashboard },
        { id: 'pos', label: 'পিওএস কাউন্টার', icon: ShoppingCart, isLocked: shopFeatures?.pos === false },
        { id: 'support', label: 'লাইভ সাপোর্ট', icon: Headset },
      ],
    },
    {
      label: 'ব্যবসা',
      items: [
        { id: 'products', label: 'পণ্য ও ইনভেন্টরি', icon: Package, isLocked: shopFeatures?.inventoryEdit === false },
        { id: 'sales', label: 'বিক্রয় খাতা', icon: Receipt, isLocked: shopFeatures?.reports === false },
        { id: 'purchases', label: 'ক্রয় ও মহাজন', icon: Truck },
        { id: 'due', label: 'বাকির খাতা', icon: BookOpen, isLocked: shopFeatures?.dueKhata === false },
      ],
    },
    ...(isOwner
      ? [
          {
            label: 'ব্যবস্থাপনা',
            items: [
              { id: 'reports', label: 'লাভ-ক্ষতি ও রিপোর্ট', icon: BarChart3, isLocked: shopFeatures?.reports === false },
              { id: 'expenses', label: 'খরচের খাতা', icon: TrendingDown, isLocked: shopFeatures?.expenses === false },
              { id: 'staff', label: 'কর্মচারী', icon: Users, isLocked: shopFeatures?.staffManagement === false },
              { id: 'backup', label: 'ডাটা ও ব্যাকআপ', icon: Database },
              { id: 'settings', label: 'সেটিংস ও নিরাপত্তা', icon: Settings },
            ],
          },
        ]
      : []),
  ];

  const go = (id) => {
    setCurrentPage(id);
    onCloseMobile?.();
  };

  return (
    <>
      {isMobileOpen && <div className="sidebar-backdrop no-print" onClick={onCloseMobile} />}
      <aside
        className={`sidebar no-print ${isCollapsed ? 'collapsed' : ''} ${isMobileOpen ? 'mobile-open' : ''}`}
        aria-label="প্রধান মেনু"
      >
        <div className="sidebar-header" style={{ justifyContent: isCollapsed ? 'center' : 'flex-start' }}>
          <div className="sidebar-logo-icon">
            <Store size={20} />
          </div>
          {!isCollapsed && (
            <div style={{ overflow: 'hidden' }}>
              <div className="sidebar-brand-name">Dokan Pro</div>
              <div className="sidebar-brand-sub">Business Suite</div>
            </div>
          )}
          {!isCollapsed && (
            <button
              onClick={() => setIsCollapsed(true)}
              className="sidebar-collapse-btn"
              title="সাইডবার ছোট করুন"
              aria-label="সাইডবার ছোট করুন"
            >
              <ChevronsLeft size={15} />
            </button>
          )}
        </div>

        <nav className="sidebar-menu">
          {sections.map((section) => (
            <React.Fragment key={section.label}>
              <div className="nav-section-label">{section.label}</div>
              {section.items.map((item) => {
                const IconComp = item.icon;
                const isActive = currentPage === item.id;
                return (
                  <button
                    key={item.id}
                    onClick={() => go(item.id)}
                    className={`nav-item ${isActive ? 'active' : ''} ${isCollapsed ? 'icon-only' : ''}`}
                    title={isCollapsed ? item.label : undefined}
                    aria-current={isActive ? 'page' : undefined}
                    style={item.isLocked ? { opacity: 0.6 } : undefined}
                  >
                    <IconComp size={19} strokeWidth={isActive ? 2.2 : 1.9} />
                    {!isCollapsed && <span>{item.label}</span>}
                    {!isCollapsed && item.isLocked && <Lock size={13} className="nav-lock" />}
                  </button>
                );
              })}
            </React.Fragment>
          ))}
        </nav>

        <div className="sidebar-footer">
          {isCollapsed ? (
            <button
              onClick={() => setIsCollapsed(false)}
              className="sidebar-collapse-btn"
              style={{ margin: '0 auto' }}
              title="সাইডবার বড় করুন"
              aria-label="সাইডবার বড় করুন"
            >
              <ChevronsRight size={15} />
            </button>
          ) : (
            <div className="sidebar-user">
              <div className="avatar">{(auth?.userName || 'U').slice(0, 1)}</div>
              <div style={{ minWidth: 0, lineHeight: 1.25 }}>
                <div style={{ fontSize: '0.85rem', fontWeight: 600, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                  {auth?.userName || 'ব্যবহারকারী'}
                </div>
                <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>
                  {isOwner ? 'মালিক • সম্পূর্ণ অ্যাক্সেস' : 'কর্মচারী'}
                </div>
              </div>
            </div>
          )}
        </div>
      </aside>
    </>
  );
}
