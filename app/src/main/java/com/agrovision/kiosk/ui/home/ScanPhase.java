package com.agrovision.kiosk.ui.home;

/**
 * ScanPhase
 *
 * Represents the current phase of the vision scanning pipeline.
 * Displayed dynamically above the Center ROI box on the camera overlay.
 */
public enum ScanPhase {

    SCANNING(
            "स्कॅनिंग सुरू आहे...",
            "1/3: System Scanning"
    ),
    MATCHING(
            "माहिती जुळवत आहे...",
            "2/3: Matching Medicine"
    ),
    RESULT_SHOWING(
            "निकाल दाखवत आहे...",
            "3/3: Displaying Results"
    );

    private final String marathiText;
    private final String englishSubtitle;

    ScanPhase(String marathiText, String englishSubtitle) {
        this.marathiText = marathiText;
        this.englishSubtitle = englishSubtitle;
    }

    public String getMarathiText() {
        return marathiText;
    }

    public String getEnglishSubtitle() {
        return englishSubtitle;
    }
}
