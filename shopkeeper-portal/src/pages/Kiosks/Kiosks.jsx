import React, { useState, useEffect } from 'react';
import { db } from '../../services/firebase/config';
import {
  collection,
  query,
  where,
  getDocs,
  getDoc,
  doc,
  updateDoc,
  serverTimestamp,
  onSnapshot
} from 'firebase/firestore';
import { useAuth } from '../../context/AuthContext';
import Layout from '../../components/Layout';
import {
  Smartphone,
  Plus,
  X,
  Check,
  ShieldAlert,
  Laptop,
  Info,
  Clock,
  ArrowRight,
  Wifi,
  Search,
  RefreshCw,
  Zap,
  Activity,
  Calendar,
  Trash2
} from 'lucide-react';

const Kiosks = () => {
  const { shopkeeperData } = useAuth();
  const [kiosks, setKiosks] = useState([]);
  const [shop, setShop] = useState(null);
  const [loading, setLoading] = useState(true);
  const [isAdding, setIsAdding] = useState(false);
  const [kioskId, setKioskId] = useState('');
  const [pairingCode, setPairingCode] = useState('');
  const [actionLoading, setActionLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [syncingId, setSyncingId] = useState(null);

  useEffect(() => {
    if (!shopkeeperData?.shopId) return;

    // Fetch Shop info
    const fetchShopInfo = async () => {
      try {
        const shopRef = doc(db, 'shops', shopkeeperData.shopId);
        const shopSnap = await getDoc(shopRef);
        if (shopSnap.exists()) {
          setShop(shopSnap.data());
        }
      } catch (err) {
        console.error("Error fetching shop info:", err);
      }
    };

    fetchShopInfo();

    // Real-time listener for kiosks
    const q = query(
      collection(db, 'kiosks'),
      where('shopId', '==', shopkeeperData.shopId)
    );

    const unsubscribe = onSnapshot(q, (snapshot) => {
      const kioskList = snapshot.docs.map(doc => ({
        id: doc.id,
        ...doc.data()
      }));
      setKiosks(kioskList);
      setLoading(false);
    }, (error) => {
      console.error("Error listening to kiosks:", error);
      setLoading(false);
    });

    return () => unsubscribe();
  }, [shopkeeperData]);

  const handlePairing = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    setActionLoading(true);

    try {
      const q = query(collection(db, 'kiosks'), where('kioskId', '==', kioskId));
      const querySnapshot = await getDocs(q);

      if (querySnapshot.empty) {
        setError('Kiosk not found. Please check the Kiosk ID.');
        setActionLoading(false);
        return;
      }

      const kioskDoc = querySnapshot.docs[0];
      const kioskData = kioskDoc.data();

      if (kioskData.shopId && kioskData.shopId !== shopkeeperData.shopId) {
        setError('This kiosk is already linked to another shop.');
        setActionLoading(false);
        return;
      }

      if (kioskData.pairingCode !== pairingCode) {
        setError('Invalid pairing code. Please check the code on the kiosk screen.');
        setActionLoading(false);
        return;
      }

      await updateDoc(doc(db, 'kiosks', kioskDoc.id), {
        shopId: shopkeeperData.shopId,
        shopName: shop?.shopName || 'AgroVision Partner',
        linkedAt: serverTimestamp(),
        status: 'PAIRED',
        pairingCode: null,
        configVersion: serverTimestamp()
      });

      setSuccess('Kiosk successfully linked to your shop!');
      setKioskId('');
      setPairingCode('');
      setTimeout(() => setIsAdding(false), 2000);

    } catch (err) {
      console.error(err);
      setError('An error occurred during pairing. Please try again.');
    }
    setActionLoading(false);
  };

  const handleManualSync = async (kiosk) => {
    setSyncingId(kiosk.id);
    try {
      await updateDoc(doc(db, 'kiosks', kiosk.id), {
        syncRequested: serverTimestamp(),
        lastCommand: 'FORCE_SYNC'
      });

      await updateDoc(doc(db, 'shops', shopkeeperData.shopId), {
        configVersion: serverTimestamp()
      });

      setTimeout(() => setSyncingId(null), 3000);
    } catch (error) {
      alert('Failed to request sync');
      setSyncingId(null);
    }
  };

  const handleUnlink = async (kiosk) => {
    if (!window.confirm(`Are you sure you want to unlink ${kiosk.kioskId}? This will return the device to the pairing screen.`)) {
      return;
    }

    setActionLoading(true);
    try {
      await updateDoc(doc(db, 'kiosks', kiosk.id), {
        shopId: null,
        pairingCode: Math.floor(100000 + Math.random() * 900000).toString(), // Generate new pairing code
        status: 'PENDING',
        updatedAt: serverTimestamp()
      });
      setSuccess('Kiosk successfully unlinked!');
      setTimeout(() => setSuccess(''), 3000);
    } catch (err) {
      console.error(err);
      setError('Failed to unlink kiosk.');
    }
    setActionLoading(false);
  };

  const getStatusInfo = (kiosk) => {
    const lastActive = kiosk.lastActiveTimestamp;
    const now = Date.now();
    const isOnline = lastActive && (now - lastActive < 10 * 60 * 1000);

    return {
      isOnline: isOnline && kiosk.status === 'ONLINE',
      label: isOnline && kiosk.status === 'ONLINE' ? 'Online' : 'Offline',
      lastSeen: lastActive ? new Date(lastActive).toLocaleTimeString() : 'Never'
    };
  };

  return (
    <Layout>
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 mb-10">
        <div>
          <h1 className="text-3xl font-extrabold text-slate-800">My Kiosks</h1>
          <p className="text-slate-500 mt-1 font-medium italic">Monitor hardware status and manage synchronization</p>
        </div>
        <button
          onClick={() => setIsAdding(true)}
          className="flex items-center gap-2 bg-primary hover:bg-primary-dark text-white px-8 py-4 rounded-2xl font-bold transition-all shadow-lg shadow-primary/20"
        >
          <Plus size={20} />
          Pair New Kiosk
        </button>
      </div>

      {isAdding && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-md">
          <div className="bg-white w-full max-w-md rounded-[2.5rem] shadow-2xl overflow-hidden animate-in zoom-in-95 duration-200">
            <div className="p-8 border-b border-slate-50 flex items-center justify-between bg-slate-50/50">
              <h3 className="font-bold text-xl text-slate-800 flex items-center gap-3">
                <Laptop size={24} className="text-primary" />
                Pair a Kiosk
              </h3>
              <button onClick={() => setIsAdding(false)} className="text-slate-400 hover:text-slate-600 bg-white p-2 rounded-xl shadow-sm border border-slate-100 transition-all">
                <X size={20} />
              </button>
            </div>

            <form onSubmit={handlePairing} className="p-8 space-y-6">
              <div className="p-5 bg-blue-50/80 border border-blue-100 text-blue-700 rounded-[1.5rem] text-sm flex gap-4">
                <Info size={24} className="shrink-0 text-blue-500" />
                <p className="leading-relaxed">Enter the <b>Kiosk ID</b> and <b>Pairing Code</b> displayed on your AgroVision kiosk screen.</p>
              </div>

              <div>
                <label className="block text-xs uppercase tracking-widest text-slate-400 font-bold mb-2 ml-1">Kiosk ID</label>
                <input
                  type="text"
                  value={kioskId}
                  onChange={(e) => setKioskId(e.target.value.toUpperCase())}
                  placeholder="e.g. AV-KIOSK-0001"
                  required
                  className="w-full px-4 py-4 bg-slate-50 border border-slate-200 rounded-2xl focus:ring-2 focus:ring-primary outline-none transition-all font-mono"
                />
              </div>

              <div>
                <label className="block text-xs uppercase tracking-widest text-slate-400 font-bold mb-2 ml-1">Pairing Code</label>
                <input
                  type="text"
                  value={pairingCode}
                  onChange={(e) => setPairingCode(e.target.value)}
                  placeholder="6-digit code"
                  required
                  className="w-full px-4 py-5 bg-slate-50 border border-slate-200 rounded-2xl focus:ring-2 focus:ring-primary outline-none transition-all tracking-[1em] font-mono text-center text-2xl font-bold"
                />
              </div>

              {error && (
                <div className="p-4 bg-red-50 text-red-600 rounded-xl text-sm font-bold flex gap-2 border border-red-100">
                  <ShieldAlert size={18} className="shrink-0" />
                  {error}
                </div>
              )}

              {success && (
                <div className="p-4 bg-green-50 text-green-600 rounded-xl text-sm font-bold flex gap-2 border border-green-100">
                  <Check size={18} className="shrink-0" />
                  {success}
                </div>
              )}

              <button
                type="submit"
                disabled={actionLoading}
                className="w-full bg-primary hover:bg-primary-dark text-white font-bold py-5 rounded-2xl transition-all shadow-lg shadow-primary/20 disabled:opacity-50 flex items-center justify-center gap-2"
              >
                {actionLoading ? 'Verifying...' : 'Complete Pairing'}
                {!actionLoading && <ArrowRight size={20} />}
              </button>
            </form>
          </div>
        </div>
      )}

      {loading ? (
        <div className="flex items-center justify-center h-64">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary"></div>
        </div>
      ) : kiosks.length > 0 ? (
        <div className="space-y-8">
          {kiosks.map((kiosk) => {
            const status = getStatusInfo(kiosk);
            return (
              <div key={kiosk.id} className="bg-white rounded-[2.5rem] p-8 shadow-sm border border-slate-100 relative overflow-hidden">
                <div className={`absolute top-0 left-0 w-2 h-full ${status.isOnline ? 'bg-green-500' : 'bg-slate-300'}`}></div>

                <div className="flex flex-col lg:flex-row gap-12">
                  {/* Status Column */}
                  <div className="lg:w-1/3 space-y-6">
                    <div className="flex items-center gap-4">
                      <div className={`w-14 h-14 rounded-2xl flex items-center justify-center ${status.isOnline ? 'bg-green-50 text-green-600' : 'bg-slate-50 text-slate-400'}`}>
                        <Smartphone size={28} />
                      </div>
                      <div>
                        <h3 className="font-extrabold text-2xl text-slate-800 tracking-tight">{kiosk.deviceName || 'AgroVision Kiosk'}</h3>
                        <p className="text-slate-400 font-mono text-sm uppercase">{kiosk.kioskId} • {shop?.shopName || 'Shop'}</p>
                      </div>
                    </div>

                    <div className="flex items-center gap-4">
                      <div className={`px-4 py-2 rounded-xl border flex items-center gap-2 font-bold text-xs uppercase tracking-widest ${
                        status.isOnline ? 'bg-green-50 text-green-700 border-green-100' : 'bg-slate-50 text-slate-500 border-slate-200'
                      }`}>
                        <div className={`w-2 h-2 rounded-full ${status.isOnline ? 'bg-green-500 animate-pulse' : 'bg-slate-400'}`}></div>
                        {status.label}
                      </div>
                      <div className="flex items-center gap-2 text-slate-400 text-xs font-bold uppercase tracking-widest">
                        <Clock size={14} />
                        Last Seen: {status.lastSeen}
                      </div>
                    </div>
                  </div>

                  {/* Config Column */}
                  <div className="lg:w-1/3 bg-slate-50/50 rounded-3xl p-6 border border-slate-100 grid grid-cols-2 gap-6">
                    <div>
                      <p className="text-[10px] text-slate-400 font-bold uppercase tracking-widest mb-1">App Version</p>
                      <p className="font-bold text-slate-700">{kiosk.appVersion || '1.6.0'}</p>
                    </div>
                    <div>
                      <p className="text-[10px] text-slate-400 font-bold uppercase tracking-widest mb-1">Data Version</p>
                      <p className="font-bold text-slate-700">{kiosk.configVersion ? 'v' + new Date(kiosk.configVersion.toDate()).getTime() : 'v1'}</p>
                    </div>
                    <div>
                      <p className="text-[10px] text-slate-400 font-bold uppercase tracking-widest mb-1">Last Sync</p>
                      <p className="font-bold text-slate-700">{kiosk.lastSyncAt ? new Date(kiosk.lastSyncAt.toDate()).toLocaleTimeString() : 'N/A'}</p>
                    </div>
                    <div>
                      <p className="text-[10px] text-slate-400 font-bold uppercase tracking-widest mb-1">Device</p>
                      <p className="font-bold text-slate-700 truncate">{kiosk.deviceId || 'Android'}</p>
                    </div>
                  </div>

                  {/* Actions Column */}
                  <div className="lg:w-1/3 flex flex-col justify-center gap-4">
                    <button
                      onClick={() => handleManualSync(kiosk)}
                      disabled={syncingId === kiosk.id}
                      className="w-full py-4 bg-white hover:bg-slate-50 text-slate-700 font-bold rounded-2xl border border-slate-200 shadow-sm transition-all flex items-center justify-center gap-3 disabled:opacity-50"
                    >
                      <RefreshCw size={20} className={syncingId === kiosk.id ? 'animate-spin text-primary' : 'text-slate-400'} />
                      {syncingId === kiosk.id ? 'Syncing...' : 'Sync Now'}
                    </button>
                    <div className="grid grid-cols-2 gap-3">
                      <button className="w-full py-4 bg-slate-800 hover:bg-slate-900 text-white font-bold rounded-2xl shadow-xl shadow-slate-200 transition-all flex items-center justify-center gap-2 group text-sm">
                        <Activity size={18} className="text-slate-400 group-hover:text-primary transition-colors" />
                        Health Logs
                      </button>
                      <button
                        onClick={() => handleUnlink(kiosk)}
                        disabled={actionLoading}
                        className="w-full py-4 bg-red-50 hover:bg-red-100 text-red-600 font-bold rounded-2xl border border-red-100 transition-all flex items-center justify-center gap-2 text-sm disabled:opacity-50"
                      >
                        <Trash2 size={18} />
                        Unlink
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      ) : (
        <div className="bg-white rounded-[3rem] p-16 text-center border-2 border-dashed border-slate-200">
          <div className="w-24 h-24 bg-slate-50 rounded-full flex items-center justify-center mx-auto mb-8 shadow-inner">
            <Smartphone size={48} className="text-slate-300" />
          </div>
          <h3 className="text-2xl font-extrabold text-slate-800 tracking-tight">No Kiosks Linked</h3>
          <p className="text-slate-500 mt-2 max-w-sm mx-auto font-medium">
            Link your AgroVision kiosk devices to monitor activity and update advertisements.
          </p>
          <button
            onClick={() => setIsAdding(true)}
            className="mt-10 bg-slate-800 hover:bg-slate-900 text-white px-10 py-4 rounded-2xl font-bold transition-all shadow-xl shadow-slate-200 flex items-center gap-2 mx-auto"
          >
            <Plus size={20} />
            Start Pairing Flow
          </button>
        </div>
      )}
    </Layout>
  );
};

export default Kiosks;
