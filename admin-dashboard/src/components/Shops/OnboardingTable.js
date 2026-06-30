import React from 'react';
import TableLayout from '../Shared/TableLayout';
import { trStyle, tdStyle, tdBoldStyle, badgeStyle, COLORS } from '../Shared/Styles';

const OnboardingTable = ({ shops, formatTimestamp, onDelete }) => (
  <TableLayout headers={['Phone Number', 'Owner', 'Shop Name', 'Registered Date', 'Status', 'Actions']}>
    {shops.map(s => (
      <tr key={s.id} style={trStyle}>
        <td style={tdStyle}>{s.phoneNumber || s.id}</td>
        <td style={tdBoldStyle}>{s.ownerName || '—'}</td>
        <td style={tdStyle}>{s.shopName || '—'}</td>
        <td style={tdStyle}>{s.onboardingDate ? new Date(formatTimestamp(s.onboardingDate)).toLocaleString() : 'N/A'}</td>
        <td style={tdStyle}>
            <span style={{
                ...badgeStyle,
                backgroundColor: s.status === 'ACTIVE' ? '#f0fdf4' : '#f1f5f9',
                color: s.status === 'ACTIVE' ? '#166534' : COLORS.textMain
            }}>
                {s.status || 'PENDING'}
            </span>
        </td>
        <td style={tdStyle}>
            <button
                onClick={() => onDelete(s.id)}
                style={{
                    padding: '8px 16px',
                    backgroundColor: '#fff1f2',
                    color: '#e11d48',
                    border: '1px solid #ffe4e6',
                    borderRadius: '8px',
                    cursor: 'pointer',
                    fontWeight: '600',
                    fontSize: '13px',
                    transition: 'all 0.2s'
                }}
                onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#ffe4e6'}
                onMouseLeave={(e) => e.currentTarget.style.backgroundColor = '#fff1f2'}
            >
                Delete
            </button>
        </td>
      </tr>
    ))}
  </TableLayout>
);

export default OnboardingTable;
