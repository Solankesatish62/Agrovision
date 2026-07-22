import React, { useEffect, useState } from 'react';
import { db } from '../../firebase';
import { collection, query, where, orderBy, onSnapshot, limit, Timestamp } from 'firebase/firestore';
import { X, CheckCircle, XCircle, Clock, Store, User, Activity } from 'lucide-react';
import { COLORS, SHADOWS, tdStyle, trStyle } from '../Shared/Styles';

const ScanHistoryModal = ({ shopId, shopName, onClose }) => {
  const [history, setHistory] = useState([]);
  const [loading, setLoading] = useState(true);
  const [shopOwner, setShopOwner] = useState('');

  useEffect(() => {
    // Fetch shop owner name
    const fetchShopInfo = async () => {
        const shopsRef = collection(db, 'shops');
        const q = query(shopsRef, where('id', '==', shopId));
        const unsubscribe = onSnapshot(q, (snapshot) => {
            if (!snapshot.empty) {
                setShopOwner(snapshot.docs[0].data().ownerName || 'N/A');
            }
        });
        return unsubscribe;
    };
    fetchShopInfo();
  }, [shopId]);

  useEffect(() => {
    setLoading(true);
    // Calculate timestamp for 7 days ago
    const oneWeekAgo = new Date();
    oneWeekAgo.setDate(oneWeekAgo.getDate() - 7);
    const startTimestamp = Timestamp.fromDate(oneWeekAgo);

    const q = query(
      collection(db, 'scan_history'),
      where('shopId', '==', shopId),
      where('timestamp', '>=', startTimestamp),
      orderBy('timestamp', 'desc')
    );

    const unsubscribe = onSnapshot(q, (snapshot) => {
      const data = snapshot.docs.map(doc => ({
        id: doc.id,
        ...doc.data()
      }));
      setHistory(data);
      setLoading(false);
    }, (error) => {
      console.error("Error fetching scan history:", error);
      setLoading(false);
    });

    return () => unsubscribe();
  }, [shopId]);

  return (
    <div style={{
      width: '100%',
      maxWidth: '800px',
      backgroundColor: COLORS.white,
      borderRadius: '20px',
      boxShadow: SHADOWS.lg,
      display: 'flex',
      flexDirection: 'column',
      maxHeight: '90vh',
      overflow: 'hidden',
      animation: 'slideUp 0.3s ease-out'
    }}>
      <style>{`
        @keyframes slideUp {
          from { opacity: 0; transform: translateY(20px); }
          to { opacity: 1; transform: translateY(0); }
        }
      `}</style>

      {/* Header */}
      <div style={{
        padding: '24px 32px',
        borderBottom: `1px solid ${COLORS.border}`,
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        backgroundColor: '#f8fafc'
      }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '4px' }}>
            <Store size={18} color={COLORS.primary} />
            <h3 style={{ margin: 0, fontSize: '20px', fontWeight: '800', color: COLORS.textMain }}>{shopName}</h3>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '14px', color: COLORS.textMuted }}>
              <User size={14} />
              <span style={{ fontWeight: '600' }}>{shopOwner}</span>
            </div>
            <div style={{ fontSize: '12px', color: COLORS.textMuted, backgroundColor: '#e2e8f0', padding: '2px 8px', borderRadius: '4px' }}>
                7-Day History
            </div>
          </div>
        </div>
        <button
          onClick={onClose}
          style={{
            padding: '8px',
            borderRadius: '50%',
            border: 'none',
            backgroundColor: 'transparent',
            cursor: 'pointer',
            color: COLORS.textMuted,
            transition: 'background 0.2s'
          }}
          onMouseOver={(e) => e.currentTarget.style.backgroundColor = '#e2e8f0'}
          onMouseOut={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
        >
          <X size={24} />
        </button>
      </div>

      {/* Table Content */}
      <div style={{ flex: 1, overflowY: 'auto', padding: '0 32px 32px 32px' }}>
        {loading ? (
          <div style={{ padding: '40px', textAlign: 'center', color: COLORS.textMuted }}>Loading history...</div>
        ) : history.length === 0 ? (
          <div style={{ padding: '60px', textAlign: 'center' }}>
             <Activity size={48} color="#cbd5e1" style={{ marginBottom: '16px' }} />
             <p style={{ color: COLORS.textMuted, margin: 0 }}>No scan history found for the last 7 days.</p>
          </div>
        ) : (
          <table style={{ width: '100%', borderCollapse: 'collapse', marginTop: '24px' }}>
            <thead>
              <tr>
                <th style={{ textAlign: 'left', padding: '12px 16px', borderBottom: `2px solid ${COLORS.border}`, color: COLORS.textMuted, fontSize: '12px', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Status</th>
                <th style={{ textAlign: 'left', padding: '12px 16px', borderBottom: `2px solid ${COLORS.border}`, color: COLORS.textMuted, fontSize: '12px', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Medicine Name</th>
                <th style={{ textAlign: 'left', padding: '12px 16px', borderBottom: `2px solid ${COLORS.border}`, color: COLORS.textMuted, fontSize: '12px', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Time</th>
              </tr>
            </thead>
            <tbody>
              {history.map((item, index) => (
                <tr key={item.id} style={{ ...trStyle, backgroundColor: index % 2 === 0 ? 'transparent' : '#f8fafc' }}>
                  <td style={tdStyle}>
                    <div style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: '6px',
                      padding: '4px 10px',
                      borderRadius: '12px',
                      fontSize: '11px',
                      fontWeight: '700',
                      backgroundColor: item.status === 'SUCCESSFUL' ? '#ecfdf5' : '#fef2f2',
                      color: item.status === 'SUCCESSFUL' ? '#059669' : '#dc2626'
                    }}>
                      {item.status === 'SUCCESSFUL' ? <CheckCircle size={12} /> : <XCircle size={12} />}
                      {item.status === 'SUCCESSFUL' ? 'SUCCESS' : 'FAILED'}
                    </div>
                  </td>
                  <td style={{ ...tdStyle, fontWeight: '600', color: COLORS.textMain }}>
                    {item.medicineName || 'Unknown Product'}
                  </td>
                  <td style={tdStyle}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px', color: COLORS.textMuted, fontSize: '13px' }}>
                      <Clock size={14} />
                      {item.timestamp?.toDate ? (
                        <>
                          <span style={{ color: COLORS.textMain, fontWeight: '500' }}>
                            {item.timestamp.toDate().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                          </span>
                          <span style={{ fontSize: '11px' }}>
                            {item.timestamp.toDate().toLocaleDateString()}
                          </span>
                        </>
                      ) : 'N/A'}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* Footer */}
      <div style={{ padding: '20px 32px', borderTop: `1px solid ${COLORS.border}`, backgroundColor: '#f8fafc', textAlign: 'right' }}>
        <p style={{ margin: 0, fontSize: '12px', color: COLORS.textMuted }}>
            Data older than 7 days is automatically cleared.
        </p>
      </div>
    </div>
  );
};

export default ScanHistoryModal;
