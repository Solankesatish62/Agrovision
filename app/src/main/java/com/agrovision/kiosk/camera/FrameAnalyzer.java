package com.agrovision.kiosk.camera;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.agrovision.kiosk.util.LogUtils;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * FrameAnalyzer
 *
 * PURPOSE:
 * - Enforce strict backpressure
 * - Perform light checks
 * - Forward frames synchronously
 *
 * HARD CONTRACT:
 * - Downstream processing MUST be synchronous
 * - ImageProxy is always closed here
 */
public final class FrameAnalyzer implements ImageAnalysis.Analyzer {

    private final AtomicBoolean isProcessing = new AtomicBoolean(false);

    private final LuminosityAnalyzer luminosityAnalyzer;
    private final SyncFrameConsumer frameConsumer;
    private final LowLightListener lowLightListener;

    public FrameAnalyzer(@NonNull LuminosityAnalyzer luminosityAnalyzer,
                         @NonNull SyncFrameConsumer frameConsumer,
                         @NonNull LowLightListener lowLightListener) {

        this.luminosityAnalyzer = luminosityAnalyzer;
        this.frameConsumer = frameConsumer;
        this.lowLightListener = lowLightListener;
    }

    @Override
    public void analyze(@NonNull ImageProxy image) {

        // 🚫 Drop if already processing
        if (!isProcessing.compareAndSet(false, true)) {
            image.close();
            return;
        }

        try {
            // 1️⃣ Lighting check (non-destructive)
            if (luminosityAnalyzer.isTooDark(image)) {
                lowLightListener.onLowLightDetected();
                return;
            }

            // 2️⃣ Synchronous downstream processing ONLY
            frameConsumer.onFrame(image);

        } catch (Exception e) {
            LogUtils.e("FrameAnalyzer failure", e);

        } finally {
            //image.close();
            isProcessing.set(false);
        }
    }

    /* =========================================================
       CONTRACT INTERFACES
       ========================================================= */

    @FunctionalInterface
    public interface SyncFrameConsumer {
        /**
         * MUST process synchronously.
         * MUST NOT retain ImageProxy.
         * MUST NOT close ImageProxy.
         */
        void onFrame(@NonNull ImageProxy image);
    }

    @FunctionalInterface
    public interface LowLightListener {
        void onLowLightDetected();
    }
}
