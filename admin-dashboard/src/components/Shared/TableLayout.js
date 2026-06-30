import React from 'react';
import { thStyle, COLORS } from './Styles';

const TableLayout = ({ headers, children }) => (
  <div style={{ width: '100%', overflowX: 'auto' }}>
    <table style={{
      width: '100%',
      borderCollapse: 'separate',
      borderSpacing: 0,
      backgroundColor: 'transparent'
    }}>
      <thead>
        <tr>
          {headers.map((h, i) => (
            <th
              key={h}
              style={{
                ...thStyle,
                borderTopLeftRadius: i === 0 ? '12px' : 0,
                borderTopRightRadius: i === headers.length - 1 ? '12px' : 0,
                backgroundColor: '#f1f5f9'
              }}
            >
              {h}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {children}
      </tbody>
    </table>
  </div>
);

export default TableLayout;
