package com.agrovision.kiosk.data.mapper;

import com.agrovision.kiosk.data.database.converter.StringListConverter;
import com.agrovision.kiosk.data.database.entity.MedicineEntity;
import com.agrovision.kiosk.data.model.Medicine;

import java.util.List;

/**
 * MedicineMapper
 *
 * Translates between Domain model (Medicine) and Database entity (MedicineEntity).
 */
public final class MedicineMapper {

    private MedicineMapper() {}

    public static MedicineEntity toEntity(Medicine medicine) {
        if (medicine == null) return null;

        MedicineEntity entity = new MedicineEntity();
        entity.id = medicine.getId();
        entity.name = medicine.getName();
        entity.company = medicine.getCompany();
        entity.cibNo = medicine.getCibNo();
        entity.chemicalName = medicine.getChemicalName();
        entity.supportedCrops = StringListConverter.fromList(medicine.getSupportedCrops());
        entity.supportedDiseases = StringListConverter.fromList(medicine.getSupportedDiseases());
        entity.imageUrls = StringListConverter.fromList(medicine.getImageUrls());
        entity.audioUrls = StringListConverter.fromList(medicine.getAudioUrls());
        entity.searchKeywords = StringListConverter.fromList(medicine.getSearchKeywords());
        entity.barcodePrefixes = StringListConverter.fromList(medicine.getBarcodePrefixes());
        entity.usageInstructions = medicine.getUsageInstructions();
        entity.warnings = medicine.getWarnings();
        entity.updatedAt = medicine.getUpdatedAt();
        entity.isRemote = medicine.isRemote();

        return entity;
    }

    public static Medicine toDomain(MedicineEntity entity) {
        if (entity == null) return null;

        List<String> crops = StringListConverter.toList(entity.supportedCrops);
        List<String> diseases = StringListConverter.toList(entity.supportedDiseases);
        List<String> images = StringListConverter.toList(entity.imageUrls);
        List<String> audios = StringListConverter.toList(entity.audioUrls);
        List<String> keywords = StringListConverter.toList(entity.searchKeywords);
        List<String> prefixes = StringListConverter.toList(entity.barcodePrefixes);

        return new Medicine(
                entity.id,
                entity.name,
                entity.company,
                entity.cibNo,
                entity.chemicalName,
                crops,
                diseases,
                entity.usageInstructions,
                entity.warnings,
                keywords,
                prefixes,
                images,
                audios,
                entity.updatedAt,
                entity.isRemote
        );
    }
}
