import React, { useState } from 'react';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import {
  LayoutDashboard,
  Smartphone,
  User,
  LogOut,
  Menu,
  X,
  Sprout,
  ChevronRight,
  Store,
  Tv,
  Settings,
  Lock
} from 'lucide-react';

const Layout = ({ children }) => {
  const { logout, shopkeeperData } = useAuth();
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  const navItems = [
    { name: 'Dashboard', path: '/', icon: LayoutDashboard, status: 'active' },
    { name: 'My Shop', path: '/shop', icon: Store, status: 'active' },
    { name: 'My Kiosks', path: '/kiosks', icon: Smartphone, status: 'active' },
    { name: 'Advertisements', path: '/ads', icon: Tv, status: 'active' },
    { name: 'Settings', path: '/settings', icon: Settings, status: 'phase2' },
  ];

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col md:flex-row">
      {/* Sidebar - Desktop */}
      <aside className="hidden md:flex flex-col w-64 bg-white border-r border-slate-200">
        <div className="p-6 border-b border-slate-100 flex items-center gap-3">
          <div className="w-10 h-10 bg-primary rounded-xl flex items-center justify-center text-white shadow-lg shadow-primary/20">
            <Sprout size={24} />
          </div>
          <div>
            <h1 className="font-bold text-slate-800 leading-none">AgroVision</h1>
            <p className="text-[10px] uppercase tracking-wider text-slate-400 font-bold mt-1">Shopkeeper</p>
          </div>
        </div>

        <nav className="flex-1 p-4 space-y-1">
          {navItems.map((item) => (
            <div key={item.path}>
              {item.status === 'active' ? (
                <Link
                  to={item.path}
                  className={`flex items-center gap-3 px-4 py-3 rounded-xl transition-all ${
                    location.pathname === item.path
                      ? 'bg-primary/10 text-primary font-semibold'
                      : 'text-slate-600 hover:bg-slate-50'
                  }`}
                >
                  <item.icon size={20} />
                  {item.name}
                  {location.pathname === item.path && <ChevronRight size={16} className="ml-auto" />}
                </Link>
              ) : (
                <div className="flex items-center gap-3 px-4 py-3 rounded-xl text-slate-300 cursor-not-allowed group relative">
                  <item.icon size={20} />
                  {item.name}
                  <span className="ml-auto text-[8px] bg-slate-100 text-slate-400 px-1.5 py-0.5 rounded uppercase font-bold">Phase 2</span>
                </div>
              )}
            </div>
          ))}
        </nav>

        <div className="p-4 border-t border-slate-100">
          <div className="flex items-center gap-3 px-4 py-3 mb-2">
            <div className="w-8 h-8 bg-slate-100 rounded-full flex items-center justify-center text-slate-500 font-bold text-xs border border-slate-200">
              {shopkeeperData?.name?.charAt(0) || 'S'}
            </div>
            <div className="flex-1 overflow-hidden">
              <p className="text-sm font-semibold text-slate-800 truncate">{shopkeeperData?.name || 'Shopkeeper'}</p>
              <p className="text-[10px] text-slate-400 truncate font-mono">{shopkeeperData?.phoneNumber}</p>
            </div>
          </div>
          <button
            onClick={handleLogout}
            className="flex items-center gap-3 w-full px-4 py-3 text-red-500 hover:bg-red-50 rounded-xl transition-all font-medium"
          >
            <LogOut size={20} />
            Sign Out
          </button>
        </div>
      </aside>

      {/* Mobile Header */}
      <header className="md:hidden bg-white border-b border-slate-200 p-4 flex items-center justify-between sticky top-0 z-30 shadow-sm">
        <div className="flex items-center gap-2">
          <div className="w-8 h-8 bg-primary rounded-lg flex items-center justify-center text-white shadow-md shadow-primary/20">
            <Sprout size={18} />
          </div>
          <h1 className="font-bold text-slate-800">AgroVision</h1>
        </div>
        <button
          onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}
          className="p-2 text-slate-600 hover:bg-slate-50 rounded-xl transition-colors active:scale-95"
        >
          {isMobileMenuOpen ? <X size={24} /> : <Menu size={24} />}
        </button>
      </header>

      {/* Mobile Menu Overlay */}
      {isMobileMenuOpen && (
        <div
          className="md:hidden fixed inset-0 z-40 bg-slate-900/60 backdrop-blur-sm transition-opacity duration-300"
          onClick={() => setIsMobileMenuOpen(false)}
        >
          <div
            className="bg-white w-[280px] h-full shadow-2xl p-6 flex flex-col animate-in slide-in-from-left duration-300 ease-out"
            onClick={e => e.stopPropagation()}
          >
             <div className="flex items-center gap-3 mb-8 pb-6 border-b border-slate-100">
                <div className="w-10 h-10 bg-primary rounded-xl flex items-center justify-center text-white shadow-lg shadow-primary/20">
                  {shopkeeperData?.name?.charAt(0) || 'S'}
                </div>
                <div className="overflow-hidden">
                  <p className="font-bold text-slate-800 truncate">{shopkeeperData?.name || 'Shopkeeper'}</p>
                  <p className="text-[10px] text-slate-400 font-bold uppercase tracking-tighter truncate">{shopkeeperData?.phoneNumber}</p>
                </div>
             </div>

             <nav className="flex-1 space-y-1">
                {navItems.map((item) => (
                  item.status === 'active' ? (
                    <Link
                      key={item.path}
                      to={item.path}
                      onClick={() => setIsMobileMenuOpen(false)}
                      className={`flex items-center gap-4 px-4 py-4 rounded-2xl transition-all active:scale-[0.98] ${
                        location.pathname === item.path
                          ? 'bg-primary text-white shadow-lg shadow-primary/30 font-bold'
                          : 'text-slate-600 hover:bg-slate-50 font-semibold'
                      }`}
                    >
                      <item.icon size={22} className={location.pathname === item.path ? 'text-white' : 'text-slate-400'} />
                      <span>{item.name}</span>
                    </Link>
                  ) : (
                    <div key={item.path} className="flex items-center gap-4 px-4 py-4 text-slate-300 opacity-60">
                      <item.icon size={22} />
                      <span className="font-semibold">{item.name}</span>
                      <span className="ml-auto text-[8px] bg-slate-100 px-1.5 py-0.5 rounded font-bold">SOON</span>
                    </div>
                  )
                ))}
              </nav>

              <div className="mt-auto pt-6 border-t border-slate-100">
                 <button
                  onClick={handleLogout}
                  className="flex items-center gap-4 w-full px-4 py-4 text-red-500 font-bold active:scale-95 transition-transform"
                >
                  <div className="w-10 h-10 bg-red-50 rounded-xl flex items-center justify-center">
                    <LogOut size={20} />
                  </div>
                  <span>Sign Out</span>
                </button>
              </div>
          </div>
        </div>
      )}

      {/* Main Content */}
      <main className="flex-1 overflow-auto">
        <div className="p-4 md:p-8 max-w-6xl mx-auto">
          {children}
        </div>
      </main>
    </div>
  );
};

export default Layout;
