import React, { useEffect, useState, useMemo } from 'react';
import { db, auth, storage } from './firebase';
import { collection, onSnapshot, query, where, orderBy, doc, getDocs, setDoc, deleteDoc, serverTimestamp } from 'firebase/firestore';
import { signOut } from 'firebase/auth';

// Shared Components
import NavButton from './components/Shared/NavButton';
import Card from './components/Shared/Card';
import { COLORS, SHADOWS } from './components/Shared/Styles';

// Feature Components
import MonitoringTable from './components/Monitoring/MonitoringTable';
import OnboardingTable from './components/Shops/OnboardingTable';
import ShopRegistrationForm from './components/Shops/ShopRegistrationForm';
import AppUpdatesView from './components/Updates/AppUpdatesView';
import MedicineTable from './components/Medicines/MedicineTable';
import MedicineForm from './components/Medicines/MedicineForm';
import BulkMedicineImporter from './components/Medicines/BulkMedicineImporter';
import IncompleteTable from './components/Medicines/IncompleteTable';
import DatabaseManager from './components/Database/DatabaseManager';
import AdvertisementManager from './components/Ads/AdvertisementManager';
import AnalyticsPage from './pages/Dashboard/AnalyticsPage';

// Firebase Storage
import { ref, listAll } from 'firebase/storage';

// Utils
import { formatTimestamp } from './utils/formatters';

