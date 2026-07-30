package com.agrovision.kiosk.vision.recognition;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.agrovision.kiosk.util.LogUtils;
import com.agrovision.kiosk.util.PerformanceProfiler;
import com.agrovision.kiosk.threading.RecognitionExecutor;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.concurrent.atomic.AtomicBoolean;

public final class OcrProcessor {

    private final TextRecognizer recognizer;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onResult(@NonNull String normalizedText);
    }

    public OcrProcessor(Context appContext) {
        recognizer = TextRecognition.getClient(
                TextRecognizerOptions.DEFAULT_OPTIONS
        );
    }

    /**
     * 🚀 Optimization 4: Public check to see if OCR is busy.
     * Removed for Phase 1 (Parallel OCR) to allow multiple simultaneous boxes.
     */
    public boolean isBusy() {
        return false;
    }

    public void process(@NonNull Bitmap bitmap,
                        @NonNull Callback callback) {

        PerformanceProfiler.log("OCR", "OCR Engine Started");
        try {
            InputImage image = InputImage.fromBitmap(bitmap, 0);

            recognizer.process(image)
                    .addOnSuccessListener(RecognitionExecutor.get(), result -> {
                        PerformanceProfiler.checkpoint("OCR", "OCR Callback Received");
                        PerformanceProfiler.log("OCR", "Recognized Characters: " + (result != null ? result.getText().length() : 0));
                        String rawText = extractText(result);
                        
                        PerformanceProfiler.start("OCR Cleaning");
                        PerformanceProfiler.start("Text Normalization");
                        String cleaned = TextCleaner.clean(rawText);
                        String normalized = TextNormalizer.normalize(cleaned);
                        PerformanceProfiler.end("Text Normalization");
                        PerformanceProfiler.end("OCR Cleaning");

                        PerformanceProfiler.end("OCR");
                        callback.onResult(normalized);
                    })
                    .addOnFailureListener(RecognitionExecutor.get(), e -> {
                        LogUtils.e("OCR Process failed", e);
                        PerformanceProfiler.end("OCR");
                        callback.onResult("");
                    });

        } catch (Exception e) {
            LogUtils.e("OCR Exception", e);
            PerformanceProfiler.end("OCR");
            mainHandler.post(() -> callback.onResult(""));
        }
    }

    private String extractText(Text text) {
        return text == null ? "" : text.getText();
    }

    public void close() {
        recognizer.close();
    }
}
