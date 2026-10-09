import React, { Suspense, lazy, useEffect, useState } from 'react';
import { ShopProvider, useShop } from './context/ShopContext';
import Navbar from './components/Navbar';
import Sidebar from './components/Sidebar';
import ReceiptModal from './components/ReceiptModal';
import FeedbackHost from './components/Feedback';
import {
  SplashScreen,
  MaintenanceScreen,
  SuspendedScreen,
  TrialExpiredScreen,
  LockedFeatureCard,
  LockScreen,
  ForcePinChangeScreen,
} from './components/StatusScreens';
import ActivationFlow from './pages/ActivationFlow';
import MobileTabBar from './components/MobileTabBar';
import InstallPrompt from './components/InstallPrompt';

// The landing page is only for the website (and the iPhone Home Screen app), not the desktop app.
const LandingPage = lazy(() => import('./pages/LandingPage'));
// "/" shows the landing page and "/#app" the app, so the browser back button moves between them.
function wantsLanding() {
  return !/Electron/i.test(navigator.userAgent) && window.location.hash !== '#app';
}

// Pages load on demand so the login screen stays fast.
const Dashboard = lazy(() => import('./pages/Dashboard'));
const PosPage = lazy(() => import('./pages/PosPage'));
const ProductsPage = lazy(() => import('./pages/ProductsPage'));
const SalesPage = lazy(() => import('./pages/SalesPage'));
const DueKhataPage = lazy(() => import('./pages/DueKhataPage'));
const StaffPage = lazy(() => import('./pages/StaffPage'));
const ExpensesPage = lazy(() => import('./pages/ExpensesPage'));
const PurchasesPage = lazy(() => import('./pages/PurchasesPage'));
const ReportsPage = lazy(() => import('./pages/ReportsPage'));
const BackupPage = lazy(() => import('./pages/BackupPage'));
const SettingsPage = lazy(() => import('./pages/SettingsPage'));
import { Megaphone, AlertTriangle, X } from 'lucide-react';
import { useTheme } from './utils/theme';

const OWNER_PAGES = new Set(['reports', 'expenses', 'staff', 'backup', 'settings']);

function readDismissed() {
  try {
    return JSON.parse(sessionStorage.getItem('dokan_dismissed_notices') || '[]');
  } catch {
    return [];
  }
}

