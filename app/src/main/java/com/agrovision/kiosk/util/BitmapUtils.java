package com.agrovision.kiosk.util;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;

public final class BitmapUtils {

    private BitmapUtils() {
        throw new AssertionError("No instances allowed");
    }

    /* =========================================================
       VALIDATION
       ========================================================= */

    public static boolean isValid(Bitmap bitmap) {
        return bitmap != null && !bitmap.isRecycled();
    }

    /* =========================================================
       SAFE COPY
       ========================================================= */

    public static Bitmap safeCopy(Bitmap source) {
        if (!isValid(source)) return null;

        try {
            return source.copy(source.getConfig(), true);
        } catch (OutOfMemoryError e) {
            LogUtils.e("Bitmap copy failed (OOM)", e);
            return null;
        }
    }

    /* =========================================================
       SAFE CROP
       ========================================================= */

    public static Bitmap safeCrop(Bitmap source, Rect cropRect) {
        if (!isValid(source) || cropRect == null) return null;

        Rect safeRect = new Rect(
                Math.max(0, cropRect.left),
                Math.max(0, cropRect.top),
                Math.min(source.getWidth(), cropRect.right),
                Math.min(source.getHeight(), cropRect.bottom)
        );

        if (safeRect.width() <= 0 || safeRect.height() <= 0) {
            return null;
        }

        // If crop covers full bitmap, force copy to guarantee ownership
        if (safeRect.left == 0
                && safeRect.top == 0
                && safeRect.width() == source.getWidth()
                && safeRect.height() == source.getHeight()) {
            return safeCopy(source);
        }

        try {
            return Bitmap.createBitmap(
                    source,
                    safeRect.left,
                    safeRect.top,
                    safeRect.width(),
                    safeRect.height()
            );
        } catch (IllegalArgumentException | OutOfMemoryError e) {
            LogUtils.e("Bitmap crop failed", e);
            return null;
        }
    }

    /* =========================================================
       SAFE SCALE
       ========================================================= */

    public static Bitmap scaleToWidth(Bitmap source, int targetWidth) {
        if (!isValid(source) || targetWidth <= 0) return null;

        float scale = targetWidth / (float) source.getWidth();
        return scale(source, scale, scale);
    }

    public static Bitmap scale(Bitmap source, float scaleX, float scaleY) {
        if (!isValid(source)) return null;

        Matrix matrix = new Matrix();
        matrix.setScale(scaleX, scaleY);

        try {
            return Bitmap.createBitmap(
                    source,
                    0,
                    0,
                    source.getWidth(),
                    source.getHeight(),
                    matrix,
                    true
            );
        } catch (OutOfMemoryError e) {
            LogUtils.e("Bitmap scale failed (OOM)", e);
            return null;
        }
    }

    /* =========================================================
       SAFE RECYCLE
       ========================================================= */

    public static void safeRecycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }
}
