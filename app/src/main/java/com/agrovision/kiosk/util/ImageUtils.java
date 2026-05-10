package com.agrovision.kiosk.util;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.ImageProxy;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

/**
 * ImageUtils
 *
 * PURPOSE:
 * - Convert CameraX ImageProxy → RGB Bitmap
 *
 * HARD RULES:
 * - MUST handle YUV_420_888
 * - MUST NOT close ImageProxy
 */
public final class ImageUtils {

    private ImageUtils() {
        throw new AssertionError("No instances allowed");
    }

    /**
     * Convert ImageProxy to Bitmap using a faster direct conversion.
     * Compatible with OUTPUT_IMAGE_FORMAT_RGBA_8888.
     */
    @Nullable
    public static Bitmap toBitmap(@NonNull ImageProxy image) {
        try {
            if (image.getFormat() == android.graphics.ImageFormat.YUV_420_888) {
                // Fallback for YUV if needed
                byte[] nv21 = yuv420ToNv21(image);
                YuvImage yuvImage = new YuvImage(nv21, android.graphics.ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                yuvImage.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 90, out);
                byte[] jpegBytes = out.toByteArray();
                return android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);
            }

            // 🚀 ULTRA FAST PATH: RGBA_8888 directly to Bitmap
            // This avoids JPEG compression and is much smoother for the preview.
            Bitmap bitmap = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(image.getPlanes()[0].getBuffer());
            return bitmap;

        } catch (Exception e) {
            LogUtils.e("ImageProxy -> Bitmap failed", e);
            return null;
        }
    }

    /* =========================================================
       INTERNAL HELPERS
       ========================================================= */

    private static byte[] yuv420ToNv21(ImageProxy image) {

        ImageProxy.PlaneProxy yPlane = image.getPlanes()[0];
        ImageProxy.PlaneProxy uPlane = image.getPlanes()[1];
        ImageProxy.PlaneProxy vPlane = image.getPlanes()[2];

        ByteBuffer yBuffer = yPlane.getBuffer();
        ByteBuffer uBuffer = uPlane.getBuffer();
        ByteBuffer vBuffer = vPlane.getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];

        // Y
        yBuffer.get(nv21, 0, ySize);

        // VU (NV21 format)
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        return nv21;
    }

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