function AppContent() {
  const {
    auth,
    authReady,
    logout,
    currentShopStatus,
    shopFeatures,
    globalNotices,
    globalSettings,
    activeShopNotice,
    isLocked,
    lockScreen,
  } = useShop();
  const { theme, toggle: toggleTheme } = useTheme();

  const [currentPage, setCurrentPage] = useState('dashboard');
  const [showLanding, setShowLanding] = useState(wantsLanding);
  useEffect(() => {
    const sync = () => setShowLanding(wantsLanding());
    window.addEventListener('popstate', sync);
    window.addEventListener('hashchange', sync);
    return () => {
      window.removeEventListener('popstate', sync);
      window.removeEventListener('hashchange', sync);
    };
  }, []);
  const openApp = () => {
    window.location.hash = 'app';
    setShowLanding(false);
    window.scrollTo(0, 0);
  };
  const goHome = () => {
    window.history.pushState(null, '', window.location.pathname + window.location.search);
    setShowLanding(true);
    window.scrollTo(0, 0);
  };
  const [activeReceiptSale, setActiveReceiptSale] = useState(null);
  const [isSidebarCollapsed, setIsSidebarCollapsed] = useState(() => {
    try {
      return localStorage.getItem('dokan_sidebar') === 'collapsed';
    } catch {
      return false;
    }
  });
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  const [dismissed, setDismissed] = useState(readDismissed);

  const setCollapsed = (v) => {
    setIsSidebarCollapsed(v);
    try {
      localStorage.setItem('dokan_sidebar', v ? 'collapsed' : 'open');
    } catch {
      /* storage blocked */
    }
  };

  if (!authReady) return <SplashScreen />;

  if (globalSettings?.maintenanceMode) {
    return <MaintenanceScreen message={globalSettings.maintenanceMessage} onLogout={auth ? logout : null} />;
  }

  if (!auth?.shopCode) {
    return showLanding ? (
      <Suspense fallback={<SplashScreen />}>
        <LandingPage onOpenApp={openApp} />
      </Suspense>
    ) : (
      <ActivationFlow onHome={/Electron/i.test(navigator.userAgent) ? undefined : goHome} />
    );
  }

  if (auth.pinChangeRequired) return <ForcePinChangeScreen />;

  if (currentShopStatus?.isSuspended) {
    return <SuspendedScreen shopCode={auth.shopCode} reason={currentShopStatus.reason} onLogout={logout} />;
  }

  const localTrialExpiry = Number(localStorage.getItem('dokan_desktop_trial_expires_at') || 0);
  const isLocalTrialExpired = localStorage.getItem('dokan_desktop_trial_shop') === auth.shopCode
    && localTrialExpiry > 0
    && Date.now() > localTrialExpiry;
  const isTrialExpired = isLocalTrialExpired || (
    (currentShopStatus?.licenseType === 'trial' || currentShopStatus?.plan === 'trial') &&
    currentShopStatus?.expiresAt &&
    Date.now() > currentShopStatus.expiresAt
  );
  if (isTrialExpired) return <TrialExpiredScreen shopCode={auth.shopCode} onLogout={logout} />;

  const isOwner = auth.role === 'owner';
  // Staff can never land on an owner page, even if state is tampered with.
  const page = !isOwner && OWNER_PAGES.has(currentPage) ? 'dashboard' : currentPage;
  const latestNotice = [activeShopNotice, ...globalNotices].find((n) => n && !dismissed.includes(n.id)) || null;

  const dismissNotice = (id) => {
    const next = [...dismissed, id];
    setDismissed(next);
    try {
      sessionStorage.setItem('dokan_dismissed_notices', JSON.stringify(next));
    } catch {
      /* storage blocked */
    }
  };

  const gated = (flag, title, element) => (shopFeatures?.[flag] === false ? <LockedFeatureCard title={title} /> : element);

  return (
    <div className="app-container" inert={isLocked || undefined}>
      <Sidebar
        currentPage={page}
        setCurrentPage={setCurrentPage}
        isCollapsed={isSidebarCollapsed}
        setIsCollapsed={setCollapsed}
        isMobileOpen={isMobileMenuOpen}
        onCloseMobile={() => setIsMobileMenuOpen(false)}
      />

      <div className="main-content">
        <Navbar
          onNavigate={setCurrentPage}
          onOpenMobileMenu={() => setIsMobileMenuOpen(true)}
          theme={theme}
          onToggleTheme={toggleTheme}
          onLockNow={lockScreen}
        />

        {latestNotice && (
          <div className={`notice-banner no-print ${latestNotice.type === 'warning' ? 'warning' : ''}`}>
            {latestNotice.type === 'warning' ? <AlertTriangle size={16} color="var(--danger)" /> : <Megaphone size={16} color="var(--info)" />}
            <strong>{latestNotice.title}</strong>
            <span style={{ color: 'var(--text-secondary)' }}>{latestNotice.message}</span>
            <button className="notice-close" onClick={() => dismissNotice(latestNotice.id)} aria-label="নোটিশ বন্ধ করুন">
              <X size={15} />
            </button>
          </div>
        )}

        <main style={{ flex: 1 }}>
          <Suspense fallback={<div className="empty-state"><span className="spin" style={{ display: 'inline-block' }}>◌</span></div>}>
          {page === 'dashboard' && <Dashboard onNavigate={setCurrentPage} onSelectSale={setActiveReceiptSale} />}
          {page === 'pos' && gated('pos', 'ক্যাশ কাউন্টার (POS)', <PosPage onCompleteSale={setActiveReceiptSale} />)}
          {page === 'products' && gated('inventoryEdit', 'পণ্য ও ইনভেন্টরি', <ProductsPage />)}
          {page === 'sales' && gated('reports', 'বিক্রয় খাতা', <SalesPage onSelectSale={setActiveReceiptSale} />)}
          {page === 'due' && gated('dueKhata', 'বাকির খাতা', <DueKhataPage />)}
          {page === 'purchases' && <PurchasesPage />}
          {isOwner && page === 'reports' && gated('reports', 'লাভ-ক্ষতি ও রিপোর্ট', <ReportsPage />)}
          {isOwner && page === 'expenses' && gated('expenses', 'খরচের খাতা', <ExpensesPage />)}
          {isOwner && page === 'staff' && gated('staffManagement', 'কর্মচারী ব্যবস্থাপনা', <StaffPage />)}
          {isOwner && page === 'backup' && <BackupPage />}
          {isOwner && page === 'settings' && <SettingsPage />}
          </Suspense>
        </main>
      </div>

      <MobileTabBar currentPage={page} onNavigate={setCurrentPage} onOpenMenu={() => setIsMobileMenuOpen(true)} />

      {activeReceiptSale && (
        <ReceiptModal
          sale={activeReceiptSale}
          onClose={() => setActiveReceiptSale(null)}
          isPrintDisabled={shopFeatures?.receiptPrint === false}
        />
      )}
    </div>
  );
}

function Shell() {
  const { isLocked } = useShop();
  return (
    <>
      <AppContent />
      {isLocked && <LockScreen />}
      <InstallPrompt />
      <FeedbackHost />
    </>
  );
}

export default function App() {
  return (
    <ShopProvider>
      <Shell />
    </ShopProvider>
  );
}
