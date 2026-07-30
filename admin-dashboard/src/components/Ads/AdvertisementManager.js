import React, { useState, useEffect, useMemo } from 'react';
import { db } from '../../firebase';
import { collection, doc, onSnapshot, getDoc, setDoc, updateDoc, deleteDoc, serverTimestamp } from 'firebase/firestore';
import { trStyle, tdStyle, tdBoldStyle, badgeStyle, exportBtnStyle, labelStyle, inputStyle, COLORS } from '../Shared/Styles';
import TableLayout from '../Shared/TableLayout';

const AdvertisementManager = ({ shops, storageFiles }) => {
    const [selectedShopId, setSelectedShopId] = useState('');
    const [shopSearchTerm, setShopSearchTerm] = useState('');
    const [adsData, setAdsData] = useState({ ads: [], interval_seconds: 60 });
    const [isFormOpen, setIsFormOpen] = useState(false);
    const [editingAd, setEditingAd] = useState(null);
    const [isSaving, setIsSaving] = useState(false);
    const [showSuggestions, setShowSuggestions] = useState(false);
    const [formData, setFormData] = useState({
        title: '',
        imageUrl: '',
        videoUrl: '',
        active: true,
        type: 'image'
    });

    // We'll use a local state to track which shops have ads to show status in the table
    const [shopsWithAds, setShopsWithAds] = useState({});

    useEffect(() => {
        // Listen to the entire kiosk_ads collection for status badges in the list
        const unsubscribe = onSnapshot(collection(db, 'kiosk_ads'), (snapshot) => {
            const statusMap = {};
            snapshot.docs.forEach(doc => {
                const data = doc.data();
                const adsCount = Array.isArray(data.ads) ? data.ads.length : 0;
                const activeCount = Array.isArray(data.ads) ? data.ads.filter(a => a.active).length : 0;
                statusMap[doc.id] = { total: adsCount, active: activeCount };
            });
            setShopsWithAds(statusMap);
        });
        return () => unsubscribe();
    }, []);

    const filteredShops = useMemo(() => {
        const term = shopSearchTerm.toLowerCase().trim();
        if (!term) return shops;
        return shops.filter(s =>
            (s.shopName || "").toLowerCase().includes(term) ||
            (s.ownerName || "").toLowerCase().includes(term) ||
            (s.phoneNumber || "").toLowerCase().includes(term) ||
            (s.id || "").toLowerCase().includes(term)
        );
    }, [shops, shopSearchTerm]);

    const suggestions = useMemo(() => {
        if (!storageFiles || !storageFiles.advertisements) return [];
        const term = (formData.type === 'image' ? formData.imageUrl : formData.videoUrl).toLowerCase();
        if (!term) return storageFiles.advertisements.slice(0, 10);
        return storageFiles.advertisements.filter(f => f.name.toLowerCase().includes(term)).slice(0, 10);
    }, [storageFiles, formData.imageUrl, formData.videoUrl, formData.type]);

    useEffect(() => {
        if (!selectedShopId) {
            setAdsData({ ads: [], interval_seconds: 60, shopBannerUrl: '', shopDisplayName: '', shopBrandingType: 'text' });
            return;
        }

        const unsubscribe = onSnapshot(doc(db, 'kiosk_ads', selectedShopId), (docSnap) => {
            if (docSnap.exists()) {
                const data = docSnap.data();
                let adsList = [];
                if (Array.isArray(data.ads)) {
                    adsList = data.ads;
                } else if (typeof data.ads === 'object' && data.ads !== null) {
                    adsList = Object.values(data.ads);
                } else if (Array.isArray(data.ad_list)) {
                    adsList = data.ad_list.map(url => ({ imageUrl: url, active: true, title: 'Legacy Ad' }));
                }
                setAdsData({
                    ads: adsList,
                    interval_seconds: data.interval_seconds || 60,
                    shopBannerUrl: data.shopBannerUrl || '',
                    shopDisplayName: data.shopDisplayName || '',
                    shopBrandingType: data.shopBrandingType || 'text'
                });
            } else {
                setAdsData({ ads: [], interval_seconds: 60, shopBannerUrl: '', shopDisplayName: '', shopBrandingType: 'text' });
            }
        });

        return () => unsubscribe();
    }, [selectedShopId]);

    const handleUpdateField = async (field, value) => {
        if (!selectedShopId) return;
        try {
            const docRef = doc(db, 'kiosk_ads', selectedShopId);
            await setDoc(docRef, { [field]: value }, { merge: true });
        } catch (error) {
            console.error(`Error updating ${field}:`, error);
        }
    };

    const handleSaveAd = async (e) => {
        e.preventDefault();
        if (!selectedShopId) return;
        setIsSaving(true);

        try {
            const docRef = doc(db, 'kiosk_ads', selectedShopId);
            let updatedAds = [...adsData.ads];

            const adToSave = {
                title: formData.title || (formData.type === 'video' ? 'Video Ad' : 'Image Ad'),
                active: formData.active,
                imageUrl: formData.type === 'image' ? formData.imageUrl : '',
                videoUrl: formData.type === 'video' ? formData.videoUrl : '',
                updatedAt: new Date().toISOString()
            };

            if (editingAd !== null) {
                updatedAds[editingAd] = adToSave;
            } else {
                updatedAds.push(adToSave);
            }

            await setDoc(docRef, {
                ads: updatedAds,
                interval_seconds: adsData.interval_seconds,
                lastUpdated: serverTimestamp()
            }, { merge: true });

            setIsFormOpen(false);
            setEditingAd(null);
            setFormData({ title: '', imageUrl: '', videoUrl: '', active: true, type: 'image' });
        } catch (error) {
            console.error("Error saving ad:", error);
            alert("Failed to save advertisement.");
        }
        setIsSaving(false);
    };

    const handleDeleteAd = async (index) => {
        if (!window.confirm("Are you sure you want to delete this advertisement?")) return;
        try {
            const docRef = doc(db, 'kiosk_ads', selectedShopId);
            const updatedAds = adsData.ads.filter((_, i) => i !== index);
            await updateDoc(docRef, { ads: updatedAds });
        } catch (error) {
            console.error("Error deleting ad:", error);
            alert("Failed to delete advertisement.");
        }
    };

    const handleToggleActive = async (index) => {
        try {
            const docRef = doc(db, 'kiosk_ads', selectedShopId);
            const updatedAds = [...adsData.ads];
            updatedAds[index].active = !updatedAds[index].active;
            await updateDoc(docRef, { ads: updatedAds });
        } catch (error) {
            console.error("Error toggling ad status:", error);
        }
    };

    const handleIntervalChange = async (newInterval) => {
        if (!selectedShopId) return;
        try {
            const docRef = doc(db, 'kiosk_ads', selectedShopId);
            await setDoc(docRef, { interval_seconds: parseInt(newInterval) }, { merge: true });
        } catch (error) {
            console.error("Error updating interval:", error);
        }
    };

    const openEditForm = (ad, index) => {
        setEditingAd(index);
        setFormData({
            title: ad.title || '',
            imageUrl: ad.imageUrl || '',
            videoUrl: ad.videoUrl || '',
            active: ad.active !== false,
            type: ad.videoUrl ? 'video' : 'image'
        });
        setIsFormOpen(true);
    };

    const selectedShop = useMemo(() => shops.find(s => s.id === selectedShopId), [shops, selectedShopId]);

    const renderShopList = () => (
        <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '25px', alignItems: 'center', flexWrap: 'wrap', gap: '15px' }}>
                <div>
                    <h2 style={{ margin: 0, color: '#1e293b', fontSize: '22px' }}>📺 Advertisement Catalog</h2>
                    <p style={{ margin: '5px 0 0 0', color: '#64748b', fontSize: '14px' }}>Select a retail partner to manage their personalized screen promotions.</p>
                </div>
                <div style={{ position: 'relative' }}>
                    <input
                        type="text"
                        placeholder="Search partners by name or phone..."
                        value={shopSearchTerm}
                        onChange={(e) => setShopSearchTerm(e.target.value)}
                        style={{
                            padding: '12px 15px',
                            paddingLeft: '40px',
                            borderRadius: '10px',
                            border: `1px solid ${COLORS.border}`,
                            width: '350px',
                            fontSize: '14px',
                            outline: 'none'
                        }}
                    />
                    <span style={{ position: 'absolute', left: '15px', top: '50%', transform: 'translateY(-50%)', color: '#94a3b8' }}>🔍</span>
                </div>
            </div>

            <div style={{ overflowX: 'auto', borderRadius: '12px', border: '1px solid #f1f5f9' }}>
                <TableLayout headers={['#', 'Retail Partner', 'Contact Info', 'Ads Status', 'Active/Total', 'Actions']}>
                    {filteredShops.map((shop, index) => {
                        const status = shopsWithAds[shop.id] || { total: 0, active: 0 };
                        return (
                            <tr key={shop.id} style={trStyle}>
                                <td style={{ ...tdStyle, width: '40px' }}>{index + 1}</td>
                                <td style={tdBoldStyle}>
                                    <div style={{ color: '#0f172a' }}>{shop.shopName || 'Unnamed Shop'}</div>
                                    <div style={{ fontSize: '11px', color: '#94a3b8', fontWeight: 'normal' }}>ID: {shop.id}</div>
                                </td>
                                <td style={tdStyle}>
                                    <div style={{ fontSize: '14px', color: '#475569' }}>{shop.ownerName || '—'}</div>
                                    <div style={{ fontSize: '13px', color: '#64748b' }}>{shop.phoneNumber}</div>
                                </td>
                                <td style={tdStyle}>
                                    {status.total > 0 ? (
                                        <span style={{ ...badgeStyle, backgroundColor: status.active > 0 ? '#f0fdf4' : '#fff1f2', color: status.active > 0 ? '#166534' : '#e11d48' }}>
                                            {status.active > 0 ? '● Live' : '○ Paused'}
                                        </span>
                                    ) : (
                                        <span style={{ ...badgeStyle, backgroundColor: '#f8fafc', color: '#94a3b8' }}>No Ads</span>
                                    )}
                                </td>
                                <td style={tdStyle}>
                                    <div style={{ fontSize: '14px', fontWeight: '600', color: status.total > 0 ? COLORS.primary : '#cbd5e1' }}>
                                        {status.active} / {status.total}
                                    </div>
                                </td>
                                <td style={tdStyle}>
                                    <button
                                        onClick={() => setSelectedShopId(shop.id)}
                                        style={{
                                            padding: '8px 16px',
                                            backgroundColor: COLORS.primary,
                                            color: 'white',
                                            border: 'none',
                                            borderRadius: '8px',
                                            cursor: 'pointer',
                                            fontWeight: '600',
                                            fontSize: '13px',
                                            boxShadow: '0 2px 4px rgba(0,0,0,0.05)'
                                        }}
                                    >
                                        Manage Ads
                                    </button>
                                </td>
                            </tr>
                        );
                    })}
                </TableLayout>
                {filteredShops.length === 0 && (
                    <div style={{ textAlign: 'center', padding: '60px', color: '#94a3b8' }}>
                        <div style={{ fontSize: '48px', marginBottom: '15px' }}>🔍</div>
                        <h3>No partners found</h3>
                        <p>Try searching for a different name or phone number.</p>
                    </div>
                )}
            </div>
        </div>
    );

    return (
        <div style={{ backgroundColor: 'white', padding: '30px', borderRadius: '16px', boxShadow: '0 4px 20px rgba(0,0,0,0.05)' }}>
            {!selectedShopId ? renderShopList() : (
                <>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '25px', alignItems: 'center', flexWrap: 'wrap', gap: '15px' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '15px' }}>
                            <button
                                onClick={() => setSelectedShopId('')}
                                style={{
                                    background: '#f1f5f9',
                                    border: 'none',
                                    borderRadius: '10px',
                                    width: '40px',
                                    height: '40px',
                                    cursor: 'pointer',
                                    color: COLORS.textMain,
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center'
                                }}
                            >
                                <i className="fas fa-arrow-left"></i>
                            </button>
                            <div>
                                <h2 style={{ margin: 0, color: '#1e293b', fontSize: '22px' }}>📺 {selectedShop?.shopName}'s Promotions</h2>
                                <p style={{ margin: '5px 0 0 0', color: '#64748b', fontSize: '14px' }}>Managing advertisements for {selectedShop?.ownerName}.</p>
                            </div>
                        </div>
                        <div style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
                            <button
                                onClick={() => { setEditingAd(null); setFormData({ title: '', imageUrl: '', videoUrl: '', active: true, type: 'image' }); setIsFormOpen(true); }}
                                style={{ ...exportBtnStyle, backgroundColor: COLORS.primary, borderRadius: '10px', padding: '12px 25px' }}
                            >
                                + Add Advertisement
                            </button>
                        </div>
                    </div>

                    <div style={{ display: 'flex', gap: '20px', marginBottom: '30px', backgroundColor: '#f8fafc', padding: '20px', borderRadius: '12px', border: '1px solid #e2e8f0' }}>
                        <div style={{ flex: 1 }}>
                            <p style={{ margin: '0 0 5px 0', fontSize: '12px', color: '#64748b', fontWeight: 'bold', textTransform: 'uppercase' }}>Shop Contact</p>
                            <p style={{ margin: 0, fontSize: '18px', color: '#1e293b', fontWeight: 'bold' }}>{selectedShop?.phoneNumber}</p>
                            <p style={{ margin: '2px 0 0 0', fontSize: '14px', color: '#64748b' }}>{selectedShop?.id}</p>
                        </div>
                        <div style={{ width: '200px' }}>
                            <label style={labelStyle}>Slideshow Interval (sec)</label>
                            <input
                                type="number"
                                value={adsData.interval_seconds}
                                onChange={(e) => handleIntervalChange(e.target.value)}
                                style={{ ...inputStyle, marginBottom: 0 }}
                                min="5"
                                max="3600"
                            />
                        </div>
                    </div>

                    <div style={{ marginBottom: '30px', backgroundColor: '#fff7ed', padding: '24px', borderRadius: '16px', border: '1px solid #ffedd5' }}>
                        <h3 style={{ margin: '0 0 20px 0', fontSize: '18px', color: '#9a3412', display: 'flex', alignItems: 'center', gap: '10px' }}>
                            <span>🏪 Shop Identity & Co-Branding</span>
                            <span style={{ fontSize: '12px', fontWeight: 'normal', backgroundColor: '#ffedd5', padding: '2px 8px', borderRadius: '10px' }}>Top Header</span>
                        </h3>

                        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '32px' }}>
                            <div>
                                <label style={labelStyle}>Branding Type</label>
                                <div style={{ display: 'flex', gap: '12px', marginBottom: '16px' }}>
                                    <button
                                        onClick={() => handleUpdateField('shopBrandingType', 'text')}
                                        style={{ flex: 1, padding: '10px', borderRadius: '8px', border: '1px solid #e2e8f0', backgroundColor: adsData.shopBrandingType === 'text' ? '#9a3412' : 'white', color: adsData.shopBrandingType === 'text' ? 'white' : '#64748b', fontWeight: 'bold', cursor: 'pointer' }}
                                    >
                                        Text Style
                                    </button>
                                    <button
                                        onClick={() => handleUpdateField('shopBrandingType', 'poster')}
                                        style={{ flex: 1, padding: '10px', borderRadius: '8px', border: '1px solid #e2e8f0', backgroundColor: adsData.shopBrandingType === 'poster' ? '#9a3412' : 'white', color: adsData.shopBrandingType === 'poster' ? 'white' : '#64748b', fontWeight: 'bold', cursor: 'pointer' }}
                                    >
                                        Poster/Logo
                                    </button>
                                </div>

                                {adsData.shopBrandingType === 'text' ? (
                                    <>
                                        <label style={labelStyle}>Shop Display Name</label>
                                        <input
                                            type="text"
                                            value={adsData.shopDisplayName}
                                            onChange={(e) => handleUpdateField('shopDisplayName', e.target.value)}
                                            style={inputStyle}
                                            placeholder="e.g. Krishi Seva Kendra"
                                        />
                                    </>
                                ) : (
                                    <>
                                        <label style={labelStyle}>Brand Logo / Poster URL</label>
                                        <input
                                            type="text"
                                            value={adsData.shopBannerUrl}
                                            onChange={(e) => handleUpdateField('shopBannerUrl', e.target.value)}
                                            style={inputStyle}
                                            placeholder="https://...logo.png"
                                        />
                                    </>
                                )}
                            </div>

                            <div style={{ backgroundColor: 'white', borderRadius: '12px', padding: '20px', border: '1px solid #fed7aa', display: 'flex', flexDirection: 'column', justifyContent: 'center' }}>
                                <p style={{ margin: '0 0 12px 0', fontSize: '12px', color: '#9a3412', fontWeight: 'bold', textAlign: 'center' }}>LIVE PREVIEW (HEADER)</p>
                                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '15px', padding: '10px', border: '1px dashed #fdba74', borderRadius: '8px', minHeight: '60px' }}>
                                    <span style={{ fontSize: '20px', fontWeight: 'bold' }}>
                                        <span style={{ color: '#006400' }}>Agro</span>
                                        <span style={{ color: '#FF8C00' }}>Vision</span>
                                    </span>
                                    <div style={{ width: '1px', height: '24px', backgroundColor: '#e2e8f0' }}></div>
                                    {adsData.shopBrandingType === 'text' ? (
                                        <span style={{ fontSize: '20px', fontWeight: 'bold' }}>
                                            {(() => {
                                                const name = adsData.shopDisplayName || 'Shop Name';
                                                const spaceIndex = name.indexOf(' ');
                                                if (spaceIndex > 0) {
                                                    return (
                                                        <>
                                                            <span style={{ color: '#006400' }}>{name.substring(0, spaceIndex)}</span>
                                                            <span style={{ color: '#FF8C00' }}>{name.substring(spaceIndex)}</span>
                                                        </>
                                                    );
                                                }
                                                return <span style={{ color: '#006400' }}>{name}</span>;
                                            })()}
                                        </span>
                                    ) : (
                                        adsData.shopBannerUrl ? <img src={adsData.shopBannerUrl} alt="Logo" style={{ height: '30px', objectFit: 'contain' }} /> : <span style={{ color: '#cbd5e1', fontSize: '14px' }}>No Logo URL</span>
                                    )}
                                </div>
                            </div>
                        </div>
                    </div>

                    <div style={{ overflowX: 'auto', borderRadius: '12px', border: '1px solid #f1f5f9' }}>
                        <TableLayout headers={['#', 'Preview', 'Title / Info', 'Type', 'Status', 'Actions']}>
                            {adsData.ads.map((ad, index) => (
                                <tr key={index} style={trStyle}>
                                    <td style={{ ...tdStyle, width: '40px' }}>{index + 1}</td>
                                    <td style={{ ...tdStyle, width: '120px' }}>
                                        {ad.videoUrl ? (
                                            <div style={{ width: '100px', height: '60px', backgroundColor: '#000', borderRadius: '6px', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'white', fontSize: '20px' }}>🎥</div>
                                        ) : (
                                            <img src={ad.imageUrl} alt="" style={{ width: '100px', height: '60px', objectFit: 'cover', borderRadius: '6px', border: '1px solid #e2e8f0' }} onError={(e) => e.target.src = 'https://via.placeholder.com/100x60?text=Error'} />
                                        )}
                                    </td>
                                    <td style={tdBoldStyle}>
                                        <div style={{ color: '#0f172a' }}>{ad.title || 'Untitled Ad'}</div>
                                        <div style={{ fontSize: '11px', color: '#94a3b8', fontWeight: 'normal', marginTop: '4px', maxWidth: '300px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                                            URL: {ad.videoUrl || ad.imageUrl}
                                        </div>
                                    </td>
                                    <td style={tdStyle}>
                                        <span style={{ ...badgeStyle, backgroundColor: ad.videoUrl ? '#eff6ff' : '#f5f3ff', color: ad.videoUrl ? '#1d4ed8' : '#6d28d9' }}>
                                            {ad.videoUrl ? 'VIDEO' : 'IMAGE'}
                                        </span>
                                    </td>
                                    <td style={tdStyle}>
                                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer' }} onClick={() => handleToggleActive(index)}>
                                            <div style={{
                                                width: '36px',
                                                height: '20px',
                                                borderRadius: '10px',
                                                backgroundColor: ad.active ? '#10b981' : '#cbd5e1',
                                                position: 'relative',
                                                transition: 'all 0.2s'
                                            }}>
                                                <div style={{
                                                    width: '14px',
                                                    height: '14px',
                                                    borderRadius: '50%',
                                                    backgroundColor: 'white',
                                                    position: 'absolute',
                                                    top: '3px',
                                                    left: ad.active ? '19px' : '3px',
                                                    transition: 'all 0.2s'
                                                }} />
                                            </div>
                                            <span style={{ fontSize: '13px', color: ad.active ? '#10b981' : '#64748b', fontWeight: '600' }}>
                                                {ad.active ? 'Active' : 'Paused'}
                                            </span>
                                        </div>
                                    </td>
                                    <td style={tdStyle}>
                                        <div style={{ display: 'flex', gap: '8px' }}>
                                            <button onClick={() => openEditForm(ad, index)} style={{ padding: '8px 16px', backgroundColor: '#f1f5f9', color: '#1e293b', border: '1px solid #e2e8f0', borderRadius: '8px', cursor: 'pointer', fontWeight: '600', fontSize: '13px' }}>Edit</button>
                                            <button onClick={() => handleDeleteAd(index)} style={{ padding: '8px 16px', backgroundColor: '#fff1f2', color: '#e11d48', border: '1px solid #ffe4e6', borderRadius: '8px', cursor: 'pointer', fontWeight: '600', fontSize: '13px' }}>Delete</button>
                                        </div>
                                    </td>
                                </tr>
                            ))}
                        </TableLayout>
                    </div>
                </>
            )}

            {isFormOpen && (
                <div style={{ position: 'fixed', top: 0, left: 0, width: '100%', height: '100%', backgroundColor: 'rgba(15, 23, 42, 0.7)', zIndex: 1000, display: 'flex', justifyContent: 'center', alignItems: 'center', backdropFilter: 'blur(4px)' }}>
                    <div style={{ backgroundColor: 'white', borderRadius: '20px', width: '500px', boxShadow: '0 25px 50px -12px rgba(0,0,0,0.25)', overflow: 'hidden' }}>
                        <div style={{ padding: '24px 32px', borderBottom: '1px solid #f1f5f9', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                            <h3 style={{ margin: 0, color: '#1e293b' }}>{editingAd !== null ? 'Edit Advertisement' : 'Add New Advertisement'}</h3>
                            <button onClick={() => setIsFormOpen(false)} style={{ background: 'none', border: 'none', fontSize: '20px', cursor: 'pointer', color: '#94a3b8' }}>✕</button>
                        </div>
                        <form onSubmit={handleSaveAd} style={{ padding: '32px' }}>
                            <div style={{ marginBottom: '20px' }}>
                                <label style={labelStyle}>Promotion Title</label>
                                <input
                                    type="text"
                                    value={formData.title}
                                    onChange={(e) => setFormData({ ...formData, title: e.target.value })}
                                    style={inputStyle}
                                    placeholder="e.g. Mahadhan Monsoon Offer"
                                    required
                                />
                            </div>

                            <div style={{ marginBottom: '20px' }}>
                                <label style={labelStyle}>Media Type</label>
                                <div style={{ display: 'flex', gap: '15px' }}>
                                    <label style={{ flex: 1, padding: '12px', border: `1px solid ${formData.type === 'image' ? COLORS.primary : '#e2e8f0'}`, borderRadius: '10px', display: 'flex', alignItems: 'center', gap: '10px', cursor: 'pointer', backgroundColor: formData.type === 'image' ? '#f0f9ff' : 'white' }}>
                                        <input type="radio" checked={formData.type === 'image'} onChange={() => setFormData({ ...formData, type: 'image' })} />
                                        <span style={{ fontWeight: '600', color: formData.type === 'image' ? COLORS.primary : '#64748b' }}>Image Banner</span>
                                    </label>
                                    <label style={{ flex: 1, padding: '12px', border: `1px solid ${formData.type === 'video' ? COLORS.primary : '#e2e8f0'}`, borderRadius: '10px', display: 'flex', alignItems: 'center', gap: '10px', cursor: 'pointer', backgroundColor: formData.type === 'video' ? '#f0f9ff' : 'white' }}>
                                        <input type="radio" checked={formData.type === 'video'} onChange={() => setFormData({ ...formData, type: 'video' })} />
                                        <span style={{ fontWeight: '600', color: formData.type === 'video' ? COLORS.primary : '#64748b' }}>Video Clip</span>
                                    </label>
                                </div>
                            </div>

                            <div style={{ marginBottom: '20px', position: 'relative' }}>
                                <label style={labelStyle}>{formData.type === 'image' ? 'Image URL' : 'Video URL'}</label>
                                <input
                                    type="text"
                                    value={formData.type === 'image' ? formData.imageUrl : formData.videoUrl}
                                    onChange={(e) => setFormData({ ...formData, [formData.type === 'image' ? 'imageUrl' : 'videoUrl']: e.target.value })}
                                    onFocus={() => setShowSuggestions(true)}
                                    style={inputStyle}
                                    placeholder={formData.type === 'image' ? "https://...image.jpg" : "https://...video.mp4"}
                                    required
                                />
                                {showSuggestions && suggestions.length > 0 && (
                                    <div style={{ position: 'absolute', top: '100%', left: 0, right: 0, zIndex: 1100, backgroundColor: 'white', border: '1px solid #cbd5e1', borderRadius: '10px', boxShadow: '0 10px 15px -3px rgba(0,0,0,0.1)', maxHeight: '200px', overflowY: 'auto', marginTop: '5px' }}>
                                        <div style={{ padding: '8px 12px', fontSize: '11px', fontWeight: 'bold', color: '#64748b', backgroundColor: '#f8fafc', borderBottom: '1px solid #e2e8f0' }}>Storage Suggestions (Ads Folder)</div>
                                        {suggestions.map((f, idx) => (
                                            <div key={idx} onClick={() => {
                                                setFormData({ ...formData, [formData.type === 'image' ? 'imageUrl' : 'videoUrl']: f.url });
                                                setShowSuggestions(false);
                                            }} style={{ padding: '10px 12px', cursor: 'pointer', borderBottom: '1px solid #f1f5f9', fontSize: '13px', display: 'flex', alignItems: 'center', gap: '10px' }}>
                                                <span style={{ fontSize: '16px' }}>{f.contentType?.includes('video') ? '🎥' : '🖼️'}</span>
                                                <span style={{ whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{f.name}</span>
                                            </div>
                                        ))}
                                    </div>
                                )}
                            </div>

                            <div style={{ marginBottom: '30px' }}>
                                <label style={{ ...labelStyle, display: 'flex', alignItems: 'center', gap: '10px', cursor: 'pointer' }}>
                                    <input
                                        type="checkbox"
                                        checked={formData.active}
                                        onChange={(e) => setFormData({ ...formData, active: e.target.checked })}
                                        style={{ width: '18px', height: '18px' }}
                                    />
                                    <span>Enable this promotion immediately</span>
                                </label>
                            </div>

                            <div style={{ display: 'flex', gap: '12px' }}>
                                <button type="button" onClick={() => setIsFormOpen(false)} style={{ flex: 1, padding: '12px', backgroundColor: '#f1f5f9', color: '#475569', border: 'none', borderRadius: '10px', cursor: 'pointer', fontWeight: 'bold' }}>Cancel</button>
                                <button type="submit" disabled={isSaving} style={{ flex: 2, padding: '12px', backgroundColor: COLORS.primary, color: 'white', border: 'none', borderRadius: '10px', cursor: 'pointer', fontWeight: 'bold', boxShadow: '0 4px 6px rgba(30, 58, 138, 0.2)' }}>
                                    {isSaving ? 'Saving...' : (editingAd !== null ? 'Update Ad' : 'Publish Advertisement')}
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
};

export default AdvertisementManager;
