import React, { useState, useEffect, useMemo, useRef } from 'react';
import TableLayout from '../Shared/TableLayout';
import { trStyle, tdStyle, tdBoldStyle, badgeStyle, COLORS, SHADOWS } from '../Shared/Styles';

const DatabaseManager = ({ files, loading }) => {
    const [activeTab, setActiveTab] = useState('images');
    const [searchTerm, setSearchTerm] = useState('');
    const [playingUrl, setPlayingUrl] = useState(null);
    const [copiedUrl, setCopiedUrl] = useState(null);
    const audioRef = useRef(null);

    const folders = [
        { id: 'images', name: 'Images', icon: '🖼️', path: 'medicine-images' },
        { id: 'audio', name: 'Audio', icon: '🎵', path: 'medicine-audio' },
        { id: 'advertisements', name: 'Advertisements', icon: '📺', path: 'advertisements' }
    ];

    useEffect(() => {
        return () => {
            if (audioRef.current) {
                audioRef.current.pause();
            }
        };
    }, []);

    const toggleAudio = (url) => {
        if (playingUrl === url) {
            audioRef.current.pause();
            setPlayingUrl(null);
        } else {
            if (audioRef.current) {
                audioRef.current.pause();
            }
            audioRef.current = new Audio(url);
            audioRef.current.play().catch(e => alert("Error playing audio: " + e.message));
            audioRef.current.onended = () => setPlayingUrl(null);
            setPlayingUrl(url);
        }
    };

    const copyToClipboard = (url) => {
        navigator.clipboard.writeText(url);
        setCopiedUrl(url);
        setTimeout(() => setCopiedUrl(null), 2000);
    };

    const formatBytes = (bytes, decimals = 2) => {
        if (bytes === 0) return '0 Bytes';
        const k = 1024;
        const dm = decimals < 0 ? 0 : decimals;
        const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i];
    };

    const filteredFiles = useMemo(() => {
        const currentFiles = files[activeTab] || [];
        if (!searchTerm) return currentFiles;
        return currentFiles.filter(f => f.name.toLowerCase().includes(searchTerm.toLowerCase()));
    }, [files, activeTab, searchTerm]);

    return (
        <div style={{ backgroundColor: 'white', padding: '30px', borderRadius: '16px', boxShadow: '0 4px 20px rgba(0,0,0,0.05)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '25px', alignItems: 'center', flexWrap: 'wrap', gap: '15px' }}>
                <div>
                    <h2 style={{ margin: 0, color: COLORS.textMain, fontSize: '22px' }}>🗄️ Storage Asset Manager</h2>
                    <p style={{ margin: '5px 0 0 0', color: COLORS.textMuted, fontSize: '14px' }}>Browse and copy URLs for assets stored in Firebase.</p>
                </div>
                <div style={{ display: 'flex', gap: '12px' }}>
                    <div style={{ position: 'relative' }}>
                        <input
                            type="text"
                            placeholder="Search files..."
                            value={searchTerm}
                            onChange={(e) => setSearchTerm(e.target.value)}
                            style={{
                                padding: '12px 15px',
                                paddingLeft: '40px',
                                borderRadius: '10px',
                                border: `1px solid ${COLORS.border}`,
                                width: '320px',
                                fontSize: '14px',
                                outline: 'none'
                            }}
                        />
                        <span style={{ position: 'absolute', left: '15px', top: '50%', transform: 'translateY(-50%)', color: '#94a3b8' }}>🔍</span>
                    </div>
                </div>
            </div>

            <div style={{ display: 'flex', gap: '10px', marginBottom: '25px', borderBottom: `1px solid ${COLORS.border}`, paddingBottom: '15px' }}>
                {folders.map(folder => (
                    <button
                        key={folder.id}
                        onClick={() => setActiveTab(folder.id)}
                        style={{
                            padding: '10px 20px',
                            borderRadius: '8px',
                            border: 'none',
                            backgroundColor: activeTab === folder.id ? COLORS.primary : 'transparent',
                            color: activeTab === folder.id ? 'white' : COLORS.textMain,
                            cursor: 'pointer',
                            fontWeight: '600',
                            transition: 'all 0.2s',
                            display: 'flex',
                            alignItems: 'center',
                            gap: '8px'
                        }}
                    >
                        <span>{folder.icon}</span>
                        {folder.name}
                        <span style={{
                            fontSize: '11px',
                            backgroundColor: activeTab === folder.id ? 'rgba(255,255,255,0.2)' : '#f1f5f9',
                            padding: '2px 8px',
                            borderRadius: '10px',
                            marginLeft: '5px'
                        }}>
                            {files[folder.id]?.length || 0}
                        </span>
                    </button>
                ))}
            </div>

            {loading ? (
                <div style={{ textAlign: 'center', padding: '50px' }}>Loading assets...</div>
            ) : (
                <div style={{ overflowX: 'auto', borderRadius: '12px', border: `1px solid ${COLORS.border}` }}>
                    <TableLayout headers={['#', 'File Name', 'Type', 'Size', 'Created', 'Action']}>
                        {filteredFiles.map((file, index) => (
                            <tr key={file.fullPath} style={trStyle}>
                                <td style={{ ...tdStyle, color: '#94a3b8', fontSize: '13px', fontWeight: '600' }}>{index + 1}</td>
                                <td style={tdBoldStyle}>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                                        {activeTab === 'images' && (
                                            <img src={file.url} alt="" style={{ width: '40px', height: '40px', borderRadius: '6px', objectFit: 'cover', border: `1px solid ${COLORS.border}` }} />
                                        )}
                                        <div style={{ maxWidth: '300px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                                            {file.name}
                                        </div>
                                    </div>
                                </td>
                                <td style={tdStyle}>
                                    <span style={{ ...badgeStyle, backgroundColor: '#f1f5f9', color: COLORS.textMain }}>
                                        {file.contentType?.split('/')[1]?.toUpperCase() || 'FILE'}
                                    </span>
                                </td>
                                <td style={tdStyle}>{formatBytes(file.size)}</td>
                                <td style={tdStyle}>{new Date(file.timeCreated).toLocaleDateString()}</td>
                                <td style={tdStyle}>
                                    <div style={{ display: 'flex', gap: '8px' }}>
                                        {activeTab === 'audio' && (
                                            <button
                                                onClick={() => toggleAudio(file.url)}
                                                style={{
                                                    padding: '8px 12px',
                                                    backgroundColor: playingUrl === file.url ? COLORS.danger : COLORS.secondary,
                                                    color: 'white',
                                                    border: 'none',
                                                    borderRadius: '8px',
                                                    cursor: 'pointer',
                                                    fontWeight: '600',
                                                    fontSize: '13px',
                                                    boxShadow: SHADOWS.sm,
                                                    minWidth: '90px'
                                                }}
                                            >
                                                {playingUrl === file.url ? '⏸️ Pause' : '▶️ Play'}
                                            </button>
                                        )}
                                        <button
                                            onClick={() => copyToClipboard(file.url)}
                                            style={{
                                                padding: '8px 16px',
                                                backgroundColor: copiedUrl === file.url ? COLORS.secondary : COLORS.primary,
                                                color: 'white',
                                                border: 'none',
                                                borderRadius: '8px',
                                                cursor: 'pointer',
                                                fontWeight: '600',
                                                fontSize: '13px',
                                                boxShadow: SHADOWS.sm,
                                                minWidth: '110px',
                                                transition: 'all 0.3s ease'
                                            }}
                                        >
                                            {copiedUrl === file.url ? '✅ Copied!' : 'Copy URL'}
                                        </button>
                                    </div>
                                </td>
                            </tr>
                        ))}
                    </TableLayout>
                    {filteredFiles.length === 0 && (
                        <div style={{ textAlign: 'center', padding: '50px', color: '#94a3b8' }}>
                            <p>No files found in this folder.</p>
                        </div>
                    )}
                </div>
            )}
        </div>
    );
};

export default DatabaseManager;
