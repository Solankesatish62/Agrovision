import React, { useState, useEffect, useMemo, useCallback } from 'react';
import { db } from '../../firebase';
import { collection, query, where, getDocs, orderBy, onSnapshot } from 'firebase/firestore';
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
  BarChart, Bar, Cell, AreaChart, Area, PieChart, Pie
} from 'recharts';
import { COLORS, SHADOWS } from '../../components/Shared/Styles';
import Card from '../../components/Shared/Card';

// 🚀 Helper to extract nested medicines from flat dot-notation keys (for legacy data)
const getMedicines = (data) => {
    if (!data) return {};
    if (data.medicines && Object.keys(data.medicines).length > 0) return data.medicines;

    // Fallback: check if medicines are stored as flat keys like "medicines.Name.count"
    const meds = {};
    Object.entries(data).forEach(([key, value]) => {
        if (key && typeof key === 'string' && key.startsWith('medicines.')) {
            const parts = key.split('.');
            if (parts.length >= 3) {
                const medId = parts[1];
                const field = parts[2];
                if (!meds[medId]) meds[medId] = {};
                meds[medId][field] = value;
            }
        }
    });
    return meds;
};

const PIE_COLORS = ['#1e3a8a', '#3b82f6', '#10b981', '#f59e0b', '#8b5cf6', '#ec4899', '#06b6d4', '#14b8a6', '#f43f5e', '#64748b'];

