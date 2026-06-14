import React, { useEffect, useState, useMemo } from 'react';
import { db } from './firebase';
import { collection, onSnapshot, query, where, orderBy, limit, doc, getDoc, setDoc, deleteDoc, serverTimestamp } from 'firebase/firestore';
import * as XLSX from 'xlsx';

const Dashboard = () => {
  const [activeView, setActiveView] = useState('monitoring');
  const [kiosks, setKiosks] = useState([]);
  const [shops, setShops] = useState([]);
  const [history, setHistory] = useState([]);
  const [medicines, setMedicines] = useState([]);
  const [searchTerm, setSearchTerm] = useState('');
  const [scans, setScans] = useState({});
  const [stats, setStats] = useState({ total: 0, online: 0, offline: 0, totalScans: 0, totalShops: 0 });
  const [updateConfig, setUpdateConfig] = useState({ latestVersionCode: 1, latestVersionName: '1.0', apkUrl: '', forceUpdate: false });
  const [isSaving, setIsSaving] = useState(false);
  const [editingMedicine, setEditingMedicine] = useState(null);
  const [isFormOpen, setIsFormOpen] = useState(false);

  const formatTimestamp = (ts) => {
    if (!ts) return 0;
    if (ts.toMillis) return ts.toMillis();
    return ts;
  };

  // 🚀 Monitoring Data
  useEffect(() => {
    const unsubscribe = onSnapshot(query(collection(db, 'kiosks')), (snapshot) => {
      const data = snapshot.docs.map(doc => ({ id: doc.id, ...doc.data() }));
      setKiosks(data);
      const now = Date.now();
      const online = data.filter(k => {
        const lastActive = formatTimestamp(k.lastActiveTimestamp);
        const isRecent = (now - lastActive) < 90 * 1000; // 90 seconds window
        return isRecent && k.status === 'ONLINE';
      }).length;
      setStats(prev => ({ ...prev, total: data.length, online: online, offline: data.length - online }));
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

  // 🚀 History Data (Last 500 scans)
  useEffect(() => {
    const q = query(collection(db, 'successful_scans'), orderBy('timestamp', 'desc'), limit(500));
    const unsubscribe = onSnapshot(q, (snapshot) => {
      setHistory(snapshot.docs.map(doc => ({ id: doc.id, ...doc.data() })));
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
        ...formData,
        updatedAt: now,
        createdAt: editingMedicine ? (editingMedicine.createdAt || now) : now
      };

      // If renaming medicine, delete the old document ID to prevent duplicates
      if (editingMedicine && editingMedicine.id !== newMedicineId) {
        await deleteDoc(doc(db, 'approved_medicines', editingMedicine.id));
      }

      await setDoc(docRef, dataToSave);
      alert('Medicine saved successfully!');
      setIsFormOpen(false);
      setEditingMedicine(null);
    } catch (error) {
      console.error("Error saving medicine:", error);
      alert('Failed to save medicine.');
    }
    setIsSaving(false);
  };

  const handleDeleteMedicine = async (medicineId) => {
    if (window.confirm(`Are you sure you want to delete "${medicineId}"?`)) {
      try {
        await deleteDoc(doc(db, 'approved_medicines', medicineId));
        alert('Medicine deleted successfully.');
      } catch (error) {
        console.error("Error deleting medicine:", error);
        alert('Failed to delete medicine.');
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

  const exportToExcel = () => {
    const worksheet = XLSX.utils.json_to_sheet(history.map(item => ({
      'Shop Name': item.shopName,
      'Shop ID': item.shopId,
      'Medicine Name': item.medicineName,
      'Medicine ID': item.medicineId,
      'Date': item.date,
      'Time': item.timestamp ? new Date(formatTimestamp(item.timestamp)).toLocaleString() : 'N/A'
    })));
    const workbook = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(workbook, worksheet, "Medicine Scans");
    XLSX.writeFile(workbook, `AgroVision_Scan_History_${new Date().toLocaleDateString()}.xlsx`);
  };

  const filteredMedicines = useMemo(() => {
    const term = searchTerm.toLowerCase().trim();

    // Sort by name by default
    const sortedAll = [...medicines].sort((a, b) => (a.medicineName || "").localeCompare(b.medicineName || ""));
    if (!term) return sortedAll;

    const matches = medicines.filter(m => {
      const searchData = [
        m.medicineName,
        m.id,
        m.company,
        m.marathiInfo,
        Array.isArray(m.crop) ? m.crop.join(' ') : m.crop,
        Array.isArray(m.disease) ? m.disease.join(' ') : m.disease,
        Array.isArray(m.ocrKeywords) ? m.ocrKeywords.join(' ') : m.ocrKeywords
      ].map(field => (field || "").toLowerCase()).join(" ");

      return searchData.includes(term);
    });

    // 🚀 SMART RELEVANCE SORTING
    return matches.sort((a, b) => {
        const aName = (a.medicineName || "").toLowerCase();
        const bName = (b.medicineName || "").toLowerCase();

        // 1. Exact name match gets top priority
        if (aName === term) return -1;
        if (bName === term) return 1;

        // 2. Name starting with term gets second priority
        const aStarts = aName.startsWith(term);
        const bStarts = bName.startsWith(term);
        if (aStarts && !bStarts) return -1;
        if (!aStarts && bStarts) return 1;

        // 3. Fallback to alphabetical
        return aName.localeCompare(bName);
    });
  }, [medicines, searchTerm]);

  return (
    <div style={{ padding: '20px', fontFamily: 'Arial, sans-serif', backgroundColor: '#f4f7f6', minHeight: '100vh' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
        <h1 style={{ color: '#1a5f7a', margin: 0 }}>AgroVision Command Center</h1>
        <div style={{ display: 'flex', gap: '10px' }}>
          <NavButton active={activeView === 'monitoring'} onClick={() => setActiveView('monitoring')} label="Monitoring" />
          <NavButton active={activeView === 'onboarding'} onClick={() => setActiveView('onboarding')} label="Shops" />
          <NavButton active={activeView === 'medicines'} onClick={() => setActiveView('medicines')} label="Medicine Catalog" />
          <NavButton active={activeView === 'history'} onClick={() => setActiveView('history')} label="Medicine History" />
          <NavButton active={activeView === 'updates'} onClick={() => setActiveView('updates')} label="App Updates" />
        </div>
      </div>

      <div style={{ display: 'flex', gap: '20px', marginBottom: '30px', flexWrap: 'wrap' }}>
        <Card title="Online Kiosks" value={stats.online} color="#27ae60" />
        <Card title="Medicines in DB" value={medicines.length} color="#e67e22" />
        <Card title="Total Shops" value={stats.totalShops} color="#8e44ad" />
      </div>

      {activeView === 'monitoring' && <MonitoringTable kiosks={kiosks} scans={scans} formatTimestamp={formatTimestamp} />}
      {activeView === 'onboarding' && <OnboardingTable shops={shops} formatTimestamp={formatTimestamp} />}
      {activeView === 'medicines' && (
        isFormOpen ? (
          <MedicineForm
            medicine={editingMedicine}
            onSave={handleSaveMedicine}
            onCancel={() => {
              setIsFormOpen(false);
              setEditingMedicine(null);
            }}
          />
        ) : (
          <MedicineTable
            medicines={filteredMedicines}
            searchTerm={searchTerm}
            setSearchTerm={setSearchTerm}
            onAdd={openAddForm}
            onEdit={openEditForm}
            onDelete={handleDeleteMedicine}
          />
        )
      )}
      {activeView === 'history' && <HistoryTable history={history} onExport={exportToExcel} formatTimestamp={formatTimestamp} />}
      {activeView === 'updates' && (
        <AppUpdatesView
          config={updateConfig}
          setConfig={setUpdateConfig}
          onSave={handleUpdateSave}
          isSaving={isSaving}
        />
      )}
    </div>
  );
};

const AppUpdatesView = ({ config, setConfig, onSave, isSaving }) => (
  <div style={{ backgroundColor: 'white', padding: '30px', borderRadius: '12px', boxShadow: '0 4px 6px rgba(0,0,0,0.05)', maxWidth: '600px' }}>
    <h2 style={{ marginTop: 0, color: '#1a5f7a' }}>🚀 Manage App Updates</h2>
    <p style={{ color: '#666', marginBottom: '25px' }}>This configuration controls the OTA (Over-The-Air) update message shown to all kiosks.</p>

    <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
      <div>
        <label style={labelStyle}>Latest Version Code</label>
        <input
          type="number"
          style={inputStyle}
          value={config.latestVersionCode}
          onChange={(e) => setConfig({ ...config, latestVersionCode: parseInt(e.target.value) || 0 })}
          placeholder="e.g. 2"
        />
        <small style={{ color: '#7f8c8d' }}>Must be higher than the current app's version code (1) to trigger update.</small>
      </div>

      <div>
        <label style={labelStyle}>Latest Version Name</label>
        <input
          type="text"
          style={inputStyle}
          value={config.latestVersionName}
          onChange={(e) => setConfig({ ...config, latestVersionName: e.target.value })}
          placeholder="e.g. 1.1-stable"
        />
      </div>

      <div>
        <label style={labelStyle}>APK Download URL</label>
        <input
          type="text"
          style={inputStyle}
          value={config.apkUrl}
          onChange={(e) => setConfig({ ...config, apkUrl: e.target.value })}
          placeholder="https://firebasestorage.googleapis.com/..."
        />
        <small style={{ color: '#e74c3c' }}>Security Note: URL must contain 'firebasestorage' or 'agrovision'.</small>
      </div>

      <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
        <input
          type="checkbox"
          id="forceUpdate"
          checked={config.forceUpdate}
          onChange={(e) => setConfig({ ...config, forceUpdate: e.target.checked })}
        />
        <label htmlFor="forceUpdate" style={{ fontWeight: 'bold', cursor: 'pointer' }}>Force Update (User cannot skip)</label>
      </div>

      <button
        onClick={onSave}
        disabled={isSaving}
        style={{
          ...exportBtnStyle,
          backgroundColor: isSaving ? '#95a5a6' : '#1a5f7a',
          padding: '15px',
          fontSize: '16px',
          marginTop: '10px'
        }}
      >
        {isSaving ? 'Saving Configuration...' : '💾 Save & Push Update to Kiosks'}
      </button>
    </div>
  </div>
);

const labelStyle = { display: 'block', marginBottom: '5px', fontWeight: 'bold', color: '#2c3e50' };
const inputStyle = { width: '100%', padding: '10px', borderRadius: '8px', border: '1px solid #ddd', boxSizing: 'border-box' };

const MedicineForm = ({ medicine, onSave, onCancel }) => {
  const padArray = (arr, minLength) => {
    const result = Array.isArray(arr) ? [...arr] : [];
    while (result.length < minLength) result.push('');
    return result;
  };

  const [formData, setFormData] = useState(medicine ? {
    ...medicine,
    ocrKeywords: padArray(medicine.ocrKeywords, 3),
    imageUrls: padArray(medicine.imageUrls, 4)
  } : {
    medicineName: '',
    company: '',
    crop: '',
    disease: '',
    usage: '',
    marathiInfo: '',
    ocrKeywords: ['', '', ''],
    imageUrls: ['', '', '', ''],
    audioUrls: ''
  });

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData({ ...formData, [name]: value });
  };

  const handleArrayChange = (index, value, field) => {
    const newArray = [...formData[field]];
    newArray[index] = value;
    setFormData({ ...formData, [field]: newArray });
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!formData.medicineName.trim()) {
      alert("Medicine Name is required!");
      return;
    }
    if (!formData.marathiInfo.trim()) {
      alert("Marathi Information is required!");
      return;
    }
    const filteredKeywords = formData.ocrKeywords.filter(k => k && k.trim() !== '');
    if (filteredKeywords.length === 0) {
        alert("At least one OCR keyword is required!");
        return;
    }
    const filteredImages = formData.imageUrls.filter(u => u && u.trim() !== '');
    if (filteredImages.length === 0) {
        alert("At least one Image URL is required!");
        return;
    }

    const finalData = {
        ...formData,
        ocrKeywords: filteredKeywords,
        imageUrls: filteredImages
    };
    onSave(finalData);
  };

  return (
    <div style={{ backgroundColor: 'white', padding: '30px', borderRadius: '12px', boxShadow: '0 4px 6px rgba(0,0,0,0.05)', maxWidth: '800px', margin: '0 auto' }}>
      <h2 style={{ marginTop: 0, color: '#1a5f7a' }}>{medicine ? 'Edit Medicine' : 'Add New Medicine'}</h2>
      <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px' }}>
            <div>
                <label style={labelStyle}>Medicine Name (ID)</label>
                <input name="medicineName" value={formData.medicineName} onChange={handleChange} style={inputStyle} required disabled={!!medicine} />
            </div>
            <div>
                <label style={labelStyle}>Company</label>
                <input name="company" value={formData.company} onChange={handleChange} style={inputStyle} />
            </div>
            <div>
                <label style={labelStyle}>Crop</label>
                <input name="crop" value={formData.crop} onChange={handleChange} style={inputStyle} placeholder="e.g. Cotton, Soyabean" />
            </div>
            <div>
                <label style={labelStyle}>Disease</label>
                <input name="disease" value={formData.disease} onChange={handleChange} style={inputStyle} placeholder="e.g. Aphids, Wilt" />
            </div>
        </div>

        <div>
            <label style={labelStyle}>Usage Instructions</label>
            <textarea name="usage" value={formData.usage} onChange={handleChange} style={{...inputStyle, height: '80px'}} />
        </div>

        <div>
            <label style={labelStyle}>Marathi Information</label>
            <textarea name="marathiInfo" value={formData.marathiInfo} onChange={handleChange} style={{...inputStyle, height: '80px'}} required />
        </div>

        <div>
            <label style={labelStyle}>OCR Keywords (Min 3 suggested)</label>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '10px' }}>
                {formData.ocrKeywords.map((kw, i) => (
                    <input key={i} value={kw} onChange={(e) => handleArrayChange(i, e.target.value, 'ocrKeywords')} style={inputStyle} placeholder={`Keyword ${i+1}`} />
                ))}
            </div>
        </div>

        <div>
            <label style={labelStyle}>Image URLs (Min 4 suggested)</label>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
                {formData.imageUrls.map((url, i) => (
                    <input key={i} value={url} onChange={(e) => handleArrayChange(i, e.target.value, 'imageUrls')} style={inputStyle} placeholder={`Image URL ${i+1}`} />
                ))}
            </div>
        </div>

        <div>
            <label style={labelStyle}>Audio URL</label>
            <input name="audioUrls" value={formData.audioUrls} onChange={handleChange} style={inputStyle} placeholder="https://..." />
        </div>

        <div style={{ display: 'flex', gap: '10px', justifyContent: 'flex-end', marginTop: '20px' }}>
            <button type="button" onClick={onCancel} style={{ ...exportBtnStyle, backgroundColor: '#95a5a6' }}>Cancel</button>
            <button type="submit" style={exportBtnStyle}>Save Medicine</button>
        </div>
      </form>
    </div>
  );
};

const MedicineTable = ({ medicines, searchTerm, setSearchTerm, onAdd, onEdit, onDelete }) => (
  <div style={{ backgroundColor: 'white', padding: '20px', borderRadius: '12px', boxShadow: '0 4px 6px rgba(0,0,0,0.05)' }}>
    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '20px', alignItems: 'center', flexWrap: 'wrap', gap: '10px' }}>
      <h2 style={{ margin: 0 }}>Approved Medicine Catalog</h2>
      <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
          <input
            type="text"
            placeholder="Search by Name, Company or Keyword..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            style={{ padding: '10px', borderRadius: '8px', border: '1px solid #ddd', width: '300px' }}
          />
          <button onClick={onAdd} style={{...exportBtnStyle, backgroundColor: '#1a5f7a'}}>+ Add New Medicine</button>
      </div>
    </div>
    <div style={{ overflowX: 'auto' }}>
      <TableLayout headers={['Medicine Name', 'Company', 'Crops', 'Diseases', 'Last Updated', 'Actions']}>
        {medicines.map(m => (
          <tr key={m.id} style={trStyle}>
            <td style={tdBoldStyle}>
                <div>{m.medicineName || m.id}</div>
                <small style={{color: '#7f8c8d', fontWeight: 'normal'}}>{m.id}</small>
            </td>
            <td style={tdStyle}>{m.company || '—'}</td>
            <td style={tdStyle}>
                <div style={{maxWidth: '200px', fontSize: '12px'}}>{Array.isArray(m.crop) ? m.crop.join(', ') : m.crop || '—'}</div>
            </td>
            <td style={tdStyle}>
                <div style={{maxWidth: '200px', fontSize: '12px'}}>{Array.isArray(m.disease) ? m.disease.join(', ') : m.disease || '—'}</div>
            </td>
            <td style={tdStyle}>
                <div style={{fontSize: '11px', color: '#7f8c8d'}}>
                    {m.updatedAt ? new Date(m.updatedAt.toMillis ? m.updatedAt.toMillis() : m.updatedAt).toLocaleDateString() : '—'}
                </div>
            </td>
            <td style={tdStyle}>
                <div style={{ display: 'flex', gap: '5px' }}>
                    <button onClick={() => onEdit(m)} style={{ padding: '6px 12px', backgroundColor: '#3498db', color: 'white', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: 'bold' }}>Edit</button>
                    <button onClick={() => onDelete(m.id)} style={{ padding: '6px 12px', backgroundColor: '#e74c3c', color: 'white', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: 'bold' }}>Delete</button>
                </div>
            </td>
          </tr>
        ))}
      </TableLayout>
    </div>
  </div>
);

const MonitoringTable = ({ kiosks, scans, formatTimestamp }) => (
  <TableLayout headers={['Kiosk ID', 'Shop Name', 'Today\'s Scans', 'Last Active', 'Version', 'Status']}>
    {kiosks.map(k => {
      const lastActiveMillis = formatTimestamp(k.lastActiveTimestamp);
      const isOnline = lastActiveMillis > 0 && (Date.now() - lastActiveMillis) < 90 * 1000 && k.status === 'ONLINE';
      return (
        <tr key={k.id} style={trStyle}>
          <td style={tdStyle}>{k.id}</td>
          <td style={tdBoldStyle}>{k.shopName}</td>
          <td style={{ ...tdStyle, color: '#27ae60', fontWeight: 'bold' }}>{scans[k.id] || 0}</td>
          <td style={tdStyle}>{lastActiveMillis > 0 ? new Date(lastActiveMillis).toLocaleString() : "Never"}</td>
          <td style={tdStyle}><span style={{...badgeStyle, backgroundColor: '#f1f2f6', color: '#2f3542'}}>{k.appVersion || '1.0'}</span></td>
          <td style={{ ...tdStyle, color: isOnline ? 'green' : 'red', fontWeight: 'bold' }}>{isOnline ? '● ONLINE' : '○ OFFLINE'}</td>
        </tr>
      );
    })}
  </TableLayout>
);

const OnboardingTable = ({ shops, formatTimestamp }) => (
  <TableLayout headers={['Phone Number', 'Owner', 'Shop Name', 'Registered Date', 'Status']}>
    {shops.map(s => (
      <tr key={s.id} style={trStyle}>
        <td style={tdStyle}>{s.phoneNumber}</td>
        <td style={tdBoldStyle}>{s.ownerName}</td>
        <td style={tdStyle}>{s.shopName}</td>
        <td style={tdStyle}>{s.onboardingDate ? new Date(formatTimestamp(s.onboardingDate)).toLocaleString() : 'N/A'}</td>
        <td style={tdStyle}><span style={badgeStyle}>{s.status}</span></td>
      </tr>
    ))}
  </TableLayout>
);

const HistoryTable = ({ history, onExport, formatTimestamp }) => (
  <div style={{ backgroundColor: 'white', padding: '20px', borderRadius: '12px', boxShadow: '0 4px 6px rgba(0,0,0,0.05)' }}>
    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '20px' }}>
      <h2 style={{ margin: 0 }}>Detailed Medicine Scan Logs</h2>
      <button onClick={onExport} style={exportBtnStyle}>📊 Download Excel Report</button>
    </div>
    <TableLayout headers={['Date/Time', 'Shop Name', 'Medicine Scanned', 'Medicine ID']}>
      {history.map(item => (
        <tr key={item.id} style={trStyle}>
          <td style={tdStyle}>{item.timestamp ? new Date(formatTimestamp(item.timestamp)).toLocaleString() : 'N/A'}</td>
          <td style={tdBoldStyle}>{item.shopName}</td>
          <td style={{ ...tdStyle, color: '#2980b9', fontWeight: 'bold' }}>{item.medicineName}</td>
          <td style={tdStyle}>{item.medicineId}</td>
        </tr>
      ))}
    </TableLayout>
  </div>
);

const TableLayout = ({ headers, children }) => (
  <table style={{ width: '100%', borderCollapse: 'collapse', backgroundColor: 'white', borderRadius: '12px', overflow: 'hidden' }}>
    <thead style={{ backgroundColor: '#f8f9fa' }}>
      <tr>{headers.map(h => <th key={h} style={thStyle}>{h}</th>)}</tr>
    </thead>
    <tbody>{children}</tbody>
  </table>
);

const NavButton = ({ active, onClick, label }) => (
  <button onClick={onClick} style={{ padding: '10px 20px', borderRadius: '8px', border: 'none', cursor: 'pointer', fontWeight: 'bold', backgroundColor: active ? '#1a5f7a' : '#ddd', color: active ? 'white' : '#555' }}>{label}</button>
);

const Card = ({ title, value, color }) => (
  <div style={{ padding: '20px', borderRadius: '12px', minWidth: '200px', backgroundColor: 'white', boxShadow: '0 4px 6px rgba(0,0,0,0.05)' }}>
    <h3 style={{ margin: '0 0 10px 0', color: '#7f8c8d', fontSize: '14px' }}>{title}</h3>
    <p style={{ fontSize: '32px', margin: 0, fontWeight: 'bold', color }}>{value}</p>
  </div>
);

const thStyle = { padding: '15px', color: '#444', borderBottom: '2px solid #eee', textAlign: 'left' };
const tdStyle = { padding: '15px', borderBottom: '1px solid #eee' };
const tdBoldStyle = { ...tdStyle, fontWeight: 'bold' };
const trStyle = { transition: 'background 0.2s' };
const badgeStyle = { padding: '4px 8px', borderRadius: '4px', backgroundColor: '#e1f5fe', color: '#01579b', fontSize: '12px', fontWeight: 'bold' };
const exportBtnStyle = { padding: '10px 20px', backgroundColor: '#27ae60', color: 'white', border: 'none', borderRadius: '8px', cursor: 'pointer', fontWeight: 'bold' };

export default Dashboard;
