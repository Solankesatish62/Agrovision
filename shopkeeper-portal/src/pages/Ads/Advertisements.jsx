import React, { useState, useEffect } from 'react';
import { useAuth } from '../../context/AuthContext';
import { getAdvertisements, toggleAdStatus, deleteAdvertisement } from '../../services/ad/adService';
import Layout from '../../components/Layout';
import {
  Tv,
  Plus,
  Edit3,
  Trash2,
  ExternalLink,
  CheckCircle2,
  XCircle,
  AlertCircle
} from 'lucide-react';
import AdForm from './AdForm';

const Advertisements = () => {
  const { shopkeeperData } = useAuth();
  const [ads, setAds] = useState([]);
  const [loading, setLoading] = useState(true);
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [editingAd, setEditingAd] = useState(null);

  useEffect(() => {
    fetchAds();
  }, [shopkeeperData]);

  const fetchAds = async () => {
    if (!shopkeeperData?.shopId) {
      console.warn("fetchAds: No shopId in shopkeeperData", shopkeeperData);
      return;
    }
    setLoading(true);
    try {
      const data = await getAdvertisements(shopkeeperData.shopId);
      console.log("fetchAds: Success. Received", data.length, "ads");
      setAds(data);
    } catch (error) {
      console.error("Error fetching ads:", error);
    }
    setLoading(false);
  };

  const handleToggleStatus = async (ad) => {
    if (ad.createdBy === 'admin') {
      alert('Official admin advertisements cannot be modified by shopkeepers.');
      return;
    }
    try {
      await toggleAdStatus(shopkeeperData.shopId, ad.adId, !ad.enabled);
      fetchAds();
    } catch (error) {
      alert('Failed to update status');
    }
  };

  const handleDelete = async (ad) => {
    if (ad.createdBy === 'admin') {
      alert('Official admin advertisements cannot be deleted by shopkeepers.');
      return;
    }
    if (!window.confirm('Are you sure you want to delete this advertisement?')) {
      return;
    }
    console.log("handleDelete: User confirmed. Ad ID:", ad.adId);
    try {
      await deleteAdvertisement(shopkeeperData.shopId, ad.adId);
      console.log("handleDelete: Success. Refreshing list...");
      fetchAds();
    } catch (error) {
      console.error("handleDelete: Failed.", error);
      alert('Failed to delete ad: ' + error.message);
    }
  };

  return (
    <Layout>
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 mb-8">
        <div>
          <h1 className="text-3xl font-extrabold text-slate-800 tracking-tight">Advertisements</h1>
          <p className="text-slate-500 mt-1 font-medium italic">Manage and track your kiosk promotional content</p>
        </div>
        <button
          onClick={() => {
            setEditingAd(null);
            setIsFormOpen(true);
          }}
          className="flex items-center gap-2 bg-primary hover:bg-primary-dark text-white px-8 py-4 rounded-2xl font-bold transition-all shadow-lg shadow-primary/20"
        >
          <Plus size={20} />
          Add Advertisement
        </button>
      </div>

      <div className="bg-transparent md:bg-white rounded-3xl md:shadow-sm md:border border-slate-100 overflow-hidden">
        {loading ? (
          <div className="flex items-center justify-center py-32 bg-white rounded-3xl shadow-sm border border-slate-100">
            <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary"></div>
          </div>
        ) : ads.length > 0 ? (
          <>
            {/* Desktop Table View */}
            <div className="hidden md:block overflow-x-auto">
                <table className="w-full text-left border-collapse">
                <thead>
                    <tr className="bg-slate-50/50 border-b border-slate-100">
                    <th className="py-5 px-8 text-[10px] font-bold text-slate-400 uppercase tracking-widest w-16">#</th>
                    <th className="py-5 px-4 text-[10px] font-bold text-slate-400 uppercase tracking-widest w-32">Preview</th>
                    <th className="py-5 px-4 text-[10px] font-bold text-slate-400 uppercase tracking-widest">Title / Info</th>
                    <th className="py-5 px-4 text-[10px] font-bold text-slate-400 uppercase tracking-widest w-32 text-center">Type / Duration</th>
                    <th className="py-5 px-4 text-[10px] font-bold text-slate-400 uppercase tracking-widest w-40 text-center">Status</th>
                    <th className="py-5 px-8 text-[10px] font-bold text-slate-400 uppercase tracking-widest w-48 text-right">Actions</th>
                    </tr>
                </thead>
                <tbody className="divide-y divide-slate-50">
                    {ads.map((ad, index) => {
                      const isAdminAd = ad.createdBy === 'admin';
                      return (
                        <tr key={ad.id} className="hover:bg-slate-50/50 transition-colors group">
                          <td className="py-6 px-8 text-sm font-bold text-slate-400 font-mono">{index + 1}</td>
                          <td className="py-6 px-4">
                            <div className="w-24 h-16 bg-slate-100 rounded-xl overflow-hidden border border-slate-200 shadow-sm relative group/thumb flex items-center justify-center">
                              {ad.type === 'video' ? (
                                <div className="text-slate-400 font-bold text-[10px]">VIDEO</div>
                              ) : (
                                <img src={ad.imageUrl} alt={ad.name} className="w-full h-full object-cover" />
                              )}
                              <div className="absolute inset-0 bg-black/40 opacity-0 group-hover/thumb:opacity-100 transition-opacity flex items-center justify-center">
                                <a href={ad.imageUrl || ad.videoUrl} target="_blank" rel="noreferrer" className="text-white p-1.5 hover:scale-110 transition-transform">
                                  <ExternalLink size={16} />
                                </a>
                              </div>
                            </div>
                          </td>
                          <td className="py-6 px-4 min-w-[200px]">
                            <div className="flex items-center gap-2 mb-1">
                              <h4 className="font-extrabold text-slate-800 leading-tight">{ad.name}</h4>
                              {isAdminAd && (
                                <span className="bg-amber-50 text-amber-600 text-[8px] font-black px-1.5 py-0.5 rounded border border-amber-100 uppercase tracking-widest">Official</span>
                              )}
                            </div>
                            <div className="flex flex-col gap-1">
                              <p className="text-[10px] text-slate-400 font-mono truncate max-w-[300px] bg-slate-50 px-2 py-1 rounded inline-block">
                                {ad.imageUrl || ad.videoUrl}
                              </p>
                              <div className="flex items-center gap-2 text-[10px] font-bold text-slate-500 uppercase tracking-tighter">
                                <span>Start: {ad.startAt?.toDate().toLocaleDateString()}</span>
                                <span className="text-slate-300">|</span>
                                <span>End: {ad.endAt?.toDate().toLocaleDateString()}</span>
                              </div>
                            </div>
                          </td>
                          <td className="py-6 px-4 text-center">
                            <div className="flex flex-col items-center gap-1">
                              <span className={`px-3 py-1 text-[10px] font-bold rounded-lg uppercase tracking-wider border ${
                                ad.type === 'video'
                                  ? 'bg-blue-50 text-blue-600 border-blue-100'
                                  : 'bg-purple-50 text-purple-600 border-purple-100'
                              }`}>
                                {ad.type === 'video' ? 'VIDEO' : 'IMAGE'}
                              </span>
                              <span className="text-[10px] font-bold text-slate-400">
                                {ad.duration || 60}s
                              </span>
                            </div>
                          </td>
                          <td className="py-6 px-4">
                            <div className="flex items-center justify-center gap-3">
                              <div
                                onClick={() => !isAdminAd && handleToggleStatus(ad)}
                                className={`w-12 h-6 rounded-full p-1 transition-all duration-300 relative ${ad.enabled ? 'bg-green-500' : 'bg-slate-200'} ${isAdminAd ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}`}
                              >
                                <div className={`w-4 h-4 bg-white rounded-full shadow-sm transition-transform duration-300 ${ad.enabled ? 'translate-x-6' : 'translate-x-0'}`} />
                              </div>
                              <span className={`text-xs font-bold ${ad.enabled ? 'text-green-600' : 'text-slate-400'} min-w-[50px]`}>
                                {ad.enabled ? 'Active' : 'Paused'}
                              </span>
                            </div>
                          </td>
                          <td className="py-6 px-8 text-right">
                            <div className="flex items-center justify-end gap-2 transition-opacity">
                              {!isAdminAd ? (
                                <>
                                  <button
                                    onClick={() => {
                                      setEditingAd(ad);
                                      setIsFormOpen(true);
                                    }}
                                    className="p-2.5 bg-slate-100 text-slate-600 rounded-xl hover:bg-primary/10 hover:text-primary transition-all border border-slate-200"
                                    title="Edit"
                                  >
                                    <Edit3 size={16} />
                                  </button>
                                  <button
                                    onClick={() => handleDelete(ad)}
                                    className="p-2.5 bg-red-50 text-red-600 rounded-xl hover:bg-red-100 transition-all border border-red-100"
                                    title="Delete"
                                  >
                                    <Trash2 size={16} />
                                  </button>
                                </>
                              ) : (
                                <div className="p-2 text-[9px] font-black text-slate-300 uppercase tracking-widest bg-slate-50 border border-slate-100 rounded-xl">
                                  System Locked
                                </div>
                              )}
                            </div>
                          </td>
                        </tr>
                      );
                    })}
                </tbody>
                </table>
            </div>

            {/* Mobile Card View */}
            <div className="md:hidden space-y-4">
                {ads.map((ad, index) => {
                    const isAdminAd = ad.createdBy === 'admin';
                    return (
                        <div key={ad.id} className="bg-white rounded-3xl p-6 shadow-sm border border-slate-100">
                            <div className="flex items-center gap-4 mb-6">
                                <div className="w-20 h-20 bg-slate-100 rounded-2xl overflow-hidden border border-slate-100 flex items-center justify-center shrink-0">
                                    {ad.type === 'video' ? (
                                        <Tv size={32} className="text-slate-400" />
                                    ) : (
                                        <img src={ad.imageUrl} alt={ad.name} className="w-full h-full object-cover" />
                                    )}
                                </div>
                                <div className="overflow-hidden">
                                    <div className="flex items-center gap-2 mb-1">
                                      <h4 className="font-black text-slate-800 tracking-tight truncate leading-tight">{ad.name}</h4>
                                      {isAdminAd && (
                                        <span className="bg-amber-50 text-amber-600 text-[7px] font-black px-1 py-0.5 rounded border border-amber-100 uppercase shrink-0">Official</span>
                                      )}
                                    </div>
                                    <div className="flex items-center gap-2">
                                        <span className={`px-2 py-0.5 text-[8px] font-black rounded border ${
                                            ad.type === 'video' ? 'bg-blue-50 text-blue-600 border-blue-100' : 'bg-purple-50 text-purple-600 border-purple-100'
                                        }`}>
                                            {ad.type === 'video' ? 'VIDEO' : 'IMAGE'}
                                        </span>
                                        <span className="text-[10px] font-bold text-slate-400 uppercase tracking-widest">{ad.duration || 60}s Display</span>
                                    </div>
                                </div>
                            </div>

                            <div className="grid grid-cols-2 gap-3 mb-6">
                                <div className="bg-slate-50 p-3 rounded-2xl">
                                    <p className="text-[8px] text-slate-400 font-black uppercase tracking-widest mb-1">Start Date</p>
                                    <p className="text-xs font-bold text-slate-600">{ad.startAt?.toDate().toLocaleDateString()}</p>
                                </div>
                                <div className="bg-slate-50 p-3 rounded-2xl">
                                    <p className="text-[8px] text-slate-400 font-black uppercase tracking-widest mb-1">End Date</p>
                                    <p className="text-xs font-bold text-slate-600">{ad.endAt?.toDate().toLocaleDateString()}</p>
                                </div>
                            </div>

                            <div className="flex items-center justify-between gap-4 pt-4 border-t border-slate-50">
                                <div
                                    onClick={() => !isAdminAd && handleToggleStatus(ad)}
                                    className={`flex items-center gap-3 px-4 py-2.5 rounded-2xl border transition-all ${
                                        ad.enabled ? 'bg-green-50 text-green-700 border-green-100' : 'bg-slate-50 text-slate-400 border-slate-200'
                                    } ${isAdminAd ? 'opacity-50' : 'cursor-pointer'}`}
                                >
                                    <div className={`w-2 h-2 rounded-full ${ad.enabled ? 'bg-green-500 animate-pulse' : 'bg-slate-300'}`} />
                                    <span className="text-xs font-black uppercase tracking-widest">{ad.enabled ? 'Live' : 'Paused'}</span>
                                </div>

                                <div className="flex items-center gap-2">
                                    {!isAdminAd ? (
                                      <>
                                        <button
                                            onClick={() => { setEditingAd(ad); setIsFormOpen(true); }}
                                            className="p-3 bg-slate-100 text-slate-600 rounded-2xl border border-slate-200 active:bg-primary active:text-white transition-all"
                                        >
                                            <Edit3 size={18} />
                                        </button>
                                        <button
                                            onClick={() => handleDelete(ad)}
                                            className="p-3 bg-red-50 text-red-600 rounded-2xl border border-red-100 active:bg-red-600 active:text-white transition-all"
                                        >
                                            <Trash2 size={18} />
                                        </button>
                                      </>
                                    ) : (
                                      <span className="text-[8px] font-black text-slate-300 uppercase tracking-widest px-3 py-3 bg-slate-50 rounded-2xl border border-slate-100">System Locked</span>
                                    )}
                                </div>
                            </div>
                        </div>
                    );
                })}
            </div>
          </>
        ) : (
          <div className="py-24 text-center bg-white rounded-[3rem] shadow-sm border border-slate-100">
            <div className="w-20 h-20 bg-slate-50 rounded-full flex items-center justify-center mx-auto mb-6 text-slate-300 border border-slate-100 shadow-inner">
              <Tv size={36} />
            </div>
            <h3 className="text-xl font-extrabold text-slate-800">No advertisements found</h3>
            <p className="text-slate-500 mt-2 max-w-sm mx-auto font-medium">
              Start by uploading your first promotional banner to display on your kiosks.
            </p>
            <button
              onClick={() => {
                setEditingAd(null);
                setIsFormOpen(true);
              }}
              className="mt-8 bg-slate-800 hover:bg-slate-900 text-white px-10 py-4 rounded-2xl font-bold transition-all shadow-xl shadow-slate-200"
            >
              + Create First Ad
            </button>
          </div>
        )}
      </div>

      {isFormOpen && (
        <AdForm
          ad={editingAd}
          onClose={() => setIsFormOpen(false)}
          onSuccess={() => {
            setIsFormOpen(false);
            fetchAds();
          }}
        />
      )}
    </Layout>
  );
};

export default Advertisements;
