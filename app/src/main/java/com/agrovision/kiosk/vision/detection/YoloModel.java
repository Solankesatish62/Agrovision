package com.agrovision.kiosk.vision.detection;

import android.graphics.Bitmap;
import android.graphics.RectF;

import java.util.List;

/**
 * YoloModel
 *
 * CONTRACT for YOLO-based detectors.
 */
public interface YoloModel {

    /**
     * Runs inference on a bitmap.
     *
     * MUST:
     * - be synchronous
     * - never return null (use empty list)
     * - never retain bitmap reference
     */
    List<RawDetection> runInference(Bitmap bitmap);

    /**
     * RawDetection
     *
     * Low-level YOLO output container.
     * PURE DATA — no logic.
     */
    final class RawDetection {

        public final float left;
        public final float top;
        public final float right;
        public final float bottom;

        public final float confidence;
        public final int classId;

        public RawDetection(float left,
                            float top,
                            float right,
                            float bottom,
                            float confidence,
                            int classId) {

            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.confidence = confidence;
            this.classId = classId;
        }

        public RectF toRectF() {
            return new RectF(left, top, right, bottom);
        }

        public float confidence() {
            return confidence;
        }
    }
}
