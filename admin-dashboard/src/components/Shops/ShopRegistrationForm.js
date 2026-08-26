import React, { useState } from 'react';
import { db } from '../../firebase';
import { collection, addDoc, setDoc, doc, serverTimestamp } from 'firebase/firestore';
import { COLORS, SHADOWS } from '../Shared/Styles';

const ShopRegistrationForm = ({ onCancel, onSuccess }) => {
    const [loading, setLoading] = useState(false);
    const [formData, setFormData] = useState({
        shopName: '',
        ownerName: '',
        phoneNumber: '',
        address: '',
        district: '',
        state: 'Maharashtra',
        status: 'active'
    });

    const handleChange = (e) => {
        const { name, value } = e.target;
        setFormData(prev => ({ ...prev, [name]: value }));
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        setLoading(true);
        try {
            // Normalize phone number for the shopkeeper-portal
            let normalizedPhone = formData.phoneNumber.trim();
            if (!normalizedPhone.startsWith('+')) {
                normalizedPhone = `+91${normalizedPhone.replace(/\s/g, '')}`;
            }

            // 1. Create the Shop document
            const shopRef = doc(collection(db, 'shops'));
            const shopId = shopRef.id;

            await setDoc(shopRef, {
                shopName: formData.shopName,
                ownerName: formData.ownerName,
                phoneNumber: normalizedPhone,
                address: formData.address,
                district: formData.district,
                state: formData.state,
                country: 'India',
                status: 'active',
                onboardingDate: serverTimestamp(),
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });

            // 2. Create the Shopkeeper document (for the portal) using normalized phone as ID
            await setDoc(doc(db, 'shopkeepers', normalizedPhone), {
                name: formData.ownerName,
                phoneNumber: normalizedPhone,
                shopId: shopId,
                status: 'active',
                createdAt: serverTimestamp(),
                updatedAt: serverTimestamp()
            });

            onSuccess();
        } catch (error) {
            console.error("Error registering shop:", error);
            alert("Failed to register shop: " + error.message);
        }
        setLoading(false);
    };

    const inputStyle = {
        width: '100%',
        padding: '12px 16px',
        borderRadius: '12px',
        border: `1.5px solid ${COLORS.border}`,
        outline: 'none',
        fontSize: '15px',
        boxSizing: 'border-box',
        backgroundColor: '#f8fafc'
    };

    const labelStyle = {
        display: 'block',
        marginBottom: '8px',
        fontSize: '14px',
        fontWeight: '600',
        color: COLORS.textMain
    };

    return (
        <div style={{
            backgroundColor: COLORS.white,
            padding: '32px',
            borderRadius: '24px',
            width: '100%',
            maxWidth: '500px',
            boxShadow: SHADOWS.lg
        }}>
            <h3 style={{ margin: '0 0 24px 0', fontSize: '20px', fontWeight: '800', color: COLORS.textMain }}>Register New Retail Partner</h3>

            <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
                <div>
                    <label style={labelStyle}>Shop Name</label>
                    <input
                        name="shopName"
                        value={formData.shopName}
                        onChange={handleChange}
                        required
                        placeholder="e.g. Mahalaxmi Krushi Seva Kendra"
                        style={inputStyle}
                    />
                </div>

                <div style={{ display: 'flex', gap: '16px' }}>
                    <div style={{ flex: 1 }}>
                        <label style={labelStyle}>Owner Name</label>
                        <input
                            name="ownerName"
                            value={formData.ownerName}
                            onChange={handleChange}
                            required
                            placeholder="Satish Solanke"
                            style={inputStyle}
                        />
                    </div>
                    <div style={{ flex: 1 }}>
                        <label style={labelStyle}>Phone Number</label>
                        <input
                            name="phoneNumber"
                            value={formData.phoneNumber}
                            onChange={handleChange}
                            required
                            placeholder="9834255537"
                            style={inputStyle}
                        />
                    </div>
                </div>

                <div>
                    <label style={labelStyle}>Address</label>
                    <input
                        name="address"
                        value={formData.address}
                        onChange={handleChange}
                        required
                        placeholder="Main Road, Beed"
                        style={inputStyle}
                    />
                </div>

                <div style={{ display: 'flex', gap: '16px' }}>
                    <div style={{ flex: 1 }}>
                        <label style={labelStyle}>District</label>
                        <input
                            name="district"
                            value={formData.district}
                            onChange={handleChange}
                            required
                            placeholder="Beed"
                            style={inputStyle}
                        />
                    </div>
                    <div style={{ flex: 1 }}>
                        <label style={labelStyle}>State</label>
                        <input
                            name="state"
                            value={formData.state}
                            onChange={handleChange}
                            required
                            style={inputStyle}
                        />
                    </div>
                </div>

                <div style={{ display: 'flex', gap: '12px', marginTop: '12px' }}>
                    <button
                        type="button"
                        onClick={onCancel}
                        style={{
                            flex: 1,
                            padding: '14px',
                            borderRadius: '12px',
                            border: `1.5px solid ${COLORS.border}`,
                            backgroundColor: 'transparent',
                            fontWeight: '700',
                            cursor: 'pointer'
                        }}
                    >
                        Cancel
                    </button>
                    <button
                        type="submit"
                        disabled={loading}
                        style={{
                            flex: 2,
                            padding: '14px',
                            borderRadius: '12px',
                            border: 'none',
                            backgroundColor: COLORS.primary,
                            color: 'white',
                            fontWeight: '700',
                            cursor: loading ? 'not-allowed' : 'pointer',
                            opacity: loading ? 0.7 : 1
                        }}
                    >
                        {loading ? 'Registering...' : 'Register Partner'}
                    </button>
                </div>
            </form>
        </div>
    );
};

export default ShopRegistrationForm;
