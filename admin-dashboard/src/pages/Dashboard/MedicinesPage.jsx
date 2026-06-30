import React, { useState, useMemo } from 'react';
import MedicineTable from '../components/Medicines/MedicineTable';
import MedicineForm from '../components/Medicines/MedicineForm';
import Modal from '../components/Shared/Modal';

const MedicinesPage = ({ medicines, onSave, onDelete }) => {
  const [searchTerm, setSearchTerm] = useState('');
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [editingMedicine, setEditingMedicine] = useState(null);

  const filteredMedicines = useMemo(() => {
    const term = searchTerm.toLowerCase().trim();
    const sortedAll = [...medicines].sort((a, b) => {
      const aVal = (a.name || a.medicineName || "").toLowerCase();
      const bVal = (b.name || b.medicineName || "").toLowerCase();
      return aVal.localeCompare(bVal);
    });

    if (!term) return sortedAll;

    const matches = medicines.filter(m => {
      const searchData = [
        m.name, m.medicineName, m.id, m.company, m.marathiInfo, m.crop, m.supportedCrops, m.disease, m.supportedDiseases,
        Array.isArray(m.searchKeywords) ? m.searchKeywords.join(' ') : m.searchKeywords,
        Array.isArray(m.ocrKeywords) ? m.ocrKeywords.join(' ') : m.ocrKeywords,
        Array.isArray(m.barcodePrefixes) ? m.barcodePrefixes.join(' ') : m.barcodePrefixes
      ].map(field => Array.isArray(field) ? field.join(' ') : (field || "")).join(" ").toLowerCase();

      return searchData.includes(term);
    });

    return matches.sort((a, b) => {
      const aName = (a.name || a.medicineName || "").toLowerCase();
      const bName = (b.name || b.medicineName || "").toLowerCase();
      if (aName === term) return -1;
      if (bName === term) return 1;
      if (aName.startsWith(term) && !bName.startsWith(term)) return -1;
      if (!aName.startsWith(term) && bName.startsWith(term)) return 1;
      return aName.localeCompare(bName);
    });
  }, [medicines, searchTerm]);

  const openAddForm = () => {
    setEditingMedicine(null);
    setIsFormOpen(true);
  };

  const openEditForm = (medicine) => {
    setEditingMedicine(medicine);
    setIsFormOpen(true);
  };

  return (
    <>
      <MedicineTable
        medicines={filteredMedicines}
        searchTerm={searchTerm}
        setSearchTerm={setSearchTerm}
        onAdd={openAddForm}
        onEdit={openEditForm}
        onDelete={onDelete}
      />
      <Modal isOpen={isFormOpen} onClose={() => setIsFormOpen(false)}>
        <MedicineForm
          medicine={editingMedicine}
          onSave={(data) => {
            onSave(data, editingMedicine?.id);
            setIsFormOpen(false);
          }}
          onCancel={() => setIsFormOpen(false)}
        />
      </Modal>
    </>
  );
};

export default MedicinesPage;
