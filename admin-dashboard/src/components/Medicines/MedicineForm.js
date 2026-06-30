import React, { useState, useMemo } from 'react';
import { labelStyle, inputStyle, exportBtnStyle } from '../Shared/Styles';

const MedicineForm = ({ medicine, storageFiles, onSave, onCancel }) => {
  const padArray = (arr, minLength) => {
    const result = Array.isArray(arr) ? [...arr] : [];
    while (result.length < minLength) result.push('');
    return result;
  };

  const [formData, setFormData] = useState(medicine ? {
    medicineName: medicine.name || medicine.medicineName || '',
    company: medicine.company || '',
    cibNo: medicine.cibNo || '',
    chemicalName: medicine.chemicalName || '',
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
  const [smartPasteText, setSmartPasteText] = useState('');
  const [activeDropdown, setActiveDropdown] = useState(null);

  const assetSuggestions = useMemo(() => {
    if (!activeDropdown || !storageFiles) return [];
    const { type } = activeDropdown;
    const list = type === 'audio' ? storageFiles.audio : storageFiles.images;

    let availableList = list;
    if (type === 'images') {
        const selectedUrls = formData.imageUrls.filter(u => u && u.trim() !== '');
        availableList = list.filter(f => !selectedUrls.includes(f.url));
    }

    const term = formData.medicineName.trim().toLowerCase();
    if (!term) return availableList.slice(0, 10);

    const cleanTerm = term.replace(/[^a-z0-9]/g, '');
    const termWords = term.split(/[^a-z0-9]+/).filter(w => w.length >= 2);

    return availableList
        .map(f => {
            const fileName = f.name.toLowerCase();
            const baseName = fileName.split('.')[0];
            const cleanFileName = baseName.replace(/[^a-z0-9]/g, '');
            const fileWords = baseName.split(/[^a-z0-9]+/).filter(w => w.length >= 2);

            let score = 0;

            // 1. Exact Match (Highest Priority)
            if (cleanFileName === cleanTerm) {
                score += 100;
            }
            // 2. Starts With Match
            else if (cleanFileName.startsWith(cleanTerm)) {
                score += 60;
            }
            // 3. Name contained in Filename
            else if (cleanFileName.includes(cleanTerm)) {
                score += 40;
            }

            // 4. Word Intersection (Smart matching for "Bio R303" vs "bior303")
            const commonWords = termWords.filter(w => fileWords.includes(w));
            score += (commonWords.length * 25);

            // 5. Reversed containment (Filename contained in Name - only if filename is significant)
            if (cleanFileName.length >= 4 && cleanTerm.includes(cleanFileName)) {
                score += 30;
            }

            return { ...f, score };
        })
        .filter(f => f.score > 0)
        .sort((a, b) => b.score - a.score)
        .slice(0, 15);
  }, [activeDropdown, storageFiles, formData.medicineName, formData.imageUrls]);

  const handleAssetSelect = (url) => {
    if (!activeDropdown) return;
    const { type, index } = activeDropdown;

    if (type === 'images') {
        handleArrayChange(index, url, 'imageUrls');
    } else {
        setFormData({ ...formData, audioUrls: url });
    }
    setActiveDropdown(null);
  };

  const generateOcrKeywords = (name, company, chemical) => {
    const keywords = new Set();
    const swaps = { 'o': '0', '0': 'o', 'i': '1', '1': 'i', 's': '5', '5': 's', 'b': '8', '8': 'b', 'z': '2', '2': 'z' };

    const cleanEng = (text) => (text || '')
        .split('(')[0]
        .replace(/[^\x00-\x7F]/g, '') // Remove non-ASCII
        .replace(/%/g, '')           // Explicitly remove %
        .toLowerCase()
        .trim();

    const addVariants = (text) => {
      if (!text || text.length < 2) return;
      const base = text.toLowerCase();
      keywords.add(base);

      // No symbols, No spaces
      const alphanumeric = base.replace(/[^a-z0-9]/g, '');
      if (alphanumeric.length > 1) keywords.add(alphanumeric);

      // Parts & Fragments
      const parts = base.split(/[^a-z0-9]+/).filter(p => p.length > 1);
      parts.forEach(p => {
        keywords.add(p);
        // Numeric/Alpha split (e.g., R303 -> R, 303)
        const alpha = p.replace(/[0-9]/g, '');
        const numeric = p.replace(/[a-z]/g, '');
        if (alpha.length > 1) keywords.add(alpha);
        if (numeric.length > 1) keywords.add(numeric);
      });

      // Character Swaps (OCR Confusion)
      const applySwaps = (str) => {
        let chars = str.split('');
        let results = [];
        chars.forEach((char, idx) => {
          if (swaps[char]) {
            let variant = [...chars];
            variant[idx] = swaps[char];
            results.push(variant.join(''));
          }
        });
        return results;
      };

      if (alphanumeric.length > 1) {
        applySwaps(alphanumeric).forEach(v => keywords.add(v));
      }
    };

    const nameEng = cleanEng(name);
    addVariants(nameEng);

    // Exact name with common symbol replacements
    if (nameEng.includes('+')) keywords.add(nameEng.replace('+', 'plus'));

    // Company and Chemical
    addVariants(cleanEng(company));
    addVariants(cleanEng(chemical));

    // Handle combinations for name (e.g. bio r 303)
    const nameParts = nameEng.split(/[^a-z0-9]+/).filter(p => p.length > 0);
    if (nameParts.length > 1) {
        keywords.add(nameParts.join(' '));
        keywords.add(nameParts.join('-'));
    }

    const filtered = Array.from(keywords).filter(k => k && k.length > 1);
    // Prioritize: Name-based first, then length (longer/specific first), then others
    return filtered.sort((a, b) => {
        const aInName = nameEng.includes(a);
        const bInName = nameEng.includes(b);
        if (aInName && !bInName) return -1;
        if (!aInName && bInName) return 1;
        return b.length - a.length;
    }).slice(0, 10); // Strictly top 10 most useful keywords
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

    const extractValue = (targetLabel) => {
      const escapedLabel = targetLabel.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
      const labelPattern = labels.map(l => l.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('|');
      const regex = new RegExp(`${escapedLabel}\\s*[:：]\\s*((?:.(?!${labelPattern}|[🎯🌱🧪⚡⚠️•]))*)`, 'i');
      const match = smartPasteText.match(regex);
      return match ? match[1].trim() : null;
    };

    const newData = { ...formData };

    const name = extractValue('Product Name');
    if (name && !medicine) newData.medicineName = name;

    const company = extractValue('Manufacturer');
    if (company) newData.company = company;

    const chemical = extractValue('Chemical Name');
    if (chemical) newData.chemicalName = chemical;

    const cib = extractValue('CIB&RC Registration Number');
    if (cib) newData.cibNo = cib;

    const crop = extractValue('हे औषध काय काम करतं');
    if (crop) newData.crop = crop;

    const disease = extractValue('कोणती समस्या सोडवतं');
    if (disease) newData.disease = disease;

    const marathi = extractValue('कोणत्या पिकावर');
    if (marathi) newData.marathiInfo = marathi;

    const usage = extractValue('मात्रा');
    if (usage) newData.usage = usage;

    // Advanced OCR Keyword Generation based on Expert Rules
    if (name) {
        const generated = generateOcrKeywords(name, company, chemical);
        const currentKeywords = newData.ocrKeywords.filter(k => k.trim() !== '');
        const combined = [...new Set([...generated, ...currentKeywords])];
        newData.ocrKeywords = padArray(combined, 10); // Increase initial slots
    }

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

    // Process bulk OCR if there's anything left there
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
      maxHeight: '90vh', // Limit height to 90% of viewport
      overflow: 'hidden' // Contain children
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
            <h3 style={sectionTitleStyle}>📦 General Information</h3>
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

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '30px' }}>
              <div>
                <label style={labelStyle}>OCR Keywords (Tokens)</label>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                  {formData.ocrKeywords.map((kw, i) => (
                    <div key={i} style={{ display: 'flex', gap: '5px' }}>
                      <input value={kw} onChange={(e) => handleArrayChange(i, e.target.value, 'ocrKeywords')} style={inputStyle} placeholder={`Keyword ${i + 1}`} />
                      <button type="button" onClick={() => removeArrayField(i, 'ocrKeywords')} style={{ background: 'none', border: 'none', color: '#ef4444', cursor: 'pointer' }}>✕</button>
                    </div>
                  ))}
                  <button type="button" onClick={() => addArrayField('ocrKeywords')} style={{ border: '1px dashed #cbd5e1', background: 'none', padding: '8px', borderRadius: '8px', cursor: 'pointer', color: '#64748b' }}>+ Add Keyword</button>
                </div>
              </div>
              <div>
                <label style={labelStyle}>Barcode/QR Prefixes</label>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                  {formData.barcodePrefixes.map((p, i) => (
                    <div key={i} style={{ display: 'flex', gap: '5px' }}>
                      <input value={p} onChange={(e) => handleArrayChange(i, e.target.value, 'barcodePrefixes')} style={inputStyle} placeholder={`Prefix ${i + 1}`} />
                      <button type="button" onClick={() => removeArrayField(i, 'barcodePrefixes')} style={{ background: 'none', border: 'none', color: '#ef4444', cursor: 'pointer' }}>✕</button>
                    </div>
                  ))}
                  <button type="button" onClick={() => addArrayField('barcodePrefixes')} style={{ border: '1px dashed #cbd5e1', background: 'none', padding: '8px', borderRadius: '8px', cursor: 'pointer', color: '#64748b' }}>+ Add Prefix</button>
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
            <div style={{ marginBottom: '20px' }}>
              <label style={labelStyle}>Image URLs (Minimum 1 Required)</label>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '15px' }}>
                {formData.imageUrls.map((url, i) => (
                  <div key={i} style={{ display: 'flex', gap: '5px', position: 'relative' }}>
                      <input
                        value={url}
                        onChange={(e) => handleArrayChange(i, e.target.value, 'imageUrls')}
                        onFocus={() => setActiveDropdown({ type: 'images', index: i })}
                        style={inputStyle}
                        placeholder={`Image URL ${i + 1}`}
                      />
                      {activeDropdown?.type === 'images' && activeDropdown?.index === i && assetSuggestions.length > 0 && (
                        <div className="suggestion-box" style={{ position: 'absolute', top: '100%', left: 0, right: '35px', zIndex: 100, backgroundColor: 'white', border: '1px solid #cbd5e1', borderRadius: '10px', boxShadow: '0 10px 15px -3px rgba(0,0,0,0.1)', maxHeight: '180px', overflowY: 'auto', marginTop: '5px' }}>
                            <div style={{ padding: '8px 12px', fontSize: '11px', fontWeight: 'bold', color: '#64748b', backgroundColor: '#f8fafc', borderBottom: '1px solid #e2e8f0' }}>Suggested Images</div>
                            {assetSuggestions.map((f, idx) => (
                                <div key={idx} onClick={() => handleAssetSelect(f.url)} style={{ padding: '8px 12px', cursor: 'pointer', borderBottom: '1px solid #f1f5f9', fontSize: '13px', display: 'flex', alignItems: 'center', gap: '10px' }} onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#f0f9ff'} onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}>
                                    <img src={f.url} alt="" style={{width: '32px', height: '32px', borderRadius: '6px', objectFit: 'cover'}} />
                                    <span style={{whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', color: '#334155'}}>{f.name}</span>
                                </div>
                            ))}
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
              {activeDropdown?.type === 'audio' && assetSuggestions.length > 0 && (
                <div className="suggestion-box" style={{ position: 'absolute', top: '100%', left: 0, right: 0, zIndex: 100, backgroundColor: 'white', border: '1px solid #cbd5e1', borderRadius: '10px', boxShadow: '0 10px 15px -3px rgba(0,0,0,0.1)', maxHeight: '180px', overflowY: 'auto', marginTop: '5px' }}>
                    <div style={{ padding: '8px 12px', fontSize: '11px', fontWeight: 'bold', color: '#64748b', backgroundColor: '#f8fafc', borderBottom: '1px solid #e2e8f0' }}>Suggested Audio Files</div>
                    {assetSuggestions.map((f, idx) => (
                        <div key={idx} onClick={() => handleAssetSelect(f.url)} style={{ padding: '8px 12px', cursor: 'pointer', borderBottom: '1px solid #f1f5f9', fontSize: '13px', display: 'flex', alignItems: 'center', gap: '10px' }} onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#f0f9ff'} onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}>
                            <span style={{fontSize: '18px'}}>🎵</span>
                            <span style={{whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', color: '#334155'}}>{f.name}</span>
                        </div>
                    ))}
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
    </div>
  );

};

export default MedicineForm;
