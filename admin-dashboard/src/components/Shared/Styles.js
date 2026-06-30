// Professional Color Palette
export const COLORS = {
  primary: '#1e3a8a', // Deep Blue
  primaryLight: '#3b82f6',
  secondary: '#10b981', // Emerald Green
  accent: '#f59e0b', // Amber
  danger: '#ef4444', // Rose Red
  background: '#f8fafc', // Slate 50
  cardBg: '#ffffff',
  textMain: '#1e293b', // Slate 800
  textMuted: '#64748b', // Slate 500
  border: '#e2e8f0', // Slate 200
  white: '#ffffff'
};

export const SHADOWS = {
  sm: '0 1px 2px 0 rgba(0, 0, 0, 0.05)',
  md: '0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06)',
  lg: '0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -2px rgba(0, 0, 0, 0.05)',
  xl: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04)'
};

export const labelStyle = {
  display: 'block',
  marginBottom: '8px',
  fontWeight: '600',
  color: COLORS.textMain,
  fontSize: '14px'
};

export const inputStyle = {
  width: '100%',
  padding: '12px 16px',
  borderRadius: '10px',
  border: `1.5px solid ${COLORS.border}`,
  boxSizing: 'border-box',
  fontSize: '15px',
  transition: 'all 0.2s ease',
  outline: 'none',
  backgroundColor: '#fff',
  '&:focus': {
    borderColor: COLORS.primaryLight,
    boxShadow: `0 0 0 3px ${COLORS.primaryLight}20`
  }
};

export const thStyle = {
  padding: '16px 20px',
  color: COLORS.textMuted,
  borderBottom: `2px solid ${COLORS.border}`,
  textAlign: 'left',
  fontSize: '13px',
  fontWeight: '700',
  textTransform: 'uppercase',
  letterSpacing: '0.05em'
};

export const tdStyle = {
  padding: '16px 20px',
  borderBottom: `1px solid ${COLORS.border}`,
  fontSize: '14px',
  color: COLORS.textMain
};

export const tdBoldStyle = {
  ...tdStyle,
  fontWeight: '600',
  color: COLORS.primary
};

export const trStyle = {
  transition: 'background-color 0.2s ease',
  cursor: 'default'
};

export const badgeStyle = {
  padding: '6px 12px',
  borderRadius: '20px',
  backgroundColor: '#e1f5fe',
  color: '#01579b',
  fontSize: '11px',
  fontWeight: '700',
  display: 'inline-flex',
  alignItems: 'center',
  textTransform: 'uppercase'
};

export const buttonBase = {
  padding: '10px 20px',
  borderRadius: '10px',
  border: 'none',
  cursor: 'pointer',
  fontWeight: '600',
  fontSize: '14px',
  transition: 'all 0.2s ease',
  display: 'inline-flex',
  alignItems: 'center',
  justifyContent: 'center',
  gap: '8px'
};

export const exportBtnStyle = {
  ...buttonBase,
  backgroundColor: COLORS.secondary,
  color: 'white',
  boxShadow: '0 4px 6px -1px rgba(16, 185, 129, 0.2)',
  '&:hover': {
    backgroundColor: '#059669',
    transform: 'translateY(-1px)'
  }
};

export const containerStyle = {
  backgroundColor: COLORS.white,
  padding: '24px',
  borderRadius: '16px',
  boxShadow: SHADOWS.md,
  border: `1px solid ${COLORS.border}`
};
