package com.agrovision.kiosk.vision.detection;

import android.graphics.RectF;

/**
 * DetectionResult
 *
 * Immutable value object representing a single detection
 * in a single frame.
 */
public final class DetectionResult {

    private final RectF boundingBox;     // FLOAT precision preserved
    private final float confidence;
    private final int classId;
    private final long timestampMs;

    public DetectionResult(RectF boundingBox,
                           float confidence,
                           int classId,
                           long timestampMs) {

        if (boundingBox == null) {
            throw new IllegalArgumentException("boundingBox cannot be null");
        }

        this.boundingBox = new RectF(boundingBox); // defensive copy
        this.confidence = confidence;
        this.classId = classId;
        this.timestampMs = timestampMs;
    }

    public RectF getBoundingBox() {
        return new RectF(boundingBox); // defensive copy
    }

    public float getConfidence() {
        return confidence;
    }

    public int getClassId() {
        return classId;
    }

    public long getTimestampMs() {
        return timestampMs;
    }
}
