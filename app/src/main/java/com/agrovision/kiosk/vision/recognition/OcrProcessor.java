package com.agrovision.kiosk.vision.recognition;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.agrovision.kiosk.util.LogUtils;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.concurrent.atomic.AtomicBoolean;

public final class OcrProcessor {

    private final TextRecognizer recognizer;
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onResult(@NonNull String normalizedText);
    }

    public OcrProcessor(Context appContext) {
        recognizer = TextRecognition.getClient(
                TextRecognizerOptions.DEFAULT_OPTIONS
        );
    }

    public void process(@NonNull Bitmap bitmap,
                        @NonNull Callback callback) {

        if (!isProcessing.getAndSet(true)) {
            try {
                // 🔍 DIAGNOSTIC: Log bitmap size to verify if it's too small or rotated
                LogUtils.d("OCR Input Bitmap Size: " + bitmap.getWidth() + "x" + bitmap.getHeight());

                InputImage image = InputImage.fromBitmap(bitmap, 0);

                recognizer.process(image)
                        .addOnSuccessListener(result -> {
                            String rawText = extractText(result);
                            
                            // 🔍 DEBUG: See what ML Kit actually found before cleaning
                            if (rawText.isEmpty()) {
                                LogUtils.w("ML Kit returned NO text for this crop.");
                            } else {
                                LogUtils.i("ML Kit Raw OCR: [" + rawText.replace("\n", " ") + "]");
                            }

                            String cleaned = TextCleaner.clean(rawText);
                            String normalized = TextNormalizer.normalize(cleaned);

                            mainHandler.post(() -> callback.onResult(normalized));
                            isProcessing.set(false);
                        })
                        .addOnFailureListener(e -> {
                            LogUtils.e("OCR Process failed", e);
                            mainHandler.post(() -> callback.onResult(""));
                            isProcessing.set(false);
                        });

            } catch (Exception e) {
                LogUtils.e("OCR Exception", e);
                mainHandler.post(() -> callback.onResult(""));
                isProcessing.set(false);
            }
        } else {
             // Already processing, skip this frame
        }
    }

    private String extractText(Text text) {
        return text == null ? "" : text.getText();
    }

    public void close() {
        recognizer.close();
    }
}