const AnalyticsPage = () => {
  const [timeRange, setTimeRange] = useState('7d'); // 'today', '7d', '30d', '1y'
  const [data, setData] = useState([]);
  const [topMedicines, setTopMedicines] = useState([]);
  const [topShops, setTopShops] = useState([]);
  const [allShops, setAllShops] = useState([]);
  const [selectedShopId, setSelectedShopId] = useState('all');
  const [loading, setLoading] = useState(true);

  // 🚀 Helper to get India Local Date String
  const getIndiaDate = (date) => {
    const indiaTime = new Date(date.getTime() + (5.5 * 60 * 60 * 1000));
    return indiaTime.toISOString().split('T')[0];
  };

  // 🚀 Fetch all shops for the filter
  useEffect(() => {
    const unsubscribe = onSnapshot(collection(db, 'shops'), (snapshot) => {
      const shops = snapshot.docs.map(doc => ({ id: doc.id, name: doc.data().shopName }));
      setAllShops(shops.sort((a, b) => (a.name || "").localeCompare(b.name || "")));
    });
    return () => unsubscribe();
  }, []);

  const processData = useCallback((rawData, shopId, range) => {
    // 1. Determine date range for zero-filling
    const now = new Date();
    // ... (rest of the date logic is fine)
    const indiaNow = new Date(now.getTime() + (5.5 * 60 * 60 * 1000));
    const dates = [];
    let numDays = 1;

    if (range === '7d') numDays = 7;
    else if (range === '30d') numDays = 30;
    else if (range === '1y') numDays = 12;

    if (range !== '1y') {
        for (let i = numDays - 1; i >= 0; i--) {
            const d = new Date(indiaNow);
            d.setDate(d.getDate() - i);
            dates.push(d.toISOString().split('T')[0]);
        }
    } else {
        // Months for 1y
        for (let i = 11; i >= 0; i--) {
            const d = new Date(indiaNow);
            d.setMonth(d.getMonth() - i);
            dates.push(d.toISOString().substring(0, 7));
        }
    }

    // 2. Map raw data to the fixed date range
    const dataMap = {};
    rawData.forEach(d => {
        dataMap[d.date] = d;
    });

    const filteredData = dates.map(dateStr => {
      const dayData = dataMap[dateStr] || { date: dateStr, scans: 0, medicines: {}, shops: {} };
      let scans = 0;
      let medicines = {};

      if (shopId === 'all') {
        scans = dayData.scans || 0;
        medicines = getMedicines(dayData);
      } else {
        const shopInfo = (dayData.shops && dayData.shops[shopId]) || null;
        if (shopInfo) {
          scans = shopInfo.count || 0;
          medicines = shopInfo.medicines || getMedicines(shopInfo);
        } else if (dayData.shopId === shopId) {
            // Fallback for direct daily_scans query results
            scans = dayData.scans || 0;
            medicines = getMedicines(dayData);
        }
      }

      return {
        date: dateStr,
        displayDate: range === '1y' ?
            new Date(dateStr + "-02").toLocaleDateString('en-US', { month: 'short', year: '2-digit' }) :
            new Date(dateStr).toLocaleDateString('en-US', { month: 'short', day: 'numeric' }),
        scans: scans,
        medicines: medicines,
        shops: dayData.shops || {}
      };
    });

    setData(filteredData);

    // 3. Aggregate for Top Lists
    const medMap = {};
    const shopMap = {};

    filteredData.forEach(day => {
      Object.entries(day.medicines || {}).forEach(([id, info]) => {
        if (!info) return;
        const name = info.name || id;
        const count = Number(info.count) || 0;
        if (!medMap[id]) medMap[id] = { name: name, count: 0 };
        medMap[id].count += count;
      });

      Object.entries(day.shops || {}).forEach(([id, info]) => {
        if (!shopMap[id]) shopMap[id] = { name: info.name, count: 0 };
        shopMap[id].count += info.count;
      });
    });

    const sortedMeds = Object.values(medMap)
      .sort((a, b) => b.count - a.count);

    const sortedShops = Object.values(shopMap)
      .sort((a, b) => b.count - a.count)
      .slice(0, 10);

    setTopMedicines(sortedMeds);
    setTopShops(sortedShops);
  }, []);

  useEffect(() => {
    setLoading(true);
    const now = new Date();
    // India Offset +5:30
    const indiaNow = new Date(now.getTime() + (5.5 * 60 * 60 * 1000));
    let startDate = new Date(indiaNow);

    if (timeRange === 'today') {
      startDate.setHours(0, 0, 0, 0);
    } else if (timeRange === '7d') {
      startDate.setDate(indiaNow.getDate() - 7);
    } else if (timeRange === '30d') {
      startDate.setDate(indiaNow.getDate() - 30);
    } else if (timeRange === '1y') {
      startDate.setFullYear(indiaNow.getFullYear() - 1);
    }

    const startDateStr = startDate.toISOString().split('T')[0];
    let q;

    if (timeRange === '1y') {
      q = query(
        collection(db, 'monthly_stats'),
        where('month', '>=', startDateStr.substring(0, 7)),
        orderBy('month', 'asc')
      );
    } else {
      q = selectedShopId !== 'all' ?
          query(collection(db, 'daily_scans'), where('shopId', '==', selectedShopId), where('date', '>=', startDateStr), orderBy('date', 'asc')) :
          query(collection(db, 'daily_scans'), where('date', '>=', startDateStr), orderBy('date', 'asc'));
    }

    const unsubscribe = onSnapshot(q, (snapshot) => {
      let processedData = [];
      if (timeRange === '1y') {
        processedData = snapshot.docs.map(doc => {
          const d = doc.data();
          return {
            date: d.month,
            scans: d.totalScans || 0,
            medicines: d.medicines || {},
            shops: d.shops || {},
            ...d
          };
        });
      } else {
        const rawDailyData = snapshot.docs.map(doc => {
          const d = doc.data();
          return {
            id: doc.id,
            ...d,
            date: d.date,
            scans: d.scanCount || 0,
            medicines: d.medicines || {},
            shopId: d.shopId
          };
        });

        if (selectedShopId === 'all') {
          const aggregated = {};
          rawDailyData.forEach(d => {
            if (!aggregated[d.date]) {
              aggregated[d.date] = { date: d.date, scans: 0, medicines: {}, shops: {} };
            }
            aggregated[d.date].scans += d.scans;
            aggregated[d.date].shops[d.shopId] = {
              count: d.scans,
              name: d.shopName,
              medicines: d.medicines,
              ...d
            };

            const meds = getMedicines(d);
            Object.entries(meds).forEach(([id, info]) => {
              if (!aggregated[d.date].medicines[id]) aggregated[d.date].medicines[id] = { name: info.name, count: 0 };
              aggregated[d.date].medicines[id].count += (info.count || 0);
            });
          });
          processedData = Object.values(aggregated).sort((a, b) => a.date.localeCompare(b.date));
        } else {
          processedData = rawDailyData;
        }
      }
      processData(processedData, selectedShopId, timeRange);
      setLoading(false);
    }, (error) => {
      console.error("Error fetching analytics:", error);
      setLoading(false);
    });

    return () => unsubscribe();
  }, [timeRange, selectedShopId, processData]);

  const stats = useMemo(() => {
    const totalScans = data.reduce((sum, d) => sum + d.scans, 0);
    const topMed = topMedicines[0]?.name || 'N/A';
    const topShop = topShops[0]?.name || 'N/A';

    return {
      totalScans,
      topMedicine: topMed,
      topShop: topShop
    };
  }, [data, topMedicines, topShops]);

  const marketShareData = useMemo(() => {
    const total = topMedicines.reduce((sum, m) => sum + m.count, 0);
    if (total === 0) return [];

    const LIMIT = 7;
    if (topMedicines.length <= LIMIT + 1) return topMedicines;

    const topN = topMedicines.slice(0, LIMIT);
    const othersCount = topMedicines.slice(LIMIT).reduce((sum, m) => sum + m.count, 0);

    return [
      ...topN,
      { name: 'Other Medicines', count: othersCount }
    ];
  }, [topMedicines]);

  const totalScansForMarketShare = useMemo(() =>
    topMedicines.reduce((sum, m) => sum + m.count, 0)
  , [topMedicines]);

  const CustomTooltip = ({ active, payload, label }) => {
    if (active && payload && payload.length) {
      return (
        <div style={{
          backgroundColor: 'white',
          padding: '12px',
          border: `1px solid ${COLORS.border}`,
          borderRadius: '12px',
          boxShadow: SHADOWS.lg
        }}>
          <p style={{ margin: '0 0 6px 0', fontWeight: '800', color: COLORS.textMain, fontSize: '14px' }}>{payload[0].payload.displayDate}</p>
          <div style={{ height: '1px', backgroundColor: COLORS.border, marginBottom: '8px' }} />
          <p style={{ margin: 0, color: COLORS.primary, fontSize: '13px', fontWeight: '600' }}>
            Scans: <span style={{ fontSize: '16px', fontWeight: '800' }}>{payload[0].value}</span>
          </p>
        </div>
      );
    }
    return null;
  };

  const PieTooltip = ({ active, payload }) => {
    if (active && payload && payload.length) {
      const data = payload[0].payload;
      const percent = totalScansForMarketShare > 0 ? ((data.count / totalScansForMarketShare) * 100).toFixed(1) : 0;
      return (
        <div style={{
          backgroundColor: 'white',
          padding: '12px',
          border: `1px solid ${COLORS.border}`,
          borderRadius: '12px',
          boxShadow: SHADOWS.lg,
          zIndex: 1000
        }}>
          <p style={{ margin: '0 0 4px 0', fontWeight: '800', color: COLORS.textMain, fontSize: '14px' }}>{data.name}</p>
          <div style={{ height: '1px', backgroundColor: COLORS.border, marginBottom: '8px' }} />
          <p style={{ margin: 0, color: COLORS.primary, fontSize: '13px', fontWeight: '600' }}>
            Scans: <span style={{ fontWeight: '800' }}>{data.count.toLocaleString()}</span>
          </p>
          <p style={{ margin: '4px 0 0 0', color: COLORS.textMuted, fontSize: '12px', fontWeight: '600' }}>
            Market Share: <span style={{ color: COLORS.textMain, fontWeight: '800' }}>{percent}%</span>
          </p>
        </div>
      );
    }
    return null;
  };

  return (
    <div style={{ padding: '0 8px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '32px', flexWrap: 'wrap', gap: '16px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
            <h3 style={{ margin: 0, fontSize: '20px', fontWeight: '700', color: COLORS.textMain }}>Performance Insights</h3>
            <select
                value={selectedShopId}
                onChange={(e) => setSelectedShopId(e.target.value)}
                style={{
                    padding: '10px 20px',
                    borderRadius: '14px',
                    border: `2px solid ${COLORS.border}`,
                    backgroundColor: 'white',
                    fontSize: '14px',
                    fontWeight: '700',
                    color: COLORS.primary,
                    outline: 'none',
                    cursor: 'pointer',
                    minWidth: '240px',
                    boxShadow: SHADOWS.sm,
                    transition: 'all 0.2s'
                }}
            >
                <option value="all">🌐 All Retail Partners</option>
                {allShops.map(shop => (
                    <option key={shop.id} value={shop.id}>🏪 {shop.name}</option>
                ))}
            </select>
        </div>

        <div style={{ display: 'flex', gap: '8px', backgroundColor: '#f1f5f9', padding: '6px', borderRadius: '16px' }}>
          {[
            { id: 'today', label: 'Today' },
            { id: '7d', label: '7 Days' },
            { id: '30d', label: '30 Days' },
            { id: '1y', label: '1 Year' }
          ].map(range => (
            <button
              key={range.id}
              onClick={() => setTimeRange(range.id)}
              style={{
                padding: '8px 20px',
                borderRadius: '12px',
                border: 'none',
                cursor: 'pointer',
                fontSize: '13px',
                fontWeight: '700',
                transition: 'all 0.2s',
                backgroundColor: timeRange === range.id ? 'white' : 'transparent',
                color: timeRange === range.id ? COLORS.primary : COLORS.textMuted,
                boxShadow: timeRange === range.id ? SHADOWS.md : 'none'
              }}
            >
              {range.label}
            </button>
          ))}
        </div>
      </div>

      <div style={{ display: 'flex', gap: '24px', marginBottom: '40px' }}>
        <Card
          title="Total Scans"
          value={stats.totalScans.toLocaleString()}
          color={COLORS.primary}
          icon="fa-qrcode"
        />
        <Card
          title="Top Performing Medicine"
          value={stats.topMedicine}
          color={COLORS.accent}
          icon="fa-pills"
        />
        {selectedShopId === 'all' && (
            <Card
                title="Top Growth Partner"
                value={stats.topShop}
                color={COLORS.secondary}
                icon="fa-store"
            />
        )}
      </div>

      {/* Main Charts */}
      <div style={{ display: 'grid', gridTemplateColumns: '1.5fr 1.2fr', gap: '32px', marginBottom: '32px' }}>
        {/* Scan Volume Trend */}
        <div style={{
          backgroundColor: 'white',
          padding: '32px',
          borderRadius: '24px',
          border: `1px solid ${COLORS.border}`,
          boxShadow: SHADOWS.md
        }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '32px' }}>
            <h4 style={{ margin: 0, fontSize: '18px', fontWeight: '800', color: COLORS.textMain }}>
                Scan Engagement Over Time
            </h4>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '12px', color: COLORS.textMuted, fontWeight: '600' }}>
                <div style={{ width: '12px', height: '12px', borderRadius: '4px', backgroundColor: COLORS.primaryLight, opacity: 0.3 }} />
                Daily Activity
            </div>
          </div>

          <div style={{ width: '100%', height: '350px' }}>
            {loading ? (
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%', color: COLORS.textMuted }}>
                    <i className="fas fa-circle-notch fa-spin" style={{ marginRight: '10px' }}></i> Loading insights...
                </div>
            ) : (
                <ResponsiveContainer>
                <AreaChart data={data}>
                    <defs>
                        <linearGradient id="colorScans" x1="0" y1="0" x2="0" y2="1">
                            <stop offset="5%" stopColor={COLORS.primaryLight} stopOpacity={0.3}/>
                            <stop offset="95%" stopColor={COLORS.primaryLight} stopOpacity={0}/>
                        </linearGradient>
                    </defs>
                    <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#f1f5f9" />
                    <XAxis
                        dataKey="displayDate"
                        axisLine={false}
                        tickLine={false}
                        tick={{ fontSize: 11, fill: COLORS.textMuted, fontWeight: '600' }}
                        dy={15}
                    />
                    <YAxis
                        axisLine={false}
                        tickLine={false}
                        tick={{ fontSize: 11, fill: COLORS.textMuted, fontWeight: '600' }}
                    />
                    <Tooltip content={<CustomTooltip />} />
                    <Area
                        type="monotone"
                        dataKey="scans"
                        stroke={COLORS.primary}
                        strokeWidth={4}
                        fillOpacity={1}
                        fill="url(#colorScans)"
                        activeDot={{ r: 8, strokeWidth: 0, fill: COLORS.primary }}
                    />
                </AreaChart>
                </ResponsiveContainer>
            )}
          </div>
        </div>

        {/* Market Share (Donut Chart) */}
        <div style={{
          backgroundColor: 'white',
          padding: '32px',
          borderRadius: '24px',
          border: `1px solid ${COLORS.border}`,
          boxShadow: SHADOWS.md,
          display: 'flex',
          flexDirection: 'column'
        }}>
          <h4 style={{ margin: '0 0 32px 0', fontSize: '18px', fontWeight: '800', color: COLORS.textMain }}>Market Share</h4>
          <div style={{ display: 'flex', flex: 1, alignItems: 'center', minHeight: '300px' }}>
            {marketShareData.length > 0 ? (
                <>
                    <div style={{ width: '55%', height: '300px' }}>
                        <ResponsiveContainer>
                            <PieChart>
                                <Pie
                                    data={marketShareData}
                                    cx="50%"
                                    cy="50%"
                                    innerRadius={70}
                                    outerRadius={105}
                                    paddingAngle={4}
                                    dataKey="count"
                                    animationBegin={0}
                                    animationDuration={1200}
                                >
                                    {marketShareData.map((entry, index) => (
                                        <Cell key={`cell-${index}`} fill={PIE_COLORS[index % PIE_COLORS.length]} stroke="none" />
                                    ))}
                                </Pie>
                                <Tooltip content={<PieTooltip />} />
                            </PieChart>
                        </ResponsiveContainer>
                    </div>
                    <div style={{ width: '45%', display: 'flex', flexDirection: 'column', gap: '10px', paddingLeft: '20px' }}>
                        {marketShareData.map((med, index) => {
                            const percent = totalScansForMarketShare > 0 ? ((med.count / totalScansForMarketShare) * 100).toFixed(1) : 0;
                            return (
                                <div key={index} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px', overflow: 'hidden' }}>
                                        <div style={{ width: '8px', height: '8px', borderRadius: '50%', backgroundColor: PIE_COLORS[index % PIE_COLORS.length], flexShrink: 0 }} />
                                        <span style={{ fontSize: '12px', fontWeight: '700', color: COLORS.textMain, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                                            {med.name}
                                        </span>
                                    </div>
                                    <span style={{ fontSize: '11px', fontWeight: '800', color: COLORS.textMuted, marginLeft: '8px' }}>{percent}%</span>
                                </div>
                            );
                        })}
                    </div>
                </>
            ) : (
                <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', color: COLORS.textMuted, fontSize: '14px', fontWeight: '600' }}>
                    No data available for this selection
                </div>
            )}
          </div>
        </div>
      </div>

      {/* Detailed Medicine Breakdown Table */}
      <div style={{
        backgroundColor: 'white',
        padding: '32px',
        borderRadius: '24px',
        border: `1px solid ${COLORS.border}`,
        boxShadow: SHADOWS.md
      }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
            <h4 style={{ margin: 0, fontSize: '18px', fontWeight: '800', color: COLORS.textMain }}>
                {selectedShopId === 'all' ? 'Global Medicine Performance' : `Medicine Breakdown: ${allShops.find(s => s.id === selectedShopId)?.name}`}
            </h4>
            {topMedicines.length > 0 && (
                <div style={{ fontSize: '13px', fontWeight: '700', color: COLORS.primary, backgroundColor: '#e0e7ff', padding: '6px 16px', borderRadius: '20px' }}>
                    {topMedicines.length} Unique Medicines Found
                </div>
            )}
        </div>

        <table style={{ width: '100%', borderCollapse: 'separate', borderSpacing: '0 8px' }}>
          <thead>
            <tr>
              <th style={{ textAlign: 'left', padding: '12px 20px', fontSize: '12px', color: COLORS.textMuted, textTransform: 'uppercase', letterSpacing: '1px' }}>SERIAL</th>
              <th style={{ textAlign: 'left', padding: '12px 20px', fontSize: '12px', color: COLORS.textMuted, textTransform: 'uppercase', letterSpacing: '1px' }}>MEDICINE NAME</th>
              <th style={{ textAlign: 'right', padding: '12px 20px', fontSize: '12px', color: COLORS.textMuted, textTransform: 'uppercase', letterSpacing: '1px' }}>SCAN COUNT</th>
              <th style={{ textAlign: 'right', padding: '12px 20px', fontSize: '12px', color: COLORS.textMuted, textTransform: 'uppercase', letterSpacing: '1px' }}>DOMINANCE</th>
            </tr>
          </thead>
          <tbody>
            {topMedicines.length > 0 ? topMedicines.map((med, index) => {
              const maxCount = topMedicines[0].count;
              const percent = (med.count / maxCount) * 100;
              return (
                <tr key={index} className="table-row-hover" style={{ backgroundColor: '#fff' }}>
                  <td style={{ padding: '16px 20px', fontSize: '14px', fontWeight: '800', color: COLORS.textMuted, borderRadius: '12px 0 0 12px', border: `1px solid ${COLORS.border}`, borderRight: 'none' }}>
                    {String(index + 1).padStart(2, '0')}
                  </td>
                  <td style={{ padding: '16px 20px', fontSize: '15px', fontWeight: '700', color: COLORS.textMain, borderTop: `1px solid ${COLORS.border}`, borderBottom: `1px solid ${COLORS.border}` }}>
                    {med.name}
                  </td>
                  <td style={{ padding: '16px 20px', fontSize: '16px', textAlign: 'right', fontWeight: '800', color: COLORS.primary, borderTop: `1px solid ${COLORS.border}`, borderBottom: `1px solid ${COLORS.border}` }}>
                    {med.count.toLocaleString()}
                  </td>
                  <td style={{ padding: '16px 20px', textAlign: 'right', borderRadius: '0 12px 12px 0', border: `1px solid ${COLORS.border}`, borderLeft: 'none' }}>
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: '16px' }}>
                      <div style={{ width: '120px', height: '10px', backgroundColor: '#f1f5f9', borderRadius: '5px', overflow: 'hidden' }}>
                        <div style={{ width: `${percent}%`, height: '100%', background: `linear-gradient(90deg, ${COLORS.primaryLight}, ${COLORS.primary})`, borderRadius: '5px' }} />
                      </div>
                      <span style={{ fontSize: '13px', color: COLORS.textMuted, fontWeight: '700', minWidth: '40px' }}>{Math.round(percent)}%</span>
                    </div>
                  </td>
                </tr>
              );
            }) : (
                <tr>
                    <td colSpan="4" style={{ padding: '60px', textAlign: 'center', color: COLORS.textMuted, borderRadius: '24px', border: `2px dashed ${COLORS.border}` }}>
                        <i className="fas fa-database" style={{ fontSize: '32px', marginBottom: '16px', display: 'block', opacity: 0.3 }}></i>
                        <p style={{ margin: 0, fontWeight: '700' }}>No detailed medicine data available for this selection.</p>
                        <p style={{ margin: '8px 0 0 0', fontSize: '13px' }}>Ensure kiosks are running the latest version and have completed scans.</p>
                    </td>
                </tr>
            )}
          </tbody>
        </table>
      </div>

      {/* Retail Partner Ranking (Only in 'all' view) */}
      {selectedShopId === 'all' && (
        <div style={{
            marginTop: '32px',
            backgroundColor: 'white',
            padding: '32px',
            borderRadius: '24px',
            border: `1px solid ${COLORS.border}`,
            boxShadow: SHADOWS.md
        }}>
            <h4 style={{ margin: '0 0 24px 0', fontSize: '18px', fontWeight: '800', color: COLORS.textMain }}>Retail Partner Network Hierarchy</h4>
            <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
                <tr>
                <th style={{ textAlign: 'left', padding: '12px 16px', fontSize: '12px', color: COLORS.textMuted, textTransform: 'uppercase' }}>RANK</th>
                <th style={{ textAlign: 'left', padding: '12px 16px', fontSize: '12px', color: COLORS.textMuted, textTransform: 'uppercase' }}>PARTNER NAME</th>
                <th style={{ textAlign: 'right', padding: '12px 16px', fontSize: '12px', color: COLORS.textMuted, textTransform: 'uppercase' }}>TOTAL SCANS</th>
                <th style={{ textAlign: 'right', padding: '12px 16px', fontSize: '12px', color: COLORS.textMuted, textTransform: 'uppercase' }}>MARKET SHARE</th>
                </tr>
            </thead>
            <tbody>
                {topShops.map((shop, index) => {
                const totalAllShops = topShops.reduce((sum, s) => sum + s.count, 0);
                const share = ((shop.count / totalAllShops) * 100).toFixed(1);
                return (
                    <tr key={index} style={{ borderBottom: index === topShops.length - 1 ? 'none' : `1px solid ${COLORS.border}` }}>
                    <td style={{ padding: '20px 16px', fontSize: '14px', fontWeight: '800', color: COLORS.textMuted }}>#{index + 1}</td>
                    <td style={{ padding: '20px 16px', fontSize: '15px', fontWeight: '700', color: COLORS.textMain }}>{shop.name}</td>
                    <td style={{ padding: '20px 16px', fontSize: '16px', textAlign: 'right', fontWeight: '800', color: COLORS.primary }}>{shop.count.toLocaleString()}</td>
                    <td style={{ padding: '20px 16px', textAlign: 'right' }}>
                        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: '12px' }}>
                        <div style={{ width: '80px', height: '8px', backgroundColor: '#f1f5f9', borderRadius: '4px', overflow: 'hidden' }}>
                            <div style={{ width: `${share}%`, height: '100%', backgroundColor: COLORS.secondary }} />
                        </div>
                        <span style={{ fontSize: '13px', color: COLORS.textMuted, fontWeight: '700', minWidth: '40px' }}>{share}%</span>
                        </div>
                    </td>
                    </tr>
                );
                })}
            </tbody>
            </table>
        </div>
      )}

      <style>{`
        .table-row-hover:hover td {
            background-color: #f8fafc !important;
            border-color: ${COLORS.primaryLight}40 !important;
        }
      `}</style>
    </div>
  );
};

export default AnalyticsPage;
