package com.agrovision.kiosk.data.holder;

import android.graphics.Bitmap;

import com.agrovision.kiosk.data.model.Medicine;

/**
 * CurrentScanHolder
 *
 * TEMPORARY, IN-MEMORY holder for the CURRENT scan only.
 *
 * WHY THIS EXISTS:
 * - Prevent TransactionTooLargeException
 * - Avoid passing Bitmaps via Intent/Bundle
 * - Safely share scan results across Activities
 *
 * LIFECYCLE:
 * - Populated during scan
 * - Read by Result UI
 * - CLEARED aggressively on:
 *      - new scan
 *      - session end
 *      - idle
 *      - fatal error
 *
 * IMPORTANT:
 * - This is NOT a cache
 * - This is NOT persistent storage
 * - This object MUST be cleared
 */
public final class CurrentScanHolder {

    /* =========================================================
       SINGLETON
       ========================================================= */

    // Single in-process instance
    private static final CurrentScanHolder INSTANCE = new CurrentScanHolder();

    /**
     * Returns the singleton instance.
     */
    public static CurrentScanHolder getInstance() {
        return INSTANCE;
    }

    /* =========================================================
       SCAN DATA (TEMPORARY)
       ========================================================= */

    // Bitmap captured or cropped during scan
    // NEVER pass this via Intent
    private Bitmap scanBitmap;

    // Matched reference medicine (immutable, safe)
    private Medicine matchedMedicine;

    // Confidence score from recognition (0.0 - 1.0)
    private float matchConfidence;

    // Raw OCR text (optional, debug only)
    private String rawOcrText;

    // Private constructor to enforce singleton
    private CurrentScanHolder() {}

    /* =========================================================
       SETTERS (WRITE ACCESS)
       ========================================================= */

    /**
     * Sets the scan bitmap.
     *
     * Caller responsibility:
     * - Ensure bitmap belongs to CURRENT scan
     */
    public synchronized void setScanBitmap(Bitmap bitmap) {
        this.scanBitmap = bitmap;
    }

    /**
     * Sets the matched medicine.
     */
    public synchronized void setMatchedMedicine(Medicine medicine) {
        this.matchedMedicine = medicine;
    }

    /**
     * Sets recognition confidence.
     */
    public synchronized void setMatchConfidence(float confidence) {
        this.matchConfidence = confidence;
    }

    /**
     * Sets raw OCR text (debug only).
     */
    public synchronized void setRawOcrText(String text) {
        this.rawOcrText = text;
    }

    /* =========================================================
       GETTERS (READ ACCESS)
       ========================================================= */

    /**
     * Returns the scan bitmap.
     *
     * NOTE:
     * - May return null if cleared
     * - UI must handle null safely
     */
    public synchronized Bitmap getScanBitmap() {
        return scanBitmap;
    }

    /**
     * Returns the matched medicine.
     */
    public synchronized Medicine getMatchedMedicine() {
        return matchedMedicine;
    }

    /**
     * Returns match confidence.
     */
    public synchronized float getMatchConfidence() {
        return matchConfidence;
    }

    /**
     * Returns raw OCR text.
     */
    public synchronized String getRawOcrText() {
        return rawOcrText;
    }

    /* =========================================================
       MEMORY CONTROL (CRITICAL)
       ========================================================= */

    /**
     * Clears ALL scan data.
     *
     * ⚠️ VERY IMPORTANT:
     * - DO NOT call Bitmap.recycle()
     * - Let GC handle bitmap memory safely
     *
     * MUST be called when:
     * - A new scan starts
     * - Session ends
     * - Idle state is entered
     * - Fatal error occurs
     */
    public synchronized void clear() {

        // Drop reference; GC will free native memory safely
        scanBitmap = null;

        matchedMedicine = null;
        matchConfidence = 0f;
        rawOcrText = null;
    }
}
