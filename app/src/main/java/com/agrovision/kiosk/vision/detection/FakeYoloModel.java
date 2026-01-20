package com.agrovision.kiosk.vision.detection;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.List;

/**
 * FakeYoloModel
 *
 * PURPOSE:
 * - Temporary implementation for development without a real TFLite model.
 * - Simulates a "detection" at the center of the screen.
 */
public final class FakeYoloModel implements YoloModel {

    // Simulates a detection if this is true
    private boolean simulateDetection = true;

    @Override
    public List<RawDetection> runInference(Bitmap bitmap) {
        List<RawDetection> results = new ArrayList<>();

        if (simulateDetection && bitmap != null) {
            // Create a fake detection box in the middle of the frame
            float width = bitmap.getWidth();
            float height = bitmap.getHeight();

            results.add(new RawDetection(
                width * 0.25f,
                height * 0.25f,
                width * 0.75f,
                height * 0.75f,
                0.92f,
                1 // e.g., "Medicine Bottle"
            ));
        }

        return results;
    }

    public void setSimulateDetection(boolean simulate) {
        this.simulateDetection = simulate;
    }
}
