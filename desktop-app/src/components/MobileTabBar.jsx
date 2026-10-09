import React from 'react';
import { BookOpen, Home, Menu, Package, ScanLine, ShoppingCart } from 'lucide-react';

const TABS = [
  { id: 'dashboard', label: 'হোম', icon: Home },
  { id: 'products', label: 'পণ্য', icon: Package },
  { id: 'pos', label: 'বিক্রয়', icon: ShoppingCart, featured: true },
  { id: 'scan', label: 'স্ক্যান', icon: ScanLine },
  { id: 'due', label: 'বাকি', icon: BookOpen },
];

/**
 * Floating bottom bar on phones, like the mobile app's FloatingNavBar.
 * "স্ক্যান" is its own shortcut: it opens the sale screen with the camera scanner running.
 */
export default function MobileTabBar({ currentPage, onNavigate, onScan, onOpenMenu }) {
  const inTabs = TABS.some((t) => t.id === currentPage);
  return (
    <nav className="mobile-tabbar no-print" aria-label="প্রধান ট্যাব">
      {TABS.map(({ id, label, icon: Icon, featured }) => {
        const active = currentPage === id;
        return (
          <button
            key={id}
            type="button"
            className={`mobile-tab ${active ? 'active' : ''} ${featured ? 'featured' : ''}`}
            aria-current={active ? 'page' : undefined}
            onClick={() => {
              if (id === 'scan') return onScan();
              onNavigate(id);
              window.scrollTo({ top: 0 });
            }}
          >
            <span className="mobile-tab-icon"><Icon size={featured ? 22 : 20} strokeWidth={active || featured ? 2.4 : 2} /></span>
            <span className="mobile-tab-label">{label}</span>
          </button>
        );
      })}
      <button type="button" className={`mobile-tab ${inTabs ? '' : 'active'}`} onClick={onOpenMenu}>
        <span className="mobile-tab-icon"><Menu size={20} /></span>
        <span className="mobile-tab-label">আরও</span>
      </button>
    </nav>
  );
}
