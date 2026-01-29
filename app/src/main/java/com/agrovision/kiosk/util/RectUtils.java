package com.agrovision.kiosk.util;

import android.graphics.Rect;
import android.graphics.RectF;

import androidx.annotation.Nullable;

/**
 * RectUtils
 *
 * PURPOSE:
 * - Safely convert RectF (float) → Rect (int)
 * - Clamp values to valid bitmap bounds
 *
 * WHY THIS EXISTS:
 * - YOLO boxes are float-based
 * - Bitmap cropping requires int Rect
 * - Prevents negative / OOB crashes
 */
public final class RectUtils {

    private RectUtils() {
        throw new AssertionError("No instances allowed");
    }

    /**
     * Convert RectF → Rect safely.
     *
     * @param rectF       YOLO bounding box
     * @param bitmapW    Bitmap width
     * @param bitmapH    Bitmap height
     * @return Safe Rect or null if invalid
     */
    @Nullable
    public static Rect toRect(
            @Nullable RectF rectF,
            int bitmapW,
            int bitmapH
    ) {
        if (rectF == null || bitmapW <= 0 || bitmapH <= 0) {
            return null;
        }

        int left   = Math.max(0, Math.round(rectF.left));
        int top    = Math.max(0, Math.round(rectF.top));
        int right  = Math.min(bitmapW, Math.round(rectF.right));
        int bottom = Math.min(bitmapH, Math.round(rectF.bottom));

        if (right <= left || bottom <= top) {
            return null;
        }

        return new Rect(left, top, right, bottom);
    }
}
