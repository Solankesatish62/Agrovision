package com.agrovision.kiosk.camera;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageProxy;

import android.graphics.ImageFormat;

import java.nio.ByteBuffer;

/**
 * LuminosityAnalyzer
 *
 * PURPOSE:
 * - Determine whether ambient lighting is sufficient
 *   for reliable vision processing.
 *
 * DESIGN:
 * - Non-destructive buffer access
 * - No allocations
 * - Deterministic
 */
public final class LuminosityAnalyzer {

    private static final int DARKNESS_THRESHOLD = 45;

    /**
     * Returns true if the frame is too dark.
     *
     * IMPORTANT:
     * - Does NOT modify buffer position
     * - Safe for downstream consumers (YOLO, OCR)
     */
    public boolean isTooDark(@NonNull ImageProxy image) {

        if (image.getFormat() != ImageFormat.YUV_420_888) {
            return false; // Fail-safe
        }

        ImageProxy.PlaneProxy yPlane = image.getPlanes()[0];
        if (yPlane == null) {
            return false;
        }

        ByteBuffer buffer = yPlane.getBuffer();
        int pixelCount = buffer.remaining();

        if (pixelCount <= 0) {
            return false;
        }

        long sum = 0;

        // ✅ Absolute indexing — does NOT move buffer position
        for (int i = 0; i < pixelCount; i++) {
            sum += (buffer.get(i) & 0xFF);
        }

        int averageLuminance = (int) (sum / pixelCount);
        return averageLuminance < DARKNESS_THRESHOLD;
    }
}