const Dashboard = () => {
  const [activeView, setActiveView] = useState('monitoring');
  const [isSidebarCollapsed, setIsSidebarCollapsed] = useState(false);
  const [kiosks, setKiosks] = useState([]);
  const [shops, setShops] = useState([]);
  const [medicines, setMedicines] = useState([]);
  const [searchTerm, setSearchTerm] = useState('');
  const [scans, setScans] = useState({});
  const [stats, setStats] = useState({ total: 0, online: 0, offline: 0, totalScans: 0, totalShops: 0 });
  const [updateConfig, setUpdateConfig] = useState({ latestVersionCode: 1, latestVersionName: '1.0', apkUrl: '', forceUpdate: false });
  const [isSaving, setIsSaving] = useState(false);
  const [editingMedicine, setEditingMedicine] = useState(null);
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [isShopFormOpen, setIsShopFormOpen] = useState(false);
  const [incompleteSearchTerm, setIncompleteSearchTerm] = useState('');

  // 🚀 Storage Assets Data (Cached in Dashboard to prevent re-loading)
  const [storageFiles, setStorageFiles] = useState({ images: [], audio: [], advertisements: [] });
  const [isStorageLoading, setIsStorageLoading] = useState(false);
  const [hasLoadedStorage, setHasLoadedStorage] = useState(false);

  // 🚀 Monitoring Data
  useEffect(() => {
    const unsubscribe = onSnapshot(query(collection(db, 'kiosks')), (snapshot) => {
      const data = snapshot.docs.map(doc => ({ id: doc.id, ...doc.data() }));
      setKiosks(data);
    });
    return () => unsubscribe();
  }, []);

  // 🚀 Onboarding Data
  useEffect(() => {
    const unsubscribe = onSnapshot(query(collection(db, 'shops'), orderBy('onboardingDate', 'desc')), (snapshot) => {
      const data = snapshot.docs.map(doc => ({ id: doc.id, ...doc.data() }));
      setShops(data);
      setStats(prev => ({ ...prev, totalShops: data.length }));
    });
    return () => unsubscribe();
  }, []);

  // 🚀 Medicine Catalog Data
  useEffect(() => {
    const unsubscribe = onSnapshot(collection(db, 'approved_medicines'), (snapshot) => {
      const data = snapshot.docs.map(doc => ({ id: doc.id, ...doc.data() }));
      setMedicines(data);
    });
    return () => unsubscribe();
  }, []);

  // 🚀 Today's Summary
  useEffect(() => {
    const now = new Date();
    // India Offset +5:30
    const indiaNow = new Date(now.getTime() + (5.5 * 60 * 60 * 1000));
    const todayStr = indiaNow.toISOString().split('T')[0];
    console.log("Monitoring scans for India Local Date:", todayStr);

    const unsubscribe = onSnapshot(query(collection(db, 'daily_scans'), where('date', '==', todayStr)), (snapshot) => {
      const scanMap = {};
      let total = 0;
      snapshot.docs.forEach(doc => {
        const d = doc.data();
        if (d.shopId) {
          scanMap[d.shopId] = d.scanCount;
          total += d.scanCount;
        }
      });
      setScans(scanMap);
      setStats(prev => ({ ...prev, totalScans: total }));
    });
    return () => unsubscribe();
  }, []);

  // 🚀 App Update Config (Real-time)
  useEffect(() => {
    const docRef = doc(db, 'app_updates', 'latest');
    const unsubscribe = onSnapshot(docRef, (docSnap) => {
      if (docSnap.exists()) {
        setUpdateConfig(docSnap.data());
      }
    });
    return () => unsubscribe();
  }, []);

  // 🚀 Fetch Storage Assets (Optimized for lazy loading)
  const fetchStorageFiles = async () => {
    setIsStorageLoading(true);
    const folders = [
        { id: 'images', path: 'medicine-images' },
        { id: 'audio', path: 'medicine-audio' },
        { id: 'advertisements', path: 'advertisements' }
    ];

    try {
        const results = {};
        for (const folder of folders) {
            try {
                const storageRef = ref(storage, folder.path);
                const listResult = await listAll(storageRef);
                // Only store basic info initially, fetch URL on demand
                results[folder.id] = listResult.items.map(item => ({
                    name: item.name,
                    fullPath: item.fullPath,
                    // url: null, // Will be fetched on demand
                    // metadata: null
                }));
            } catch (e) { results[folder.id] = []; }
        }
        setStorageFiles(results);
        setHasLoadedStorage(true);
    } catch (error) {
        console.error("Error fetching storage:", error);
    }
    setIsStorageLoading(false);
  };

  useEffect(() => {
    fetchStorageFiles();
  }, []);

  const handleUpdateSave = async () => {
    setIsSaving(true);
    try {
      await setDoc(doc(db, 'app_updates', 'latest'), updateConfig);
      alert('Update configuration saved successfully!');
    } catch (error) {
      console.error("Error saving update config:", error);
      alert('Failed to save configuration.');
    }
    setIsSaving(false);
  };

  const handleSaveMedicine = async (formData) => {
    setIsSaving(true);
    try {
      const newMedicineId = formData.medicineName.trim();
      const docRef = doc(db, 'approved_medicines', newMedicineId);
      const now = serverTimestamp();

      const dataToSave = {
        name: formData.medicineName,
        company: formData.company || '',
        cibNo: formData.cibNo || '',
        chemicalName: formData.chemicalName || '',
        searchKeywords: formData.ocrKeywords.filter(k => k && k.trim() !== ''),
        barcodePrefixes: formData.barcodePrefixes.filter(p => p && p.trim() !== ''),
        crop: formData.crop || '',
        disease: formData.disease || '',
        usage: formData.usage || '',
        marathiInfo: formData.marathiInfo || '',
        imageUrls: formData.imageUrls.filter(u => u && u.trim() !== ''),
        audioUrls: formData.audioUrls || '',
        updatedAt: now,
        createdAt: editingMedicine ? (editingMedicine.createdAt || now) : now
      };

      if (editingMedicine && editingMedicine.id !== newMedicineId) {
        await deleteDoc(doc(db, 'approved_medicines', editingMedicine.id));
      }

      await setDoc(docRef, dataToSave);
      setIsFormOpen(false);
      setEditingMedicine(null);
    } catch (error) {
      console.error("Error saving medicine:", error);
      alert('Failed to save medicine: ' + error.message);
    }
    setIsSaving(false);
  };

  const handleSaveBulkMedicines = async (medicinesList) => {
    setIsSaving(true);
    try {
      const now = serverTimestamp();
      const promises = medicinesList.map(m => {
        const id = m.medicineName.trim();
        const docRef = doc(db, 'approved_medicines', id);
        const data = {
            ...m,
            name: m.medicineName,
            searchKeywords: m.ocrKeywords,
            updatedAt: now,
            createdAt: now
        };
        delete data.medicineName; // Match the storage format
        delete data.ocrKeywords; // Match the storage format
        return setDoc(docRef, data);
      });
      await Promise.all(promises);
    } catch (error) {
      console.error("Error saving bulk medicines:", error);
      throw error;
    }
    setIsSaving(false);
  };

  const handleDeleteMedicine = async (medicineId) => {
    if (window.confirm(`Are you sure you want to delete "${medicineId}"?`)) {
      try {
        await deleteDoc(doc(db, 'approved_medicines', medicineId));
      } catch (error) {
        console.error("Error deleting medicine:", error);
        alert('Failed to delete medicine.');
      }
    }
  };

  const handleDeleteShop = async (shopId) => {
    if (window.confirm(`Are you sure you want to delete this retail partner? This action cannot be undone.`)) {
      try {
        // 1. Delete Shop Document
        await deleteDoc(doc(db, 'shops', shopId));

        // 2. Also cleanup corresponding Shopkeeper record (if it exists)
        // Usually, shopId and shopkeeper mobile/id are related.
        // We'll search for shopkeeper records linked to this shopId.
        const sq = query(collection(db, 'shopkeepers'), where('shopId', '==', shopId));
        const sks = await getDocs(sq);
        for (const skDoc of sks.docs) {
            await deleteDoc(skDoc.ref);
        }

        // 3. Delete linked Kiosks (Hardware IDs)
        const kq = query(collection(db, 'kiosks'), where('shopId', '==', shopId));
        const ks = await getDocs(kq);
        for (const kDoc of ks.docs) {
            await deleteDoc(kDoc.ref);
        }

      } catch (error) {
        console.error("Error deleting shop:", error);
        alert('Failed to delete shop.');
      }
    }
  };

  const openAddForm = () => {
    setEditingMedicine(null);
    setIsFormOpen(true);
  };

  const openEditForm = (medicine) => {
    setEditingMedicine(medicine);
    setIsFormOpen(true);
  };

  const incompleteMedicines = useMemo(() => {
    const list = medicines.filter(m => {
        const hasName = m.name || m.medicineName;
        const hasMarathi = m.marathiInfo || m.warnings;
        const hasKeywords = (m.searchKeywords && m.searchKeywords.length > 0) || (m.ocrKeywords && m.ocrKeywords.length > 0);
        const hasImages = (m.imageUrls && m.imageUrls.length > 0) || (m.imageurls && m.imageurls.length > 0);
        const hasCrop = m.crop || (m.supportedCrops && m.supportedCrops.length > 0);
        const hasDisease = m.disease || (m.supportedDiseases && m.supportedDiseases.length > 0);
        const hasCompany = m.company && m.company !== 'Unknown';
        const hasAudio = m.audioUrls || m.audiourls;
        const hasChemical = m.chemicalName;

        return !hasName || !hasMarathi || !hasKeywords || !hasImages || !hasCrop || !hasDisease || !hasCompany || !hasAudio || !hasChemical;
    }).sort((a, b) => (a.name || a.medicineName || "").localeCompare(b.name || b.medicineName || ""));

    const term = incompleteSearchTerm.toLowerCase().trim();
    if (!term) return list;

    return list.filter(m => (m.name || m.medicineName || "").toLowerCase().includes(term));
  }, [medicines, incompleteSearchTerm]);

  const filteredMedicines = useMemo(() => {
    const term = searchTerm.toLowerCase().trim();
    const sortedAll = [...medicines].sort((a, b) =>
      (a.name || a.medicineName || "").toLowerCase().localeCompare((b.name || b.medicineName || "").toLowerCase())
    );

    if (!term) return sortedAll;

    return medicines.filter(m => {
      const searchData = [
        m.name, m.medicineName, m.id, m.company, m.marathiInfo, m.crop, m.disease, m.cibNo, m.chemicalName
      ].join(" ").toLowerCase();
      return searchData.includes(term);
    }).sort((a, b) => {
        const aName = (a.name || a.medicineName || "").toLowerCase();
        const bName = (b.name || b.medicineName || "").toLowerCase();
        if (aName.startsWith(term) && !bName.startsWith(term)) return -1;
        if (!aName.startsWith(term) && bName.startsWith(term)) return 1;
        return aName.localeCompare(bName);
    });
  }, [medicines, searchTerm]);

  const visibleKiosks = useMemo(() => {
    const shopIds = new Set(shops.map(s => s.id));
    return kiosks.filter(k => k.shopId && shopIds.has(k.shopId));
  }, [kiosks, shops]);

  const derivedStats = useMemo(() => {
    const now = Date.now();
    const online = visibleKiosks.filter(k => {
      const lastActive = formatTimestamp(k.lastActiveTimestamp);
      const isRecent = (now - lastActive) < 25 * 60 * 1000; // 25 minute threshold
      return isRecent;
    }).length;

    return {
      total: visibleKiosks.length,
      online: online,
      offline: visibleKiosks.length - online,
      totalScans: stats.totalScans,
      totalShops: stats.totalShops
    };
  }, [visibleKiosks, stats.totalScans, stats.totalShops]);

  const handleLogout = async () => {
    if (window.confirm('Are you sure you want to log out?')) {
      try {
        await signOut(auth);
      } catch (error) {
        console.error("Error logging out:", error);
      }
    }
  };

  const activeViewContent = useMemo(() => {
    const viewMap = {
      'monitoring': <MonitoringTable kiosks={visibleKiosks} scans={scans} formatTimestamp={formatTimestamp} />,
      'onboarding': (
        <div>
            <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: '24px' }}>
                <button
                    onClick={() => setIsShopFormOpen(true)}
                    style={{
                        padding: '12px 24px',
                        backgroundColor: COLORS.primary,
                        color: 'white',
                        border: 'none',
                        borderRadius: '12px',
                        fontWeight: '700',
                        cursor: 'pointer',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '8px',
                        boxShadow: SHADOWS.md
                    }}
                >
                    <i className="fas fa-plus"></i> Register New Partner
                </button>
            </div>
            <OnboardingTable shops={shops} formatTimestamp={formatTimestamp} onDelete={handleDeleteShop} />
        </div>
      ),
      'database': <DatabaseManager files={storageFiles} loading={isStorageLoading} onRefresh={fetchStorageFiles} />,
      'analytics': <AnalyticsPage />,
      'medicines': <MedicineTable medicines={filteredMedicines} searchTerm={searchTerm} setSearchTerm={setSearchTerm} onAdd={openAddForm} onEdit={openEditForm} onDelete={handleDeleteMedicine} onBulk={() => setActiveView('bulk-import')} />,
      'ads': <AdvertisementManager shops={shops} storageFiles={storageFiles} />,
      'bulk-import': <BulkMedicineImporter existingMedicines={medicines} onSaveAll={handleSaveBulkMedicines} onCancel={() => setActiveView('medicines')} />,
      'incomplete': <IncompleteTable medicines={incompleteMedicines} searchTerm={incompleteSearchTerm} setSearchTerm={setIncompleteSearchTerm} onEdit={openEditForm} />,
      'updates': <AppUpdatesView config={updateConfig} setConfig={setUpdateConfig} onSave={handleUpdateSave} isSaving={isSaving} />
    };
    return viewMap[activeView] || viewMap['monitoring'];
  }, [activeView, visibleKiosks, scans, shops, storageFiles, isStorageLoading, filteredMedicines, searchTerm, incompleteMedicines, incompleteSearchTerm, updateConfig, isSaving]);

  const renderActiveView = () => (
    <div key={activeView} style={{ animation: 'fadeIn 0.3s ease-out' }}>
      {activeViewContent}
    </div>
  );

  return (
    <div style={{ display: 'flex', minHeight: '100vh', backgroundColor: COLORS.background }}>
      <style>{`
        @keyframes fadeIn {
          from { opacity: 0; transform: translateY(10px); }
          to { opacity: 1; transform: translateY(0); }
        }
        .nav-button:hover {
            background-color: #f1f5f9 !important;
            color: #1a5f7a !important;
            transform: translateX(4px);
        }
        .nav-button:active {
            transform: scale(0.98);
        }
        .nav-button.active:hover {
            background-color: ${COLORS.primary} !important;
            color: white !important;
            transform: none;
        }
        ::-webkit-scrollbar { width: 8px; }
        ::-webkit-scrollbar-track { background: #f1f5f9; }
        ::-webkit-scrollbar-thumb { background: #cbd5e1; borderRadius: 4px; }
        ::-webkit-scrollbar-thumb:hover { background: #94a3b8; }
      `}</style>

      {/* Sidebar Navigation */}
      <div style={{
        width: isSidebarCollapsed ? '80px' : '280px',
        backgroundColor: COLORS.white,
        borderRight: `1px solid ${COLORS.border}`,
        padding: isSidebarCollapsed ? '32px 12px' : '32px 20px',
        display: 'flex',
        flexDirection: 'column',
        gap: '40px',
        position: 'sticky',
        top: 0,
        height: '100vh',
        transition: 'width 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
        zIndex: 100
      }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: isSidebarCollapsed ? 'center' : 'space-between', padding: '0 12px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <div style={{
                width: '42px',
                height: '42px',
                background: `linear-gradient(135deg, ${COLORS.primary}, ${COLORS.primaryLight})`,
                borderRadius: '12px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: 'white',
                boxShadow: '0 4px 12px rgba(30, 58, 138, 0.2)',
                minWidth: '42px'
            }}>
              <i className="fas fa-seedling" style={{ fontSize: '20px' }}></i>
            </div>
            {!isSidebarCollapsed && <h1 style={{ color: COLORS.primary, margin: 0, fontSize: '22px', fontWeight: '800', letterSpacing: '-0.025em' }}>AgroVision</h1>}
          </div>
          {!isSidebarCollapsed && (
            <button
              onClick={() => setIsSidebarCollapsed(true)}
              style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: '14px', color: COLORS.textMuted, padding: '8px' }}
            >
              <i className="fas fa-chevron-left"></i>
            </button>
          )}
        </div>

        {isSidebarCollapsed && (
            <button
                onClick={() => setIsSidebarCollapsed(false)}
                style={{
                    position: 'absolute',
                    right: '-15px',
                    top: '40px',
                    width: '30px',
                    height: '30px',
                    borderRadius: '50%',
                    backgroundColor: COLORS.white,
                    border: `1px solid ${COLORS.border}`,
                    cursor: 'pointer',
                    boxShadow: SHADOWS.md,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: '10px',
                    color: COLORS.primary,
                    zIndex: 10
                }}
            >
                <i className="fas fa-chevron-right"></i>
            </button>
        )}

        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          {!isSidebarCollapsed && <p style={{ fontSize: '12px', fontWeight: '700', color: COLORS.textMuted, padding: '0 12px', marginBottom: '8px', textTransform: 'uppercase', letterSpacing: '1px' }}>Menu</p>}
          <NavButton active={activeView === 'monitoring'} onClick={() => setActiveView('monitoring')} label="Monitoring" icon="fa-chart-line" isCollapsed={isSidebarCollapsed} />
          <NavButton active={activeView === 'analytics'} onClick={() => setActiveView('analytics')} label="Analytics" icon="fa-chart-pie" isCollapsed={isSidebarCollapsed} />
          <NavButton active={activeView === 'onboarding'} onClick={() => setActiveView('onboarding')} label="Retail Partners" icon="fa-store" isCollapsed={isSidebarCollapsed} />
          <NavButton active={activeView === 'medicines'} onClick={() => setActiveView('medicines')} label="Medicine Catalog" icon="fa-pills" isCollapsed={isSidebarCollapsed} />
          <NavButton active={activeView === 'ads'} onClick={() => setActiveView('ads')} label="Ads & Promotions" icon="fa-tv" isCollapsed={isSidebarCollapsed} />
          <NavButton active={activeView === 'database'} onClick={() => setActiveView('database')} label="Database" icon="fa-database" isCollapsed={isSidebarCollapsed} />
          <NavButton active={activeView === 'incomplete'} onClick={() => setActiveView('incomplete')} label="Data Health" icon="fa-stethoscope" isCollapsed={isSidebarCollapsed} />
          <NavButton active={activeView === 'updates'} onClick={() => setActiveView('updates')} label="App Releases" icon="fa-rocket" isCollapsed={isSidebarCollapsed} />
          <div style={{ marginTop: 'auto', borderTop: `1px solid ${COLORS.border}`, paddingTop: '16px' }}>
            <NavButton onClick={handleLogout} label="Logout" icon="fa-sign-out-alt" isCollapsed={isSidebarCollapsed} style={{ color: COLORS.danger }} />
          </div>
        </div>

        {!isSidebarCollapsed && (
            <div style={{ marginTop: 'auto', padding: '20px', backgroundColor: '#f8fafc', borderRadius: '16px', border: `1px solid ${COLORS.border}` }}>
                <p style={{ margin: 0, fontSize: '13px', color: COLORS.textMain, fontWeight: '600' }}>Admin Dashboard</p>
                <p style={{ margin: '4px 0 0 0', fontSize: '11px', color: COLORS.textMuted }}>v2.4.0 Stable</p>
            </div>
        )}
      </div>

      {/* Main Content Area */}
      <div style={{ flex: 1, padding: '40px 48px', overflowY: 'auto' }}>
        <header style={{ marginBottom: '40px', display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end' }}>
          <div>
            <h2 style={{ margin: 0, fontSize: '28px', fontWeight: '800', color: COLORS.textMain, letterSpacing: '-0.5px' }}>
              {activeView === 'monitoring' && 'Kiosk Monitoring'}
              {activeView === 'analytics' && 'Analytics & Trends'}
              {activeView === 'onboarding' && 'Retail Partners'}
              {activeView === 'medicines' && 'Medicine Catalog'}
              {activeView === 'bulk-import' && 'Bulk Medicine Import'}
              {activeView === 'database' && 'Storage Assets Manager'}
              {activeView === 'ads' && 'Ad Campaign Manager'}
              {activeView === 'incomplete' && 'Data Health Check'}
              {activeView === 'updates' && 'Application Releases'}
            </h2>
            <p style={{ margin: '8px 0 0 0', color: COLORS.textMuted, fontSize: '15px' }}>
              Welcome back! Here's what's happening across your network today.
            </p>
          </div>
          <div style={{ color: COLORS.textMuted, fontSize: '14px', fontWeight: '500' }}>
            📅 {new Date().toLocaleDateString('en-US', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' })}
          </div>
        </header>

        {/* Global Stats */}
        <div style={{ display: 'flex', gap: '24px', marginBottom: '40px' }}>
          <Card title="Online Status" value={`${derivedStats.online} / ${derivedStats.total}`} color={COLORS.secondary} icon="fa-signal" />
          <Card title="Total Partners" value={derivedStats.totalShops} color={COLORS.primary} icon="fa-building" />
          <Card title="Catalog Size" value={medicines.length} color={COLORS.accent} icon="fa-box-open" />
          <Card title="Health Issues" value={incompleteMedicines.length} color={COLORS.danger} icon="fa-triangle-exclamation" />
        </div>

        {/* Active View Rendering */}
        <main style={{
          backgroundColor: COLORS.white,
          borderRadius: '24px',
          boxShadow: SHADOWS.lg,
          border: `1px solid ${COLORS.border}`,
          padding: '32px',
          minHeight: '400px',
          position: 'relative',
          overflow: 'hidden'
        }}>
          {renderActiveView()}
        </main>

        {isFormOpen && (
          <div style={{
            position: 'fixed',
            top: 0,
            left: 0,
            width: '100%',
            height: '100%',
            backgroundColor: 'rgba(15, 23, 42, 0.8)',
            zIndex: 2000,
            display: 'flex',
            justifyContent: 'center',
            alignItems: 'center',
            padding: '20px',
            backdropFilter: 'blur(8px)'
          }}>
              <MedicineForm
                  medicine={editingMedicine}
                  medicines={medicines}
                  storageFiles={storageFiles}
                  onSave={handleSaveMedicine}
                  onCancel={() => {
                      setIsFormOpen(false);
                      setEditingMedicine(null);
                  }}
              />
          </div>
        )}

        {isShopFormOpen && (
          <div style={{
            position: 'fixed',
            top: 0,
            left: 0,
            width: '100%',
            height: '100%',
            backgroundColor: 'rgba(15, 23, 42, 0.8)',
            zIndex: 2000,
            display: 'flex',
            justifyContent: 'center',
            alignItems: 'center',
            padding: '20px',
            backdropFilter: 'blur(8px)'
          }}>
              <ShopRegistrationForm
                  onSuccess={() => {
                      setIsShopFormOpen(false);
                      alert('Retail Partner registered successfully!');
                  }}
                  onCancel={() => setIsShopFormOpen(false)}
              />
          </div>
        )}
      </div>
    </div>
  );
};

export default Dashboard;
