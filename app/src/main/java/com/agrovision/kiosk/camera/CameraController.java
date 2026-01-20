package com.agrovision.kiosk.camera;

import android.content.Context;

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

import com.agrovision.kiosk.util.LogUtils;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.Executor;

/**
 * CameraController
 *
 * PURPOSE:
 * - Own CameraX binding/unbinding
 * - Deliver frames to exactly ONE analyzer
 * - Render preview to a supplied PreviewView
 */
public final class CameraController {

    private final Context appContext;
    private final Executor cameraExecutor;

    private ProcessCameraProvider cameraProvider;
    private Camera camera;

    private ImageAnalysis imageAnalysis;
    private ImageCapture imageCapture;
    private Preview preview;

    public CameraController(@NonNull Context context,
                            @NonNull Executor cameraExecutor) {
        this.appContext = context.getApplicationContext();
        this.cameraExecutor = cameraExecutor;
    }

    /* =========================================================
       CAMERA START
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
       BIND USE CASES
       ========================================================= */

    private void bindUseCases(@NonNull LifecycleOwner lifecycleOwner,
                              @NonNull PreviewView previewView) {

        if (cameraProvider == null) return;

        cameraProvider.unbindAll();

        CameraSelector selector =
                new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build();

        imageAnalysis =
                new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

        // 🔒 Placeholder for analyzer
        // imageAnalysis.setAnalyzer(cameraExecutor, analyzer);

        imageCapture =
                new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

        preview = new Preview.Builder().build();

        // ✅ Attach surface
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        camera =
                cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        selector,
                        preview,
                        imageAnalysis,
                        imageCapture
                );
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
