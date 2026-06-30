import React from 'react';
import { labelStyle, inputStyle, COLORS, SHADOWS, buttonBase } from '../Shared/Styles';

const AppUpdatesView = ({ config, setConfig, onSave, isSaving }) => {
  const isUrlValid = config.apkUrl.includes('firebasestorage') || config.apkUrl.includes('agrovision');

  return (
    <div style={{ display: 'grid', gridTemplateColumns: '1fr 400px', gap: '40px', alignItems: 'start' }}>
      {/* Configuration Form */}
      <div style={{
        backgroundColor: COLORS.white,
        padding: '32px',
        borderRadius: '20px',
        border: `1px solid ${COLORS.border}`,
        boxShadow: SHADOWS.sm
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '24px' }}>
            <div style={{ fontSize: '24px' }}>🚀</div>
            <h2 style={{ margin: 0, fontSize: '20px', color: COLORS.textMain }}>Release Configuration</h2>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px' }}>
            <div>
                <label style={labelStyle}>Version Code (Internal)</label>
                <input
                    type="number"
                    style={inputStyle}
                    value={config.latestVersionCode}
                    onChange={(e) => setConfig({ ...config, latestVersionCode: parseInt(e.target.value) || 0 })}
                    placeholder="e.g. 2"
                />
                <p style={{ fontSize: '12px', color: COLORS.textMuted, marginTop: '6px' }}>Must be incremented to trigger updates.</p>
            </div>
            <div>
                <label style={labelStyle}>Version Name (Display)</label>
                <input
                    type="text"
                    style={inputStyle}
                    value={config.latestVersionName}
                    onChange={(e) => setConfig({ ...config, latestVersionName: e.target.value })}
                    placeholder="e.g. 1.2.0-stable"
                />
                <p style={{ fontSize: '12px', color: COLORS.textMuted, marginTop: '6px' }}>Public version string shown to users.</p>
            </div>
          </div>

          <div>
            <label style={labelStyle}>APK Artifact URL</label>
            <input
              type="text"
              style={{
                  ...inputStyle,
                  borderColor: config.apkUrl && !isUrlValid ? COLORS.danger : COLORS.border,
                  fontFamily: 'monospace',
                  fontSize: '13px'
              }}
              value={config.apkUrl}
              onChange={(e) => setConfig({ ...config, apkUrl: e.target.value })}
              placeholder="https://firebasestorage.googleapis.com/..."
            />
            {!isUrlValid && config.apkUrl && (
                <p style={{ fontSize: '12px', color: COLORS.danger, marginTop: '6px', fontWeight: '600' }}>
                    ⚠️ Warning: URL should be from a trusted AgroVision or Firebase source.
                </p>
            )}
          </div>

          <div style={{
              padding: '20px',
              backgroundColor: config.forceUpdate ? '#fff1f2' : '#f0fdf4',
              borderRadius: '12px',
              border: `1px solid ${config.forceUpdate ? '#fecaca' : '#bbf7d0'}`,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between'
          }}>
            <div>
                <p style={{ margin: 0, fontWeight: '700', color: config.forceUpdate ? '#991b1b' : '#166534', fontSize: '14px' }}>
                    {config.forceUpdate ? 'Critical Force Update' : 'Optional Update'}
                </p>
                <p style={{ margin: '4px 0 0 0', fontSize: '12px', color: COLORS.textMuted }}>
                    {config.forceUpdate ? 'Users must update to continue using the app.' : 'Users can skip this update and continue.'}
                </p>
            </div>
            <div
                onClick={() => setConfig({ ...config, forceUpdate: !config.forceUpdate })}
                style={{
                    width: '50px',
                    height: '26px',
                    backgroundColor: config.forceUpdate ? COLORS.danger : '#cbd5e1',
                    borderRadius: '13px',
                    position: 'relative',
                    cursor: 'pointer',
                    transition: 'all 0.2s ease'
                }}
            >
                <div style={{
                    width: '20px',
                    height: '20px',
                    backgroundColor: 'white',
                    borderRadius: '50%',
                    position: 'absolute',
                    top: '3px',
                    left: config.forceUpdate ? '27px' : '3px',
                    transition: 'all 0.2s ease',
                    boxShadow: SHADOWS.sm
                }} />
            </div>
          </div>

          <button
            onClick={onSave}
            disabled={isSaving || !config.apkUrl}
            style={{
              ...buttonBase,
              backgroundColor: COLORS.primary,
              color: 'white',
              padding: '16px',
              fontSize: '16px',
              marginTop: '10px',
              opacity: (isSaving || !config.apkUrl) ? 0.7 : 1,
              boxShadow: SHADOWS.md
            }}
          >
            {isSaving ? '⏳ Deploying...' : '🚀 Publish Update to Kiosks'}
          </button>
        </div>
      </div>

      {/* Live Preview */}
      <div style={{ position: 'sticky', top: '40px' }}>
        <p style={{ fontSize: '12px', fontWeight: '700', color: COLORS.textMuted, marginBottom: '12px', textTransform: 'uppercase', letterSpacing: '1px' }}>Kiosk Live Preview</p>
        <div style={{
            backgroundColor: '#0f172a',
            borderRadius: '24px',
            padding: '40px 20px',
            boxShadow: SHADOWS.xl,
            border: '8px solid #334155',
            height: '500px',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            position: 'relative',
            overflow: 'hidden'
        }}>
            {/* Mock Kiosk UI */}
            <div style={{
                backgroundColor: 'white',
                width: '100%',
                borderRadius: '16px',
                padding: '24px',
                textAlign: 'center',
                boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.5)'
            }}>
                <div style={{ fontSize: '40px', marginBottom: '16px' }}>🆙</div>
                <h3 style={{ margin: '0 0 8px 0', fontSize: '18px', color: '#1e293b' }}>New Update Available!</h3>
                <p style={{ fontSize: '13px', color: '#64748b', margin: '0 0 20px 0' }}>
                    Version {config.latestVersionName || '1.0.0'} is ready to install.
                </p>

                <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                    <div style={{
                        backgroundColor: COLORS.primary,
                        color: 'white',
                        padding: '12px',
                        borderRadius: '8px',
                        fontSize: '14px',
                        fontWeight: '700'
                    }}>
                        UPDATE NOW
                    </div>
                    {!config.forceUpdate && (
                        <div style={{
                            color: COLORS.textMuted,
                            fontSize: '12px',
                            fontWeight: '600'
                        }}>
                            Maybe Later
                        </div>
                    )}
                </div>
            </div>

            {/* Background elements */}
            <div style={{ position: 'absolute', bottom: '20px', color: '#475569', fontSize: '10px', fontWeight: 'bold' }}>AGROVISION KIOSK TERMINAL</div>
        </div>
        <p style={{ fontSize: '12px', color: COLORS.textMuted, textAlign: 'center', marginTop: '16px', fontStyle: 'italic' }}>
            This is how the update prompt will appear on all active kiosks.
        </p>
      </div>
    </div>
  );
};

export default AppUpdatesView;
