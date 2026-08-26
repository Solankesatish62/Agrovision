import React, { useState, useEffect } from 'react';
import { useAuth } from '../../context/AuthContext';
import { db } from '../../services/firebase/config';
import { doc, getDoc, setDoc, serverTimestamp } from 'firebase/firestore';
import Layout from '../../components/Layout';
import {
  Store,
  User,
  Phone,
  MapPin,
  Globe,
  ShieldCheck,
  Languages,
  Save,
  AlertCircle,
  CheckCircle2,
  Upload,
  Image as ImageIcon,
  Edit2
} from 'lucide-react';
import { uploadShopLogo, updateShopProfile, updateShopBranding } from '../../services/ad/adService';

const MyShop = () => {
  const { shopkeeperData } = useAuth();
  const [shop, setShop] = useState(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [isEditingProfile, setIsEditingProfile] = useState(false);
  const [message, setMessage] = useState({ type: '', text: '' });

  const [formData, setFormData] = useState({
    shopName: '',
    ownerName: '',
    address: '',
    district: '',
    state: '',
    preferredLanguage: 'mr',
    shopDisplayName: '',
    shopBannerUrl: '',
    shopBrandingType: 'text'
  });
  const [logoFile, setLogoFile] = useState(null);
  const [logoPreview, setLogoPreview] = useState(null);

  useEffect(() => {
    const fetchShop = async () => {
      if (shopkeeperData?.shopId) {
        try {
          const shopDoc = await getDoc(doc(db, 'shops', shopkeeperData.shopId));
          if (shopDoc.exists()) {
            const data = shopDoc.data();
            setShop(data);
            setFormData({
              shopName: data.shopName || '',
              ownerName: data.ownerName || '',
              address: data.address || '',
              district: data.district || '',
              state: data.state || '',
              preferredLanguage: data.preferredLanguage || 'mr',
              shopDisplayName: data.shopDisplayName || '',
              shopBannerUrl: data.shopBannerUrl || '',
              shopBrandingType: data.shopBrandingType || 'text'
            });
            setLogoPreview(data.shopBannerUrl || null);
          }
        } catch (error) {
          console.error("Error fetching shop:", error);
        }
      }
      setLoading(false);
    };
    fetchShop();
  }, [shopkeeperData]);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
  };

  const handleLogoChange = (e) => {
    const file = e.target.files[0];
    if (file) {
      setLogoFile(file);
      setLogoPreview(URL.createObjectURL(file));
    }
  };

  const handleProfileSubmit = async (e) => {
    e.preventDefault();
    setSaving(true);
    setMessage({ type: '', text: '' });

    try {
      const profileFields = {
        shopName: formData.shopName,
        ownerName: formData.ownerName,
        address: formData.address,
        district: formData.district,
        state: formData.state,
        preferredLanguage: formData.preferredLanguage
      };

      await updateShopProfile(shopkeeperData.shopId, profileFields);
      setIsEditingProfile(false);
      setMessage({ type: 'success', text: 'Shop profile updated successfully!' });
      setShop(prev => ({ ...prev, ...profileFields }));
    } catch (error) {
      console.error("Error updating profile:", error);
      setMessage({ type: 'error', text: 'Failed to update profile.' });
    }
    setSaving(false);
  };

  const handleBrandingSubmit = async (e) => {
    e.preventDefault();
    setSaving(true);
    setMessage({ type: '', text: '' });

    try {
      const brandingFields = {
        shopDisplayName: formData.shopDisplayName,
        shopBannerUrl: formData.shopBannerUrl,
        shopBrandingType: formData.shopBrandingType
      };

      const finalLogoUrl = await updateShopBranding(shopkeeperData.shopId, brandingFields, logoFile);

      setFormData(prev => ({ ...prev, shopBannerUrl: finalLogoUrl }));
      setLogoFile(null);
      setMessage({ type: 'success', text: 'Branding updated! Changes will sync to Kiosk soon.' });
    } catch (error) {
      console.error("Error updating branding:", error);
      setMessage({ type: 'error', text: 'Failed to update branding.' });
    }
    setSaving(false);
  };

  if (loading) {
    return (
      <Layout>
        <div className="flex items-center justify-center h-64">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary"></div>
        </div>
      </Layout>
    );
  }

  const inputStyle = "w-full px-4 py-3 bg-slate-50 border border-slate-200 rounded-xl focus:ring-2 focus:ring-primary outline-none transition-all text-slate-700 font-medium";
  const labelStyle = "block text-xs uppercase tracking-widest text-slate-400 font-bold mb-2 ml-1";
  const readonlyStyle = "w-full px-4 py-3 bg-slate-100 border border-slate-200 rounded-xl text-slate-400 font-mono text-sm cursor-not-allowed";

  const ProfileItem = ({ label, value, icon: Icon }) => (
    <div className="flex items-center gap-4 p-4 bg-slate-50/50 rounded-2xl border border-slate-100">
      <div className="w-10 h-10 bg-white rounded-xl flex items-center justify-center text-slate-400 shadow-sm">
        <Icon size={18} />
      </div>
      <div>
        <p className="text-[10px] font-black uppercase text-slate-400 tracking-widest leading-none mb-1">{label}</p>
        <p className="text-sm font-bold text-slate-700">{value || '—'}</p>
      </div>
    </div>
  );

  return (
    <Layout>
      <div className="mb-10">
        <h2 className="text-[10px] uppercase tracking-[0.2em] text-primary font-black mb-1">Identity & Branding</h2>
        <h1 className="text-3xl md:text-4xl font-black text-slate-800 tracking-tight leading-none">My Shop</h1>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 md:gap-10">
        <div className="lg:col-span-2 space-y-10">

          {/* Shop Profile Section */}
          <div className="bg-white rounded-[3rem] p-8 md:p-12 shadow-sm border border-slate-50">
             <div className="flex items-center justify-between mb-10">
                <h3 className="text-2xl font-black text-slate-800 tracking-tight">Business Profile</h3>
                {!isEditingProfile && (
                    <button
                        onClick={() => setIsEditingProfile(true)}
                        className="flex items-center gap-2 px-5 py-2.5 bg-slate-100 text-slate-600 rounded-2xl font-black text-[10px] uppercase tracking-widest hover:bg-slate-200 transition-colors"
                    >
                        <Edit2 size={14} />
                        Update Info
                    </button>
                )}
             </div>

             {message.text && (
              <div className={`mb-10 p-6 rounded-[2rem] flex items-center gap-4 animate-in fade-in slide-in-from-top-4 duration-300 ${
                message.type === 'success' ? 'bg-green-50 text-green-700 border border-green-100' : 'bg-red-50 text-red-700 border border-red-100'
              }`}>
                <div className={`w-10 h-10 rounded-xl flex items-center justify-center ${message.type === 'success' ? 'bg-green-100' : 'bg-red-100'}`}>
                    {message.type === 'success' ? <CheckCircle2 size={22} /> : <AlertCircle size={22} />}
                </div>
                <p className="text-sm font-black uppercase tracking-widest">{message.text}</p>
              </div>
            )}

             {!isEditingProfile ? (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div className="md:col-span-2">
                        <ProfileItem label="Business Name" value={shop?.shopName} icon={Store} />
                    </div>
                    <ProfileItem label="Owner Name" value={shop?.ownerName} icon={User} />
                    <ProfileItem label="Interface Language" value={formData.preferredLanguage === 'mr' ? 'Marathi' : formData.preferredLanguage === 'hi' ? 'Hindi' : 'English'} icon={Languages} />
                    <div className="md:col-span-2">
                        <ProfileItem label="Physical Address" value={shop?.address} icon={MapPin} />
                    </div>
                    <ProfileItem label="District" value={shop?.district} icon={MapPin} />
                    <ProfileItem label="State" value={shop?.state} icon={Globe} />
                </div>
             ) : (
                <form onSubmit={handleProfileSubmit} className="space-y-8 animate-in fade-in duration-500">
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                        <div className="md:col-span-2">
                            <label className={labelStyle}>Business Name</label>
                            <input name="shopName" value={formData.shopName} onChange={handleChange} required className={`${inputStyle} h-16 rounded-2xl`} />
                        </div>
                        <div>
                            <label className={labelStyle}>Owner Name</label>
                            <input name="ownerName" value={formData.ownerName} onChange={handleChange} required className={`${inputStyle} h-16 rounded-2xl`} />
                        </div>
                        <div>
                            <label className={labelStyle}>Interface Language</label>
                            <select name="preferredLanguage" value={formData.preferredLanguage} onChange={handleChange} className={`${inputStyle} h-16 rounded-2xl appearance-none`}>
                                <option value="en">English</option>
                                <option value="mr">Marathi</option>
                                <option value="hi">Hindi</option>
                            </select>
                        </div>
                        <div className="md:col-span-2">
                            <label className={labelStyle}>Physical Address</label>
                            <input name="address" value={formData.address} onChange={handleChange} required className={`${inputStyle} h-16 rounded-2xl`} />
                        </div>
                        <input name="district" value={formData.district} onChange={handleChange} required className={`${inputStyle} h-16 rounded-2xl`} placeholder="District" />
                        <input name="state" value={formData.state} onChange={handleChange} required className={`${inputStyle} h-16 rounded-2xl`} placeholder="State" />
                    </div>
                    <div className="flex gap-4">
                        <button type="submit" disabled={saving} className="flex-1 bg-primary text-white font-black py-4 rounded-2xl uppercase tracking-widest text-xs shadow-lg active:scale-95 disabled:opacity-50">
                            {saving ? 'Saving...' : 'Save Changes'}
                        </button>
                        <button type="button" onClick={() => setIsEditingProfile(false)} className="px-8 bg-slate-100 text-slate-500 font-black py-4 rounded-2xl uppercase tracking-widest text-xs">
                            Cancel
                        </button>
                    </div>
                </form>
             )}
          </div>

          {/* Kiosk Branding Section */}
          <div className="bg-white rounded-[3rem] p-8 md:p-12 shadow-sm border border-slate-50 space-y-10">
                <h3 className="text-2xl font-black text-slate-800 flex items-center gap-3 tracking-tight">
                    <div className="w-10 h-10 bg-orange-100 text-orange-600 rounded-2xl flex items-center justify-center">
                        <ImageIcon size={22} />
                    </div>
                    Kiosk Branding
                </h3>

                <div className="grid grid-cols-1 xl:grid-cols-2 gap-8 bg-slate-50/50 p-6 md:p-10 rounded-[2.5rem] border border-slate-100/50">
                    <div className="space-y-8">
                        <div>
                            <label className={labelStyle}>Branding Mode</label>
                            <div className="flex p-1.5 bg-white rounded-2xl border border-slate-200 gap-1">
                                <button
                                    type="button"
                                    onClick={() => setFormData({...formData, shopBrandingType: 'text'})}
                                    className={`flex-1 py-3.5 rounded-[0.9rem] font-black text-[10px] uppercase tracking-widest transition-all ${
                                        formData.shopBrandingType === 'text'
                                            ? 'bg-slate-900 text-white shadow-lg'
                                            : 'text-slate-400 hover:bg-slate-50'
                                    }`}
                                >
                                    Custom Text
                                </button>
                                <button
                                    type="button"
                                    onClick={() => setFormData({...formData, shopBrandingType: 'poster'})}
                                    className={`flex-1 py-3.5 rounded-[0.9rem] font-black text-[10px] uppercase tracking-widest transition-all ${
                                        formData.shopBrandingType === 'poster'
                                            ? 'bg-slate-900 text-white shadow-lg'
                                            : 'text-slate-400 hover:bg-slate-50'
                                    }`}
                                >
                                    Upload Logo
                                </button>
                            </div>
                        </div>

                        {formData.shopBrandingType === 'text' ? (
                            <div className="animate-in fade-in slide-in-from-left-4 duration-300">
                                <label className={labelStyle}>Header Name</label>
                                <input
                                    name="shopDisplayName"
                                    value={formData.shopDisplayName}
                                    onChange={handleChange}
                                    placeholder="e.g. Krishi Seva Kendra"
                                    className={`${inputStyle} h-16 rounded-2xl`}
                                />
                                <p className="text-[10px] text-slate-400 mt-3 font-bold uppercase tracking-tighter ml-1 italic">Type your shop name exactly as it should appear.</p>
                            </div>
                        ) : (
                            <div className="animate-in fade-in slide-in-from-left-4 duration-300 space-y-4">
                                <label className={labelStyle}>Personal Logo</label>
                                <div className="flex items-center gap-4">
                                    <label className="flex-1 flex items-center justify-center gap-3 px-6 py-4 bg-white border-2 border-dashed border-slate-200 rounded-2xl cursor-pointer hover:border-primary/50 hover:bg-primary/5 transition-all group">
                                        <Upload size={20} className="text-slate-400 group-hover:text-primary" />
                                        <span className="text-sm font-black text-slate-600 group-hover:text-primary uppercase tracking-widest">
                                            {logoFile ? 'Change Logo' : 'Select From Device'}
                                        </span>
                                        <input type="file" accept="image/*" onChange={handleLogoChange} className="hidden" />
                                    </label>
                                    {logoPreview && (
                                        <button
                                            type="button"
                                            onClick={() => { setLogoFile(null); setLogoPreview(null); setFormData(p => ({...p, shopBannerUrl: ''})); }}
                                            className="p-4 bg-red-50 text-red-500 rounded-2xl hover:bg-red-100 transition-colors shadow-sm"
                                        >
                                            <ImageIcon size={20} />
                                        </button>
                                    )}
                                </div>
                                <p className="text-[10px] text-slate-400 font-bold uppercase tracking-tighter ml-1">Transparent PNG works best.</p>
                            </div>
                        )}

                        <button
                            onClick={handleBrandingSubmit}
                            disabled={saving}
                            className="w-full bg-slate-900 text-white font-black py-4 rounded-2xl uppercase tracking-widest text-xs shadow-xl active:scale-95 flex items-center justify-center gap-2"
                        >
                            {saving ? <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin"></div> : <Save size={16} />}
                            Apply Branding
                        </button>
                    </div>

                    <div className="bg-white rounded-[2rem] p-8 border border-slate-100 flex flex-col justify-center shadow-sm relative overflow-hidden h-fit self-center w-full">
                        <p className="text-[10px] font-black text-orange-600 uppercase tracking-[0.2em] text-center mb-6">Device Header Preview</p>
                        <div className="flex items-center justify-center gap-4 py-8 px-6 border-2 border-dashed border-slate-100 rounded-2xl bg-slate-50/50">
                             <div className="flex items-center gap-1 shrink-0 scale-110">
                                <span className="font-black text-lg text-[#006400]">Agro</span>
                                <span className="font-black text-lg text-[#FF8C00]">Vision</span>
                             </div>
                             <div className="w-px h-8 bg-slate-200" />
                             {formData.shopBrandingType === 'text' ? (
                                <span className="font-black text-xl truncate text-slate-700 animate-in fade-in zoom-in-95 duration-300">
                                    {formData.shopDisplayName || 'Shop Name'}
                                </span>
                             ) : (
                                logoPreview ? (
                                    <img src={logoPreview} alt="Logo" className="h-10 object-contain animate-in fade-in zoom-in-95 duration-500" />
                                ) : (
                                    <div className="text-slate-300 font-black text-[10px] uppercase tracking-widest flex items-center gap-2">
                                        <ImageIcon size={14} />
                                        No Logo Selected
                                    </div>
                                )
                             )}
                        </div>
                        <p className="text-[8px] text-slate-400 font-bold uppercase text-center mt-6 tracking-widest">Simulated Kiosk Hardware</p>
                    </div>
                </div>
          </div>
        </div>

        <div className="lg:col-span-1 space-y-6">
          <div className="bg-slate-900 rounded-[3rem] p-10 shadow-2xl text-white">
            <h3 className="text-xl font-black mb-8 flex items-center gap-3 tracking-tight">
              <ShieldCheck size={24} className="text-primary" />
              Security
            </h3>

            <div className="space-y-8">
              <div>
                <label className="block text-[10px] uppercase tracking-widest text-slate-500 font-black mb-3">Shop ID</label>
                <div className="bg-white/5 p-5 rounded-2xl border border-white/10 font-mono text-sm text-primary font-black break-all">
                    {shopkeeperData?.shopId}
                </div>
              </div>
              <div>
                <label className="block text-[10px] uppercase tracking-widest text-slate-500 font-black mb-3">Registered Mobile</label>
                <div className="relative">
                  <div className="absolute inset-y-0 left-0 pl-5 flex items-center pointer-events-none text-slate-600">
                    <Phone size={18} />
                  </div>
                  <input value={shopkeeperData?.phoneNumber} readOnly className="w-full pl-14 pr-5 py-5 bg-white/5 border border-white/10 rounded-2xl text-slate-300 font-black tracking-widest outline-none" />
                </div>
              </div>
              <div className="pt-4">
                <div className="px-6 py-3 bg-green-500/10 text-green-400 rounded-2xl inline-flex items-center gap-3 text-[10px] font-black uppercase tracking-[0.2em] border border-green-500/20">
                  <div className="w-2 h-2 bg-green-400 rounded-full animate-pulse shadow-[0_0_10px_rgba(74,222,128,0.5)]" />
                  {shopkeeperData?.status || 'Active'}
                </div>
              </div>
            </div>
          </div>

          <div className="bg-white rounded-[3rem] p-10 text-slate-800 shadow-sm border border-slate-50 relative overflow-hidden group hover:shadow-xl transition-all duration-500">
            <div className="relative z-10">
              <h4 className="font-black text-2xl mb-3 tracking-tighter">Support</h4>
              <p className="text-slate-400 text-sm mb-8 leading-relaxed font-bold">
                To update your business registration details or registered phone number, please contact our support team.
              </p>
              <button className="w-full py-5 bg-slate-900 text-white hover:bg-primary transition-all rounded-2xl font-black text-xs uppercase tracking-widest active:scale-95 shadow-lg shadow-slate-200">
                Contact Technical Support
              </button>
            </div>
            <div className="absolute -bottom-10 -right-10 opacity-5 rotate-12 group-hover:rotate-0 transition-transform duration-700">
              <Globe size={180} />
            </div>
          </div>
        </div>
      </div>
    </Layout>
  );
};

export default MyShop;
