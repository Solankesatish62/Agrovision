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
 * - Convert CameraX ImageProxy → upright RGB Bitmap
 *
 * HARD RULES:
 * - MUST handle YUV_420_888
 * - MUST respect rotationDegrees
 * - MUST NOT close ImageProxy
 */
public final class ImageUtils {

    private ImageUtils() {
        throw new AssertionError("No instances allowed");
    }

    /**
     * Convert ImageProxy to upright Bitmap.
     *
     * @return Bitmap or null if conversion fails
     */
    @Nullable
    public static Bitmap toBitmap(@NonNull ImageProxy image) {

        if (image.getFormat() != ImageFormat.YUV_420_888) {
            LogUtils.e("Unsupported image format: " + image.getFormat());
            return null;
        }

        try {
            // 1️⃣ YUV → NV21 byte array
            byte[] nv21 = yuv420ToNv21(image);

            // 2️⃣ NV21 → JPEG
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

            // 3️⃣ JPEG → Bitmap
            Bitmap bitmap = android.graphics.BitmapFactory
                    .decodeByteArray(jpegBytes, 0, jpegBytes.length);

            if (bitmap == null) return null;

            // 4️⃣ Apply rotation
            int rotation = image.getImageInfo().getRotationDegrees();
            if (rotation != 0) {
                bitmap = rotate(bitmap, rotation);
            }

            return bitmap;

        } catch (Exception e) {
            LogUtils.e("ImageProxy → Bitmap failed", e);
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
