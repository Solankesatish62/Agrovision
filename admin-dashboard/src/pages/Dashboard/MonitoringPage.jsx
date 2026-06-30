import React from 'react';
import MonitoringTable from '../components/Monitoring/MonitoringTable';

const MonitoringPage = ({ kiosks, scans, formatTimestamp }) => {
  return (
    <div>
      <MonitoringTable kiosks={kiosks} scans={scans} formatTimestamp={formatTimestamp} />
    </div>
  );
};

export default MonitoringPage;
