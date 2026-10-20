package com.agrovision.kiosk.camera;

import java.util.Locale;

/**
 * ScanMetrics
 *
 * Holder for per-phase analytical timings and final total duration of a scan attempt.
 */
public final class ScanMetrics {

    public final String scanAttemptId;
    public final long scanStartMs;

    public long conversionMs;
    public long roiCalcMs;
    public long cropMs;
    public long qualityCheckMs;
    public long ocrMs;
    public long normMs;
    public long matchingMs;
    public long totalMs;

    public boolean success = false;
    public String matchedMedicineName;

    public ScanMetrics(String scanAttemptId, long scanStartMs) {
        this.scanAttemptId = scanAttemptId;
        this.scanStartMs = scanStartMs;
    }

    public double getTotalSeconds() {
        return totalMs / 1000.0;
    }

    public String formatLog() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n========================================================================\n");
        sb.append(String.format(Locale.US, "📊 SCAN ANALYTICS REPORT (Attempt: %s)\n", scanAttemptId));
        sb.append("------------------------------------------------------------------------\n");
        sb.append(String.format(Locale.US, "  • Phase 1: Frame Acquisition & Bitmap Conversion : %d ms\n", conversionMs));
        sb.append(String.format(Locale.US, "  • Phase 2: Center ROI Coordinate Calculation     : %d ms\n", roiCalcMs));
        sb.append(String.format(Locale.US, "  • Phase 3: ROI Bitmap Cropping                  : %d ms\n", cropMs));
        sb.append(String.format(Locale.US, "  • Phase 4: Image Quality & Luminance Check      : %d ms\n", qualityCheckMs));
        sb.append(String.format(Locale.US, "  • Phase 5: ML Kit OCR Processing                 : %d ms\n", ocrMs));
        sb.append(String.format(Locale.US, "  • Phase 6: Text Cleaning & Normalization        : %d ms\n", normMs));
        sb.append(String.format(Locale.US, "  • Phase 7: Medicine Matching (Catalog Search)   : %d ms\n", matchingMs));
        sb.append("------------------------------------------------------------------------\n");
        if (success) {
            sb.append(String.format(Locale.US, "✅ SUCCESSFUL SCAN MATCH: \"%s\"\n", matchedMedicineName != null ? matchedMedicineName : "Known Medicine"));
            sb.append(String.format(Locale.US, "⏱️ FINAL SUCCESSFUL SCAN TIME: %.3f seconds (%d ms)\n", getTotalSeconds(), totalMs));
        } else {
            sb.append("⚠️ SCAN RESULT: No matching medicine candidate found\n");
            sb.append(String.format(Locale.US, "⏱️ SCAN DURATION: %.3f seconds (%d ms)\n", getTotalSeconds(), totalMs));
        }
        sb.append("========================================================================");
        return sb.toString();
    }
}
