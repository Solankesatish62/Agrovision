package com.agrovision.kiosk.vision.mapping;

import androidx.annotation.Nullable;

import com.agrovision.kiosk.data.model.Medicine;

/**
 * MatchResult
 *
 * Immutable value object representing the outcome
 * of matching OCR text against the medicine catalog.
 *
 * This class is a PURE RESULT:
 * - No logic
 * - No mutation
 * - No side effects
 */
public final class MatchResult {

    /* =========================================================
       MATCH TYPE
       ========================================================= */

    public enum MatchType {
        EXACT,      // Perfect confident match
        FUZZY,      // Matched but confidence < 1.0
        NONE        // No match
    }

    /* =========================================================
       STATE (IMMUTABLE)
       ========================================================= */

    @Nullable
    private final Medicine medicine;

    private final float confidence;

    private final String matchedText;

    private final MatchType matchType;

    private MatchResult(
            @Nullable Medicine medicine,
            float confidence,
            String matchedText,
            MatchType matchType
    ) {
        this.medicine = medicine;
        this.confidence = confidence;
        this.matchedText = matchedText == null ? "" : matchedText;
        this.matchType = matchType;
    }

    /* =========================================================
       FACTORY METHODS (ONLY WAY TO CREATE)
       ========================================================= */

    public static MatchResult exact(
            Medicine medicine,
            String matchedText
    ) {
        if (medicine == null) {
            throw new IllegalArgumentException(
                    "Exact match requires non-null Medicine"
            );
        }

        return new MatchResult(
                medicine,
                1.0f,
                matchedText,
                MatchType.EXACT
        );
    }

    public static MatchResult fuzzy(
            Medicine medicine,
            float confidence,
            String matchedText
    ) {
        if (medicine == null) {
            throw new IllegalArgumentException(
                    "Fuzzy match requires non-null Medicine"
            );
        }

        if (confidence <= 0f || confidence >= 1f) {
            throw new IllegalArgumentException(
                    "Fuzzy confidence must be between 0 and 1"
            );
        }

        return new MatchResult(
                medicine,
                confidence,
                matchedText,
                MatchType.FUZZY
        );
    }

    public static MatchResult none(String matchedText) {
        return new MatchResult(
                null,
                0.0f,
                matchedText,
                MatchType.NONE
        );
    }

    /* =========================================================
       SEMANTIC ACCESSORS
       ========================================================= */

    /**
     * @return true if any medicine matched (EXACT or FUZZY)
     */
    public boolean isMatched() {
        return matchType != MatchType.NONE;
    }

    /**
     * @return true if match exists but confidence is risky
     * Used by pipeline to mark ScanResult as low confidence.
     */
    public boolean isLowConfidence() {
        return matchType == MatchType.FUZZY && confidence < 0.75f;
    }

    /* =========================================================
       RAW ACCESSORS (READ-ONLY)
       ========================================================= */

    @Nullable
    public Medicine getMedicine() {
        return medicine;
    }

    public float getConfidence() {
        return confidence;
    }

    public String getMatchedText() {
        return matchedText;
    }

    public MatchType getMatchType() {
        return matchType;
    }
}
