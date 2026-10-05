import React, { useState, useMemo, useEffect } from 'react';
import { labelStyle, inputStyle, exportBtnStyle, SHADOWS } from '../Shared/Styles';
import { searchAssetsByNameOrQuery } from '../../utils/AssetMatcher';
import { storage } from '../../firebase';
import { ref, getDownloadURL } from 'firebase/storage';

const MedicineForm = ({ medicine, medicines = [], storageFiles = { images: [], audio: [] }, onSave, onCancel }) => {
  const cleanDataField = (val) => {
    if (!val) return '';
    // Removes sequences of 2 or more dots/dashes/pipes at the end, often used as separators in sources
    return val.toString().replace(/[\s\-.|_]{2,}$/, '').trim();
  };

  const padArray = (arr, minLength) => {
    const result = Array.isArray(arr) ? [...arr] : [];
    while (result.length < minLength) result.push('');
    return result;
  };

  const [formData, setFormData] = useState(medicine ? {
    medicineName: medicine.name || medicine.medicineName || '',
    company: medicine.company || '',
    cibNo: cleanDataField(medicine.cibNo),
    chemicalName: cleanDataField(medicine.chemicalName),
    crop: Array.isArray(medicine.crop) ? medicine.crop.join(', ') : (medicine.supportedCrops && Array.isArray(medicine.supportedCrops) ? medicine.supportedCrops.join(', ') : (medicine.crop || '')),
    disease: Array.isArray(medicine.disease) ? medicine.disease.join(', ') : (medicine.supportedDiseases && Array.isArray(medicine.supportedDiseases) ? medicine.supportedDiseases.join(', ') : (medicine.disease || '')),
    usage: medicine.usage || medicine.usageInstructions || '',
    marathiInfo: medicine.marathiInfo || medicine.warnings || '',
    ocrKeywords: padArray(medicine.searchKeywords || medicine.ocrKeywords || [], 3),
    barcodePrefixes: padArray(medicine.barcodePrefixes || [], 5),
    imageUrls: padArray(medicine.imageUrls || medicine.imageurls || [], 4),
    audioUrls: medicine.audioUrls || medicine.audiourls || ''
  } : {
    medicineName: '',
    company: '',
    cibNo: '',
    chemicalName: '',
    crop: '',
    disease: '',
    usage: '',
    marathiInfo: '',
    ocrKeywords: ['', '', ''],
    barcodePrefixes: ['', '', '', '', ''],
    imageUrls: ['', '', '', ''],
    audioUrls: ''
  });

  const [bulkOcr, setBulkOcr] = useState('');
  const [bulkImageSearch, setBulkImageSearch] = useState('');
  const [smartPasteText, setSmartPasteText] = useState('');
  const [activeDropdown, setActiveDropdown] = useState(null);
  const [copied, setCopied] = useState(false);
  const [previewImage, setPreviewImage] = useState(null);
  const [resolvedUrls, setResolvedUrls] = useState({});
  const [debouncedFormData, setDebouncedFormData] = useState(formData);

  // Debounce form data changes for smooth performance
  useEffect(() => {
    const handler = setTimeout(() => {
        setDebouncedFormData(formData);
    }, 200);
    return () => clearTimeout(handler);
  }, [formData]);

  const handleCopyData = () => {
    const textToCopy = `Medicine Name (Unique ID): ${formData.medicineName}
Manufacturing Company: ${formData.company}
CIB&RC Registration Number: ${formData.cibNo}
Chemical Composition / Name: ${formData.chemicalName}
Target Crops: ${formData.crop}
Target Diseases/Pests: ${formData.disease}
Marathi Information (Farmer Facing): ${formData.marathiInfo}
Usage Instructions (English): ${formData.usage}`;

    navigator.clipboard.writeText(textToCopy);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const currentQuery = useMemo(() => {
    if (!activeDropdown) return '';
    const { type, index } = activeDropdown;
    return type === 'images' ? (formData.imageUrls[index] || '') : (formData.audioUrls || '');
  }, [activeDropdown, formData.imageUrls, formData.audioUrls]);

  const assetSuggestions = useMemo(() => {
    if (!activeDropdown) return [];
    const { type } = activeDropdown;
    const isAudio = type === 'audio';
    const storageList = isAudio
      ? (storageFiles?.audio || [])
      : (storageFiles?.images || []);

    const query = currentQuery.trim();

    return searchAssetsByNameOrQuery({
      storageList,
      medicineName: debouncedFormData.medicineName,
      query: query,
      allMedicines: medicines || [],
      isAudio,
      limit: 20
    });
  }, [activeDropdown, storageFiles, medicines, debouncedFormData.medicineName, currentQuery]);

  // Lazy load URLs for suggestions when dropdown is active
  useEffect(() => {
    if (!activeDropdown || assetSuggestions.length === 0) return;

    const fetchUrls = async () => {
        const newUrls = { ...resolvedUrls };
        let changed = false;

        // Only fetch top 8 to save bandwidth
        const toFetch = assetSuggestions.slice(0, 8);
        for (const asset of toFetch) {
            if (asset.url) continue; // Already has URL (from DB cross-ref)
            if (newUrls[asset.fullPath]) continue; // Already resolved

            try {
                const assetRef = ref(storage, asset.fullPath);
                const url = await getDownloadURL(assetRef);
                newUrls[asset.fullPath] = url;
                changed = true;
            } catch (e) { console.warn("Failed to fetch asset URL", e); }
        }

        if (changed) setResolvedUrls(newUrls);
    };

    fetchUrls();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeDropdown, assetSuggestions]);

  const handleAssetSelect = async (asset) => {
    if (!activeDropdown) return;
    const { type, index } = activeDropdown;

    let url = asset.url;
    if (!url && asset.fullPath) {
        // Fetch URL if not already resolved
        url = resolvedUrls[asset.fullPath];
        if (!url) {
            try {
                const assetRef = ref(storage, asset.fullPath);
                url = await getDownloadURL(assetRef);
            } catch (e) {
                alert("Failed to select asset: " + e.message);
                return;
            }
        }
    }

    if (type === 'images') {
        const newImages = [...formData.imageUrls];
        newImages[index] = url;
        setFormData(prev => ({ ...prev, imageUrls: newImages }));

        // Auto-advance dropdown to next search query field if available
        const nextIndex = index + 1;
        if (nextIndex < newImages.length && (!newImages[nextIndex] || !newImages[nextIndex].trim().toLowerCase().startsWith('http'))) {
            setActiveDropdown({ type: 'images', index: nextIndex });
        } else {
            setActiveDropdown(null);
        }
    } else {
        setFormData(prev => ({ ...prev, audioUrls: url }));
        setActiveDropdown(null);
    }
  };

  const handleDistributeImageQueries = () => {
    if (!bulkImageSearch.trim()) return;

    const queries = bulkImageSearch
      .split(/[,|\n]+/)
      .map(q => q.trim())
      .filter(q => q !== '');

    if (queries.length === 0) return;

    const newImageUrls = [...formData.imageUrls];

    let queryIdx = 0;
    // Fill empty or non-HTTP query slots first
    for (let i = 0; i < newImageUrls.length && queryIdx < queries.length; i++) {
      const current = newImageUrls[i]?.trim() || '';
      if (!current || !current.toLowerCase().startsWith('http')) {
        newImageUrls[i] = queries[queryIdx];
        queryIdx++;
      }
    }

    // Append remaining queries as new image fields
    while (queryIdx < queries.length) {
      newImageUrls.push(queries[queryIdx]);
      queryIdx++;
    }

    setFormData(prev => ({
      ...prev,
      imageUrls: newImageUrls
    }));

    setBulkImageSearch('');

    // Focus first query index to trigger auto-advancing dropdown sequence
    const firstQueryIndex = newImageUrls.findIndex(u => u === queries[0]);
    if (firstQueryIndex !== -1) {
      setActiveDropdown({ type: 'images', index: firstQueryIndex });
    }
  };

  const handleSmartPaste = () => {
    if (!smartPasteText.trim()) return;

    const labels = [
        'Product Name', 'Chemical Name', 'Manufacturer',
        'CIB&RC Registration Number', 'हे औषध काय काम करतं',
        'कोणती समस्या सोडवतं', 'कोणत्या पिकावर', 'कोणत्या टप्प्यात',
        'मात्रा', 'प्रति एकर', 'कसं वापरायचं', 'काय बदल दिसेल',
        'किती दिवसात', 'काय करू नये', 'पाऊस / हवामान',
        'इतर औषधांसोबत', 'PHI'
    ];

    const labelPattern = labels.map(l => l.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('|');

    const extractValue = (targetLabel) => {
      const escapedLabel = targetLabel.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
      const regex = new RegExp(`(?:^|\\n)\\s*[*•-]?\\s*${escapedLabel}\\s*[:：]\\s*([\\s\\S]*?)(?=\\n\\s*[*•-]?\\s*(?:${labelPattern})\\s*[:：]|[🎯🌱🧪⚡⚠️•]|$)`, 'i');
      const match = smartPasteText.match(regex);
      if (!match) return null;

      let val = match[1].trim();

      val = val.replace(/\[\d+(?:,\s*\d+)*\]/g, '');
      val = val.replace(/पाठीवरील पंपाने पिकावर संपूर्ण पाने व्यवस्थित भिजतील अशी फवारणी.*?करावी[.]?/g, '');
      val = val.replace(/पिकावर संपूर्ण पाने व्यवस्थित भिजतील अशी फवारणी करावी[.]?/g, '');

      val = val.replace(/\s+/g, ' ').replace(/[\s\-._|]{2,}$/, '').trim();
      return val;
    };

    const newData = { ...formData };

    const name = extractValue('Product Name');
    if (name && (!medicine || !formData.medicineName)) newData.medicineName = name;

    const company = extractValue('Manufacturer');
    if (company) newData.company = company;

    const chemical = extractValue('Chemical Name');
    if (chemical) newData.chemicalName = chemical;

    const cib = extractValue('CIB&RC Registration Number');
    if (cib) newData.cibNo = cib;

    const work = extractValue('हे औषध काय काम करतं');
    if (work) newData.crop = work;

    const disease = extractValue('कोणती समस्या सोडवतं');
    if (disease) newData.disease = disease;

    const targetCrops = extractValue('कोणत्या पिकावर');
    const stageInfo = extractValue('कोणत्या टप्प्यात');
    let marathiParts = [];
    if (targetCrops) marathiParts.push(`कोणत्या पिकावर: ${targetCrops}`);
    if (stageInfo) marathiParts.push(`कोणत्या टप्प्यात: ${stageInfo}`);
    if (marathiParts.length > 0) newData.marathiInfo = marathiParts.join('\n\n');

    const dose = extractValue('मात्रा');
    const acre = extractValue('प्रति एकर');
    const method = extractValue('कसं वापरायचं');
    let usageParts = [];
    if (dose) usageParts.push(`मात्रा: ${dose}`);
    if (acre) usageParts.push(`प्रति एकर: ${acre}`);
    if (method) usageParts.push(`कसं वापरायचं: ${method}`);
    if (usageParts.length > 0) newData.usage = usageParts.join('\n');

    setFormData(newData);
    setSmartPasteText('');
  };

  const handleProcessBulkOcr = () => {
    if (!bulkOcr.trim()) return;
    const newKeywords = bulkOcr.split(',').map(k => k.trim()).filter(k => k !== '');
    if (newKeywords.length > 0) {
      setFormData(prev => ({
        ...prev,
        ocrKeywords: [...newKeywords, ...prev.ocrKeywords].filter((v, i, a) => v && a.indexOf(v) === i)
      }));
      setBulkOcr('');
    }
  };

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData({ ...formData, [name]: value });
  };

  const handleArrayChange = (index, value, field) => {
    const newArray = [...formData[field]];
    newArray[index] = value;
    setFormData({ ...formData, [field]: newArray });
  };

  const addArrayField = (field) => {
    setFormData({ ...formData, [field]: [...formData[field], ''] });
  };

  const removeArrayField = (index, field) => {
    const newArray = formData[field].filter((_, i) => i !== index);
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

    let finalKeywords = [...formData.ocrKeywords];
    if (bulkOcr.trim()) {
      const extra = bulkOcr.split(',').map(k => k.trim()).filter(k => k !== '');
      finalKeywords = [...extra, ...finalKeywords];
    }

    const filteredKeywords = finalKeywords.filter(k => k && k.trim() !== '');
    if (filteredKeywords.length === 0) {
        alert("At least one OCR keyword is required!");
        return;
    }
    const filteredPrefixes = formData.barcodePrefixes.filter(p => p && p.trim() !== '');
    const filteredImages = formData.imageUrls.filter(u => u && u.trim() !== '');

    const finalData = {
        ...formData,
        ocrKeywords: filteredKeywords,
        barcodePrefixes: filteredPrefixes,
        imageUrls: filteredImages
    };

    if (!finalData.barcodePrefixes) {
        finalData.barcodePrefixes = [];
    }

    onSave(finalData);
  };

  const sectionStyle = {
    backgroundColor: '#f8fafc',
    padding: '20px',
    borderRadius: '10px',
    border: '1px solid #e2e8f0',
    marginBottom: '20px'
  };

  const sectionTitleStyle = {
    marginTop: 0,
    marginBottom: '15px',
    fontSize: '16px',
    color: '#334155',
    borderBottom: '2px solid #3b82f6',
    display: 'inline-block',
    paddingBottom: '4px'
  };

  return (
    <div style={{
      backgroundColor: 'white',
      borderRadius: '16px',
      boxShadow: '0 10px 25px rgba(0,0,0,0.1)',
      width: '95%',
      maxWidth: '1200px',
      margin: '0 auto',
      display: 'flex',
      flexDirection: 'column',
      maxHeight: '90vh',
      overflow: 'hidden'
    }}>
      {/* Fixed Header */}
      <div style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        padding: '24px 40px',
        borderBottom: '1px solid #eee'
      }}>
        <h2 style={{ margin: 0, color: '#1a5f7a', fontSize: '24px' }}>{medicine ? '📝 Edit Medicine' : '➕ Add New Medicine'}</h2>
        <button type="button" onClick={onCancel} style={{ background: 'none', border: 'none', color: '#64748b', cursor: 'pointer', fontSize: '20px' }}>✕</button>
      </div>

      <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', overflow: 'hidden', flex: 1 }}>
        {/* Scrollable Body */}
        <div style={{ padding: '30px 40px', overflowY: 'auto', flex: 1 }}>

          {/* ✨ Smart Magic Importer */}
          <div style={{ ...sectionStyle, backgroundColor: '#fdf4ff', borderColor: '#d946ef' }}>
            <h3 style={{ ...sectionTitleStyle, borderBottomColor: '#d946ef', color: '#701a75' }}>✨ Smart Magic Importer</h3>
            <p style={{ fontSize: '13px', color: '#a21caf', marginBottom: '12px', marginTop: '-10px' }}>
              Paste the entire product info block from Google/Source below. It will automatically fill the form fields.
            </p>
            <div style={{ display: 'flex', gap: '15px' }}>
              <textarea
                value={smartPasteText}
                onChange={(e) => setSmartPasteText(e.target.value)}
                style={{ ...inputStyle, height: '80px', flex: 1, borderColor: '#f0abfc' }}
                placeholder="Paste product info here (e.g. Product Name: ..., Manufacturer: ..., etc.)"
              />
              <button
                type="button"
                onClick={handleSmartPaste}
                style={{
                  ...exportBtnStyle,
                  backgroundColor: '#d946ef',
                  height: '80px',
                  width: '140px',
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  justifyContent: 'center',
                  gap: '5px'
                }}
              >
                <span style={{ fontSize: '20px' }}>🪄</span>
                <span>Smart Fill</span>
              </button>
            </div>
          </div>

          {/* Section 1: Basic Info */}
          <div style={sectionStyle}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '15px' }}>
              <h3 style={{ ...sectionTitleStyle, marginBottom: 0 }}>📦 General Information</h3>
              <button
                type="button"
                onClick={handleCopyData}
                style={{
                  padding: '6px 12px',
                  backgroundColor: copied ? '#10b981' : '#f1f5f9',
                  color: copied ? 'white' : '#475569',
                  border: `1px solid ${copied ? '#10b981' : '#cbd5e1'}`,
                  borderRadius: '6px',
                  fontSize: '12px',
                  fontWeight: '600',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '5px',
                  transition: 'all 0.2s'
                }}
              >
                <span>{copied ? '✅' : '📋'}</span>
                {copied ? 'Copied!' : 'Copy Medicine Data'}
              </button>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px' }}>
              <div>
                <label style={labelStyle}>Medicine Name (Unique ID)</label>
                <input name="medicineName" value={formData.medicineName} onChange={handleChange} style={inputStyle} required disabled={!!medicine} placeholder="e.g. Bio-R303+" />
              </div>
              <div>
                <label style={labelStyle}>Manufacturing Company</label>
                <input name="company" value={formData.company} onChange={handleChange} style={inputStyle} placeholder="e.g. FMC, Bayer" />
              </div>
              <div>
                <label style={labelStyle}>CIB&RC Registration Number</label>
                <input name="cibNo" value={formData.cibNo} onChange={handleChange} style={inputStyle} placeholder="e.g. CIR-123/2023" />
              </div>
              <div>
                <label style={labelStyle}>Chemical Composition / Name</label>
                <input name="chemicalName" value={formData.chemicalName} onChange={handleChange} style={inputStyle} placeholder="e.g. Amino acid, Humic acid" />
              </div>
              <div>
                <label style={labelStyle}>Target Crops</label>
                <input name="crop" value={formData.crop} onChange={handleChange} style={inputStyle} placeholder="e.g. Cotton, Soyabean, Chilli" />
              </div>
              <div>
                <label style={labelStyle}>Target Diseases/Pests</label>
                <input name="disease" value={formData.disease} onChange={handleChange} style={inputStyle} placeholder="e.g. Thrips, Mites, Aphids" />
              </div>
            </div>
          </div>

          {/* Section 2: Regional Info */}
          <div style={sectionStyle}>
            <h3 style={sectionTitleStyle}>🌍 Regional Details</h3>
            <div style={{ marginBottom: '15px' }}>
              <label style={labelStyle}>Marathi Information (Farmer Facing)</label>
              <textarea name="marathiInfo" value={formData.marathiInfo} onChange={handleChange} style={{ ...inputStyle, height: '100px', fontSize: '15px' }} required placeholder="Describe the medicine in Marathi for the kiosk display..." />
            </div>
            <div>
              <label style={labelStyle}>Usage Instructions (English)</label>
              <textarea name="usage" value={formData.usage} onChange={handleChange} style={{ ...inputStyle, height: '80px' }} placeholder="Technical usage details..." />
            </div>
          </div>

          {/* Section 3: Vision Engine Data */}
          <div style={sectionStyle}>
            <h3 style={sectionTitleStyle}>🔍 AI Vision & Recognition</h3>

            <div style={{ backgroundColor: '#eff6ff', padding: '15px', borderRadius: '8px', marginBottom: '20px', border: '1px dashed #3b82f6' }}>
              <label style={{...labelStyle, color: '#1e40af'}}>⚡ Bulk Keyword Importer (Comma Separated)</label>
              <div style={{ display: 'flex', gap: '10px' }}>
                <textarea
                  value={bulkOcr}
                  onChange={(e) => setBulkOcr(e.target.value)}
                  style={{ ...inputStyle, height: '60px', flex: 1 }}
                  placeholder="Paste many keywords here: r303, bio, bior303, 303..."
                />
                <button
                  type="button"
                  onClick={handleProcessBulkOcr}
                  style={{ ...exportBtnStyle, backgroundColor: '#3b82f6', height: '60px', width: '120px' }}
                >
                  Distribute
                </button>
              </div>
              <small style={{ color: '#60a5fa', marginTop: '5px', display: 'block' }}>Keywords will be split by comma and added to the list below.</small>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr', gap: '20px' }}>
              <div>
                <label style={labelStyle}>OCR Keywords (Tokens)</label>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '10px', alignItems: 'center' }}>
                  {formData.ocrKeywords.map((kw, i) => (
                    <div key={i} style={{ display: 'flex', gap: '5px', width: 'calc(20% - 10px)', minWidth: '150px' }}>
                      <input value={kw} onChange={(e) => handleArrayChange(i, e.target.value, 'ocrKeywords')} style={{ ...inputStyle, padding: '8px 12px' }} placeholder={`Keyword ${i + 1}`} />
                      <button type="button" onClick={() => removeArrayField(i, 'ocrKeywords')} style={{ background: 'none', border: 'none', color: '#ef4444', cursor: 'pointer' }}>✕</button>
                    </div>
                  ))}
                  <button type="button" onClick={() => addArrayField('ocrKeywords')} style={{ border: '1px dashed #cbd5e1', background: 'none', padding: '8px 15px', borderRadius: '8px', cursor: 'pointer', color: '#64748b', fontSize: '13px' }}>+ Add Keyword</button>
                </div>
              </div>
              <div>
                <label style={labelStyle}>Barcode/QR Prefixes</label>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '10px', alignItems: 'center' }}>
                  {formData.barcodePrefixes.map((p, i) => (
                    <div key={i} style={{ display: 'flex', gap: '5px', width: 'calc(20% - 10px)', minWidth: '150px' }}>
                      <input value={p} onChange={(e) => handleArrayChange(i, e.target.value, 'barcodePrefixes')} style={{ ...inputStyle, padding: '8px 12px' }} placeholder={`Prefix ${i + 1}`} />
                      <button type="button" onClick={() => removeArrayField(i, 'barcodePrefixes')} style={{ background: 'none', border: 'none', color: '#ef4444', cursor: 'pointer' }}>✕</button>
                    </div>
                  ))}
                  <button type="button" onClick={() => addArrayField('barcodePrefixes')} style={{ border: '1px dashed #cbd5e1', background: 'none', padding: '8px 15px', borderRadius: '8px', cursor: 'pointer', color: '#64748b', fontSize: '13px' }}>+ Add Prefix</button>
                </div>
              </div>
            </div>
          </div>

          {/* Section 4: Multimedia */}
          <div style={sectionStyle} onClick={(e) => {
              if (e.target.tagName !== 'INPUT' && !e.target.closest('.suggestion-box')) {
                  setActiveDropdown(null);
              }
          }}>
            <h3 style={sectionTitleStyle}>🖼️ Media Assets</h3>

            {/* Master Bulk Image Search Importer */}
            <div style={{ backgroundColor: '#eff6ff', padding: '15px', borderRadius: '8px', marginBottom: '20px', border: '1px dashed #3b82f6' }}>
              <label style={{ ...labelStyle, color: '#1e40af' }}>⚡ Bulk Image Search Importer (Comma Separated)</label>
              <div style={{ display: 'flex', gap: '10px' }}>
                <input
                  type="text"
                  value={bulkImageSearch}
                  onChange={(e) => setBulkImageSearch(e.target.value)}
                  style={{ ...inputStyle, flex: 1 }}
                  placeholder="Paste multiple search terms: bior303 front, bior303 back, bior303 bottle, bior303 box..."
                />
                <button
                  type="button"
                  onClick={handleDistributeImageQueries}
                  style={{ ...exportBtnStyle, backgroundColor: '#3b82f6', height: '42px', padding: '0 20px', minWidth: '160px' }}
                >
                  Distribute Queries
                </button>
              </div>
              <small style={{ color: '#60a5fa', marginTop: '5px', display: 'block' }}>
                Queries will be placed into separate Image fields below for 1-click auto-advancing selection.
              </small>
            </div>

            <div style={{ marginBottom: '20px' }}>
              <label style={labelStyle}>Image URLs (Minimum 1 Required)</label>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '15px' }}>
                {formData.imageUrls.map((url, i) => (
                  <div key={i} style={{ display: 'flex', gap: '5px', position: 'relative' }}>
                      <div style={{ position: 'relative', flex: 1 }}>
                        <input
                            value={url}
                            onChange={(e) => handleArrayChange(i, e.target.value, 'imageUrls')}
                            onFocus={() => setActiveDropdown({ type: 'images', index: i })}
                            style={{
                                ...inputStyle,
                                paddingRight: url ? '40px' : '16px'
                            }}
                            placeholder={`Image URL ${i + 1}`}
                        />
                        {url && url.startsWith('http') && (
                            <button
                                type="button"
                                onClick={() => setPreviewImage(url)}
                                title="Preview Image"
                                style={{
                                    position: 'absolute',
                                    right: '8px',
                                    top: '50%',
                                    transform: 'translateY(-50%)',
                                    background: '#f1f5f9',
                                    border: 'none',
                                    borderRadius: '4px',
                                    padding: '4px 6px',
                                    cursor: 'pointer',
                                    fontSize: '14px'
                                }}
                            >
                                👁️
                            </button>
                        )}
                      </div>
                      {activeDropdown?.type === 'images' && activeDropdown?.index === i && (
                        <div className="suggestion-box" style={{ position: 'absolute', top: '100%', left: 0, right: '35px', zIndex: 100, backgroundColor: 'white', border: '1px solid #cbd5e1', borderRadius: '10px', boxShadow: '0 10px 15px -3px rgba(0,0,0,0.1)', maxHeight: '220px', overflowY: 'auto', marginTop: '5px' }}>
                            <div style={{ padding: '8px 12px', fontSize: '11px', fontWeight: 'bold', color: '#64748b', backgroundColor: '#f8fafc', borderBottom: '1px solid #e2e8f0', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                <span>
                                    {formData.imageUrls[i]?.trim() && !formData.imageUrls[i].trim().toLowerCase().startsWith('http')
                                      ? `🔍 Search results for "${formData.imageUrls[i].trim()}"`
                                      : (formData.medicineName ? `📦 Images matching "${formData.medicineName}"` : `🔍 Image Search`)}
                                </span>
                                <span style={{ fontSize: '10px', color: '#94a3b8' }}>{assetSuggestions.length} found</span>
                            </div>
                            {assetSuggestions.length > 0 ? (
                                assetSuggestions.map((f, idx) => {
                                    const imgUrl = f.url || resolvedUrls[f.fullPath];
                                    return (
                                        <div key={idx} onClick={() => handleAssetSelect(f)} style={{ padding: '8px 12px', cursor: 'pointer', borderBottom: '1px solid #f1f5f9', fontSize: '13px', display: 'flex', alignItems: 'center', gap: '10px' }} onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#f0f9ff'} onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}>
                                            {imgUrl ? (
                                                <img src={imgUrl} alt="" style={{width: '32px', height: '32px', borderRadius: '6px', objectFit: 'cover'}} />
                                            ) : (
                                                <div style={{width: '32px', height: '32px', borderRadius: '6px', backgroundColor: '#f1f5f9', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '10px'}}>...</div>
                                            )}
                                            <div style={{ display: 'flex', flexDirection: 'column', flex: 1, minWidth: 0 }}>
                                                <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                                                    <span style={{whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', color: '#334155', fontWeight: '500'}}>{f.name}</span>
                                                    {f.isFromDb && <span style={{ fontSize: '9px', backgroundColor: '#dbeafe', color: '#1e40af', padding: '1px 5px', borderRadius: '4px' }}>Database</span>}
                                                </div>
                                                {f.score && <span style={{ fontSize: '10px', color: '#10b981' }}>Match Score: {Math.round(f.score)}%</span>}
                                            </div>
                                        </div>
                                    );
                                })
                            ) : (
                                <div style={{ padding: '12px', fontSize: '12px', color: '#64748b', textAlign: 'center' }}>
                                    {!formData.medicineName && !formData.imageUrls[i]?.trim()
                                        ? "Enter a Medicine Name above or type a search query here to find images."
                                        : `No matching images found. Type a filename or product name.`}
                                </div>
                            )}
                        </div>
                      )}
                      <button type="button" onClick={() => removeArrayField(i, 'imageUrls')} style={{ background: 'none', border: 'none', color: '#ef4444', cursor: 'pointer' }}>✕</button>
                  </div>
                ))}
              </div>
              <button type="button" onClick={() => addArrayField('imageUrls')} style={{ border: '1px dashed #cbd5e1', background: 'none', padding: '8px', borderRadius: '8px', cursor: 'pointer', color: '#64748b', marginTop: '10px', width: '100%' }}>+ Add More Images</button>
            </div>
            <div style={{ position: 'relative' }}>
              <label style={labelStyle}>Audio URL (Information Narration)</label>
              <input
                name="audioUrls"
                value={formData.audioUrls}
                onChange={handleChange}
                onFocus={() => setActiveDropdown({ type: 'audio' })}
                style={inputStyle}
                placeholder="https://firebasestorage.googleapis.com/..."
              />
              {activeDropdown?.type === 'audio' && (
                <div className="suggestion-box" style={{ position: 'absolute', top: '100%', left: 0, right: 0, zIndex: 100, backgroundColor: 'white', border: '1px solid #cbd5e1', borderRadius: '10px', boxShadow: '0 10px 15px -3px rgba(0,0,0,0.1)', maxHeight: '220px', overflowY: 'auto', marginTop: '5px' }}>
                    <div style={{ padding: '8px 12px', fontSize: '11px', fontWeight: 'bold', color: '#64748b', backgroundColor: '#f8fafc', borderBottom: '1px solid #e2e8f0', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <span>
                            {formData.audioUrls?.trim() && !formData.audioUrls.trim().toLowerCase().startsWith('http')
                              ? `🎵 Search results for "${formData.audioUrls.trim()}"`
                              : (formData.medicineName ? `🎵 Audio matching "${formData.medicineName}"` : `🎵 Audio Search`)}
                        </span>
                        <span style={{ fontSize: '10px', color: '#94a3b8' }}>{assetSuggestions.length} found</span>
                    </div>
                    {assetSuggestions.length > 0 ? (
                        assetSuggestions.map((f, idx) => (
                            <div key={idx} onClick={() => handleAssetSelect(f)} style={{ padding: '8px 12px', cursor: 'pointer', borderBottom: '1px solid #f1f5f9', fontSize: '13px', display: 'flex', alignItems: 'center', gap: '10px' }} onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#f0f9ff'} onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}>
                                <span style={{fontSize: '18px'}}>🎵</span>
                                <div style={{ display: 'flex', flexDirection: 'column', flex: 1, minWidth: 0 }}>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                                        <span style={{whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', color: '#334155', fontWeight: '500'}}>{f.name}</span>
                                        {f.isFromDb && <span style={{ fontSize: '9px', backgroundColor: '#dbeafe', color: '#1e40af', padding: '1px 5px', borderRadius: '4px' }}>Database</span>}
                                    </div>
                                    {f.score && <span style={{ fontSize: '10px', color: '#10b981' }}>Match Score: {Math.round(f.score)}%</span>}
                                </div>
                            </div>
                        ))
                    ) : (
                        <div style={{ padding: '12px', fontSize: '12px', color: '#64748b', textAlign: 'center' }}>
                            {!formData.medicineName && !formData.audioUrls?.trim()
                                ? "Enter a Medicine Name above or type a search query here to find audio files."
                                : `No matching audio files found.`}
                        </div>
                    )}
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Fixed Footer */}
        <div style={{
          display: 'flex',
          gap: '15px',
          justifyContent: 'flex-end',
          padding: '20px 40px',
          borderTop: '1px solid #eee',
          backgroundColor: '#fff',
          borderBottomLeftRadius: '16px',
          borderBottomRightRadius: '16px'
        }}>
          <button type="button" onClick={onCancel} style={{ padding: '12px 25px', backgroundColor: '#f1f5f9', color: '#475569', border: 'none', borderRadius: '10px', cursor: 'pointer', fontWeight: 'bold' }}>Cancel</button>
          <button type="submit" style={{ ...exportBtnStyle, padding: '12px 40px', backgroundColor: '#1a5f7a', boxShadow: '0 4px 6px rgba(26, 95, 122, 0.2)' }}>
            {medicine ? 'Update Medicine' : 'Save New Medicine'}
          </button>
        </div>
      </form>

      {/* Preview Modal */}
      {previewImage && (
        <div
            onClick={() => setPreviewImage(null)}
            style={{
                position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
                backgroundColor: 'rgba(0,0,0,0.8)', zIndex: 3000,
                display: 'flex', justifyContent: 'center', alignItems: 'center',
                backdropFilter: 'blur(5px)'
            }}
        >
            <div style={{ position: 'relative', maxWidth: '90%', maxHeight: '90%' }} onClick={e => e.stopPropagation()}>
                <img src={previewImage} alt="Preview" style={{ maxWidth: '100%', maxHeight: '80vh', borderRadius: '12px', boxShadow: SHADOWS.xl }} />
                <button
                    onClick={() => setPreviewImage(null)}
                    style={{ position: 'absolute', top: '-40px', right: '-40px', background: 'white', border: 'none', borderRadius: '50%', width: '40px', height: '40px', cursor: 'pointer', fontSize: '20px' }}
                >
                    ✕
                </button>
            </div>
        </div>
      )}
    </div>
  );

};

export default MedicineForm;
