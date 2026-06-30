import React from 'react';
import { SHADOWS, COLORS } from './Styles';

const Card = ({ title, value, color, icon }) => (
  <div style={{
    padding: '24px',
    borderRadius: '16px',
    minWidth: '240px',
    flex: 1,
    backgroundColor: 'white',
    boxShadow: SHADOWS.md,
    border: `1px solid ${COLORS.border}`,
    display: 'flex',
    flexDirection: 'column',
    position: 'relative',
    overflow: 'hidden',
    transition: 'transform 0.2s ease, box-shadow 0.2s ease',
    cursor: 'default'
  }}>
    <div style={{
      position: 'absolute',
      top: '-10px',
      right: '-10px',
      fontSize: '80px',
      opacity: 0.05,
      color: color
    }}>
      {icon}
    </div>
    <h3 style={{
      margin: '0 0 12px 0',
      color: COLORS.textMuted,
      fontSize: '13px',
      fontWeight: '700',
      textTransform: 'uppercase',
      letterSpacing: '0.05em'
    }}>
      {title}
    </h3>
    <div style={{ display: 'flex', alignItems: 'baseline', gap: '8px' }}>
      <p style={{
        fontSize: '32px',
        margin: 0,
        fontWeight: '800',
        color: COLORS.textMain,
        letterSpacing: '-0.02em'
      }}>
        {value}
      </p>
      <div style={{
        width: '8px',
        height: '8px',
        borderRadius: '50%',
        backgroundColor: color
      }} />
    </div>
  </div>
);

export default Card;
