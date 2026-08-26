import React, { useState, useEffect } from 'react';
import {
  X,
  Upload,
  Calendar,
  Building,
  CheckCircle2,
  AlertCircle,
  Loader2,
  Image as ImageIcon,
  Monitor
} from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import { createAdvertisement, updateAdvertisement } from '../../services/ad/adService';
import { Timestamp } from 'firebase/firestore';

const AdForm = ({ ad, onClose, onSuccess }) => {
  const { shopkeeperData } = useAuth();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [preview, setPreview] = useState(ad?.imageUrl || null);
  const [imageFile, setImageFile] = useState(null);

  const [formData, setFormData] = useState({
    name: ad?.name || '',
    advertiserName: ad?.advertiserName || '',
    type: ad?.type || 'image',
    videoUrl: ad?.videoUrl || '',
    startAt: ad?.startAt?.toDate().toISOString().split('T')[0] || new Date().toISOString().split('T')[0],
    endAt: ad?.endAt?.toDate().toISOString().split('T')[0] || new Date(Date.now() + 14 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
    priority: ad?.priority || 2,
    duration: ad?.duration || 15,
    enabled: ad?.enabled !== false,
    description: ad?.description || '',
  });

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
  };

  const handleImageChange = (e) => {
    const file = e.target.files[0];
    if (!file) return;

    // Basic Validation
    const validTypes = formData.type === 'image'
      ? ['image/jpeg', 'image/jpg', 'image/png', 'image/webp']
      : ['video/mp4', 'video/webm', 'video/ogg'];

    if (!validTypes.includes(file.type)) {
      setError(`Invalid file type. Please upload ${formData.type === 'image' ? 'JPG, PNG or WEBP' : 'MP4, WEBM or OGG'}.`);
      return;
    }

    if (file.size > (formData.type === 'image' ? 15 : 50) * 1024 * 1024) {
      setError(`File too large. Maximum size is ${formData.type === 'image' ? '15MB' : '50MB'}.`);
      return;
    }

    setError('');
    setImageFile(file);
    setPreview(URL.createObjectURL(file));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!shopkeeperData?.shopId) {
      setError("Critical Error: Shop ID not found. Try logging out and back in.");
      return;
    }

    if (!preview && !imageFile) {
      setError('Please upload a banner image.');
      return;
    }

    const start = new Date(formData.startAt);
    const end = new Date(formData.endAt);

    if (end <= start) {
      setError('End date must be after the start date.');
      return;
    }

    setLoading(true);
    setError('');

    try {
      if (ad && ad.createdBy === 'admin') {
        throw new Error("System advertisements cannot be modified.");
      }

      const payload = {
        ...formData,
        startAt: Timestamp.fromDate(start),
        endAt: Timestamp.fromDate(end),
        priority: Number(formData.priority),
        duration: Number(formData.duration),
        createdBy: shopkeeperData.id || "unknown"
      };

      console.log("DEBUG: Submitting ad for shop:", shopkeeperData.shopId, payload);

      if (ad) {
        await updateAdvertisement(shopkeeperData.shopId, ad.adId, payload, imageFile);
      } else {
        await createAdvertisement(shopkeeperData.shopId, payload, imageFile);
      }

      console.log("DEBUG: Submission success!");
      onSuccess();
    } catch (err) {
      console.error("DEBUG: Submission failed:", err);
      setError(`Failed: ${err.message}`);
    }
    setLoading(false);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-0 md:p-4 bg-slate-900/90 md:backdrop-blur-md overflow-hidden">
      <div className="bg-white w-full h-full md:h-auto md:max-w-5xl md:max-h-[90vh] md:rounded-[3rem] shadow-2xl flex flex-col animate-in fade-in zoom-in-95 duration-300">

        <div className="p-6 md:p-8 border-b border-slate-100 flex items-center justify-between bg-white sticky top-0 z-10">
          <div className="flex items-center gap-4">
            <div className="w-12 h-12 bg-primary/10 rounded-2xl flex items-center justify-center text-primary shadow-inner">
              <Upload size={24} />
            </div>
            <div>
              <h3 className="font-black text-xl md:text-2xl text-slate-800 tracking-tight">{ad ? 'Edit' : 'New'} Advertisement</h3>
              <p className="text-slate-400 text-[10px] font-black uppercase tracking-widest hidden sm:block">Visual Asset Control</p>
            </div>
          </div>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-600 bg-slate-50 p-3 rounded-2xl border border-slate-100 transition-all active:scale-90">
            <X size={24} />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="flex-1 overflow-y-auto p-6 md:p-12 space-y-10">
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-10 md:gap-16">

            <div className="space-y-10">
              <div className="space-y-6">
                <h4 className="text-[10px] font-black text-slate-400 uppercase tracking-[0.2em] flex items-center gap-3">
                  <Monitor size={16} />
                  Creative Asset
                </h4>

                <div className="aspect-video bg-slate-900 rounded-[2rem] overflow-hidden border-8 border-white shadow-2xl relative group">
                  {formData.type === 'video' ? (
                    <div className="w-full h-full flex flex-col items-center justify-center text-slate-400">
                      {preview ? (
                        <video src={preview} className="w-full h-full object-contain" controls />
                      ) : (
                        <>
                          <Monitor size={48} className="mb-4 opacity-20 text-white" />
                          <p className="text-xs font-black text-white uppercase tracking-widest">Select Video Clip</p>
                        </>
                      )}
                    </div>
                  ) : preview ? (
                    <img src={preview} alt="preview" className="w-full h-full object-contain" />
                  ) : (
                    <div className="w-full h-full flex flex-col items-center justify-center text-slate-400 p-8 text-center bg-slate-50">
                      <ImageIcon size={48} className="mb-4 opacity-20" />
                      <p className="text-xs font-black uppercase tracking-[0.2em] text-slate-300">Upload Banner Image</p>
                    </div>
                  )}

                  <label className="absolute inset-0 bg-slate-900/60 opacity-0 group-hover:opacity-100 transition-opacity flex flex-col items-center justify-center text-white cursor-pointer backdrop-blur-sm">
                    <div className="w-16 h-16 bg-white/20 rounded-full flex items-center justify-center mb-4">
                        <Upload size={32} />
                    </div>
                    <span className="font-black uppercase tracking-widest text-sm">Update Media</span>
                    <input type="file" accept={formData.type === 'image' ? "image/*" : "video/*"} onChange={handleImageChange} className="hidden" />
                  </label>
                </div>
              </div>

              <div className="bg-slate-50 p-8 rounded-[2rem] border border-slate-100 space-y-8">
                 <div>
                    <label className="block text-[10px] uppercase tracking-[0.2em] text-slate-400 font-black mb-4">Content Category</label>
                    <div className="flex p-1.5 bg-white rounded-2xl border border-slate-200 gap-1 shadow-sm">
                        <button
                          type="button"
                          onClick={() => { setFormData({...formData, type: 'image'}); setPreview(null); setImageFile(null); }}
                          className={`flex-1 py-3 rounded-xl font-black text-[10px] uppercase tracking-widest transition-all ${
                            formData.type === 'image'
                              ? 'bg-slate-900 text-white shadow-lg'
                              : 'text-slate-400 hover:bg-slate-50'
                          }`}
                        >
                          Still Image
                        </button>
                        <button
                          type="button"
                          onClick={() => { setFormData({...formData, type: 'video'}); setPreview(null); setImageFile(null); }}
                          className={`flex-1 py-3 rounded-xl font-black text-[10px] uppercase tracking-widest transition-all ${
                            formData.type === 'video'
                              ? 'bg-slate-900 text-white shadow-lg'
                              : 'text-slate-400 hover:bg-slate-50'
                          }`}
                        >
                          Video Motion
                        </button>
                    </div>
                 </div>

                 <div className="space-y-4">
                    <label className="block text-[10px] uppercase tracking-[0.2em] text-slate-400 font-black">Display Loop (Seconds)</label>
                    <div className="flex items-center gap-6 bg-white border border-slate-200 rounded-2xl p-4 shadow-sm">
                      <input
                        type="range"
                        min="5"
                        max="60"
                        step="5"
                        value={formData.duration}
                        onChange={(e) => setFormData({...formData, duration: parseInt(e.target.value)})}
                        className="flex-1 accent-slate-900 h-1.5 bg-slate-100 rounded-lg appearance-none cursor-pointer"
                      />
                      <span className="w-16 h-12 flex items-center justify-center font-black text-slate-800 bg-slate-50 rounded-xl border border-slate-100 text-lg">
                        {formData.duration}s
                      </span>
                    </div>
                 </div>
              </div>
            </div>

            <div className="space-y-8">
              <div className="space-y-6">
                <div>
                  <label className="block text-[10px] uppercase tracking-[0.2em] text-slate-400 font-black mb-3 ml-1">Internal Reference Name</label>
                  <input
                    name="name"
                    value={formData.name}
                    onChange={handleChange}
                    required
                    placeholder="e.g. Monsoon Blast Offer"
                    className="w-full px-6 py-5 bg-slate-50 border border-slate-100 rounded-2xl focus:ring-4 focus:ring-primary/10 focus:border-primary focus:bg-white outline-none transition-all font-black text-slate-800 tracking-tight h-16"
                  />
                </div>

                <div>
                  <label className="block text-[10px] uppercase tracking-[0.2em] text-slate-400 font-black mb-3 ml-1">Affiliated Brand / Company</label>
                  <div className="relative">
                    <div className="absolute inset-y-0 left-0 pl-6 flex items-center pointer-events-none text-slate-300">
                      <Building size={20} />
                    </div>
                    <input
                      name="advertiserName"
                      value={formData.advertiserName}
                      onChange={handleChange}
                      required
                      placeholder="e.g. Mahadhan Fertilizers"
                      className="w-full pl-14 pr-6 py-5 bg-slate-50 border border-slate-100 rounded-2xl focus:ring-4 focus:ring-primary/10 focus:border-primary focus:bg-white outline-none transition-all font-bold h-16"
                    />
                  </div>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">
                  <div>
                    <label className="block text-[10px] uppercase tracking-[0.2em] text-slate-400 font-black mb-3 ml-1">Live Date</label>
                    <input
                      type="date"
                      name="startAt"
                      value={formData.startAt}
                      onChange={handleChange}
                      required
                      className="w-full px-6 py-5 bg-slate-50 border border-slate-100 rounded-2xl focus:ring-4 focus:ring-primary/10 outline-none font-bold h-16"
                    />
                  </div>
                  <div>
                    <label className="block text-[10px] uppercase tracking-[0.2em] text-slate-400 font-black mb-3 ml-1">Expiry Date</label>
                    <input
                      type="date"
                      name="endAt"
                      value={formData.endAt}
                      onChange={handleChange}
                      required
                      className="w-full px-6 py-5 bg-slate-50 border border-slate-100 rounded-2xl focus:ring-4 focus:ring-primary/10 outline-none font-bold h-16"
                    />
                  </div>
                </div>

                <div>
                  <label className="block text-[10px] uppercase tracking-[0.2em] text-slate-400 font-black mb-3 ml-1">Campaign Notes (Optional)</label>
                  <textarea
                    name="description"
                    value={formData.description}
                    onChange={handleChange}
                    rows="3"
                    placeholder="Provide additional context for this promotion..."
                    className="w-full px-6 py-5 bg-slate-50 border border-slate-100 rounded-2xl focus:ring-4 focus:ring-primary/10 outline-none transition-all text-sm font-medium min-h-[120px]"
                  ></textarea>
                </div>
              </div>

              {error && (
                <div className="p-6 bg-red-50 text-red-600 rounded-[1.5rem] text-xs font-black uppercase tracking-widest border border-red-100 flex gap-4 animate-bounce">
                  <AlertCircle size={20} className="shrink-0" />
                  {error}
                </div>
              )}
            </div>
          </div>
        </form>

        <div className="p-6 md:p-8 bg-slate-50 border-t border-slate-100 flex flex-col sm:flex-row gap-4 sticky bottom-0 z-10">
          <button
            type="button"
            onClick={onClose}
            className="flex-1 py-5 bg-white text-slate-500 font-black uppercase tracking-[0.2em] text-xs rounded-2xl hover:bg-slate-100 transition-all border border-slate-200 active:scale-95 h-16"
          >
            Cancel
          </button>
          <button
            onClick={handleSubmit}
            disabled={loading}
            className="flex-[2] bg-primary hover:bg-primary-dark text-white font-black uppercase tracking-[0.2em] text-xs py-5 rounded-2xl transition-all shadow-xl shadow-primary/20 flex items-center justify-center gap-3 disabled:opacity-50 active:scale-[0.98] h-16"
          >
            {loading ? <Loader2 className="animate-spin" /> : <CheckCircle2 size={20} />}
            <span>{ad ? 'Update Campaign' : 'Launch Promotion'}</span>
          </button>
        </div>
      </div>
    </div>
  );
};

export default AdForm;
