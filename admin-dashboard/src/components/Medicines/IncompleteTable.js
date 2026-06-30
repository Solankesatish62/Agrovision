import React from 'react';
import TableLayout from '../Shared/TableLayout';
import { trStyle, tdStyle, tdBoldStyle, badgeStyle } from '../Shared/Styles';

const IncompleteTable = ({ medicines, searchTerm, setSearchTerm, onEdit }) => (
  <div style={{ backgroundColor: 'white', padding: '20px', borderRadius: '12px', boxShadow: '0 4px 6px rgba(0,0,0,0.05)' }}>
    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '20px', alignItems: 'center' }}>
      <div>
        <h2 style={{ margin: 0 }}>⚠️ Incomplete Medicine Profiles</h2>
        <p style={{ color: '#666', margin: '5px 0 0 0' }}>These medicines are missing critical data like Images, Audio, Marathi Info, or Keywords.</p>
      </div>
      <input
        type="text"
        placeholder="Search incomplete medicines..."
        value={searchTerm}
        onChange={(e) => setSearchTerm(e.target.value)}
        style={{
            padding: '10px 15px',
            borderRadius: '10px',
            border: '1px solid #e2e8f0',
            width: '300px',
            fontSize: '14px'
        }}
      />
    </div>
    <TableLayout headers={['#', 'Medicine Name', 'Missing Fields', 'Actions']}>
      {medicines.map((m, index) => {
          const missing = [];
          if (!(m.marathiInfo || m.warnings)) missing.push('Marathi Info');
          if (!((m.searchKeywords && m.searchKeywords.length > 0) || (m.ocrKeywords && m.ocrKeywords.length > 0))) missing.push('Keywords');
          if (!((m.imageUrls && m.imageUrls.length > 0) || (m.imageurls && m.imageurls.length > 0))) missing.push('Images');
          if (!(m.audioUrls || m.audiourls)) missing.push('Audio');
          if (!(m.crop || (m.supportedCrops && m.supportedCrops.length > 0))) missing.push('Crops');
          if (!(m.disease || (m.supportedDiseases && m.supportedDiseases.length > 0))) missing.push('Diseases');
          if (!m.company || m.company === 'Unknown') missing.push('Company');

          return (
            <tr key={m.id} style={trStyle}>
                <td style={tdStyle}>{index + 1}</td>
                <td style={tdBoldStyle}>{m.name || m.medicineName || m.id}</td>
                <td style={tdStyle}>
                    {missing.map(f => <span key={f} style={{...badgeStyle, backgroundColor: '#fff1f2', color: '#e11d48', marginRight: '5px'}}>{f}</span>)}
                </td>
                <td style={tdStyle}>
                    <button onClick={() => onEdit(m)} style={{ padding: '6px 12px', backgroundColor: '#3498db', color: 'white', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: 'bold' }}>Fix Now</button>
                </td>
            </tr>
          );
      })}
    </TableLayout>
    {medicines.length === 0 && (
        <div style={{ textAlign: 'center', padding: '40px', color: '#94a3b8' }}>
            <p>No incomplete medicines found.</p>
        </div>
    )}
  </div>
);

export default IncompleteTable;
