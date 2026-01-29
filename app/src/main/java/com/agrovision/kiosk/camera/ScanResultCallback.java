package com.agrovision.kiosk.camera;

import java.util.List;

public interface ScanResultCallback {
    void onScanCompleted(List<String> normalizedTexts);
}
