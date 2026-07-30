package com.agrovision.kiosk.vision.detection;

import android.graphics.RectF;

import androidx.annotation.Nullable;

/**
 * BoxStabilityTracker
 *
 * PURPOSE:
 * - Track temporal stability of YOLO detections
 * - Decide when a detection is stable enough to trust
 *
 * KEY GUARANTEE:
 * - Stability is evaluated against an anchor box
 * - Cropping always uses the most recent box
 */
public final class BoxStabilityTracker {

    /* ---------------- CONFIG (OPTIMIZED) ---------------- */

    // 🚀 Optimization 1: Set to near-instant for faster kiosk response
    private static final long DEFAULT_STABLE_DURATION_MS = 100;
    private static final long INSTANT_STABLE_DURATION_MS = 0; // Trigger immediately if very high confidence
    private static final float INSTANT_CONFIDENCE_THRESHOLD = 0.92f;
    
    private static final float MIN_IOU_THRESHOLD = 0.35f; // 🚀 Reduced from 0.5 to allow handheld jitter
    private static final float MIN_CONFIDENCE = 0.3f;

    /* ---------------- INTERNAL STATE ---------------- */

    // Anchor for stability comparison
    private RectF anchorBox;

    // Latest observed box (used for cropping)
    private RectF latestBox;

    private long stableStartTimeMs = 0;
    private boolean stable = false;
    private int frameCount = 0;

    /* ---------------- PUBLIC API ---------------- */

    public void reset() {
        anchorBox = null;
        latestBox = null;
        stableStartTimeMs = 0;
        stable = false;
        frameCount = 0;
    }

    public int getFrameCount() {
        return frameCount;
    }

    public long getStableStartTimeMs() {
        return stableStartTimeMs;
    }

    /**
     * Feed a new detection into the tracker.
     *
     * @return true if detection JUST became stable
     */
    public boolean update(DetectionResult detection) {
        frameCount++;

        if (detection == null) {
            reset();
            return false;
        }

        if (detection.getConfidence() < MIN_CONFIDENCE) {
            reset();
            return false;
        }

        RectF currentBox = detection.getBoundingBox();
        long timestampMs = detection.getTimestampMs();

        // Always update latest box
        latestBox = currentBox;

        // First valid detection
        if (anchorBox == null) {
            anchorBox = currentBox;
            stableStartTimeMs = timestampMs;
            stable = false;
            return false;
        }

        float iou = calculateIoU(anchorBox, currentBox);

        if (iou < MIN_IOU_THRESHOLD) {
            // Movement too large → reset anchor
            anchorBox = currentBox;
            stableStartTimeMs = timestampMs;
            stable = false;
            return false;
        }

        // Stable overlap → check time
        long requiredDuration = (detection.getConfidence() >= INSTANT_CONFIDENCE_THRESHOLD) 
                ? INSTANT_STABLE_DURATION_MS : DEFAULT_STABLE_DURATION_MS;

        if (!stable && (timestampMs - stableStartTimeMs) >= requiredDuration) {
            stable = true;
            return true;
        }

        return false;
    }

    public boolean isStable() {
        return stable;
    }

    /**
     * Returns the most recent stable bounding box
     * (safe for cropping).
     */
    @Nullable
    public RectF getStableBox() {
        return stable && latestBox != null ? new RectF(latestBox) : null;
    }

    /* ---------------- MATH ---------------- */

    private static float calculateIoU(RectF a, RectF b) {

        float intersectionLeft   = Math.max(a.left, b.left);
        float intersectionTop    = Math.max(a.top, b.top);
        float intersectionRight  = Math.min(a.right, b.right);
        float intersectionBottom = Math.min(a.bottom, b.bottom);

        float intersectionWidth  = intersectionRight - intersectionLeft;
        float intersectionHeight = intersectionBottom - intersectionTop;

        if (intersectionWidth <= 0f || intersectionHeight <= 0f) {
            return 0f;
        }

        float intersectionArea = intersectionWidth * intersectionHeight;

        float areaA = a.width() * a.height();
        float areaB = b.width() * b.height();

        float union = areaA + areaB - intersectionArea;

        // 🔒 Guard against division by zero / NaN
        if (union <= 0f) {
            return 0f;
        }

        return intersectionArea / union;
    }
}
