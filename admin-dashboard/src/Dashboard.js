import React, { useEffect, useState, useMemo } from 'react';
import { db } from './firebase';
import { collection, onSnapshot, query, where, orderBy, doc, getDoc, setDoc, deleteDoc, serverTimestamp } from 'firebase/firestore';

// Shared Components
import NavButton from './components/Shared/NavButton';
import Card from './components/Shared/Card';
import { COLORS, SHADOWS } from './components/Shared/Styles';

// Feature Components
import MonitoringTable from './components/Monitoring/MonitoringTable';
import OnboardingTable from './components/Shops/OnboardingTable';
import AppUpdatesView from './components/Updates/AppUpdatesView';
import MedicineTable from './components/Medicines/MedicineTable';
import MedicineForm from './components/Medicines/MedicineForm';
import BulkMedicineImporter from './components/Medicines/BulkMedicineImporter';
import IncompleteTable from './components/Medicines/IncompleteTable';
import AiMedicineWizard from './components/Medicines/AiMedicineWizard';
import DatabaseManager from './components/Database/DatabaseManager';

// Firebase Storage
import { storage } from './firebase';
import { ref, listAll, getDownloadURL, getMetadata } from 'firebase/storage';

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
    const localISOTime = new Date().toISOString().split('T')[0];
    const unsubscribe = onSnapshot(query(collection(db, 'daily_scans'), where('date', '==', localISOTime)), (snapshot) => {
      const scanMap = {};
      let total = 0;
      snapshot.docs.forEach(doc => {
        const d = doc.data();
        scanMap[d.shopId] = d.scanCount;
        total += d.scanCount;
      });
      setScans(scanMap);
      setStats(prev => ({ ...prev, totalScans: total }));
    });
    return () => unsubscribe();
  }, []);

  // 🚀 App Update Config
  useEffect(() => {
    const fetchUpdateConfig = async () => {
      const docRef = doc(db, 'app_updates', 'latest');
      const docSnap = await getDoc(docRef);
      if (docSnap.exists()) {
        setUpdateConfig(docSnap.data());
      }
    };
    fetchUpdateConfig();
  }, []);

  // 🚀 Fetch Storage Assets Once
  useEffect(() => {
    const viewsThatNeedStorage = ['database', 'medicines', 'ai-wizard', 'incomplete'];
    if (viewsThatNeedStorage.includes(activeView) && !hasLoadedStorage) {
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
                    const filePromises = listResult.items.map(async (item) => {
                        try {
                            const url = await getDownloadURL(item);
                            const metadata = await getMetadata(item);
                            return {
                                name: item.name,
                                fullPath: item.fullPath,
                                url,
                                size: metadata.size,
                                timeCreated: metadata.timeCreated,
                                contentType: metadata.contentType
                            };
                        } catch (e) { return null; }
                    });
                    const resolvedFiles = await Promise.all(filePromises);
                    results[folder.id] = resolvedFiles.filter(f => f !== null);
                } catch (e) { results[folder.id] = []; }
            }
            setStorageFiles(results);
            setHasLoadedStorage(true);
        } catch (error) {
            console.error("Error fetching storage:", error);
        }
        setIsStorageLoading(false);
      };
      fetchStorageFiles();
    }
  }, [activeView, hasLoadedStorage]);

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
        await deleteDoc(doc(db, 'shops', shopId));
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

        return !hasName || !hasMarathi || !hasKeywords || !hasImages || !hasCrop || !hasDisease || !hasCompany || !hasAudio;
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
        m.name, m.medicineName, m.id, m.company, m.marathiInfo, m.crop, m.disease
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
    return kiosks.filter(k => shopIds.has(k.id));
  }, [kiosks, shops]);

  const derivedStats = useMemo(() => {
    const now = Date.now();
    const online = visibleKiosks.filter(k => {
      const lastActive = formatTimestamp(k.lastActiveTimestamp);
      const isRecent = (now - lastActive) < 90 * 1000;
      return isRecent && k.status === 'ONLINE';
    }).length;

    return {
      total: visibleKiosks.length,
      online: online,
      offline: visibleKiosks.length - online,
      totalScans: stats.totalScans,
      totalShops: stats.totalShops
    };
  }, [visibleKiosks, stats.totalScans, stats.totalShops]);

  const activeViewContent = useMemo(() => {
    const viewMap = {
      'monitoring': <MonitoringTable kiosks={visibleKiosks} scans={scans} formatTimestamp={formatTimestamp} />,
      'onboarding': <OnboardingTable shops={shops} formatTimestamp={formatTimestamp} onDelete={handleDeleteShop} />,
      'database': <DatabaseManager files={storageFiles} loading={isStorageLoading} />,
      'medicines': <MedicineTable medicines={filteredMedicines} searchTerm={searchTerm} setSearchTerm={setSearchTerm} onAdd={openAddForm} onEdit={openEditForm} onDelete={handleDeleteMedicine} onBulk={() => setActiveView('bulk-import')} />,
      'ai-wizard': <AiMedicineWizard />,
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
                width: '40px',
                height: '40px',
                backgroundColor: COLORS.primary,
                borderRadius: '10px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: 'white',
                fontWeight: 'bold',
                fontSize: '20px',
                minWidth: '40px'
            }}>A</div>
            {!isSidebarCollapsed && <h1 style={{ color: COLORS.primary, margin: 0, fontSize: '20px', fontWeight: '800', letterSpacing: '-0.5px' }}>AgroVision</h1>}
          </div>
          {!isSidebarCollapsed && (
            <button
              onClick={() => setIsSidebarCollapsed(true)}
              style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: '18px', color: COLORS.textMuted }}
            >
              ◀
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
                    boxShadow: SHADOWS.sm,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: '12px',
                    color: COLORS.primary
                }}
            >
                ▶
            </button>
        )}

        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          {!isSidebarCollapsed && <p style={{ fontSize: '12px', fontWeight: '700', color: COLORS.textMuted, padding: '0 12px', marginBottom: '8px', textTransform: 'uppercase', letterSpacing: '1px' }}>Menu</p>}
          <NavButton active={activeView === 'monitoring'} onClick={() => setActiveView('monitoring')} label="Monitoring" icon="📊" isCollapsed={isSidebarCollapsed} />
          <NavButton active={activeView === 'onboarding'} onClick={() => setActiveView('onboarding')} label="Retail Partners" icon="🏪" isCollapsed={isSidebarCollapsed} />
          <NavButton active={activeView === 'medicines'} onClick={() => setActiveView('medicines')} label="Medicine Catalog" icon="🌿" isCollapsed={isSidebarCollapsed} />
          <NavButton active={activeView === 'ai-wizard'} onClick={() => setActiveView('ai-wizard')} label="AI Wizard" icon="✨" isCollapsed={isSidebarCollapsed} />
          <NavButton active={activeView === 'database'} onClick={() => setActiveView('database')} label="Database" icon="🗄️" isCollapsed={isSidebarCollapsed} />
          <NavButton active={activeView === 'incomplete'} onClick={() => setActiveView('incomplete')} label="Data Health" icon="🩺" isCollapsed={isSidebarCollapsed} />
          <NavButton active={activeView === 'updates'} onClick={() => setActiveView('updates')} label="App Releases" icon="🚀" isCollapsed={isSidebarCollapsed} />
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
              {activeView === 'onboarding' && 'Retail Partners'}
              {activeView === 'medicines' && 'Medicine Catalog'}
              {activeView === 'bulk-import' && 'Bulk Medicine Import'}
              {activeView === 'database' && 'Storage Assets Manager'}
              {activeView === 'ai-wizard' && 'AI Medicine Assistant'}
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
          <Card title="Online Status" value={`${derivedStats.online} / ${derivedStats.total}`} color={COLORS.secondary} icon="📶" />
          <Card title="Total Partners" value={derivedStats.totalShops} color={COLORS.primary} icon="🏢" />
          <Card title="Catalog Size" value={medicines.length} color={COLORS.accent} icon="📦" />
          <Card title="Health Issues" value={incompleteMedicines.length} color={COLORS.danger} icon="🚨" />
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
                  storageFiles={storageFiles}
                  onSave={handleSaveMedicine}
                  onCancel={() => {
                      setIsFormOpen(false);
                      setEditingMedicine(null);
                  }}
              />
          </div>
        )}
      </div>
    </div>
  );
};

export default Dashboard;
