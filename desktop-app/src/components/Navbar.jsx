import React, { useMemo, useState } from 'react';
import { useShop } from '../context/ShopContext';
import { WifiOff, RefreshCw, LogOut, Menu, Moon, Sun, Lock, ShieldCheck, Search, Package, Receipt, Users, MessageCircle } from 'lucide-react';
import { useSupport } from '../utils/supportStore';
import { formatDateTime } from '../utils/formatters';
import { confirmDialog } from './Feedback';
import NotificationBell from './NotificationBell';

export default function Navbar({ onNavigate, onOpenMobileMenu, theme, onToggleTheme, onLockNow }) {
  const { auth, logout, shopInfo, products, sales, customers, isFirebaseConnected, isSyncing, lastSyncTime, secureMode } = useShop();
  const [query, setQuery] = useState('');
  const { messages: supportMessages, readAt: supportReadAt } = useSupport();
  const unreadChat = supportMessages.filter((m) => m.sender === 'support' && m.ts > supportReadAt).length;
  const isOwner = auth?.role === 'owner';
  const searchResults = useMemo(() => {
    const needle = query.trim().toLocaleLowerCase();
    if (!needle) return [];
    return [
      ...products.filter((item) => `${item.nameBn || ''} ${item.nameEn || ''} ${item.barcode || ''}`.toLocaleLowerCase().includes(needle)).slice(0, 4).map((item) => ({ label: item.nameBn || item.nameEn, detail: item.nameEn || 'পণ্য ও ইনভেন্টরি', icon: Package, page: 'products' })),
      ...sales.filter((item) => `${item.invoiceNumber || ''} ${item.customerName || ''}`.toLocaleLowerCase().includes(needle)).slice(0, 3).map((item) => ({ label: item.invoiceNumber, detail: item.customerName || 'বিক্রয়', icon: Receipt, page: 'sales' })),
      ...customers.filter((item) => `${item.name || ''} ${item.phone || ''}`.toLocaleLowerCase().includes(needle)).slice(0, 3).map((item) => ({ label: item.name, detail: item.phone || 'কাস্টমার', icon: Users, page: 'due' })),
    ].slice(0, 7);
  }, [query, products, sales, customers]);

  const handleLogout = async () => {
    const ok = await confirmDialog('এই ডিভাইস থেকে লগআউট করতে চান?', {
      title: 'লগআউট',
      confirmText: 'লগআউট',
      danger: false,
    });
    if (ok) logout();
  };

  return (
    <header className="top-navbar no-print">
      <div className="navbar-left">
        <button className="icon-btn show-mobile" onClick={onOpenMobileMenu} aria-label="মেনু খুলুন">
          <Menu size={18} />
        </button>
        <div style={{ minWidth: 0 }}>
          <div className="navbar-shop-name">{shopInfo.shopName || 'Dokan Pro'}</div>
        </div>
        {auth?.shopCode && <span className="shop-code-chip hide-mobile">{auth.shopCode}</span>}
      </div>

      <div className="global-search-wrap hide-mobile">
        <Search size={18} />
        <input
          aria-label="পণ্য, অর্ডার বা কাস্টমার খুঁজুন"
          placeholder="পণ্য, বিক্রয় বা কাস্টমার খুঁজুন…"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter' && searchResults[0]) { onNavigate(searchResults[0].page); setQuery(''); }
            if (event.key === 'Escape') setQuery('');
          }}
        />
        {query.trim() && <div className="global-search-results">
          {searchResults.length ? searchResults.map((result, index) => {
            const ResultIcon = result.icon;
            return <button key={`${result.page}-${result.label}-${index}`} onClick={() => { onNavigate(result.page); setQuery(''); }}><ResultIcon size={16} /><span><strong>{result.label}</strong><small>{result.detail}</small></span></button>;
          }) : <div className="global-search-empty">কোনো ফলাফল পাওয়া যায়নি</div>}
        </div>}
      </div>

      <div className="navbar-right">
        <div
          className={`sync-status-pill ${isSyncing ? 'syncing' : isFirebaseConnected ? 'online' : 'offline'}`}
          title={lastSyncTime ? `সর্বশেষ সিঙ্ক: ${formatDateTime(lastSyncTime)}` : ''}
        >
          {isSyncing ? (
            <>
              <RefreshCw size={13} className="spin" />
              <span className="hide-mobile">সিঙ্ক হচ্ছে…</span>
            </>
          ) : isFirebaseConnected ? (
            <>
              <span className="pulse-dot" />
              <span className="hide-mobile">লাইভ সিঙ্ক</span>
            </>
          ) : (
            <>
              <WifiOff size={13} />
              <span className="hide-mobile">অফলাইন</span>
            </>
          )}
        </div>

        {secureMode && (
          <span className="badge badge-success hide-mobile" title="সার্ভার-যাচাইকৃত নিরাপদ সেশন">
            <ShieldCheck size={12} /> সুরক্ষিত
          </span>
        )}

        <button className="icon-btn chat-btn" onClick={() => onNavigate('support')} title="লাইভ চ্যাট" aria-label={`লাইভ চ্যাট${unreadChat ? ` (${unreadChat}টি নতুন উত্তর)` : ''}`}>
          <MessageCircle size={17} />
          {unreadChat > 0 && <span className="notif-count chat-count">{unreadChat > 9 ? '9+' : unreadChat}</span>}
        </button>

        <NotificationBell onNavigate={onNavigate} />

        <button className="icon-btn" onClick={onToggleTheme} title={theme === 'dark' ? 'লাইট মোড' : 'ডার্ক মোড'} aria-label="থিম পরিবর্তন">
          {theme === 'dark' ? <Sun size={17} /> : <Moon size={17} />}
        </button>

        {isOwner && (
          <button className="icon-btn hide-mobile" onClick={onLockNow} title="স্ক্রিন লক করুন" aria-label="স্ক্রিন লক করুন">
            <Lock size={16} />
          </button>
        )}

        <div className="user-chip hide-mobile">
          <div className="avatar">{(auth?.userName || 'U').slice(0, 1)}</div>
          <div style={{ lineHeight: 1.15 }}>
            <div style={{ fontSize: '0.8rem', fontWeight: 600 }}>{auth?.userName || 'ব্যবহারকারী'}</div>
            <div style={{ fontSize: '0.68rem', color: 'var(--text-muted)' }}>{isOwner ? 'মালিক' : 'কর্মচারী'}</div>
          </div>
        </div>

        <button onClick={handleLogout} className="icon-btn" title="লগআউট" aria-label="লগআউট">
          <LogOut size={16} />
        </button>
      </div>
    </header>
  );
}
