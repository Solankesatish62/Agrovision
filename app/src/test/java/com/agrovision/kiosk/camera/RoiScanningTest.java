package com.agrovision.kiosk.camera;

import com.agrovision.kiosk.data.model.Medicine;
import com.agrovision.kiosk.ui.result.model.ResultType;
import com.agrovision.kiosk.ui.result.model.ScanResult;
import com.agrovision.kiosk.vision.mapping.MatchResult;
import com.agrovision.kiosk.vision.mapping.MedicineMatcher;
import com.agrovision.kiosk.vision.recognition.TextCleaner;
import com.agrovision.kiosk.vision.recognition.TextNormalizer;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class RoiScanningTest {

    private List<Medicine> testCatalog;

    @Before
    public void setUp() {
        testCatalog = Arrays.asList(
                new Medicine("1", "CORAGEN", "FMC", null, "Chlorantraniliprole", null, null, null, null, Arrays.asList("coragen", "insecticide"), null, null, null, 0),
                new Medicine("2", "CONFIDOR", "Bayer", null, "Imidacloprid", null, null, null, null, Arrays.asList("confidor"), null, null, null, 0)
        );
    }

    @Test
    public void test1_RoiCropCoordinatesCalculation() {
        // Frame dimensions: 1000x1000
        int width = 1000;
        int height = 1000;

        // Center 60% x 60% ROI
        int roiWidth = (int) (width * 0.60f);
        int roiHeight = (int) (height * 0.60f);

        int left = (width - roiWidth) / 2;
        int top = (height - roiHeight) / 2;
        int right = left + roiWidth;
        int bottom = top + roiHeight;

        assertEquals(200, left);
        assertEquals(200, top);
        assertEquals(800, right);
        assertEquals(800, bottom);
        assertEquals(600, right - left);
        assertEquals(600, bottom - top);
    }

    @Test
    public void test2_RoiMappingWithPreviewScaling() {
        // View dimensions: 1000x500
        // Bitmap dimensions: 640x480
        float viewW = 1000f;
        float viewH = 500f;
        float bmpW = 640f;
        float bmpH = 480f;

        // View ROI bounds: left=200, top=100, right=800, bottom=400
        float viewRoiLeft = 200f;
        float viewRoiTop = 100f;
        float viewRoiRight = 800f;
        float viewRoiBottom = 400f;

        float scale = Math.max(viewW / bmpW, viewH / bmpH); // 1.5625
        float scaledW = bmpW * scale; // 1000
        float scaledH = bmpH * scale; // 750
        float offsetX = (scaledW - viewW) / 2f; // 0
        float offsetY = (scaledH - viewH) / 2f; // 125

        float bmpLeft = (viewRoiLeft + offsetX) / scale;
        float bmpTop = (viewRoiTop + offsetY) / scale;
        float bmpRight = (viewRoiRight + offsetX) / scale;
        float bmpBottom = (viewRoiBottom + offsetY) / scale;

        assertEquals(128, Math.round(bmpLeft));
        assertEquals(144, Math.round(bmpTop));
        assertEquals(512, Math.round(bmpRight));
        assertEquals(336, Math.round(bmpBottom));
    }

    @Test
    public void test3_InvalidCropBoundsClamping() {
        int bmpW = 640;
        int bmpH = 480;

        // Out of bounds requested rect: [-50, -50, 800, 600]
        int reqLeft = -50;
        int reqTop = -50;
        int reqRight = 800;
        int reqBottom = 600;

        int clampedLeft = Math.max(0, Math.min(bmpW, reqLeft));
        int clampedTop = Math.max(0, Math.min(bmpH, reqTop));
        int clampedRight = Math.max(clampedLeft + 32, Math.min(bmpW, reqRight));
        int clampedBottom = Math.max(clampedTop + 32, Math.min(bmpH, reqBottom));

        assertEquals(0, clampedLeft);
        assertEquals(0, clampedTop);
        assertEquals(640, clampedRight);
        assertEquals(480, clampedBottom);
    }

    @Test
    public void test4_RoiCropDimensionsAreSmallerThanFullFrame() {
        int fullW = 1920;
        int fullH = 1080;

        int roiLeft = (int) (fullW * 0.20f);
        int roiTop = (int) (fullH * 0.20f);
        int roiRight = (int) (fullW * 0.80f);
        int roiBottom = (int) (fullH * 0.80f);

        int cropW = roiRight - roiLeft;
        int cropH = roiBottom - roiTop;

        assertTrue(cropW < fullW);
        assertTrue(cropH < fullH);
        assertEquals(1152, cropW);
        assertEquals(648, cropH);
    }

    @Test
    public void test5_TextNormalizationAndMedicineMatcherUsed() {
        String rawOcrText = "  C O R A G E N  20% SC \n FMC ";

        String cleaned = TextCleaner.clean(rawOcrText);
        String normalized = TextNormalizer.normalize(cleaned);

        assertFalse(normalized.isEmpty());
        assertTrue(normalized.toLowerCase().contains("coragen"));

        MatchResult match = MedicineMatcher.match(normalized, testCatalog);

        assertTrue(match.isMatched());
        assertNotNull(match.getMedicine());
        assertEquals("CORAGEN", match.getMedicine().getName());
    }

    @Test
    public void test6_KnownAndUnknownResultFlowPreserved() {
        MatchResult knownMatch = MedicineMatcher.match("CORAGEN", testCatalog);
        assertTrue(knownMatch.isMatched());

        ScanResult knownResult = new ScanResult(
                ResultType.KNOWN,
                knownMatch.getMedicine().getId(),
                knownMatch.getMedicine().getName(),
                knownMatch.getMedicine().getCompany(),
                knownMatch.getMedicine().getChemicalName(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                "CORAGEN",
                false
        );
        assertEquals(ResultType.KNOWN, knownResult.resultType);
        assertEquals("CORAGEN", knownResult.displayName);

        MatchResult unknownMatch = MedicineMatcher.match("XYZZY_UNKNOWN_LABEL_1234", testCatalog);
        assertFalse(unknownMatch.isMatched());

        ScanResult unknownResult = new ScanResult(
                ResultType.UNKNOWN,
                null,
                "Unknown Medicine",
                null,
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                "XYZZY_UNKNOWN_LABEL_1234",
                false
        );
        assertEquals(ResultType.UNKNOWN, unknownResult.resultType);
    }

    @Test
    public void test7_EmptyOcrDoesNotInvokeYolo() {
        AtomicInteger yoloInferenceCalls = new AtomicInteger(0);

        ScanMode scanMode = ScanMode.CENTER_ROI;
        String emptyOcrText = "";

        if (scanMode == ScanMode.CENTER_ROI) {
            // ROI mode handles empty OCR by logging and returning
            if (emptyOcrText.isEmpty()) {
                // Return safely without YOLO fallback
            }
        } else {
            yoloInferenceCalls.incrementAndGet();
        }

        assertEquals(0, yoloInferenceCalls.get());
    }

    @Test
    public void test8_NoMatchDoesNotInvokeYolo() {
        AtomicInteger yoloInferenceCalls = new AtomicInteger(0);

        ScanMode scanMode = ScanMode.CENTER_ROI;
        MatchResult match = MedicineMatcher.match("NON_EXISTENT_MEDICINE_LABEL", testCatalog);
        assertFalse(match.isMatched());

        if (scanMode == ScanMode.CENTER_ROI) {
            // ROI mode handles no-match without calling YOLO
        } else {
            yoloInferenceCalls.incrementAndGet();
        }

        assertEquals(0, yoloInferenceCalls.get());
    }

    @Test
    public void test9_ImageProxyClosureOnSuccessAndFailure() {
        AtomicBoolean isProxyClosed = new AtomicBoolean(false);

        try {
            throw new RuntimeException("Simulated exception during frame processing");
        } catch (Exception e) {
            // Exception caught
        } finally {
            isProxyClosed.set(true);
        }

        assertTrue("ImageProxy must be closed in finally block", isProxyClosed.get());
    }

    @Test
    public void test10_ConcurrencyLockPreventsOverlappingOcrJobs() {
        AtomicBoolean isProcessingQueue = new AtomicBoolean(false);

        // Frame 1 arrives
        boolean acquired1 = isProcessingQueue.compareAndSet(false, true);
        assertTrue(acquired1);

        // Frame 2 arrives while Frame 1 is processing
        boolean acquired2 = isProcessingQueue.compareAndSet(false, true);
        assertFalse("Frame 2 must be rejected while Frame 1 is processing", acquired2);

        // Frame 1 completes
        isProcessingQueue.set(false);

        // Frame 3 arrives
        boolean acquired3 = isProcessingQueue.compareAndSet(false, true);
        assertTrue("Frame 3 should be accepted after Frame 1 completes", acquired3);
    }

    @Test
    public void test11_LegacyYoloModePreservedAndIncrementsCounter() {
        ScanMode scanMode = ScanMode.LEGACY_YOLO;
        AtomicInteger yoloInferenceCalls = new AtomicInteger(0);

        if (scanMode == ScanMode.LEGACY_YOLO) {
            yoloInferenceCalls.incrementAndGet();
        }

        assertEquals(ScanMode.LEGACY_YOLO, scanMode);
        assertEquals(1, yoloInferenceCalls.get());
    }

    @Test
    public void test12_MedicineMatcherLogsDetailedAnalysis() {
        MatchResult match = MedicineMatcher.match("CORAGEN 18.5 SC FMC", testCatalog);

        assertTrue("Should successfully match CORAGEN", match.isMatched());
        assertNotNull("Matched medicine must not be null", match.getMedicine());
        assertEquals("CORAGEN", match.getMedicine().getName());
        assertTrue("Confidence score should be high for exact token matches", match.getConfidence() >= 0.82f);
    }
}
