import React from 'react';
import { COLORS, SHADOWS } from './Styles';

const NavButton = ({ active, onClick, label, icon, isCollapsed }) => (
  <button
    onClick={onClick}
    className={`nav-button ${active ? 'active' : ''}`}
    title={isCollapsed ? label : ''}
    style={{
      padding: isCollapsed ? '12px' : '12px 20px',
      borderRadius: '12px',
      border: 'none',
      cursor: 'pointer',
      fontWeight: '600',
      fontSize: '14px',
      display: 'flex',
      alignItems: 'center',
      justifyContent: isCollapsed ? 'center' : 'flex-start',
      gap: isCollapsed ? '0' : '10px',
      transition: 'all 0.2s cubic-bezier(0.4, 0, 0.2, 1)',
      backgroundColor: active ? COLORS.primary : 'transparent',
      color: active ? COLORS.white : COLORS.textMuted,
      boxShadow: active ? SHADOWS.md : 'none',
      outline: 'none',
      width: '100%',
      overflow: 'hidden',
      whiteSpace: 'nowrap',
      marginBottom: '4px'
    }}
  >
    <span style={{ fontSize: '20px', minWidth: '24px', display: 'flex', justifyContent: 'center' }}>{icon}</span>
    {!isCollapsed && <span style={{ opacity: 1, transition: 'opacity 0.2s' }}>{label}</span>}
  </button>
);

export default NavButton;
