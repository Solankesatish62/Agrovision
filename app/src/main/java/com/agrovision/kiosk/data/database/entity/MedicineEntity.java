package com.agrovision.kiosk.data.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * MedicineEntity
 *
 * DATABASE representation of Medicine.
 *
 * IMPORTANT:
 * - This is a persistence model, NOT a domain model.
 * - It mirrors Medicine fields but in a SQLite-friendly way.
 *
 * RULES:
 * - NO Bitmaps
 * - NO OCR text
 * - NO session/runtime data
 * - Strings only (Lists handled via TypeConverters)
 */
@Entity(tableName = "medicines")
public class MedicineEntity {

    /**
     * Unique medicine ID.
     *
     * This MUST match Medicine.id.
     * Acts as the primary key in SQLite.
     */
    @PrimaryKey
    @NonNull
    public String id;

    /**
     * Display name of the medicine.
     */
    public String name;

    /**
     * Company / brand name.
     */
    public String company;

    /**
     * Supported crops.
     *
     * Stored as CSV string via StringListConverter.
     * Example: "Cotton,Rice,Wheat"
     */
    public String supportedCrops;

    /**
     * Supported diseases.
     *
     * Stored as CSV string via StringListConverter.
     */
    public String supportedDiseases;

    /**
     * Usage instructions (plain text).
     */
    public String usageInstructions;

    /**
     * Warnings / safety notes.
     */
    public String warnings;
}
