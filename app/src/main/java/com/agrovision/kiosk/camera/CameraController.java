package com.agrovision.kiosk.camera;

import android.content.Context;
import android.view.Display;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.agrovision.kiosk.threading.DetectionExecutor;
import com.agrovision.kiosk.util.LogUtils;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.Executor;

/**
 * CameraController
 *
 * SINGLE OWNER of CameraX + Frame pipeline.
 * Uses CameraConfig for all behavior settings.
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

        // Camera owns the pipeline internally
        this.frameAnalyzer = new FrameAnalyzer(
                new LuminosityAnalyzer(),
                image -> {
                    // Vision pipeline bridge (YOLO/OCR)
                },
                () -> LogUtils.w("Low light detected at counter")
        );
    }

    /* =========================================================
       CAMERA START (UI-SAFE)
       ========================================================= */

    public void startCamera(@NonNull LifecycleOwner lifecycleOwner,
                            @NonNull PreviewView previewView) {

        ListenableFuture<ProcessCameraProvider> providerFuture =
                ProcessCameraProvider.getInstance(appContext);

        providerFuture.addListener(() -> {
            try {
                cameraProvider = providerFuture.get();
                bindUseCases(lifecycleOwner, previewView);
            } catch (Exception e) {
                LogUtils.e("Failed to start camera", e);
            }
        }, ContextCompat.getMainExecutor(appContext));
    }

    /* =========================================================
       USE CASE BINDING
       ========================================================= */

    private void bindUseCases(@NonNull LifecycleOwner lifecycleOwner,
                              @NonNull PreviewView previewView) {

        if (cameraProvider == null) return;

        cameraProvider.unbindAll();

        // 🔒 FIX: Dynamically fetch rotation from the display for landscape sync
        Display display = previewView.getDisplay();
        int rotation = (display != null) ? display.getRotation() : Surface.ROTATION_0;

        // Use CONFIG: LENS_FACING
        CameraSelector selector = new CameraSelector.Builder()
                .requireLensFacing(CameraConfig.LENS_FACING)
                .build();

        // Use CONFIG: RESOLUTION, FORMAT, BACKPRESSURE, ROTATION
        this.imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(CameraConfig.ANALYSIS_RESOLUTION)
                .setBackpressureStrategy(CameraConfig.BACKPRESSURE_STRATEGY)
                .setOutputImageFormat(CameraConfig.IMAGE_FORMAT)
                .setTargetRotation(rotation) // 🔒 FIX: Use the dynamic rotation variable
                .build();

        // Analyzer processing loop
        this.imageAnalysis.setAnalyzer(cameraExecutor, image -> {
            try {
                frameAnalyzer.analyze(image);
            } catch (Exception e) {
                LogUtils.e("Frame analysis failed", e);
            } finally {
                image.close(); // HARD GUARANTEE to prevent buffer leak
            }
        });

        // Use CONFIG: ROTATION
        this.preview = new Preview.Builder()
                .setTargetRotation(rotation) // 🔒 FIX: Use the dynamic rotation variable
                .build();
        this.preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // Use CONFIG: CAPTURE_MODE, ROTATION
        this.imageCapture = new ImageCapture.Builder()
                .setCaptureMode(CameraConfig.CAPTURE_MODE)
                .setTargetRotation(rotation) // 🔒 FIX: Use the dynamic rotation variable
                .build();

        try {
            camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    this.preview,
                    this.imageAnalysis,
                    this.imageCapture
            );
            
            // Apply CONFIG: TORCH
            camera.getCameraControl().enableTorch(CameraConfig.TORCH_ENABLED_BY_DEFAULT);

        } catch (Exception e) {
            LogUtils.e("Camera bind failed", e);
        }
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
