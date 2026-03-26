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
import com.agrovision.kiosk.vision.recognition.ScanDebouncer;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * CameraController
 *
 * SINGLE OWNER of CameraX + vision pipeline.
 * Synchronizes rotation with display for upright preview and processing.
 * Optimized for 1-2 second end-to-end latency.
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

    // Vision pipeline modules
    private final YoloDetector yoloDetector;
    private final BoxStabilityTracker stabilityTracker = new BoxStabilityTracker();
    private final OcrProcessor ocrProcessor;
    private final ScanDebouncer scanDebouncer = new ScanDebouncer();
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

        this.yoloDetector = new YoloDetector(
                new TfliteYoloModel(appContext)
        );

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
        // 🚀 Optimization 4: Skip YOLO/Stability if OCR is already busy
        if (ocrProcessor.isBusy()) {
            return;
        }

        long pipelineStart = System.currentTimeMillis();

        Bitmap bitmap = ImageUtils.toBitmap(image);
        if (bitmap == null) return;

        // ⏱️ Log: Detection Time
        long detStart = System.currentTimeMillis();
        List<DetectionResult> detections = yoloDetector.detect(bitmap);
        long detEnd = System.currentTimeMillis();

        if (detections.isEmpty()) {
            BitmapUtils.safeRecycle(bitmap);
            return;
        }

        for (DetectionResult detection : detections) {
            // ⏱️ Log: Stability Check
            long stabStart = System.currentTimeMillis();
            boolean justStable = stabilityTracker.update(detection);
            long stabEnd = System.currentTimeMillis();

            if (!justStable) continue;

            RectF stableBox = stabilityTracker.getStableBox();
            if (stableBox == null) continue;

            // 🚀 Optimization 3: Crop Label Region (Slightly smaller than full bottle box for better OCR)
            // We use the full box here as it's the most reliable for current YOLO model
            Bitmap cropped = BitmapUtils.safeCrop(
                    bitmap,
                    RectUtils.toRect(
                            stableBox,
                            bitmap.getWidth(),
                            bitmap.getHeight()
                    )
            );

            if (cropped != null) {
                // 🚀 Optimization 2: Resize OCR Input (Max width 640px)
                Bitmap ocrInput = BitmapUtils.scaleToWidth(cropped, 640);
                if (ocrInput != cropped) {
                    BitmapUtils.safeRecycle(cropped);
                }

                LogUtils.i(String.format("✅ Stable bottle [Det: %dms, Stab: %dms] → OCR", 
                        (detEnd - detStart), (stabEnd - stabStart)));

                long ocrStart = System.currentTimeMillis();
                ocrProcessor.process(ocrInput, normalizedText -> {
                    long ocrEnd = System.currentTimeMillis();
                    BitmapUtils.safeRecycle(ocrInput);

                    if (normalizedText == null || normalizedText.isEmpty()) {
                        return;
                    }

                    if (!scanDebouncer.shouldProcess(normalizedText)) {
                        return;
                    }

                    long matchStart = System.currentTimeMillis();
                    if (scanResultCallback != null) {
                        scanResultCallback.onScanCompleted(List.of(normalizedText));
                    }
                    long matchEnd = System.currentTimeMillis();

                    // ⏱️ Total Pipeline Metrics (Rule 7)
                    LogUtils.i(String.format("🚀 Latency: Total=%dms [OCR=%dms, Match=%dms]", 
                            (System.currentTimeMillis() - pipelineStart),
                            (ocrEnd - ocrStart),
                            (matchEnd - matchStart)));
                });
            }

            stabilityTracker.reset();
            break;
        }
        
        BitmapUtils.safeRecycle(bitmap);
    }

    /* =========================================================
       CAMERA START / STOP
       ========================================================= */

    public void startCamera(@NonNull LifecycleOwner owner,
                            @NonNull PreviewView previewView) {

        previewView.post(() -> {
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
        });
    }

    private void bindUseCases(@NonNull LifecycleOwner owner,
                              @NonNull PreviewView previewView) {

        if (cameraProvider == null) return;
        cameraProvider.unbindAll();

        Display display = previewView.getDisplay();
        int rotation = (display != null) ? display.getRotation() : Surface.ROTATION_0;

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

        try {
            camera = cameraProvider.bindToLifecycle(
                    owner,
                    selector,
                    preview,
                    imageAnalysis,
                    imageCapture
            );

            if (camera.getCameraInfo().hasFlashUnit()) {
                camera.getCameraControl().enableTorch(CameraConfig.TORCH_ENABLED_BY_DEFAULT);
            }
        } catch (Exception e) {
            LogUtils.e("Camera bind failed", e);
        }
    }

    public void stopCamera() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        camera = null;
    }
}
