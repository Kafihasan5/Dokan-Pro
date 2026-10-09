import React from 'react';
import { BookOpen, LayoutDashboard, Menu, Package, ShoppingCart } from 'lucide-react';

const TABS = [
  { id: 'dashboard', label: 'হোম', icon: LayoutDashboard },
  { id: 'pos', label: 'বিক্রি', icon: ShoppingCart },
  { id: 'products', label: 'পণ্য', icon: Package },
  { id: 'due', label: 'বাকি', icon: BookOpen },
];

/** App-style bottom navigation on phones; "আরও" opens the full menu drawer. */
export default function MobileTabBar({ currentPage, onNavigate, onOpenMenu }) {
  const inTabs = TABS.some((t) => t.id === currentPage);
  return (
    <nav className="mobile-tabbar no-print" aria-label="প্রধান ট্যাব">
      {TABS.map(({ id, label, icon: Icon }) => (
        <button
          key={id}
          type="button"
          className={`mobile-tab ${currentPage === id ? 'active' : ''}`}
          aria-current={currentPage === id ? 'page' : undefined}
          onClick={() => {
            onNavigate(id);
            window.scrollTo({ top: 0 });
          }}
        >
          <span className="mobile-tab-icon"><Icon size={21} strokeWidth={currentPage === id ? 2.4 : 2} /></span>
          <span className="mobile-tab-label">{label}</span>
        </button>
      ))}
      <button type="button" className={`mobile-tab ${inTabs ? '' : 'active'}`} onClick={onOpenMenu}>
        <span className="mobile-tab-icon"><Menu size={21} /></span>
        <span className="mobile-tab-label">আরও</span>
      </button>
    </nav>
  );
}
