package com.agrovision.kiosk.data.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Medicine
 *
 * Represents a SINGLE medicine entry in the system.
 */
public final class Medicine {

    private final String id;
    private final String name;
    private final String company;
    private final List<String> supportedCrops;
    private final List<String> supportedDiseases;
    private final String usageInstructions;
    private final String warnings;
    private final List<String> searchKeywords;
    private final List<String> imageUrls;
    private final List<String> audioUrls;
    private final long updatedAt;
    private final boolean remote;

    public Medicine(
            String id,
            String name,
            String company,
            List<String> supportedCrops,
            List<String> supportedDiseases,
            String usageInstructions,
            String warnings,
            List<String> searchKeywords,
            List<String> imageUrls,
            List<String> audioUrls,
            long updatedAt
    ) {
        this(id, name, company, supportedCrops, supportedDiseases, usageInstructions, warnings, searchKeywords, imageUrls, audioUrls, updatedAt, false);
    }

    public Medicine(
            String id,
            String name,
            String company,
            List<String> supportedCrops,
            List<String> supportedDiseases,
            String usageInstructions,
            String warnings,
            List<String> searchKeywords,
            List<String> imageUrls,
            List<String> audioUrls,
            long updatedAt,
            boolean remote
    ) {
        this.id = id;
        this.name = name;
        this.company = company;
        this.supportedCrops = supportedCrops == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(supportedCrops));
        this.supportedDiseases = supportedDiseases == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(supportedDiseases));
        this.searchKeywords = searchKeywords == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(searchKeywords));
        this.usageInstructions = usageInstructions;
        this.warnings = warnings;
        this.imageUrls = imageUrls == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(imageUrls));
        this.audioUrls = audioUrls == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(audioUrls));
        this.updatedAt = updatedAt;
        this.remote = remote;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getCompany() { return company; }
    public List<String> getSupportedCrops() { return supportedCrops; }
    public List<String> getSupportedDiseases() { return supportedDiseases; }
    public String getUsageInstructions() { return usageInstructions; }
    public String getWarnings() { return warnings; }
    public List<String> getSearchKeywords() { return searchKeywords; }
    public List<String> getImageUrls() { return imageUrls; }
    public List<String> getAudioUrls() { return audioUrls; }
    public long getUpdatedAt() { return updatedAt; }
    public boolean isRemote() { return remote; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Medicine)) return false;
        Medicine medicine = (Medicine) o;
        return Objects.equals(id, medicine.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Medicine{id='" + id + "', name='" + name + "'}";
    }
}
