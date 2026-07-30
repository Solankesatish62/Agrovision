package com.agrovision.kiosk.util;

import android.graphics.Bitmap;
import android.graphics.Matrix;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.ImageProxy;

/**
 * ImageUtils
 *
 * PURPOSE:
 * - Convert CameraX ImageProxy → RGB Bitmap using the official stable API.
 *
 * HARD RULES:
 * - MUST NOT close ImageProxy
 */
public final class ImageUtils {

    private ImageUtils() {
        throw new AssertionError("No instances allowed");
    }

    /**
     * Convert ImageProxy to Bitmap using the stable official CameraX 1.4.0 API.
     * This method is much safer and handles YUV/RGBA/JPEG formats natively.
     */
    @Nullable
    public static Bitmap toBitmap(@NonNull ImageProxy image) {
        try {
            // 🚀 STABILITY FIX: Use official CameraX toBitmap() method
            // This is optimized and stable, preventing manual buffer crashes.
            Bitmap result = image.toBitmap();

            int rotation = image.getImageInfo().getRotationDegrees();
            if (rotation != 0) {
                PerformanceProfiler.start("Rotate Bitmap");
                result = rotate(result, rotation);
                PerformanceProfiler.end("Rotate Bitmap");
            }
            return result;

        } catch (Exception e) {
            LogUtils.e("ImageProxy -> Bitmap failed", e);
            return null;
        }
    }

    /* =========================================================
       INTERNAL HELPERS
       ========================================================= */

    private static Bitmap rotate(@NonNull Bitmap source, int degrees) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);

        Bitmap rotated = Bitmap.createBitmap(
                source,
                0,
                0,
                source.getWidth(),
                source.getHeight(),
                matrix,
                true
        );

        if (rotated != source) {
            BitmapUtils.safeRecycle(source);
        }

        return rotated;
    }
}
