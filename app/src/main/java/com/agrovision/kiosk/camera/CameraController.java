package com.agrovision.kiosk.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.util.Log;
import android.view.Display;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.camera.core.*;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.agrovision.kiosk.state.StateEvent;
import com.agrovision.kiosk.state.StateMachine;
import com.agrovision.kiosk.threading.DetectionExecutor;
import com.agrovision.kiosk.ui.home.BoundingBoxOverlay;
import com.agrovision.kiosk.util.BitmapUtils;
import com.agrovision.kiosk.util.ImageUtils;
import com.agrovision.kiosk.util.LogUtils;
import com.agrovision.kiosk.util.RectUtils;
import com.agrovision.kiosk.vision.detection.*;
import com.agrovision.kiosk.vision.recognition.OcrProcessor;
import com.agrovision.kiosk.vision.recognition.ScanDebouncer;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private final StateMachine stateMachine;

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

    // UI Overlay
    private BoundingBoxOverlay overlayView;

    // 🚀 Multi-object detection fields
    private final AtomicBoolean isProcessingQueue = new AtomicBoolean(false);
    private final AtomicBoolean isDetectionEnabled = new AtomicBoolean(true);
    private final Queue<DetectionResult> pendingDetections = new LinkedList<>();
    private final Set<String> processedResults = new HashSet<>();
    private Bitmap currentProcessingBitmap;

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
        this.stateMachine = StateMachine.getInstance(appContext);

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

    public void setOverlayView(BoundingBoxOverlay overlayView) {
        this.overlayView = overlayView;
    }

    /**
     * 🚀 ENABLE/DISABLE DETECTION
     * Completely pauses the vision pipeline (YOLO + OCR).
     */
    public void setDetectionEnabled(boolean enabled) {
        Log.i("PIPELINE_TRACE", "Detection " + (enabled ? "ENABLED" : "DISABLED"));
        Log.d("SCAN_DEBUG", "Vision analyzer " + (enabled ? "RESUMED" : "PAUSED"));
        isDetectionEnabled.set(enabled);
        if (!enabled) {
            resetPipeline();
            // Clear overlay immediately when disabled
            if (overlayView != null) {
                overlayView.post(() -> overlayView.setBoxes(Collections.emptyList()));
            }
        }
    }

    /**
     * 🚀 RESET PIPELINE
     * Clears all pending detections and resets the processing lock.
     * Should be called when scanning starts/resumes to prevent deadlocks.
     */
    public void resetPipeline() {
        Log.i("PIPELINE_TRACE", "Resetting vision pipeline status");
        isProcessingQueue.set(false);
        pendingDetections.clear();
        processedResults.clear();
        if (currentProcessingBitmap != null) {
            BitmapUtils.safeRecycle(currentProcessingBitmap);
            currentProcessingBitmap = null;
        }
    }

    /**
     * Creates a CameraSelector based on configuration.
     * Falls back to any available camera if the configured lens facing is not available.
     */
    @androidx.annotation.OptIn(markerClass = androidx.camera.core.ExperimentalLensFacing.class)
    private CameraSelector createCameraSelector() {
        if (cameraProvider == null) return CameraSelector.DEFAULT_BACK_CAMERA;

        // 1. Try Configured Lens (usually EXTERNAL for kiosks)
        try {
            CameraSelector selector = new CameraSelector.Builder()
                    .requireLensFacing(CameraConfig.LENS_FACING)
                    .build();
            if (cameraProvider.hasCamera(selector)) {
                return selector;
            }
        } catch (Exception ignored) {}

        // 2. Try BACK camera
        try {
            if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                return CameraSelector.DEFAULT_BACK_CAMERA;
            }
        } catch (Exception ignored) {}

        // 3. Try FRONT camera
        try {
            if (cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                return CameraSelector.DEFAULT_FRONT_CAMERA;
            }
        } catch (Exception ignored) {}

        // 4. EMULATOR/KIOSK FALLBACK: Just take the first available camera
        try {
            List<CameraInfo> available = cameraProvider.getAvailableCameraInfos();
            if (!available.isEmpty()) {
                return available.get(0).getCameraSelector();
            }
        } catch (Exception ignored) {}

        return CameraSelector.DEFAULT_BACK_CAMERA;
    }

    /* =========================================================
       FRAME PIPELINE
       ========================================================= */

    private long lastProcessTime = 0;
    private static final long PROCESS_THROTTLE_MS = 100; // 🚀 Reduced from 200ms to 100ms for "instant" feel

    private void handleFrame(@NonNull ImageProxy image) {
        if (!isDetectionEnabled.get()) {
            image.close();
            return;
        }

        // 🚀 PERFORMANCE OPTIMIZATION:
        // If the pipeline is busy processing previous detections (OCR/Matching),
        // we skip detection on new frames to free up CPU resources.
        // This prevents the "stuttering" or "stuck" feel on lower-end kiosk hardware.
        if (isProcessingQueue.get()) {
            return;
        }

        long now = System.currentTimeMillis();
        Log.v("PIPELINE_TRACE", "1. Frame received");

        // 🚀 Step 2: Still throttle detection to avoid overworking CPU
        if (now - lastProcessTime < PROCESS_THROTTLE_MS) {
            return;
        }
        lastProcessTime = now;

        try {
            Bitmap bitmap = ImageUtils.toBitmap(image);
            if (bitmap == null) return;

            // 🚀 ALWAYS DETECT (Step 8: Detection continues always)
            List<DetectionResult> detections = yoloDetector.detect(bitmap);
            Log.v("PIPELINE_TRACE", "2. YOLO Detection finished. Boxes: " + detections.size());

            // 🚀 ALWAYS UPDATE UI OVERLAY
            updateOverlay(detections, bitmap.getWidth(), bitmap.getHeight());

            if (detections.isEmpty()) {
                BitmapUtils.safeRecycle(bitmap);
                return;
            }

            // 🚀 START PROCESSING QUEUE IF IDLE (Step 6: Create Processing Queue)
            // Use compareAndSet to prevent race conditions when multiple frames are processed
            if (isProcessingQueue.compareAndSet(false, true)) {
                List<DetectionResult> validDetections = new ArrayList<>();
                for (DetectionResult det : detections) {
                    // STEP 3: confidence > 0.5
                    if (det.getConfidence() > 0.5f) {
                        validDetections.add(det);
                    }
                }

                if (!validDetections.isEmpty()) {
                    Log.i("PIPELINE_TRACE", "3. Processing triggered. Valid Boxes: " + validDetections.size());

                    // 🚀 STATE TRANSITION: Notify that an object is detected
                    stateMachine.transition(StateEvent.OBJECT_DETECTED);

                    // STEP 4: LIMIT MAX OBJECTS
                    validDetections.sort((a, b) -> Float.compare(b.getConfidence(), a.getConfidence()));
                    int limit = Math.min(validDetections.size(), 3);
                    
                    pendingDetections.clear();
                    processedResults.clear();
                    
                    for (int i = 0; i < limit; i++) {
                        pendingDetections.add(validDetections.get(i));
                    }
                    
                    currentProcessingBitmap = bitmap;
                    Log.d("SCAN_DEBUG", "Boxes detected: " + validDetections.size() + ". Starting queue processing.");
                    processNext();
                    return; // Don't recycle bitmap, processNext will do it.
                } else {
                    // Reset flag if no valid detections found
                    isProcessingQueue.set(false);
                    Log.v("PIPELINE_TRACE", "3. No boxes passed confidence threshold (>0.5)");
                }
            } else {
                Log.v("PIPELINE_TRACE", "3. Processing skipped: Queue is busy");
            }

            BitmapUtils.safeRecycle(bitmap);

        } catch (Exception e) {
            LogUtils.e("Frame processing failed", e);
        }
    }

    private void processNext() {
        DetectionResult detection = pendingDetections.poll();

        if (detection == null) {
            // STEP 6: Queue empty -> return
            Log.d("PIPELINE_TRACE", "Queue empty, resetting processing flag.");
            isProcessingQueue.set(false);
            if (currentProcessingBitmap != null) {
                BitmapUtils.safeRecycle(currentProcessingBitmap);
                currentProcessingBitmap = null;
            }
            return;
        }

        Log.d("PIPELINE_TRACE", "4. Cropping box index: " + pendingDetections.size());
        Log.d("SCAN_DEBUG", "Processing next box in queue...");

        RectF normBox = detection.getBoundingBox();

        // 🚀 SCALE NORMALIZED -> PIXELS for cropping
        float left = normBox.left * currentProcessingBitmap.getWidth();
        float top = normBox.top * currentProcessingBitmap.getHeight();
        float right = normBox.right * currentProcessingBitmap.getWidth();
        float bottom = normBox.bottom * currentProcessingBitmap.getHeight();
        RectF pixelBox = new RectF(left, top, right, bottom);

        Bitmap cropped = BitmapUtils.safeCrop(
                currentProcessingBitmap,
                RectUtils.toRect(
                        pixelBox,
                        currentProcessingBitmap.getWidth(),
                        currentProcessingBitmap.getHeight()
                )
        );

        if (cropped != null) {
            Log.d("PIPELINE_TRACE", "5. OCR Started");
            Bitmap ocrInput = BitmapUtils.scaleToWidth(cropped, 640);
            if (ocrInput != cropped) {
                BitmapUtils.safeRecycle(cropped);
            }

            ocrProcessor.process(ocrInput, normalizedText -> {
                Log.d("PIPELINE_TRACE", "6. OCR Finished. Text: [" + normalizedText + "]");
                BitmapUtils.safeRecycle(ocrInput);

                if (normalizedText != null && !normalizedText.isEmpty()) {
                    // STEP 7: AVOID DUPLICATES & PREVENT SPAM (using ScanDebouncer)
                    if (scanDebouncer.shouldProcess(normalizedText) && !processedResults.contains(normalizedText)) {
                        processedResults.add(normalizedText);

                        Log.i("PIPELINE_TRACE", "7. Notifying callback with result");
                        // STEP 9: UPDATE RESULT FLOW - Send one-by-one for immediate display
                        if (scanResultCallback != null) {
                            scanResultCallback.onScanCompleted(Collections.singletonList(normalizedText));
                        }
                    } else {
                        Log.d("PIPELINE_TRACE", "7. Result debounced or already processed");
                    }
                } else {
                    Log.d("PIPELINE_TRACE", "7. OCR returned empty/null");
                }

                // STEP 6: After complete -> call processNext()
                processNext();
            });
        } else {
            Log.w("PIPELINE_TRACE", "5. Crop failed");
            processNext();
        }
    }

    @OptIn(markerClass = ExperimentalLensFacing.class)
    private void updateOverlay(List<DetectionResult> detections, int bitmapW, int bitmapH) {
        if (overlayView == null) return;

        float viewW = overlayView.getWidth();
        float viewH = overlayView.getHeight();

        if (viewW <= 0 || viewH <= 0 || bitmapW <= 0 || bitmapH <= 0) return;

        // 🚀 CALCULATE FILL_CENTER SCALE
        float scale = Math.max(viewW / (float) bitmapW, viewH / (float) bitmapH);
        float scaledW = bitmapW * scale;
        float scaledH = bitmapH * scale;

        // 🚀 CALCULATE OFFSETS (Centering)
        float offsetX = (scaledW - viewW) / 2f;
        float offsetY = (scaledH - viewH) / 2f;

        // 🚀 MIRRORING DETECTION
        // Kiosk external cameras often behave like front-facing cameras or
        // come mirrored by default. We enable mirroring if it's front OR external.
        boolean isMirrored = false;
        try {
            if (camera != null) {
                Integer lens = camera.getCameraInfo().getLensFacing();
                isMirrored = (lens != null && (lens == CameraSelector.LENS_FACING_FRONT ||
                                              lens == CameraSelector.LENS_FACING_EXTERNAL));
            }
        } catch (Exception ignored) {}

        List<RectF> mappedBoxes = new ArrayList<>();
        for (DetectionResult det : detections) {
            RectF box = det.getBoundingBox(); // Normalized [0, 1]

            float left = (box.left * scaledW) - offsetX;
            float top = (box.top * scaledH) - offsetY;
            float right = (box.right * scaledW) - offsetX;
            float bottom = (box.bottom * scaledH) - offsetY;

            if (isMirrored) {
                float mLeft = viewW - right;
                float mRight = viewW - left;
                left = mLeft;
                right = mRight;
            }

            mappedBoxes.add(new RectF(left, top, right, bottom));
        }

        overlayView.post(() -> overlayView.setBoxes(mappedBoxes));
    }

    /* =========================================================
       CAMERA START / STOP
       ========================================================= */

    public void startCamera(@NonNull LifecycleOwner owner,
                            @NonNull PreviewView previewView) {

        previewView.post(() -> {
            // 🚀 PERFORMANCE FIX: Use PERFORMANCE mode (SurfaceView) instead of COMPATIBLE (TextureView)
            // SurfaceView is much more efficient and reduces stuttering significantly on kiosk hardware.
            previewView.setImplementationMode(PreviewView.ImplementationMode.PERFORMANCE);

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

    public void startSilentAnalysis(@NonNull LifecycleOwner owner) {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(appContext);

        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                if (cameraProvider == null) return;

                cameraProvider.unbindAll();

                CameraSelector selector = createCameraSelector();

                ResolutionSelector resSelector = new ResolutionSelector.Builder()
                        .setResolutionStrategy(new ResolutionStrategy(
                                CameraConfig.ANALYSIS_RESOLUTION,
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER))
                        .build();

                imageAnalysis = new ImageAnalysis.Builder()
                        .setResolutionSelector(resSelector)
                        .setBackpressureStrategy(CameraConfig.BACKPRESSURE_STRATEGY)
                        .setOutputImageFormat(CameraConfig.IMAGE_FORMAT)
                        .setTargetRotation(Surface.ROTATION_90)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, frameAnalyzer);

                try {
                    camera = cameraProvider.bindToLifecycle(
                            owner,
                            selector,
                            imageAnalysis
                    );
                } catch (Exception e) {
                    LogUtils.w("Silent analysis bind with RGBA failed, falling back to YUV");
                    imageAnalysis = new ImageAnalysis.Builder()
                            .setResolutionSelector(resSelector)
                            .setBackpressureStrategy(CameraConfig.BACKPRESSURE_STRATEGY)
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                            .setTargetRotation(Surface.ROTATION_90)
                            .build();
                    imageAnalysis.setAnalyzer(cameraExecutor, frameAnalyzer);
                    camera = cameraProvider.bindToLifecycle(
                            owner,
                            selector,
                            imageAnalysis
                    );
                }
            } catch (Exception e) {
                LogUtils.e("Silent analysis start failed", e);
            }
        }, ContextCompat.getMainExecutor(appContext));
    }

    private void bindUseCases(@NonNull LifecycleOwner owner,
                              @NonNull PreviewView previewView) {

        if (cameraProvider == null) return;
        cameraProvider.unbindAll();

        Display display = previewView.getDisplay();
        int rotation = (display != null) ? display.getRotation() : Surface.ROTATION_0;

        CameraSelector selector = createCameraSelector();

        // 🚀 RESOLUTION STRATEGY: 
        // Use 1080p for analysis to handle shop glare and high-quality sensors.
        // FALLBACK_RULE_CLOSEST_LOWER ensures compatibility if 1080p is not supported.
        ResolutionSelector resSelector = new ResolutionSelector.Builder()
                .setResolutionStrategy(new ResolutionStrategy(
                        CameraConfig.ANALYSIS_RESOLUTION,
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER))
                .build();

        imageAnalysis = new ImageAnalysis.Builder()
                .setResolutionSelector(resSelector)
                .setBackpressureStrategy(CameraConfig.BACKPRESSURE_STRATEGY)
                .setOutputImageFormat(CameraConfig.IMAGE_FORMAT)
                .setTargetRotation(rotation)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, frameAnalyzer);

        preview = new Preview.Builder()
                .setResolutionSelector(resSelector)
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
            LogUtils.e("Primary camera bind failed (1080p/RGBA), retrying with safe fallback (720p/YUV)", e);
            // 🚀 EMULATOR & COMPATIBILITY FALLBACK: 
            // If high resolution or RGBA fails, try 720p with YUV which is most supported.
            try {
                cameraProvider.unbindAll();

                ResolutionSelector fallbackResSelector = new ResolutionSelector.Builder()
                        .setResolutionStrategy(new ResolutionStrategy(
                                new android.util.Size(1280, 720),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER))
                        .build();
                
                imageAnalysis = new ImageAnalysis.Builder()
                        .setResolutionSelector(fallbackResSelector)
                        .setBackpressureStrategy(CameraConfig.BACKPRESSURE_STRATEGY)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                        .setTargetRotation(rotation)
                        .build();
                imageAnalysis.setAnalyzer(cameraExecutor, frameAnalyzer);

                preview = new Preview.Builder()
                        .setResolutionSelector(fallbackResSelector)
                        .setTargetRotation(rotation)
                        .build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                camera = cameraProvider.bindToLifecycle(
                        owner,
                        selector,
                        preview,
                        imageAnalysis,
                        imageCapture
                );
            } catch (Exception fatal) {
                LogUtils.e("Camera bind failed permanently", fatal);
            }
        }
    }

    public void stopCamera() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        camera = null;
    }
}
