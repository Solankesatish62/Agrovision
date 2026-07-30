import React from 'react';
import TableLayout from '../Shared/TableLayout';
import { trStyle, tdStyle, tdBoldStyle, badgeStyle, exportBtnStyle, COLORS } from '../Shared/Styles';

const MedicineTable = ({ medicines, searchTerm, setSearchTerm, onAdd, onEdit, onDelete, onBulk }) => {
  const cleanCib = (cib) => {
    if (!cib) return 'N/A';
    return cib.toString().replace(/[\s\-.|]{2,}$/, '').trim();
  };

  return (
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
            <button onClick={onBulk} style={{...exportBtnStyle, backgroundColor: '#d946ef', borderRadius: '10px', padding: '12px 25px', boxShadow: '0 4px 12px rgba(217, 70, 239, 0.2)'}}>✨ Bulk Import</button>
            <button onClick={onAdd} style={{...exportBtnStyle, backgroundColor: '#1a5f7a', borderRadius: '10px', padding: '12px 25px', boxShadow: '0 4px 12px rgba(26, 95, 122, 0.2)'}}>+ Add New Medicine</button>
        </div>
      </div>
      <div style={{ overflowX: 'auto', borderRadius: '12px', border: '1px solid #f1f5f9' }}>
        <TableLayout headers={['#', 'Composition & CIB', 'Status', 'Actions']}>
          {medicines.map((m, index) => {
            const name = m.name || m.medicineName || m.id;

            return (
              <tr key={m.id} style={{...trStyle, borderBottom: '1px solid #f1f5f9'}}>
                <td style={{...tdStyle, color: '#94a3b8', fontSize: '13px', fontWeight: '600', verticalAlign: 'middle', width: '50px'}}>{index + 1}</td>

                {/* Column 1: Medicine, Chemical & CIB Combined */}
                <td style={{...tdStyle, verticalAlign: 'middle', minWidth: '350px'}}>
                    <div>
                        <div style={{ color: '#0f172a', fontWeight: '700', fontSize: '16px' }}>{name}</div>
                        <div style={{ color: '#64748b', fontSize: '12px', marginBottom: '6px' }}>{m.company || 'Unknown Company'}</div>

                        <div style={{ fontSize: '13px', color: '#475569', fontWeight: '600' }}>
                            {m.chemicalName || <span style={{color: '#cbd5e1', fontWeight: '400'}}>No chemical info</span>}
                        </div>
                        <div style={{ color: '#94a3b8', fontSize: '11px', display: 'flex', alignItems: 'center', gap: '4px', marginTop: '2px' }}>
                            <span style={{backgroundColor: '#f1f5f9', color: '#64748b', padding: '1px 5px', borderRadius: '4px', fontWeight: '800', fontSize: '9px'}}>CIB</span>
                            {cleanCib(m.cibNo)}
                        </div>
                    </div>
                </td>

                {/* Column 2: Status */}
                <td style={{...tdStyle, verticalAlign: 'middle'}}>
                    <div style={{fontSize: '12px'}}>
                        <span style={{ color: '#10b981', fontWeight: '700', display: 'flex', alignItems: 'center', gap: '4px' }}>
                            <span style={{width: '6px', height: '6px', backgroundColor: '#10b981', borderRadius: '50%'}}></span>
                            Active
                        </span>
                        <div style={{ color: '#94a3b8', marginTop: '2px' }}>
                            {m.updatedAt ? new Date(m.updatedAt.toMillis ? m.updatedAt.toMillis() : m.updatedAt).toLocaleDateString() : 'Recently added'}
                        </div>
                    </div>
                </td>

                {/* Column 3: Actions */}
                <td style={{...tdStyle, verticalAlign: 'middle'}}>
                    <div style={{ display: 'flex', gap: '8px' }}>
                        <button onClick={() => onEdit(m)} style={{
                            padding: '8px 16px',
                            backgroundColor: '#f1f5f9',
                            color: '#1e293b',
                            border: 'none',
                            borderRadius: '8px',
                            cursor: 'pointer',
                            fontWeight: '600',
                            fontSize: '13px',
                            transition: 'all 0.2s'
                        }}>Edit</button>
                        <button onClick={() => onDelete(m.id)} style={{
                            padding: '8px 16px',
                            backgroundColor: '#fff1f2',
                            color: '#e11d48',
                            border: 'none',
                            borderRadius: '8px',
                            cursor: 'pointer',
                            fontWeight: '600',
                            fontSize: '13px',
                            transition: 'all 0.2s'
                        }}>Delete</button>
                    </div>
                </td>
              </tr>
            );
          })}
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
};

export default MedicineTable;
