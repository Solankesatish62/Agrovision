import React from 'react';
import TableLayout from '../Shared/TableLayout';
import { trStyle, tdStyle, tdBoldStyle, badgeStyle, exportBtnStyle } from '../Shared/Styles';

const MedicineTable = ({ medicines, searchTerm, setSearchTerm, onAdd, onEdit, onDelete, onBulk }) => (
  <div style={{ backgroundColor: 'white', padding: '30px', borderRadius: '16px', boxShadow: '0 4px 20px rgba(0,0,0,0.05)' }}>
    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '25px', alignItems: 'center', flexWrap: 'wrap', gap: '15px' }}>
      <div>
        <h2 style={{ margin: 0, color: '#1e293b', fontSize: '22px' }}>🌿 Approved Medicine Catalog</h2>
        <p style={{ margin: '5px 0 0 0', color: '#64748b', fontSize: '14px' }}>Manage the list of medicines recognized by the AI Vision engine.</p>
      </div>
      <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
          <div style={{ position: 'relative' }}>
            <input
                type="text"
                placeholder="Search medicines..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                style={{
                    padding: '12px 15px',
                    paddingLeft: '40px',
                    borderRadius: '10px',
                    border: '1px solid #e2e8f0',
                    width: '320px',
                    fontSize: '14px',
                    outline: 'none',
                    transition: 'border-color 0.2s'
                }}
            />
            <span style={{ position: 'absolute', left: '15px', top: '50%', transform: 'translateY(-50%)', color: '#94a3b8' }}>🔍</span>
          </div>
          <button onClick={onBulk} style={{...exportBtnStyle, backgroundColor: '#d946ef', borderRadius: '10px', padding: '12px 25px'}}>✨ Bulk Import</button>
          <button onClick={onAdd} style={{...exportBtnStyle, backgroundColor: '#1a5f7a', borderRadius: '10px', padding: '12px 25px'}}>+ Add New Medicine</button>
      </div>
    </div>
    <div style={{ overflowX: 'auto', borderRadius: '12px', border: '1px solid #f1f5f9' }}>
      <TableLayout headers={['#', 'Medicine Name', 'Company', 'Crops / Target', 'AI Prefixes', 'Status', 'Actions']}>
        {medicines.map((m, index) => (
          <tr key={m.id} style={trStyle}>
            <td style={{...tdStyle, color: '#94a3b8', fontSize: '13px', fontWeight: '600'}}>{index + 1}</td>
            <td style={tdBoldStyle}>
                <div style={{ color: '#0f172a' }}>{m.name || m.medicineName || m.id}</div>
                <div style={{ fontSize: '11px', color: '#94a3b8', fontWeight: 'normal', fontFamily: 'monospace', marginTop: '2px' }}>ID: {m.id}</div>
            </td>
            <td style={tdStyle}>
                <div style={{ color: '#475569', fontSize: '14px' }}>{m.company || '—'}</div>
            </td>
            <td style={tdStyle}>
                <div style={{ color: '#64748b', fontSize: '13px', maxWidth: '220px', lineHeight: '1.4' }}>
                    <strong>Crops:</strong> {Array.isArray(m.crop) ? m.crop.join(', ') : (Array.isArray(m.supportedCrops) ? m.supportedCrops.join(', ') : (m.crop || '—'))}<br/>
                    <strong>Pests:</strong> {Array.isArray(m.disease) ? m.disease.join(', ') : (Array.isArray(m.supportedDiseases) ? m.supportedDiseases.join(', ') : (m.disease || '—'))}
                </div>
            </td>
            <td style={tdStyle}>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px', maxWidth: '200px' }}>
                    {Array.isArray(m.barcodePrefixes) && m.barcodePrefixes.length > 0 ?
                        m.barcodePrefixes.slice(0, 3).map(p => <span key={p} style={{...badgeStyle, backgroundColor: '#f0fdf4', color: '#166534', border: '1px solid #dcfce7'}}>{p}</span>)
                        : <span style={{color: '#cbd5e1'}}>No prefixes</span>
                    }
                    {Array.isArray(m.barcodePrefixes) && m.barcodePrefixes.length > 3 && <span style={{fontSize: '11px', color: '#94a3b8', alignSelf: 'center'}}>+{m.barcodePrefixes.length - 3} more</span>}
                </div>
            </td>
            <td style={tdStyle}>
                <div style={{fontSize: '12px'}}>
                    <span style={{ color: '#10b981' }}>● Active</span><br/>
                    <small style={{ color: '#94a3b8' }}>{m.updatedAt ? new Date(m.updatedAt.toMillis ? m.updatedAt.toMillis() : m.updatedAt).toLocaleDateString() : '—'}</small>
                </div>
            </td>
            <td style={tdStyle}>
                <div style={{ display: 'flex', gap: '8px' }}>
                    <button onClick={() => onEdit(m)} style={{ padding: '8px 16px', backgroundColor: '#f1f5f9', color: '#1e293b', border: '1px solid #e2e8f0', borderRadius: '8px', cursor: 'pointer', fontWeight: '600', fontSize: '13px' }}>Edit</button>
                    <button onClick={() => onDelete(m.id)} style={{ padding: '8px 16px', backgroundColor: '#fff1f2', color: '#e11d48', border: '1px solid #ffe4e6', borderRadius: '8px', cursor: 'pointer', fontWeight: '600', fontSize: '13px' }}>Delete</button>
                </div>
            </td>
          </tr>
        ))}
      </TableLayout>
    </div>
    {medicines.length === 0 && (
        <div style={{ textAlign: 'center', padding: '50px', color: '#94a3b8' }}>
            <div style={{ fontSize: '40px', marginBottom: '10px' }}>🔍</div>
            <p>No medicines found matching your search.</p>
        </div>
    )}
  </div>
);

export default MedicineTable;
