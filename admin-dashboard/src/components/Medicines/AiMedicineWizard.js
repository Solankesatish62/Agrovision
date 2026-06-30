import React, { useState } from 'react';
import { db, storage } from '../../firebase';
import { doc, setDoc, serverTimestamp } from 'firebase/firestore';
import { ref, uploadBytes, getDownloadURL } from 'firebase/storage';
import { labelStyle, inputStyle, exportBtnStyle, COLORS, SHADOWS } from '../Shared/Styles';
import { fetchMedicineAiInfo } from '../../services/geminiService';

const AiMedicineWizard = () => {
  const [step, setStep] = useState(1);
  const [loading, setLoading] = useState(false);
  const [productName, setProductName] = useState('');
  const [companyName, setCompanyName] = useState('');
  const [targetCrop, setTargetCrop] = useState('');
  const [activeIngredient, setActiveIngredient] = useState('');
  const [cibNo, setCibNo] = useState('');
  const [formulation, setFormulation] = useState('');
  const [scannedImage, setScannedImage] = useState(null);

  const [aiData, setAiData] = useState({
    working_mr: '',
    problem_mr: '',
    crops_mr: '',
    dosage: '',
    ocr_keywords: [],
    searchQueries: {
      product: '',
      problem: '',
      solution: ''
    }
  });

  const [selectedImages, setSelectedImages] = useState([
    { category: 'product', url: '', preview: '' },
    { category: 'problem', url: '', preview: '' },
    { category: 'solution', url: '', preview: '' },
    { category: 'solution', url: '', preview: '' }
  ]);

  const fileToBase64 = (file) => {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.readAsDataURL(file);
      reader.onload = () => resolve(reader.result.split(',')[1]);
      reader.onerror = (error) => reject(error);
    });
  };

  const handleImageScan = async (file) => {
    if (!file) return;
    setLoading(true);
    try {
      const base64 = await fileToBase64(file);
      const data = await fetchMedicineAiInfo('', '', targetCrop, '', '', base64);

      // Auto-fill extracted info
      setProductName(data.extracted_name || '');
      setCompanyName(data.extracted_company || '');
      setActiveIngredient(data.extracted_ingredient || '');
      setFormulation(data.extracted_formulation || '');

      // Set the rest of the AI data
      setAiData({
        working_mr: data.working_mr,
        problem_mr: data.problem_mr,
        crops_mr: data.crops_mr,
        dosage: data.dosage_mr,
        ocr_keywords: data.ocr_keywords || [],
        searchQueries: {
          product: data.search_query_product,
          problem: data.search_query_problem,
          solution: data.search_query_solution
        }
      });
      setStep(2);
    } catch (error) {
      console.error('Scan Error:', error);
      alert("Error scanning image: " + error.message);
    }
    setLoading(false);
  };

  const handleFetchAiInfo = async () => {
    if (!productName || !companyName) {
      alert('Please enter at least Product and Company name');
      return;
    }
    setLoading(true);
    try {
      const data = await fetchMedicineAiInfo(productName, companyName, targetCrop, activeIngredient, formulation);
      setAiData({
        working_mr: data.working_mr,
        problem_mr: data.problem_mr,
        crops_mr: data.crops_mr,
        dosage: data.dosage_mr,
        ocr_keywords: data.ocr_keywords || [],
        searchQueries: {
          product: data.search_query_product,
          problem: data.search_query_problem,
          solution: data.search_query_solution
        }
      });
      setStep(2);
    } catch (error) {
      console.error('API Error:', error);
      alert("Error fetching AI data: " + error.message);
    }
    setLoading(false);
  };

  const openSearch = (query) => {
    window.open(`https://www.google.com/search?q=${encodeURIComponent(query)}&tbm=isch`, '_blank');
  };

  const handleImageUrlChange = (index, url) => {
    const newImages = [...selectedImages];
    newImages[index] = { ...newImages[index], url, preview: url };
    setSelectedImages(newImages);
  };

  const handleKeywordChange = (index, value) => {
    const newKeywords = [...aiData.ocr_keywords];
    newKeywords[index] = value;
    setAiData({ ...aiData, ocr_keywords: newKeywords });
  };

  const removeKeyword = (index) => {
    setAiData({ ...aiData, ocr_keywords: aiData.ocr_keywords.filter((_, i) => i !== index) });
  };

  const handleSave = async () => {
    setLoading(true);
    try {
      const medicineId = productName.trim().replace(/\s+/g, '_').toLowerCase();
      const uploadedImageUrls = await Promise.all(
        selectedImages.map(async (img, idx) => {
          if (!img.url) return null;
          try {
            const response = await fetch(img.url);
            const blob = await response.blob();
            const storageRef = ref(storage, `medicines/${medicineId}/${img.category}_${idx}.jpg`);
            await uploadBytes(storageRef, blob);
            return await getDownloadURL(storageRef);
          } catch (err) {
            console.error('Error processing image:', img.url, err);
            return img.url;
          }
        })
      );
      const finalImageUrls = uploadedImageUrls.filter(url => url !== null);
      const marathiInfoCombined = `१. औषधाचे कार्य: ${aiData.working_mr}\n२. समस्या: ${aiData.problem_mr}\n३. पिके: ${aiData.crops_mr}\n४. मात्रा: ${aiData.dosage}`;
      const dataToSave = {
        name: productName,
        company: companyName,
        cibNo: cibNo,
        chemicalName: activeIngredient, // Map active ingredient to chemicalName
        crop: targetCrop,
        activeIngredient: activeIngredient,
        formulation: formulation,
        marathiInfo: marathiInfoCombined,
        imageUrls: finalImageUrls,
        searchKeywords: aiData.ocr_keywords.filter(k => k && k.trim() !== ''),
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
        status: 'approved',
        source: 'AI_WIZARD'
      };
      await setDoc(doc(db, 'approved_medicines', medicineId), dataToSave);
      alert('Medicine saved successfully!');
      setStep(1);
      resetForm();
    } catch (error) {
      console.error('Error saving:', error);
      alert('Error saving medicine: ' + error.message);
    }
    setLoading(false);
  };

  const resetForm = () => {
    setProductName('');
    setCompanyName('');
    setTargetCrop('');
    setActiveIngredient('');
    setFormulation('');
    setAiData({ working_mr: '', problem_mr: '', crops_mr: '', dosage: '', ocr_keywords: [], searchQueries: { product: '', problem: '', solution: '' } });
    setSelectedImages([
        { category: 'product', url: '', preview: '' },
        { category: 'problem', url: '', preview: '' },
        { category: 'solution', url: '', preview: '' },
        { category: 'solution', url: '', preview: '' }
    ]);
  };

  return (
    <div style={{ padding: '20px' }}>
      <div style={{ marginBottom: '30px', textAlign: 'center' }}>
        <h2 style={{ color: COLORS.primary, margin: '0 0 10px 0' }}>✨ AI Medicine Information Wizard</h2>
        <p style={{ color: COLORS.textMuted }}>Automate your database gathering with AI Vision</p>
      </div>

      {step === 1 && (
        <div style={cardStyle}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px', borderBottom: '1px solid #eee', paddingBottom: '15px' }}>
            <h3 style={{ margin: 0 }}>Step 1: Product Details</h3>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <span style={{ fontSize: '13px', color: COLORS.textMuted }}>Save time:</span>
                <label style={{
                    backgroundColor: COLORS.primary,
                    color: 'white',
                    padding: '8px 16px',
                    borderRadius: '8px',
                    cursor: 'pointer',
                    fontSize: '13px',
                    fontWeight: 'bold',
                    boxShadow: SHADOWS.sm
                }}>
                    📷 Scan Bottle Image
                    <input type="file" accept="image/*" style={{ display: 'none' }} onChange={(e) => handleImageScan(e.target.files[0])} />
                </label>
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px' }}>
            <div>
              <label style={labelStyle}>Medicine/Product Name</label>
              <input style={inputStyle} value={productName} onChange={(e) => setProductName(e.target.value)} placeholder="e.g. Isabion, Coragen" />
            </div>
            <div>
              <label style={labelStyle}>Manufacturing Company</label>
              <input style={inputStyle} value={companyName} onChange={(e) => setCompanyName(e.target.value)} placeholder="e.g. Syngenta, FMC" />
            </div>
            <div>
              <label style={labelStyle}>Active Ingredient / Chemical Name</label>
              <input style={inputStyle} value={activeIngredient} onChange={(e) => setActiveIngredient(e.target.value)} placeholder="e.g. Chlorantraniliprole" />
            </div>
            <div>
              <label style={labelStyle}>CIB&RC Registration Number</label>
              <input style={inputStyle} value={cibNo} onChange={(e) => setCibNo(e.target.value)} placeholder="e.g. CIR-123/2023" />
            </div>
            <div>
              <label style={labelStyle}>Formulation</label>
              <input style={inputStyle} value={formulation} onChange={(e) => setFormulation(e.target.value)} placeholder="e.g. 18.5% SC" />
            </div>
            <div style={{ gridColumn: 'span 2' }}>
              <label style={labelStyle}>Primary Target Crop</label>
              <input style={inputStyle} value={targetCrop} onChange={(e) => setTargetCrop(e.target.value)} placeholder="e.g. Tomato, Cotton" />
            </div>
          </div>

          <div style={{ marginTop: '30px', display: 'flex', flexDirection: 'column', gap: '10px' }}>
            <button onClick={handleFetchAiInfo} disabled={loading} style={{ ...exportBtnStyle, width: '100%', height: '50px' }}>
                {loading ? '🤖 AI is analyzing...' : 'Fetch Complete AI Information'}
            </button>
            <p style={{ textAlign: 'center', fontSize: '12px', color: COLORS.textMuted }}>
                Tip: Use "Scan Bottle Image" to automatically extract details from a photo.
            </p>
          </div>
        </div>
      )}

      {step === 2 && (
        <div style={{ ...cardStyle, maxWidth: '1200px' }}>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '25px' }}>
            <div style={reviewColumnStyle}>
              <h4 style={columnTitleStyle}>🌍 Farmer Info (Marathi)</h4>
              <div style={fieldGroupStyle}>
                <label style={labelStyle}>१. औषधाचे कार्य</label>
                <textarea style={{ ...inputStyle, height: '70px' }} value={aiData.working_mr} onChange={(e) => setAiData({...aiData, working_mr: e.target.value})} />
                <label style={labelStyle}>२. निवारण (Problems)</label>
                <textarea style={{ ...inputStyle, height: '70px' }} value={aiData.problem_mr} onChange={(e) => setAiData({...aiData, problem_mr: e.target.value})} />
                <label style={labelStyle}>३. शिफारसीत पिके</label>
                <input style={inputStyle} value={aiData.crops_mr} onChange={(e) => setAiData({...aiData, crops_mr: e.target.value})} />
                <label style={labelStyle}>४. मात्रा (Dosage)</label>
                <input style={inputStyle} value={aiData.dosage} onChange={(e) => setAiData({...aiData, dosage: e.target.value})} />
              </div>
            </div>

            <div style={reviewColumnStyle}>
              <h4 style={columnTitleStyle}>🔍 OCR Recognition Keywords</h4>
              <div style={{ maxHeight: '430px', overflowY: 'auto', paddingRight: '5px', display: 'flex', flexWrap: 'wrap', gap: '5px' }}>
                {aiData.ocr_keywords.map((kw, idx) => (
                  <div key={idx} style={tagStyle}>
                    <input
                      value={kw}
                      onChange={(e) => handleKeywordChange(idx, e.target.value)}
                      style={{ border: 'none', background: 'none', fontSize: '12px', width: 'auto', outline: 'none' }}
                    />
                    <span onClick={() => removeKeyword(idx)} style={{ cursor: 'pointer', marginLeft: '5px', color: '#ef4444' }}>×</span>
                  </div>
                ))}
              </div>
            </div>

            <div style={reviewColumnStyle}>
              <h4 style={columnTitleStyle}>🖼️ Media Assets</h4>
              <div style={{ backgroundColor: '#f0f9ff', padding: '10px', borderRadius: '8px', marginBottom: '15px', fontSize: '11px', display: 'flex', gap: '5px' }}>
                <button onClick={() => openSearch(aiData.searchQueries.product)} style={searchBtnStyle}>Search Bottle</button>
                <button onClick={() => openSearch(aiData.searchQueries.problem)} style={searchBtnStyle}>Search Problem</button>
                <button onClick={() => openSearch(aiData.searchQueries.solution)} style={searchBtnStyle}>Search Healthy</button>
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
                {selectedImages.map((img, idx) => (
                  <div key={idx} style={{ border: '1px solid #e2e8f0', borderRadius: '8px', padding: '8px' }}>
                    <label style={{ fontSize: '10px', fontWeight: 'bold', color: COLORS.textMuted }}>{img.category}</label>
                    <input
                      style={{ ...inputStyle, fontSize: '10px', padding: '5px', marginTop: '3px' }}
                      placeholder="Paste Image URL"
                      value={img.url}
                      onChange={(e) => handleImageUrlChange(idx, e.target.value)}
                    />
                    {img.preview && <img src={img.preview} alt="prev" style={{ width: '100%', height: '50px', objectFit: 'cover', marginTop: '5px', borderRadius: '4px' }} />}
                  </div>
                ))}
              </div>
            </div>
          </div>

          <div style={{ marginTop: '30px', display: 'flex', gap: '15px' }}>
            <button onClick={() => setStep(1)} style={{ padding: '12px 25px', backgroundColor: '#f1f5f9', color: '#475569', border: 'none', borderRadius: '10px', cursor: 'pointer', fontWeight: 'bold' }}>Back</button>
            <button onClick={handleSave} disabled={loading} style={{ ...exportBtnStyle, flex: 1, backgroundColor: COLORS.secondary }}>
              {loading ? 'Processing & Saving...' : 'Confirm & Save Medicine'}
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

const cardStyle = { backgroundColor: 'white', padding: '30px', borderRadius: '20px', boxShadow: SHADOWS.md, border: `1px solid ${COLORS.border}`, maxWidth: '800px', margin: '0 auto' };
const reviewColumnStyle = { display: 'flex', flexDirection: 'column', gap: '10px' };
const columnTitleStyle = { margin: '0 0 15px 0', fontSize: '16px', color: COLORS.primary, borderBottom: `2px solid ${COLORS.secondary}`, display: 'inline-block', paddingBottom: '5px' };
const fieldGroupStyle = { display: 'flex', flexDirection: 'column', gap: '10px' };
const tagStyle = { backgroundColor: '#f1f5f9', padding: '4px 8px', borderRadius: '15px', display: 'flex', alignItems: 'center', border: '1px solid #e2e8f0' };
const searchBtnStyle = { padding: '5px 10px', fontSize: '10px', backgroundColor: '#3b82f6', color: 'white', border: 'none', borderRadius: '4px', cursor: 'pointer', flex: 1 };

export default AiMedicineWizard;
