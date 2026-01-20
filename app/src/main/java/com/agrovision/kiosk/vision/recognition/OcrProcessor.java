package com.agrovision.kiosk.vision.recognition;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OcrProcessor
 *
 * PURPOSE:
 * - Run OCR on a cropped bitmap
 * - Clean and normalize text deterministically
 *
 * THREADING CONTRACT:
 * - onResult() is ALWAYS delivered on the MAIN THREAD
 *
 * IMAGE CONTRACT:
 * - Input bitmap MUST already be correctly rotated/upright.
 * - This class does NOT handle rotation.
 */
public final class OcrProcessor {

    private final TextRecognizer recognizer;

    // Prevent concurrent OCR executions
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);

    // Ensures consistent callback threading
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onResult(@NonNull String normalizedText);
    }

    public OcrProcessor() {
        recognizer = TextRecognition.getClient(
                TextRecognizerOptions.DEFAULT_OPTIONS
        );
    }

    /**
     * Runs OCR → TextCleaner → TextNormalizer.
     *
     * @param bitmap   Cropped, upright bitmap
     * @param callback Result callback (MAIN THREAD)
     */
    public void process(@NonNull Bitmap bitmap,
                        @NonNull Callback callback) {

        // Reject overlapping OCR calls
        if (!isProcessing.compareAndSet(false, true)) {
            // 🔒 FIX: Always post rejection on MAIN thread
            mainHandler.post(() -> callback.onResult(""));
            return;
        }

        try {
            // Rotation is assumed to be handled upstream
            InputImage image = InputImage.fromBitmap(bitmap, 0);

            recognizer.process(image)
                    .addOnSuccessListener(result -> {
                        String rawText = extractText(result);
                        String cleaned = TextCleaner.clean(rawText);
                        String normalized = TextNormalizer.normalize(cleaned);

                        // ML Kit already runs this on MAIN thread,
                        // but we enforce consistency anyway
                        mainHandler.post(() -> callback.onResult(normalized));
                        isProcessing.set(false);
                    })
                    .addOnFailureListener(e -> {
                        mainHandler.post(() -> callback.onResult(""));
                        isProcessing.set(false);
                    });

        } catch (Exception e) {
            mainHandler.post(() -> callback.onResult(""));
            isProcessing.set(false);
        }
    }

    /**
     * Extract plain text from ML Kit result.
     */
    private String extractText(Text text) {
        return text.getText() == null ? "" : text.getText();
    }

    /**
     * Releases native OCR resources.
     *
     * MUST be called when vision pipeline shuts down.
     */
    public void close() {
        recognizer.close();
    }
}
