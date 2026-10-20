package com.agrovision.kiosk.camera;

import java.util.List;

public interface ScanResultCallback {
    void onScanCompleted(List<String> normalizedTexts);

    default void onScanCompleted(List<String> normalizedTexts, ScanMetrics metrics) {
        onScanCompleted(normalizedTexts);
    }
}
