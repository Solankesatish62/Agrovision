import React from 'react';
import AppUpdatesView from '../components/Updates/AppUpdatesView';

const UpdatesPage = ({ config, setConfig, onSave, isSaving }) => {
  return (
    <div>
      <AppUpdatesView config={config} setConfig={setConfig} onSave={onSave} isSaving={isSaving} />
    </div>
  );
};

export default UpdatesPage;
