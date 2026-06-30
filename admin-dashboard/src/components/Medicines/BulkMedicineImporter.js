import React, { useState, useRef } from 'react';
import { labelStyle, inputStyle, exportBtnStyle } from '../Shared/Styles';
import * as XLSX from 'xlsx';

const BulkMedicineImporter = ({ existingMedicines, onSaveAll, onCancel }) => {
  const [bulkData, setBulkData] = useState('');
  const [parsedMedicines, setParsedMedicines] = useState([]);
  const [isProcessing, setIsProcessing] = useState(false);
  const fileInputRef = useRef(null);

  // ... (rest of the helper functions)
  const isDuplicate = (name) => {
    if (!name) return false;
    const searchName = name.trim().toLowerCase();
    return existingMedicines.some(m =>
        (m.id || '').toLowerCase() === searchName ||
        (m.name || '').toLowerCase() === searchName ||
        (m.medicineName || '').toLowerCase() === searchName
    );
  };

  const generateOcrKeywords = (name, company, chemical) => {
    const keywords = new Set();
    const swaps = { 'o': '0', '0': 'o', 'i': '1', '1': 'i', 's': '5', '5': 's', 'b': '8', '8': 'b', 'z': '2', '2': 'z' };
    const cleanEng = (text) => (text || '')
        .split('(')[0]
        .replace(/[^\x20-\x7E]/g, '') // Remove non-ASCII
        .replace(/%/g, '')           // Explicitly remove %
        .toLowerCase()
        .trim();

    const addVariants = (text) => {
      if (!text || text.length < 2) return;
      const base = text.toLowerCase();
      keywords.add(base);
      const alphanumeric = base.replace(/[^a-z0-9]/g, '');
      if (alphanumeric.length > 1) keywords.add(alphanumeric);
      const parts = base.split(/[^a-z0-9]+/).filter(p => p.length > 1);
      parts.forEach(p => {
        keywords.add(p);
        const alpha = p.replace(/[0-9]/g, '');
        const numeric = p.replace(/[a-z]/g, '');
        if (alpha.length > 1) keywords.add(alpha);
        if (numeric.length > 1) keywords.add(numeric);
      });

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
    if (nameEng.includes('+')) keywords.add(nameEng.replace('+', 'plus'));
    addVariants(cleanEng(company));
    addVariants(cleanEng(chemical));

    const nameParts = nameEng.split(/[^a-z0-9]+/).filter(p => p.length > 0);
    if (nameParts.length > 1) {
        keywords.add(nameParts.join(' '));
        keywords.add(nameParts.join('-'));
    }

    const filtered = Array.from(keywords).filter(k => k && k.length > 1);
    return filtered.sort((a, b) => {
        const aInName = nameEng.includes(a);
        const bInName = nameEng.includes(b);
        if (aInName && !bInName) return -1;
        if (!aInName && bInName) return 1;
        return b.length - a.length;
    }).slice(0, 10);
  };

  const handleParse = (manualData) => {
    const dataToParse = (typeof manualData === 'string') ? manualData : bulkData;
    if (!dataToParse.trim()) return;

    // Split by "Product Name:" (case insensitive, supporting various formats)
    const blocks = dataToParse.split(/Product Name\s*[:：]/i).filter(b => b.trim().length > 10);

    const labels = [
        'Chemical Name', 'Manufacturer',
        'CIB&RC Registration Number', 'हे औषध काय काम करतं',
        'कोणती समस्या सोडवतं', 'कोणत्या पिकावर', 'कोणत्या टप्प्यात',
        'मात्रा', 'प्रति एकर', 'कसं वापरायचं', 'काय बदल दिसेल',
        'किती दिवसात', 'काय करू नये', 'पाऊस / हवामान',
        'इतर औषधांसोबत', 'PHI'
    ];

    const results = blocks.map(block => {
      // Re-add "Product Name:" for parsing since it was the delimiter
      const fullBlock = "Product Name: " + block;

      const extractValue = (targetLabel) => {
        const escapedLabel = targetLabel.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
        const labelPattern = labels.concat(['Product Name']).map(l => l.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('|');
        const regex = new RegExp(`${escapedLabel}\\s*[:：]\\s*((?:.(?!${labelPattern}|[🎯🌱🧪⚡⚠️•]))*)`, 'i');
        const match = fullBlock.match(regex);
        return match ? match[1].trim() : '';
      };

      const name = extractValue('Product Name');
      const company = extractValue('Manufacturer');
      const chemical = extractValue('Chemical Name');
      const crop = extractValue('हे औषध काय काम करतं');
      const disease = extractValue('कोणती समस्या सोडवतं');
      const marathi = extractValue('कोणत्या पिकावर');
      const usage = extractValue('मात्रा');

      if (!name) return null;

      const keywords = generateOcrKeywords(name, company, chemical);
      const duplicate = isDuplicate(name);

      return {
        medicineName: name,
        company: company || 'Unknown',
        crop: crop,
        disease: disease,
        marathiInfo: marathi,
        usage: usage,
        ocrKeywords: keywords,
        barcodePrefixes: [],
        imageUrls: [],
        audioUrls: '',
        createdAt: new Date(),
        updatedAt: new Date(),
        isDuplicate: duplicate
      };
    }).filter(m => m !== null);

    setParsedMedicines(results);

    const duplicateCount = results.filter(m => m.isDuplicate).length;
    if (duplicateCount > 0) {
        alert(`Detected ${duplicateCount} duplicate medicines. These will be highlighted and excluded from the import.`);
    }
  };

  const handleFileUpload = (e) => {
    const file = e.target.files[0];
    if (!file) return;

    const reader = new FileReader();
    const extension = file.name.split('.').pop().toLowerCase();

    if (extension === 'xlsx' || extension === 'xls') {
      reader.onload = (evt) => {
        const bstr = evt.target.result;
        const wb = XLSX.read(bstr, { type: 'binary' });
        const wsname = wb.SheetNames[0];
        const ws = wb.Sheets[wsname];
        const data = XLSX.utils.sheet_to_json(ws, { header: 1 });

        // Convert Excel rows back to a big string for the parser
        const textContent = data.map(row => row.join(' ')).join('\n\n');
        setBulkData(textContent);
        handleParse(textContent);
      };
      reader.readAsBinaryString(file);
    } else if (extension === 'docx') {
        alert("Word file (.docx) support requires an additional library (mammoth). Please copy-paste the content into the text area for now, or use an Excel file.");
    } else {
      alert("Unsupported file type. Please upload .xlsx, .xls, or .docx");
    }
  };

  const handleSave = async () => {
    const toSave = parsedMedicines.filter(m => !m.isDuplicate);
    if (toSave.length === 0) {
        alert("No new medicines to save (all detected are duplicates or invalid).");
        return;
    }

    setIsProcessing(true);
    try {
        await onSaveAll(toSave);
        onCancel();
    } catch (error) {
        alert("Error saving: " + error.message);
    }
    setIsProcessing(false);
  };

  return (
    <div style={{ padding: '20px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '30px' }}>
        <div>
          <h2 style={{ margin: 0, color: '#1e293b' }}>🚀 Bulk Medicine Importer</h2>
          <p style={{ color: '#64748b', margin: '5px 0 0 0' }}>Paste multiple product info blocks to import them all at once.</p>
        </div>
        <button onClick={onCancel} style={{ ...exportBtnStyle, backgroundColor: '#f1f5f9', color: '#475569' }}>Back to Catalog</button>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: parsedMedicines.length > 0 ? '1fr 1.5fr' : '1fr', gap: '30px' }}>
        {/* Paste Area */}
        <div style={{ backgroundColor: '#f8fafc', padding: '25px', borderRadius: '16px', border: '1px solid #e2e8f0' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '15px' }}>
            <label style={{ ...labelStyle, margin: 0 }}>Paste Bulk Info or Upload File</label>
            <div style={{ display: 'flex', gap: '10px' }}>
                <input
                    type="file"
                    ref={fileInputRef}
                    onChange={handleFileUpload}
                    accept=".xlsx, .xls, .docx"
                    style={{ display: 'none' }}
                />
                <button
                    onClick={() => fileInputRef.current.click()}
                    style={{
                        padding: '6px 12px',
                        backgroundColor: '#fff',
                        border: '1px solid #cbd5e1',
                        borderRadius: '6px',
                        fontSize: '12px',
                        cursor: 'pointer',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '5px'
                    }}
                >
                    📁 Upload Excel/Word
                </button>
            </div>
          </div>
          <textarea
            value={bulkData}
            onChange={(e) => setBulkData(e.target.value)}
            style={{ ...inputStyle, height: '400px', backgroundColor: 'white' }}
            placeholder="Paste multiple medicines info here, or upload a file above..."
          />
          <button
            onClick={handleParse}
            disabled={!bulkData.trim()}
            style={{ ...exportBtnStyle, width: '100%', marginTop: '20px', backgroundColor: '#1a5f7a', opacity: bulkData.trim() ? 1 : 0.6 }}
          >
            🔍 Parse and Preview
          </button>
        </div>

        {/* Preview Area */}
        {parsedMedicines.length > 0 && (
          <div style={{ backgroundColor: 'white', border: '1px solid #e2e8f0', borderRadius: '16px', overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>
            <div style={{ padding: '20px', backgroundColor: '#f1f5f9', borderBottom: '1px solid #e2e8f0', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <h3 style={{ margin: 0, fontSize: '16px' }}>Preview ({parsedMedicines.length} Detected)</h3>
                <small style={{ color: '#ef4444' }}>{parsedMedicines.filter(m => m.isDuplicate).length} duplicates will be skipped</small>
              </div>
              <button
                onClick={handleSave}
                disabled={isProcessing || parsedMedicines.every(m => m.isDuplicate)}
                style={{ ...exportBtnStyle, backgroundColor: '#10b981', opacity: parsedMedicines.every(m => m.isDuplicate) ? 0.5 : 1 }}
              >
                {isProcessing ? 'Saving...' : `✅ Import ${parsedMedicines.filter(m => !m.isDuplicate).length} New Medicines`}
              </button>
            </div>
            <div style={{ overflowY: 'auto', maxHeight: '500px', padding: '20px' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                <thead>
                  <tr style={{ textAlign: 'left', borderBottom: '2px solid #f1f5f9' }}>
                    <th style={{ padding: '12px' }}>Name</th>
                    <th style={{ padding: '12px' }}>Company</th>
                    <th style={{ padding: '12px' }}>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {parsedMedicines.map((m, i) => (
                    <tr key={i} style={{ borderBottom: '1px solid #f1f5f9', backgroundColor: m.isDuplicate ? '#fff1f2' : 'transparent' }}>
                      <td style={{ padding: '12px', fontWeight: 'bold' }}>
                        {m.medicineName}
                        {m.isDuplicate && <div style={{ color: '#e11d48', fontSize: '10px', fontWeight: 'normal' }}>Already exists in catalog</div>}
                      </td>
                      <td style={{ padding: '12px' }}>{m.company}</td>
                      <td style={{ padding: '12px' }}>
                        {m.isDuplicate ?
                            <span style={{ color: '#e11d48', fontWeight: 'bold' }}>🚫 Skip</span> :
                            <span style={{ color: '#10b981', fontWeight: 'bold' }}>🆕 New</span>
                        }
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default BulkMedicineImporter;
