import React, { useState, useEffect } from 'react';
import { db } from '../../services/firebase/config';
import { doc, getDoc, collection, query, where, getDocs, onSnapshot } from 'firebase/firestore';
import { useAuth } from '../../context/AuthContext';
import Layout from '../../components/Layout';
import {
  Store,
  Smartphone,
  CheckCircle2,
  Zap,
  Settings,
  Tv,
  ScanLine,
  Activity
} from 'lucide-react';
import { useNavigate } from 'react-router-dom';

const Dashboard = () => {
  const { shopkeeperData } = useAuth();
  const [shop, setShop] = useState(null);
  const [counts, setCounts] = useState({ kiosks: 0, ads: 0, scans: 0 });
  const [primaryKiosk, setPrimaryKiosk] = useState(null);
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();

  useEffect(() => {
    if (!shopkeeperData?.shopId) return;

    const fetchData = async () => {
      try {
        const shopDoc = await getDoc(doc(db, 'shops', shopkeeperData.shopId));
        if (shopDoc.exists()) setShop(shopDoc.data());
      } catch (e) { console.error(e); }
    };

    fetchData();

    // Real-time listeners for counts
    const unsubKiosks = onSnapshot(
      query(collection(db, 'kiosks'), where('shopId', '==', shopkeeperData.shopId)),
      (snap) => {
        setCounts(prev => ({ ...prev, kiosks: snap.size }));
        if (!snap.empty) setPrimaryKiosk(snap.docs[0].data());
      }
    );

    const unsubAds = onSnapshot(
      query(collection(db, `shops/${shopkeeperData.shopId}/advertisements`), where('deleted', '==', false)),
      (snap) => setCounts(prev => ({ ...prev, ads: snap.size }))
    );

    const today = new Date().toISOString().split('T')[0];
    const scanDocId = `${shopkeeperData.shopId}_${today}`;
    const unsubScans = onSnapshot(doc(db, 'daily_scans', scanDocId), (docSnap) => {
      if (docSnap.exists()) {
        setCounts(prev => ({ ...prev, scans: docSnap.data().scanCount || 0 }));
      } else {
        setCounts(prev => ({ ...prev, scans: 0 }));
      }
    });

    setLoading(false);
    return () => {
      unsubKiosks();
      unsubAds();
      unsubScans();
    };
  }, [shopkeeperData]);

  const stats = [
    { label: 'Connected Kiosks', value: counts.kiosks.toString(), icon: Smartphone, color: 'text-blue-600', bg: 'bg-blue-50' },
    { label: 'Active Ads', value: counts.ads.toString(), icon: Tv, color: 'text-orange-600', bg: 'bg-orange-50' },
    { label: "Today's Scans", value: counts.scans.toString(), icon: ScanLine, color: 'text-purple-600', bg: 'bg-purple-50' },
  ];

  if (loading) {
    return (
      <Layout>
        <div className="flex items-center justify-center h-64">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary"></div>
        </div>
      </Layout>
    );
  }

  const getKioskStatus = () => {
    if (!primaryKiosk) return null;
    const lastActive = primaryKiosk.lastActiveTimestamp;
    const isOnline = lastActive && (Date.now() - lastActive < 10 * 60 * 1000);
    return isOnline ? 'ONLINE' : 'OFFLINE';
  };

  return (
    <Layout>
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-6 mb-10">
        <div>
          <h2 className="text-[10px] uppercase tracking-[0.2em] text-primary font-black mb-1">Control Center</h2>
          <h1 className="text-3xl md:text-4xl font-black text-slate-800 tracking-tight leading-none">Dashboard</h1>
        </div>
        <div className="bg-white p-4 rounded-3xl border border-slate-100 shadow-sm flex items-center gap-4 group hover:border-primary/20 transition-colors">
          <div className="w-12 h-12 bg-primary/10 rounded-2xl flex items-center justify-center text-primary group-hover:bg-primary group-hover:text-white transition-all duration-300 shadow-inner">
            <Store size={22} />
          </div>
          <div>
            <p className="text-[10px] text-slate-400 font-bold uppercase tracking-widest leading-none mb-1">Your Shop</p>
            <p className="text-base font-black text-slate-700 tracking-tight">{shop?.shopName || 'Shop'}</p>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 md:gap-8 mb-10">
        {stats.map((stat, i) => (
          <div key={i} className="bg-white rounded-[2.5rem] p-8 shadow-sm border border-slate-50 hover:shadow-xl hover:shadow-slate-200/50 transition-all duration-300 group">
            <div className={`w-16 h-16 rounded-[1.5rem] ${stat.bg} ${stat.color} flex items-center justify-center mb-6 group-hover:scale-110 transition-transform duration-300 shadow-sm`}>
              <stat.icon size={28} />
            </div>
            <p className="text-slate-400 text-xs font-black uppercase tracking-widest mb-1">{stat.label}</p>
            <h4 className="text-4xl font-black text-slate-800 tracking-tighter">
              {stat.value}
            </h4>
          </div>
        ))}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 md:gap-10">
        <div className="lg:col-span-1 bg-slate-900 rounded-[3rem] p-10 shadow-2xl relative overflow-hidden group">
          <div className="absolute top-0 right-0 p-10 opacity-10 text-white group-hover:scale-125 transition-transform duration-700">
            <Activity size={160} />
          </div>

          <div className="relative z-10">
            <div className="flex items-center gap-3 mb-10">
                <div className="w-10 h-10 bg-white/10 rounded-2xl flex items-center justify-center text-amber-400 backdrop-blur-md">
                    <Zap size={22} fill="currentColor" />
                </div>
                <h3 className="text-xl font-black text-white tracking-tight">Kiosk Status</h3>
            </div>

            {primaryKiosk ? (
                <div className="space-y-8">
                <div className="bg-white/5 rounded-3xl p-6 border border-white/10 backdrop-blur-md">
                    <p className="text-[10px] text-slate-400 font-bold uppercase tracking-widest mb-2">Active Hardware ID</p>
                    <p className="font-mono text-lg font-black text-slate-200 tracking-wider break-all">{primaryKiosk.kioskId}</p>
                </div>

                <div className="flex items-center justify-between bg-white/5 p-6 rounded-3xl border border-white/5">
                    <div className="flex items-center gap-3">
                    <div className={`w-4 h-4 rounded-full ${getKioskStatus() === 'ONLINE' ? 'bg-green-400 shadow-[0_0_15px_rgba(74,222,128,0.5)] animate-pulse' : 'bg-slate-600'}`}></div>
                    <span className="text-sm font-black text-slate-300 uppercase tracking-widest">{getKioskStatus() || 'Offline'}</span>
                    </div>
                    <button
                        onClick={() => navigate('/kiosks')}
                        className="px-6 py-3 bg-white text-slate-900 rounded-2xl text-xs font-black uppercase tracking-widest hover:bg-primary hover:text-white transition-all shadow-lg active:scale-95"
                    >
                        Manage
                    </button>
                </div>
                </div>
            ) : (
                <div className="text-center py-6 bg-white/5 rounded-[2rem] border border-dashed border-white/10">
                <Smartphone className="mx-auto text-slate-700 mb-4 opacity-50" size={64} />
                <p className="text-slate-400 font-bold italic">No kiosks connected</p>
                <button
                    onClick={() => navigate('/kiosks')}
                    className="mt-6 px-10 py-4 bg-primary text-white rounded-2xl font-black uppercase tracking-widest shadow-xl shadow-primary/20 hover:scale-105 transition-transform"
                >
                    Pair Kiosk
                </button>
                </div>
            )}
          </div>
        </div>

        <div className="lg:col-span-2 bg-white rounded-[3rem] p-10 shadow-sm border border-slate-50 flex flex-col">
          <h3 className="text-2xl font-black text-slate-800 mb-10 tracking-tight">Quick Actions</h3>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-6 flex-1">
            <button
              onClick={() => navigate('/ads')}
              className="flex flex-col items-start p-10 bg-amber-50 rounded-[2.5rem] border border-amber-100/50 hover:bg-amber-100 hover:border-amber-200 transition-all group relative overflow-hidden"
            >
              <div className="w-14 h-14 bg-white rounded-2xl shadow-sm flex items-center justify-center text-amber-600 mb-6 group-hover:scale-110 transition-transform duration-300">
                <Tv size={32} />
              </div>
              <p className="font-black text-slate-800 text-2xl tracking-tighter mb-1">Ads Manager</p>
              <p className="text-amber-700/60 font-bold text-xs uppercase tracking-widest">Push Promotions</p>
              <div className="absolute bottom-0 right-0 p-8 opacity-5 text-amber-600 group-hover:scale-150 transition-transform duration-700">
                  <Tv size={80} />
              </div>
            </button>

            <button
              onClick={() => navigate('/kiosks')}
              className="flex flex-col items-start p-10 bg-slate-50 rounded-[2.5rem] border border-slate-100 hover:bg-slate-100 hover:border-slate-200 transition-all group relative overflow-hidden"
            >
              <div className="w-14 h-14 bg-white rounded-2xl shadow-sm flex items-center justify-center text-slate-600 mb-6 group-hover:scale-110 transition-transform duration-300">
                <Smartphone size={32} />
              </div>
              <p className="font-black text-slate-800 text-2xl tracking-tighter mb-1">Pair Device</p>
              <p className="text-slate-500/60 font-bold text-xs uppercase tracking-widest">Connect Hardware</p>
              <div className="absolute bottom-0 right-0 p-8 opacity-5 text-slate-600 group-hover:scale-150 transition-transform duration-700">
                  <Smartphone size={80} />
              </div>
            </button>
          </div>
        </div>
      </div>
    </Layout>
  );
};

export default Dashboard;
