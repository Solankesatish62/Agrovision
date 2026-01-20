// Package declaration.
// Places this class in the data model layer.
// This layer must remain Android-free and pure Java.
package com.agrovision.kiosk.data.model;

// Imports for working with lists and immutability.
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Medicine
 *
 * Represents a SINGLE medicine entry in the system.
 *
 * IMPORTANT CONCEPT:
 * This is REFERENCE DATA, not transaction data.
 *
 * - Loaded from configuration (JSON / server)
 * - Does NOT change at runtime
 * - Safe to cache and reuse
 *
 * HARD RULES:
 * - No Android dependencies
 * - No Bitmap or camera-related data
 * - Immutable after creation
 */
public final class Medicine {

    // Unique identifier for the medicine.
    // This must be stable across app restarts and updates.
    // Used for equality, database references, and session tracking.
    private final String id;

    // Human-readable medicine name.
    // Displayed in UI.
    private final String name;

    // Manufacturer or brand name.
    // Useful for disambiguation and trust.
    private final String company;

    // List of crops this medicine can be applied to.
    // IMMUTABLE defensive copy stored internally.
    private final List<String> supportedCrops;

    // List of diseases this medicine treats.
    // IMMUTABLE defensive copy stored internally.
    private final List<String> supportedDiseases;

    // Instructions for how to use the medicine.
    // Pure text, no formatting assumptions.
    private final String usageInstructions;

    // Warnings or safety notes.
    // Example: dosage limits, environmental precautions.
    private final String warnings;

    /**
     * Constructor.
     *
     * Builds an immutable Medicine object.
     *
     * IMPORTANT:
     * - Defensive copies are made for all lists
     * - External references can never mutate internal state
     */
    public Medicine(
            String id,
            String name,
            String company,
            List<String> supportedCrops,
            List<String> supportedDiseases,
            String usageInstructions,
            String warnings
    ) {
        // Assign immutable scalar fields directly.
        this.id = id;
        this.name = name;
        this.company = company;

        /*
         * Defensive copy for supportedCrops.
         *
         * Why?
         * - Collections.unmodifiableList() alone is NOT enough
         * - The original list might still be modified by the caller
         *
         * This breaks aliasing and guarantees immutability.
         */
        this.supportedCrops = supportedCrops == null
                ? Collections.emptyList() // Safe empty immutable list
                : Collections.unmodifiableList(
                new ArrayList<>(supportedCrops) // Defensive copy
        );

        /*
         * Defensive copy for supportedDiseases.
         *
         * Same reasoning as above:
         * - No shared mutable references
         * - Thread-safe read access
         */
        this.supportedDiseases = supportedDiseases == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(
                new ArrayList<>(supportedDiseases)
        );

        // Assign textual fields.
        // These are immutable Strings, safe to store directly.
        this.usageInstructions = usageInstructions;
        this.warnings = warnings;
    }

    /* =========================================================
       Getters (READ-ONLY ACCESS)
       ========================================================= */

    // Returns the unique medicine ID.
    public String getId() {
        return id;
    }

    // Returns the display name of the medicine.
    public String getName() {
        return name;
    }

    // Returns the company / brand name.
    public String getCompany() {
        return company;
    }

    /*
     * Returns supported crops.
     *
     * IMPORTANT:
     * - This list is immutable
     * - Callers cannot modify internal state
     */
    public List<String> getSupportedCrops() {
        return supportedCrops;
    }

    /*
     * Returns supported diseases.
     *
     * Same immutability guarantee as supportedCrops.
     */
    public List<String> getSupportedDiseases() {
        return supportedDiseases;
    }

    // Returns usage instructions text.
    public String getUsageInstructions() {
        return usageInstructions;
    }

    // Returns warnings or safety notes.
    public String getWarnings() {
        return warnings;
    }

    /* =========================================================
       Equality & Debugging
       ========================================================= */

    /**
     * Equality is based ONLY on ID.
     *
     * Reason:
     * - ID represents the real-world product identity
     * - Other fields may change between versions
     * - Prevents duplicate medicine entries
     */
    @Override
    public boolean equals(Object o) {
        // Same reference → equal
        if (this == o) return true;

        // Different type → not equal
        if (!(o instanceof Medicine)) return false;

        // Compare IDs only
        Medicine medicine = (Medicine) o;
        return Objects.equals(id, medicine.id);
    }

    /**
     * Hashcode based ONLY on ID.
     *
     * Required for correct behavior in HashSet / HashMap.
     */
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /**
     * String representation.
     *
     * Intentionally short:
     * - Avoid dumping large lists into logs
     * - Useful for debugging and analytics traces
     */
    @Override
    public String toString() {
        return "Medicine{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", company='" + company + '\'' +
                '}';
    }
}
