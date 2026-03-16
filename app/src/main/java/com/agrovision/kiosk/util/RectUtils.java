package com.agrovision.kiosk.util;

import android.graphics.Rect;
import android.graphics.RectF;

/**
 * RectUtils
 *
 * PURPOSE:
 * - Convert between RectF (ML) and Rect (Android Graphics/Bitmaps)
 * - Handle coordinate scaling and padding
 */
public final class RectUtils {

    private RectUtils() {}

    /**
     * Converts normalized/scaled RectF to integer Rect for Bitmap cropping.
     * Enforces a minimum size of 32x32 for ML Kit compatibility.
     */
    public static Rect toRect(RectF rectF, int maxWidth, int maxHeight) {
        
        // Add 10% padding to ensure text isn't cut off at the edges
        float paddingW = rectF.width() * 0.10f;
        float paddingH = rectF.height() * 0.10f;

        int left   = (int) Math.max(0, rectF.left - paddingW);
        int top    = (int) Math.max(0, rectF.top - paddingH);
        int right  = (int) Math.min(maxWidth, rectF.right + paddingW);
        int bottom = (int) Math.min(maxHeight, rectF.bottom + paddingH);

        // 🛡️ Ensure minimum width/height of 32 pixels for ML Kit
        if ((right - left) < 32) {
            right = Math.min(maxWidth, left + 32);
        }
        if ((bottom - top) < 32) {
            bottom = Math.min(maxHeight, top + 32);
        }

        return new Rect(left, top, right, bottom);
    }
}
