package com.agrovision.kiosk.data.mapper;

import com.agrovision.kiosk.data.database.converter.StringListConverter;
import com.agrovision.kiosk.data.database.entity.MedicineEntity;
import com.agrovision.kiosk.data.model.Medicine;

import java.util.List;

/**
 * MedicineMapper
 *
 * Translates between:
 * - Domain model (Medicine)
 * - Database entity (MedicineEntity)
 *
 * WHY THIS EXISTS:
 * - Domain models must remain clean (no Room, no CSV logic)
 * - Database entities must remain SQLite-friendly
 *
 * RULES:
 * - Stateless
 * - Pure functions only
 * - No Android framework usage
 */
public final class MedicineMapper {

    // Prevent instantiation
    private MedicineMapper() {}

    /**
     * Converts a domain Medicine into a database MedicineEntity.
     *
     * Used when persisting catalog data into Room.
     */
    public static MedicineEntity toEntity(Medicine medicine) {
        if (medicine == null) return null;

        MedicineEntity entity = new MedicineEntity();

        entity.id = medicine.getId();
        entity.name = medicine.getName();
        entity.company = medicine.getCompany();

        // Convert List<String> → CSV String
        entity.supportedCrops =
                StringListConverter.fromList(medicine.getSupportedCrops());

        entity.supportedDiseases =
                StringListConverter.fromList(medicine.getSupportedDiseases());

        entity.usageInstructions = medicine.getUsageInstructions();
        entity.warnings = medicine.getWarnings();

        return entity;
    }

    /**
     * Converts a database MedicineEntity back into a domain Medicine.
     *
     * Used when reading from Room into business logic.
     */
    public static Medicine toDomain(MedicineEntity entity) {
        if (entity == null) return null;

        List<String> crops =
                StringListConverter.toList(entity.supportedCrops);

        List<String> diseases =
                StringListConverter.toList(entity.supportedDiseases);

        return new Medicine(
                entity.id,
                entity.name,
                entity.company,
                crops,
                diseases,
                entity.usageInstructions,
                entity.warnings
        );
    }
}
