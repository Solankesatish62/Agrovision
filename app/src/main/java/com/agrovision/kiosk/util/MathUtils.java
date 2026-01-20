package com.agrovision.kiosk.util;

/**
 * Math utility helpers.
 *
 * PURPOSE:
 * - Prevent floating point comparison bugs
 * - Centralize threshold & confidence logic
 * - Make ML numeric behavior predictable
 *
 * RULES:
 * - No Android dependencies
 * - No magic numbers
 * - No direct float equality checks
 */
public final class MathUtils {

    private MathUtils() {
        throw new AssertionError("No instances allowed");
    }

    /* =========================================================
       FLOAT SAFETY
       ========================================================= */

    // General-purpose tolerance for float comparison
    public static final float EPSILON = 1e-6f;

    public static boolean nearlyEqual(float a, float b) {
        return Math.abs(a - b) < EPSILON;
    }

    public static boolean greaterThan(float value, float threshold) {
        return value > threshold && !nearlyEqual(value, threshold);
    }

    public static boolean greaterOrEqual(float value, float threshold) {
        return value > threshold || nearlyEqual(value, threshold);
    }

    public static boolean lessThan(float value, float threshold) {
        return value < threshold && !nearlyEqual(value, threshold);
    }

    public static boolean lessOrEqual(float value, float threshold) {
        return value < threshold || nearlyEqual(value, threshold);
    }

    /* =========================================================
       RANGE & CLAMPING
       ========================================================= */

    /**
     * Clamps a float value into a fixed range.
     */
    public static float clamp(float value, float min, float max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    /**
     * Checks if a value lies within a closed range.
     */
    public static boolean isInRange(float value, float min, float max) {
        return value >= min && value <= max;
    }

    /* =========================================================
       CONFIDENCE & PERCENTAGE
       ========================================================= */

    /**
     * Normalizes confidence values into [0,1].
     * Protects against ML glitches.
     */
    public static float normalizeConfidence(float confidence) {
        return clamp(confidence, 0.0f, 1.0f);
    }

    /**
     * Converts confidence (0–1) to percentage (0–100).
     */
    public static int confidenceToPercent(float confidence) {
        return Math.round(normalizeConfidence(confidence) * 100f);
    }

    /* =========================================================
       SMOOTHING (FOR STABILITY TRACKING)
       ========================================================= */

    /**
     * Exponential Moving Average.
     *
     * alpha ∈ (0,1)
     * Higher alpha = more weight to new value
     */
    public static float ema(float previous, float current, float alpha) {
        alpha = clamp(alpha, 0.0f, 1.0f);
        return alpha * current + (1.0f - alpha) * previous;
    }

    /* =========================================================
       SAFE DIVISION
       ========================================================= */

    /**
     * Prevents NaN / Infinity propagation.
     */
    public static float safeDivide(float numerator, float denominator) {
        if (Math.abs(denominator) < EPSILON) {
            return 0.0f;
        }
        return numerator / denominator;
    }
}
