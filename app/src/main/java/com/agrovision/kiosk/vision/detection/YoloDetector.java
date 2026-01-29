package com.agrovision.kiosk.vision.detection;

import android.graphics.Bitmap;
import android.graphics.RectF;

import androidx.annotation.NonNull;

import com.agrovision.kiosk.util.TimeUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * YoloDetector
 *
 * Adapter between raw YOLO output and DetectionResult.
 */
public final class YoloDetector {

    private static final float MIN_YOLO_CONFIDENCE = 0.50f;

    private final YoloModel yoloModel;

    public YoloDetector(@NonNull YoloModel yoloModel) {
        this.yoloModel = yoloModel;
    }

    @NonNull
    public List<DetectionResult> detect(@NonNull Bitmap bitmap) {

        final long timestampMs = TimeUtils.nowMs();

        List<YoloModel.RawDetection> rawDetections =
                yoloModel.runInference(bitmap);

        if (rawDetections == null || rawDetections.isEmpty()) {
            return Collections.emptyList();
        }

        List<DetectionResult> results = new ArrayList<>();

        for (YoloModel.RawDetection raw : rawDetections) {

            if (raw.confidence < MIN_YOLO_CONFIDENCE) {
                continue;
            }

            RectF box = new RectF(
                    raw.left,
                    raw.top,
                    raw.right,
                    raw.bottom
            );

            results.add(new DetectionResult(
                    box,
                    raw.confidence,
                    raw.classId,
                    timestampMs
            ));
        }

        return results;
    }
}

