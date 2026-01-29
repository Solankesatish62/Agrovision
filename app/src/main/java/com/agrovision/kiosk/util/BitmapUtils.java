package com.agrovision.kiosk.util;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;

import androidx.annotation.Nullable;
import androidx.camera.core.ImageProxy;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public final class BitmapUtils {

    private BitmapUtils() {
        throw new AssertionError("No instances allowed");
    }

    /* =========================================================
       IMAGE PROXY → BITMAP  (CRITICAL)
       ========================================================= */

    /**
     * Converts CameraX ImageProxy (YUV_420_888) to Bitmap.
     *
     * RULES:
     * - DOES NOT close ImageProxy
     * - Caller owns Bitmap lifecycle
     */
    @Nullable
    public static Bitmap imageProxyToBitmap(ImageProxy image) {

        if (image == null || image.getFormat() != ImageFormat.YUV_420_888) {
            return null;
        }

        try {
            ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
            ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
            ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            byte[] nv21 = new byte[ySize + uSize + vSize];

            // Y
            yBuffer.get(nv21, 0, ySize);
            // VU (NV21 format)
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);

            YuvImage yuvImage = new YuvImage(
                    nv21,
                    ImageFormat.NV21,
                    image.getWidth(),
                    image.getHeight(),
                    null
            );

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(
                    new Rect(0, 0, image.getWidth(), image.getHeight()),
                    90,
                    out
            );

            byte[] jpegBytes = out.toByteArray();

            return android.graphics.BitmapFactory
                    .decodeByteArray(jpegBytes, 0, jpegBytes.length);

        } catch (Exception e) {
            LogUtils.e("ImageProxy → Bitmap failed", e);
            return null;
        }
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
