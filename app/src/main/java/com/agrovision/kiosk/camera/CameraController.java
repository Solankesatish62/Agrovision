package com.agrovision.kiosk.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
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
import com.agrovision.kiosk.util.PerformanceProfiler;
import com.agrovision.kiosk.util.RectUtils;
import com.agrovision.kiosk.vision.detection.*;
import com.agrovision.kiosk.vision.recognition.OcrProcessor;
import com.agrovision.kiosk.vision.recognition.ScanDebouncer;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * CameraController
 *
 * SINGLE OWNER of CameraX + vision pipeline.
 * Synchronizes rotation with display for upright preview and processing.
 * Optimized for Center ROI scanning without YOLO inference.
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

    // Mode Boundary & YOLO tracking
    private volatile ScanMode scanMode = ScanMode.CENTER_ROI;
    private final AtomicInteger yoloInferenceCount = new AtomicInteger(0);
    private final AtomicLong scanAttemptCounter = new AtomicLong(0);

    // UI Overlay
    private BoundingBoxOverlay overlayView;

    // Multi-object detection fields
    private final AtomicBoolean isProcessingQueue = new AtomicBoolean(false);
    private final AtomicBoolean isDetectionEnabled = new AtomicBoolean(true);
    private final Queue<DetectionResult> pendingDetections = new LinkedList<>();
    private final Set<String> processedResults = ConcurrentHashMap.newKeySet();
    private final Object pipelineLock = new Object();
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

    public void setScanMode(ScanMode mode) {
        this.scanMode = mode;
        if (overlayView != null) {
            overlayView.setScanMode(mode);
        }
        Log.i("CENTER_ROI_SCAN", "Scan mode set to: " + mode);
    }

    public ScanMode getScanMode() {
        return scanMode;
    }

    public int getYoloInferenceCount() {
        return yoloInferenceCount.get();
    }

    public void resetYoloInferenceCount() {
        yoloInferenceCount.set(0);
    }

    public void setScanResultCallback(ScanResultCallback callback) {
        this.scanResultCallback = callback;
    }

    public void setOverlayView(BoundingBoxOverlay overlayView) {
        this.overlayView = overlayView;
        if (overlayView != null) {
            overlayView.setScanMode(scanMode);
        }
    }

    /**
     * ENABLE/DISABLE DETECTION
     * Completely pauses the vision pipeline (YOLO + OCR).
     */
    public void setDetectionEnabled(boolean enabled) {
        Log.i("PIPELINE_TRACE", "Detection " + (enabled ? "ENABLED" : "DISABLED"));
        Log.d("SCAN_DEBUG", "Vision analyzer " + (enabled ? "RESUMED" : "PAUSED"));
        if (enabled) {
            PerformanceProfiler.log("Queue Profiling", "Preview Resumed");
        }
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
     * RESET PIPELINE
     * Clears all pending detections and resets the processing lock.
     * Should be called when scanning starts/resumes to prevent deadlocks.
     */
    public void resetPipeline() {
        synchronized (pipelineLock) {
            Log.i("PIPELINE_TRACE", "Resetting vision pipeline status");
            isProcessingQueue.set(false);
            PerformanceProfiler.unlock();
            pendingDetections.clear();
            processedResults.clear();
            if (currentProcessingBitmap != null) {
                BitmapUtils.safeRecycle(currentProcessingBitmap);
                currentProcessingBitmap = null;
            }
        }
    }

    /**
     * Creates a CameraSelector based on configuration.
     * Falls back to any available camera if the configured lens facing is not available.
     */
    @OptIn(markerClass = ExperimentalLensFacing.class)
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
    private long lastPreviewFrameTime = 0;
    private static final long PROCESS_THROTTLE_MS = 200; // Throttle to prevent GC churn

    private void handleFrame(@NonNull ImageProxy image) {
        lastPreviewFrameTime = System.currentTimeMillis();
        if (!isDetectionEnabled.get()) {
            image.close();
            return;
        }

        if (scanMode == ScanMode.CENTER_ROI) {
            processCenterRoiFrame(image);
        } else {
            processLegacyYoloFrame(image);
        }
    }

    private void processCenterRoiFrame(@NonNull ImageProxy image) {
        long frameArrivalMs = SystemClock.elapsedRealtime();
        long scanAttemptId = scanAttemptCounter.incrementAndGet();
        String attemptTag = "scan_" + frameArrivalMs + "_" + scanAttemptId;

        ScanMetrics metrics = new ScanMetrics(attemptTag, frameArrivalMs);

        if (isProcessingQueue.get()) {
            image.close();
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastProcessTime < PROCESS_THROTTLE_MS) {
            image.close();
            return;
        }
        lastProcessTime = now;

        if (!isProcessingQueue.compareAndSet(false, true)) {
            image.close();
            return;
        }

        Log.i("CENTER_ROI_SCAN", "[" + attemptTag + "] ROI mode activated");
        Log.i("CENTER_ROI_SCAN", "[" + attemptTag + "] YOLO skipped because ROI mode is active");
        Log.i("CENTER_ROI_SCAN", "[" + attemptTag + "] ImageAnalysis frame received: " + image.getWidth() + "x" + image.getHeight() + ", rotation: " + image.getImageInfo().getRotationDegrees());

        long convStart = SystemClock.elapsedRealtime();
        Bitmap fullBitmap = null;
        try {
            fullBitmap = ImageUtils.toBitmap(image);
        } catch (Exception e) {
            LogUtils.e("Failed to convert image proxy to bitmap in ROI mode", e);
        } finally {
            image.close(); // CRITICAL: Close ImageProxy immediately
        }

        metrics.conversionMs = SystemClock.elapsedRealtime() - convStart;

        if (fullBitmap == null) {
            isProcessingQueue.set(false);
            Log.w("CENTER_ROI_SCAN", "[" + attemptTag + "] Image Proxy to Bitmap conversion failed");
            return;
        }

        // 1. Calculate Center ROI coordinates mapped to bitmap space
        long roiStartMs = SystemClock.elapsedRealtime();
        Rect cropRect = calculateCenterRoiCropRect(fullBitmap);
        metrics.roiCalcMs = SystemClock.elapsedRealtime() - roiStartMs;
        Log.i("CENTER_ROI_SCAN", "[" + attemptTag + "] ROI coordinates calculated: " + cropRect + " in " + metrics.roiCalcMs + " ms");

        // 2. Crop Center ROI
        long cropStartMs = SystemClock.elapsedRealtime();
        Bitmap roiBitmap = BitmapUtils.safeCrop(fullBitmap, cropRect);
        BitmapUtils.safeRecycle(fullBitmap);
        metrics.cropMs = SystemClock.elapsedRealtime() - cropStartMs;

        if (roiBitmap == null) {
            isProcessingQueue.set(false);
            Log.w("CENTER_ROI_SCAN", "[" + attemptTag + "] ROI crop creation failed");
            return;
        }

        Log.i("CENTER_ROI_SCAN", "[" + attemptTag + "] ROI crop created: " + roiBitmap.getWidth() + "x" + roiBitmap.getHeight() + " in " + metrics.cropMs + " ms");

        // 3. Image-quality check
        long qualityStartMs = SystemClock.elapsedRealtime();
        boolean qualityPass = checkImageQuality(roiBitmap);
        metrics.qualityCheckMs = SystemClock.elapsedRealtime() - qualityStartMs;
        Log.i("CENTER_ROI_SCAN", "[" + attemptTag + "] Image-quality check completed: " + (qualityPass ? "PASSED" : "FAILED") + " in " + metrics.qualityCheckMs + " ms");

        if (!qualityPass) {
            BitmapUtils.safeRecycle(roiBitmap);
            isProcessingQueue.set(false);
            Log.w("CENTER_ROI_SCAN", "[" + attemptTag + "] Candidate rejected: low image quality / blur / darkness");
            return;
        }

        // 4. Downscale for OCR performance
        Bitmap ocrInput = BitmapUtils.scaleToWidth(roiBitmap, 640);
        if (ocrInput != roiBitmap) {
            BitmapUtils.safeRecycle(roiBitmap);
        }

        // 5. OCR Execution
        long ocrStartMs = SystemClock.elapsedRealtime();
        Log.i("CENTER_ROI_SCAN", "[" + attemptTag + "] OCR started");

        ocrProcessor.process(ocrInput, (normalizedText, normDurationMs) -> {
            metrics.ocrMs = SystemClock.elapsedRealtime() - ocrStartMs;
            metrics.normMs = normDurationMs;

            Log.i("CENTER_ROI_SCAN", "[" + attemptTag + "] OCR completed in " + metrics.ocrMs + " ms. Text length: " + (normalizedText != null ? normalizedText.length() : 0));
            BitmapUtils.safeRecycle(ocrInput);

            if (normalizedText == null || normalizedText.trim().isEmpty()) {
                Log.w("CENTER_ROI_SCAN", "[" + attemptTag + "] OCR empty/error: no text recognized");
                isProcessingQueue.set(false);
                return;
            }

            Log.i("CENTER_ROI_SCAN", "[" + attemptTag + "] Normalization completed in " + normDurationMs + " ms: [" + normalizedText + "]");

            if (scanDebouncer.shouldProcess(normalizedText) && !processedResults.contains(normalizedText)) {
                processedResults.add(normalizedText);

                Log.i("CENTER_ROI_SCAN", "[" + attemptTag + "] Medicine matching started for: [" + normalizedText + "]");

                if (scanResultCallback != null) {
                    Log.i("CENTER_ROI_SCAN", "[" + attemptTag + "] Candidate accepted, notifying callback with metrics");
                    scanResultCallback.onScanCompleted(Collections.singletonList(normalizedText), metrics);
                } else {
                    Log.w("CENTER_ROI_SCAN", "[" + attemptTag + "] No candidate callback registered");
                }
            } else {
                Log.i("CENTER_ROI_SCAN", "[" + attemptTag + "] Candidate rejected: duplicate or debounced text");
            }

            isProcessingQueue.set(false);
        });
    }

    public Rect calculateCenterRoiCropRect(Bitmap fullBitmap) {
        int bitmapW = fullBitmap.getWidth();
        int bitmapH = fullBitmap.getHeight();

        if (overlayView != null && overlayView.getWidth() > 0 && overlayView.getHeight() > 0) {
            float viewW = overlayView.getWidth();
            float viewH = overlayView.getHeight();

            RectF viewRoi = overlayView.getCenterRoiRectInView();

            float scale = Math.max(viewW / (float) bitmapW, viewH / (float) bitmapH);
            float scaledW = bitmapW * scale;
            float scaledH = bitmapH * scale;

            float offsetX = (scaledW - viewW) / 2f;
            float offsetY = (scaledH - viewH) / 2f;

            float bmpLeft = (viewRoi.left + offsetX) / scale;
            float bmpTop = (viewRoi.top + offsetY) / scale;
            float bmpRight = (viewRoi.right + offsetX) / scale;
            float bmpBottom = (viewRoi.bottom + offsetY) / scale;

            int left = (int) Math.max(0, Math.min(bitmapW - 32, bmpLeft));
            int top = (int) Math.max(0, Math.min(bitmapH - 32, bmpTop));
            int right = (int) Math.max(left + 32, Math.min(bitmapW, bmpRight));
            int bottom = (int) Math.max(top + 32, Math.min(bitmapH, bmpBottom));

            return new Rect(left, top, right, bottom);
        } else {
            // Fallback: Center 60% x 60% of bitmap
            int left = (int) (bitmapW * 0.20f);
            int top = (int) (bitmapH * 0.20f);
            int right = (int) (bitmapW * 0.80f);
            int bottom = (int) (bitmapH * 0.80f);
            return new Rect(left, top, right, bottom);
        }
    }

    public boolean checkImageQuality(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled() || bitmap.getWidth() < 32 || bitmap.getHeight() < 32) {
            return false;
        }

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int stepX = Math.max(1, width / 10);
        int stepY = Math.max(1, height / 10);
        long totalLuma = 0;
        int sampled = 0;

        for (int x = 0; x < width; x += stepX) {
            for (int y = 0; y < height; y += stepY) {
                int pixel = bitmap.getPixel(x, y);
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;
                int luma = (r * 299 + g * 587 + b * 114) / 1000;
                totalLuma += luma;
                sampled++;
            }
        }

        if (sampled == 0) return false;
        float avgLuma = totalLuma / (float) sampled;
        return avgLuma >= 12f; // Reject pitch dark images
    }

    private void processLegacyYoloFrame(@NonNull ImageProxy image) {
        if (isProcessingQueue.get()) {
            image.close();
            return;
        }

        PerformanceProfiler.startScan();
        PerformanceProfiler.start("Camera Frame");
        PerformanceProfiler.log("Camera Frame", "Frame Number: " + image.hashCode());
        PerformanceProfiler.log("Camera Frame", "Resolution: " + image.getWidth() + "x" + image.getHeight());
        PerformanceProfiler.log("Camera Frame", "Rotation: " + image.getImageInfo().getRotationDegrees());
        PerformanceProfiler.end("Camera Frame");

        long now = System.currentTimeMillis();

        if (now - lastProcessTime < PROCESS_THROTTLE_MS) {
            image.close();
            return;
        }
        lastProcessTime = now;

        try {
            PerformanceProfiler.start("YUV -> RGB");
            Bitmap bitmap = ImageUtils.toBitmap(image);
            PerformanceProfiler.end("YUV -> RGB");
            image.close();

            if (bitmap == null) return;

            PerformanceProfiler.start("YOLO");
            yoloInferenceCount.incrementAndGet(); // Increment YOLO counter
            List<DetectionResult> detections = yoloDetector.detect(bitmap);
            PerformanceProfiler.end("YOLO");

            PerformanceProfiler.start("Overlay Draw");
            updateOverlay(detections, bitmap.getWidth(), bitmap.getHeight());
            PerformanceProfiler.end("Overlay Draw");

            PerformanceProfiler.printSummary();

            if (detections.isEmpty()) {
                BitmapUtils.safeRecycle(bitmap);
                return;
            }

            DetectionResult topDetection = detections.get(0);
            PerformanceProfiler.start("Bounding Stability");
            boolean justBecameStable = stabilityTracker.update(topDetection);

            if (!stabilityTracker.isStable()) {
                PerformanceProfiler.log("Bounding Stability", "Frames Seen: " + stabilityTracker.getFrameCount());
                PerformanceProfiler.end("Bounding Stability");
                Log.v("PIPELINE_TRACE", "3. Waiting for stability...");
                BitmapUtils.safeRecycle(bitmap);
                return;
            }
            PerformanceProfiler.log("Bounding Stability", "Stable after " + stabilityTracker.getFrameCount() + " frames");
            PerformanceProfiler.end("Bounding Stability");

            if (isProcessingQueue.compareAndSet(false, true)) {
                PerformanceProfiler.lock();
                PerformanceProfiler.log("Queue Profiling", "Preview Paused");
                List<DetectionResult> validDetections = new ArrayList<>();
                for (DetectionResult det : detections) {
                    if (det.getConfidence() > 0.5f) {
                        validDetections.add(det);
                    }
                }

                if (!validDetections.isEmpty()) {
                    Log.i("PIPELINE_TRACE", "4. Stable & Processing triggered. Valid Boxes: " + validDetections.size());
                    stateMachine.transition(StateEvent.OBJECT_DETECTED);

                    validDetections.sort((a, b) -> Float.compare(b.getConfidence(), a.getConfidence()));
                    int limit = Math.min(validDetections.size(), 3);

                    List<DetectionResult> targetDetections = new ArrayList<>();
                    for (int i = 0; i < limit; i++) {
                        targetDetections.add(validDetections.get(i));
                    }

                    currentProcessingBitmap = bitmap;
                    processDetectionsParallel(targetDetections);
                    return;
                } else {
                    isProcessingQueue.set(false);
                }
            }

            BitmapUtils.safeRecycle(bitmap);

        } catch (Exception e) {
            LogUtils.e("Frame processing failed", e);
        }
    }

    private void processDetectionsParallel(List<DetectionResult> detections) {
        if (detections == null || detections.isEmpty()) {
            resetPipeline();
            return;
        }

        Log.d("PIPELINE_TRACE", "4. Processing " + detections.size() + " detections in parallel.");
        AtomicInteger pendingCount = new AtomicInteger(detections.size());
        processedResults.clear();

        for (DetectionResult detection : detections) {
            RectF normBox = detection.getBoundingBox();
            Bitmap cropped = null;
            int width = 0;
            int height = 0;

            synchronized (pipelineLock) {
                if (currentProcessingBitmap != null && !currentProcessingBitmap.isRecycled()) {
                    width = currentProcessingBitmap.getWidth();
                    height = currentProcessingBitmap.getHeight();

                    float left = normBox.left * width;
                    float top = normBox.top * height;
                    float right = normBox.right * width;
                    float bottom = normBox.bottom * height;
                    RectF pixelBox = new RectF(left, top, right, bottom);

                    PerformanceProfiler.start("Crop");
                    cropped = BitmapUtils.safeCrop(
                            currentProcessingBitmap,
                            RectUtils.toRect(
                                    pixelBox,
                                    width,
                                    height
                            )
                    );
                    PerformanceProfiler.end("Crop");
                }
            }

            if (cropped != null) {
                Log.d("PIPELINE_TRACE", "5. OCR Task Submitted");
                Bitmap ocrInput = BitmapUtils.scaleToWidth(cropped, 640);
                if (ocrInput != cropped) {
                    BitmapUtils.safeRecycle(cropped);
                }

                ocrProcessor.process(ocrInput, normalizedText -> {
                    Log.d("PIPELINE_TRACE", "6. OCR Finished. Text: [" + normalizedText + "]");
                    BitmapUtils.safeRecycle(ocrInput);

                    if (normalizedText != null && !normalizedText.isEmpty()) {
                        if (scanDebouncer.shouldProcess(normalizedText) && !processedResults.contains(normalizedText)) {
                            processedResults.add(normalizedText);
                            Log.i("PIPELINE_TRACE", "7. Notifying callback with result");
                            if (scanResultCallback != null) {
                                scanResultCallback.onScanCompleted(Collections.singletonList(normalizedText));
                            }
                        }
                    }

                    synchronized (pipelineLock) {
                        if (pendingCount.decrementAndGet() == 0) {
                            Log.d("PIPELINE_TRACE", "All parallel OCR tasks finished. Resetting queue.");
                            isProcessingQueue.set(false);
                            if (currentProcessingBitmap != null) {
                                BitmapUtils.safeRecycle(currentProcessingBitmap);
                                currentProcessingBitmap = null;
                            }
                        }
                    }
                });
            } else {
                synchronized (pipelineLock) {
                    if (pendingCount.decrementAndGet() == 0) {
                        isProcessingQueue.set(false);
                        if (currentProcessingBitmap != null) {
                            BitmapUtils.safeRecycle(currentProcessingBitmap);
                            currentProcessingBitmap = null;
                        }
                    }
                }
            }
        }
    }

    @OptIn(markerClass = ExperimentalLensFacing.class)
    private void updateOverlay(List<DetectionResult> detections, int bitmapW, int bitmapH) {
        if (overlayView == null) return;

        float viewW = overlayView.getWidth();
        float viewH = overlayView.getHeight();

        if (viewW <= 0 || viewH <= 0 || bitmapW <= 0 || bitmapH <= 0) return;

        float scale = Math.max(viewW / (float) bitmapW, viewH / (float) bitmapH);
        float scaledW = bitmapW * scale;
        float scaledH = bitmapH * scale;

        float offsetX = (scaledW - viewW) / 2f;
        float offsetY = (scaledH - viewH) / 2f;

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
            RectF box = det.getBoundingBox();

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
