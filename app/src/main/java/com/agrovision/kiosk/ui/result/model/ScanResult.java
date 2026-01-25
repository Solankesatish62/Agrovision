package com.agrovision.kiosk.ui.result.model;

import java.io.Serializable;
import java.util.List;

/**
 * ScanResult
 *
 * FINAL immutable output of the recognition pipeline
 * for a single medicine bottle.
 */
public final class ScanResult implements Serializable {

    public final ResultType resultType;

    // KNOWN only
    public final String medicineId;
    public final String displayName;
    public final List<Integer> imageResIds;
    public final List<ResultInfoItem> infoItems;

    // UNKNOWN / diagnostics
    public final String rawOcrText;
    public final boolean isConfidenceLow;

    private ScanResult(
            ResultType resultType,
            String medicineId,
            String displayName,
            List<Integer> imageResIds,
            List<ResultInfoItem> infoItems,
            String rawOcrText,
            boolean isConfidenceLow
    ) {
        this.resultType = resultType;
        this.medicineId = medicineId;
        this.displayName = displayName;
        this.imageResIds = imageResIds;
        this.infoItems = infoItems;
        this.rawOcrText = rawOcrText;
        this.isConfidenceLow = isConfidenceLow;
    }

    // ================= FACTORIES =================

    public static ScanResult known(
            String medicineId,
            String displayName,
            List<Integer> imageResIds,
            List<ResultInfoItem> infoItems
    ) {
        return new ScanResult(
                ResultType.KNOWN,
                medicineId,
                displayName,
                imageResIds,
                infoItems,
                null,
                false
        );
    }

    public static ScanResult unknown(
            String rawOcrText,
            boolean isConfidenceLow
    ) {
        return new ScanResult(
                ResultType.UNKNOWN,
                null,
                null,
                null,
                null,
                rawOcrText,
                isConfidenceLow
        );
    }

}

