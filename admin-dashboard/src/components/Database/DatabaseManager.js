import React, { useState, useEffect, useMemo, useRef } from 'react';
import TableLayout from '../Shared/TableLayout';
import { trStyle, tdStyle, tdBoldStyle, badgeStyle, COLORS, SHADOWS } from '../Shared/Styles';
import { calculateMatchScore } from '../../utils/AssetMatcher';
import { storage } from '../../firebase';
import { ref, getDownloadURL, getMetadata } from 'firebase/storage';

const DatabaseManager = ({ files, loading, onRefresh }) => {
    const [activeTab, setActiveTab] = useState('images');
    const [searchTerm, setSearchTerm] = useState('');
    const [playingUrl, setPlayingUrl] = useState(null);
    const [copiedUrl, setCopiedUrl] = useState(null);
    const [resolvedFiles, setResolvedFiles] = useState({}); // { fullPath: { url, metadata } }
    const [page, setPage] = useState(1);
    const pageSize = 20;

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

    const toggleAudio = async (asset) => {
        let url = asset.url || resolvedFiles[asset.fullPath]?.url;

        if (!url) {
            try {
                const assetRef = ref(storage, asset.fullPath);
                url = await getDownloadURL(assetRef);
                setResolvedFiles(prev => ({
                    ...prev,
                    [asset.fullPath]: { ...prev[asset.fullPath], url }
                }));
            } catch (e) {
                alert("Error getting audio URL: " + e.message);
                return;
            }
        }

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

    const copyToClipboard = async (asset) => {
        let url = asset.url || resolvedFiles[asset.fullPath]?.url;

        if (!url) {
            try {
                const assetRef = ref(storage, asset.fullPath);
                url = await getDownloadURL(assetRef);
                setResolvedFiles(prev => ({
                    ...prev,
                    [asset.fullPath]: { ...prev[asset.fullPath], url }
                }));
            } catch (e) {
                alert("Error getting URL: " + e.message);
                return;
            }
        }

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
        setPage(1); // Reset page on tab or search change

        if (!searchTerm) return currentFiles;

        return currentFiles
            .map(f => ({ ...f, score: calculateMatchScore(f.name, [searchTerm]) }))
            .filter(f => f.score > 0)
            .sort((a, b) => b.score - a.score);
    }, [files, activeTab, searchTerm]);

    const pagedFiles = useMemo(() => {
        return filteredFiles.slice(0, page * pageSize);
    }, [filteredFiles, page]);

    // Fetch metadata and URLs for the current page
    useEffect(() => {
        if (pagedFiles.length === 0) return;

        const fetchMissingData = async () => {
            const newResolved = { ...resolvedFiles };
            let changed = false;

            for (const file of pagedFiles) {
                if (newResolved[file.fullPath]?.url && newResolved[file.fullPath]?.metadata) continue;

                try {
                    const assetRef = ref(storage, file.fullPath);
                    const [url, metadata] = await Promise.all([
                        newResolved[file.fullPath]?.url ? Promise.resolve(newResolved[file.fullPath].url) : getDownloadURL(assetRef),
                        newResolved[file.fullPath]?.metadata ? Promise.resolve(newResolved[file.fullPath].metadata) : getMetadata(assetRef)
                    ]);

                    newResolved[file.fullPath] = { url, metadata };
                    changed = true;
                } catch (e) { console.warn("Failed to fetch asset info", e); }
            }

            if (changed) setResolvedFiles(newResolved);
        };

        fetchMissingData();
    }, [pagedFiles]);

    return (
        <div style={{ backgroundColor: 'white', padding: '30px', borderRadius: '16px', boxShadow: '0 4px 20px rgba(0,0,0,0.05)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '25px', alignItems: 'center', flexWrap: 'wrap', gap: '15px' }}>
                <div>
                    <h2 style={{ margin: 0, color: COLORS.textMain, fontSize: '22px' }}>🗄️ Storage Asset Manager</h2>
                    <p style={{ margin: '5px 0 0 0', color: COLORS.textMuted, fontSize: '14px' }}>Browse and copy URLs for assets stored in Firebase.</p>
                </div>
                <div style={{ display: 'flex', gap: '12px' }}>
                    <button
                        onClick={onRefresh}
                        disabled={loading}
                        style={{
                            padding: '10px 20px',
                            borderRadius: '10px',
                            border: `1px solid ${COLORS.border}`,
                            backgroundColor: 'white',
                            color: COLORS.textMain,
                            cursor: 'pointer',
                            fontWeight: '600',
                            display: 'flex',
                            alignItems: 'center',
                            gap: '8px'
                        }}
                    >
                        <span style={{ transform: loading ? 'rotate(360deg)' : 'none', transition: 'transform 1s infinite linear', display: 'inline-block' }}>🔄</span>
                        {loading ? 'Refreshing...' : 'Refresh Storage'}
                    </button>
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
                        onClick={() => { setActiveTab(folder.id); setPage(1); }}
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
                        {pagedFiles.map((file, index) => {
                            const resolved = resolvedFiles[file.fullPath] || {};
                            return (
                                <tr key={file.fullPath} style={trStyle}>
                                    <td style={{ ...tdStyle, color: '#94a3b8', fontSize: '13px', fontWeight: '600' }}>{index + 1}</td>
                                    <td style={tdBoldStyle}>
                                        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                                            {activeTab === 'images' && (
                                                resolved.url ? (
                                                    <img src={resolved.url} alt="" style={{ width: '40px', height: '40px', borderRadius: '6px', objectFit: 'cover', border: `1px solid ${COLORS.border}` }} />
                                                ) : (
                                                    <div style={{ width: '40px', height: '40px', borderRadius: '6px', backgroundColor: '#f1f5f9', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '10px' }}>...</div>
                                                )
                                            )}
                                            <div style={{ maxWidth: '300px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                                                {file.name}
                                            </div>
                                        </div>
                                    </td>
                                    <td style={tdStyle}>
                                        <span style={{ ...badgeStyle, backgroundColor: '#f1f5f9', color: COLORS.textMain }}>
                                            {resolved.metadata?.contentType?.split('/')[1]?.toUpperCase() || '...'}
                                        </span>
                                    </td>
                                    <td style={tdStyle}>{resolved.metadata ? formatBytes(resolved.metadata.size) : '...'}</td>
                                    <td style={tdStyle}>{resolved.metadata ? new Date(resolved.metadata.timeCreated).toLocaleDateString() : '...'}</td>
                                    <td style={tdStyle}>
                                        <div style={{ display: 'flex', gap: '8px' }}>
                                            {activeTab === 'audio' && (
                                                <button
                                                    onClick={() => toggleAudio(file)}
                                                    style={{
                                                        padding: '8px 12px',
                                                        backgroundColor: playingUrl === resolved.url && resolved.url ? COLORS.danger : COLORS.secondary,
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
                                                    {playingUrl === resolved.url && resolved.url ? '⏸️ Pause' : '▶️ Play'}
                                                </button>
                                            )}
                                            <button
                                                onClick={() => copyToClipboard(file)}
                                                style={{
                                                    padding: '8px 16px',
                                                    backgroundColor: copiedUrl === resolved.url && resolved.url ? COLORS.secondary : COLORS.primary,
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
                                                {copiedUrl === resolved.url && resolved.url ? '✅ Copied!' : 'Copy URL'}
                                            </button>
                                        </div>
                                    </td>
                                </tr>
                            );
                        })}
                    </TableLayout>
                    {pagedFiles.length === 0 && (
                        <div style={{ textAlign: 'center', padding: '50px', color: '#94a3b8' }}>
                            <p>No files found in this folder.</p>
                        </div>
                    )}
                    {filteredFiles.length > pagedFiles.length && (
                        <div style={{ textAlign: 'center', padding: '20px' }}>
                            <button
                                onClick={() => setPage(p => p + 1)}
                                style={{
                                    padding: '10px 30px',
                                    borderRadius: '10px',
                                    border: `1px solid ${COLORS.primary}`,
                                    backgroundColor: 'white',
                                    color: COLORS.primary,
                                    cursor: 'pointer',
                                    fontWeight: '600'
                                }}
                            >
                                Load More Assets ({filteredFiles.length - pagedFiles.length} remaining)
                            </button>
                        </div>
                    )}
                </div>
            )}
        </div>
    );
};

export default DatabaseManager;
