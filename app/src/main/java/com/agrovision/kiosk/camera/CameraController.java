package com.agrovision.kiosk.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.view.Display;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.agrovision.kiosk.threading.DetectionExecutor;
import com.agrovision.kiosk.util.BitmapUtils;
import com.agrovision.kiosk.util.ImageUtils;
import com.agrovision.kiosk.util.LogUtils;
import com.agrovision.kiosk.util.RectUtils;
import com.agrovision.kiosk.vision.detection.*;
import com.agrovision.kiosk.vision.recognition.OcrProcessor;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * CameraController
 *
 * SINGLE OWNER of CameraX + vision pipeline.
 */
public final class CameraController {

    private static volatile CameraController instance;

    private final Context appContext;
    private final Executor cameraExecutor;

    private ProcessCameraProvider cameraProvider;
    private Camera camera;

    private ImageAnalysis imageAnalysis;
    private ImageCapture imageCapture;
    private Preview preview;

    // 🔥 Vision pipeline
    private final YoloDetector yoloDetector;
    private final BoxStabilityTracker stabilityTracker = new BoxStabilityTracker();
    private final OcrProcessor ocrProcessor;
    private ScanResultCallback scanResultCallback;

    private final FrameAnalyzer frameAnalyzer;

    public static CameraController getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (CameraController.class) {
                if (instance == null) {
                    instance = new CameraController(
                            context.getApplicationContext(),
                            DetectionExecutor.get()
                    );
                }
            }
        }
        return instance;
    }

    private CameraController(@NonNull Context context,
                             @NonNull Executor executor) {

        this.appContext = context;
        this.cameraExecutor = executor;

        // ⚠️ TEMP YOLO MODEL STUB (replace later)
        this.yoloDetector = new YoloDetector(
                new YoloModel() {
                    @Override
                    public List<RawDetection> runInference(Bitmap bitmap) {
                        return List.of(); // stub
                    }
                }
        );

        // 🔤 OCR (initialized ONCE)
        this.ocrProcessor = new OcrProcessor(appContext);

        this.frameAnalyzer = new FrameAnalyzer(
                new LuminosityAnalyzer(),
                this::handleFrame,
                () -> LogUtils.w("Low light detected at counter")
        );
    }

    public void setScanResultCallback(ScanResultCallback callback) {
        this.scanResultCallback = callback;
    }

    /* =========================================================
       FRAME PIPELINE
       ========================================================= */

    private void handleFrame(@NonNull ImageProxy image) {

        // 1️⃣ ImageProxy → Bitmap
        Bitmap bitmap = ImageUtils.toBitmap(image);
        if (bitmap == null) return;

        // 2️⃣ YOLO detection
        List<DetectionResult> detections =
                yoloDetector.detect(bitmap);

        if (detections.isEmpty()) return;

        for (DetectionResult detection : detections) {

            boolean justStable =
                    stabilityTracker.update(detection);

            if (!justStable) continue;

            RectF stableBox = stabilityTracker.getStableBox();
            if (stableBox == null) return;

            // 3️⃣ Crop stable region
            Bitmap cropped = BitmapUtils.safeCrop(
                    bitmap,
                    RectUtils.toRect(
                            stableBox,
                            bitmap.getWidth(),
                            bitmap.getHeight()
                    )
            );

            if (cropped != null) {

                LogUtils.i("✅ Stable bottle detected → OCR");

                // 4️⃣ OCR (ASYNC, CALLBACK-BASED — CORRECT)
                ocrProcessor.process(cropped, normalizedText -> {

                    if (normalizedText == null || normalizedText.isEmpty()) {
                        LogUtils.w("OCR empty");
                        return;
                    }

                    if (scanResultCallback != null) {
                        scanResultCallback.onScanCompleted(
                                List.of(normalizedText)
                        );
                    }
                });


                BitmapUtils.safeRecycle(cropped);
            }

            stabilityTracker.reset();
            break; // ONE bottle at a time
        }
    }

    /* =========================================================
       CAMERA START
       ========================================================= */

    public void startCamera(@NonNull LifecycleOwner owner,
                            @NonNull PreviewView previewView) {

        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(appContext);

        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                bindUseCases(owner, previewView);
            } catch (Exception e) {
                LogUtils.e("Camera start failed", e);
            }
        }, ContextCompat.getMainExecutor(appContext));
    }

    private void bindUseCases(@NonNull LifecycleOwner owner,
                              @NonNull PreviewView previewView) {

        cameraProvider.unbindAll();

        Display display = previewView.getDisplay();
        int rotation = display != null
                ? display.getRotation()
                : Surface.ROTATION_0;

        CameraSelector selector = new CameraSelector.Builder()
                .requireLensFacing(CameraConfig.LENS_FACING)
                .build();

        imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(CameraConfig.ANALYSIS_RESOLUTION)
                .setBackpressureStrategy(CameraConfig.BACKPRESSURE_STRATEGY)
                .setOutputImageFormat(CameraConfig.IMAGE_FORMAT)
                .setTargetRotation(rotation)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, frameAnalyzer);

        preview = new Preview.Builder()
                .setTargetRotation(rotation)
                .build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        imageCapture = new ImageCapture.Builder()
                .setTargetRotation(rotation)
                .setCaptureMode(CameraConfig.CAPTURE_MODE)
                .build();

        camera = cameraProvider.bindToLifecycle(
                owner,
                selector,
                preview,
                imageAnalysis,
                imageCapture
        );

        camera.getCameraControl()
                .enableTorch(CameraConfig.TORCH_ENABLED_BY_DEFAULT);
    }

    /* =========================================================
       CAMERA STOP
       ========================================================= */

    public void stopCamera() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        camera = null;
    }
}
