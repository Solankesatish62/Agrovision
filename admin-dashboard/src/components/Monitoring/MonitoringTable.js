import React from 'react';
import TableLayout from '../Shared/TableLayout';
import { trStyle, tdStyle, tdBoldStyle, badgeStyle, COLORS } from '../Shared/Styles';

const MonitoringTable = ({ kiosks, scans, formatTimestamp }) => (
  <TableLayout headers={['Device', 'Retail Partner', 'Daily Usage', 'Last Heartbeat', 'Version', 'Status']}>
    {kiosks.map(k => {
      const lastActiveMillis = formatTimestamp(k.lastActiveTimestamp);
      const isOnline = lastActiveMillis > 0 && (Date.now() - lastActiveMillis) < 90 * 1000 && k.status === 'ONLINE';

      return (
        <tr key={k.id} style={trStyle}>
          <td style={tdStyle}>
            <div style={{ fontSize: '12px', fontFamily: 'monospace', color: COLORS.textMuted }}>{k.id}</div>
          </td>
          <td style={tdBoldStyle}>{k.shopName}</td>
          <td style={tdStyle}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <span style={{ fontWeight: '700', color: COLORS.secondary }}>{scans[k.id] || 0}</span>
                <span style={{ fontSize: '11px', color: COLORS.textMuted }}>scans</span>
            </div>
          </td>
          <td style={tdStyle}>
            {lastActiveMillis > 0 ? (
                <div style={{ fontSize: '13px' }}>
                    <div>{new Date(lastActiveMillis).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</div>
                    <div style={{ fontSize: '11px', color: COLORS.textMuted }}>{new Date(lastActiveMillis).toLocaleDateString()}</div>
                </div>
            ) : "Never"}
          </td>
          <td style={tdStyle}>
            <span style={{...badgeStyle, backgroundColor: '#f1f5f9', color: COLORS.textMain, border: `1px solid ${COLORS.border}`}}>
                v{k.appVersion || '1.0'}
            </span>
          </td>
          <td style={tdStyle}>
            <div style={{
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
                color: isOnline ? COLORS.secondary : COLORS.danger,
                fontWeight: '700',
                fontSize: '13px'
            }}>
                <div style={{
                    width: '8px',
                    height: '8px',
                    borderRadius: '50%',
                    backgroundColor: isOnline ? COLORS.secondary : COLORS.danger,
                    boxShadow: isOnline ? `0 0 10px ${COLORS.secondary}80` : 'none'
                }} />
                {isOnline ? 'ONLINE' : 'OFFLINE'}
            </div>
          </td>
        </tr>
      );
    })}
  </TableLayout>
);

export default MonitoringTable;
