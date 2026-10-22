package com.agrovision.kiosk.vision.mapping;

import com.agrovision.kiosk.camera.ScanMetrics;
import com.agrovision.kiosk.data.model.Medicine;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class MedicineMatcherTest {

    private List<Medicine> catalog;

    @Before
    public void setUp() {
        catalog = Arrays.asList(
                new Medicine("1", "CORAGEN", "FMC", null, "Chlorantraniliprole", null, null, null, null, Arrays.asList("coragen", "insecticide", "fmc"), null, null, null, 1000L),
                new Medicine("2", "CONFIDOR", "Bayer", null, "Imidacloprid", null, null, null, null, Arrays.asList("confidor", "insecticide", "bayer"), null, null, null, 1000L),
                new Medicine("3", "FUSION", "Tata Rallis", null, "Syngenta Blend", null, null, null, null, Arrays.asList("fusion", "fungicide", "tata"), null, null, null, 1000L)
        );
    }

    @Test
    public void testPrepareIndexAndMatchPerformance() {
        MedicineMatcher.prepareIndex(catalog);

        ScanMetrics metrics = new ScanMetrics("test_scan_1", System.currentTimeMillis());
        MatchResult match = MedicineMatcher.match("CORAGEN 18.5 SC", catalog, metrics);

        assertTrue("Should match CORAGEN", match.isMatched());
        assertEquals("CORAGEN", match.getMedicine().getName());
        assertEquals("Index duration should be 0ms on cache hit", 0L, metrics.matchIndexMs);
    }

    @Test
    public void testCacheHitOnNewListInstanceWithSameContent() {
        MedicineMatcher.prepareIndex(catalog);

        // Create a new List instance containing copies of the same medicines (simulating Room/JSON re-load)
        List<Medicine> reloadedCatalog = new ArrayList<>(catalog);

        ScanMetrics metrics = new ScanMetrics("test_scan_2", System.currentTimeMillis());
        MatchResult match = MedicineMatcher.match("CONFIDOR", reloadedCatalog, metrics);

        assertTrue("Should match CONFIDOR", match.isMatched());
        assertEquals("CONFIDOR", match.getMedicine().getName());
        assertEquals("Index duration should be 0ms on fingerprint cache hit", 0L, metrics.matchIndexMs);
    }

    @Test
    public void testMatchingCorrectnessAndFusionMatch() {
        MedicineMatcher.prepareIndex(catalog);

        MatchResult match = MedicineMatcher.match("FUSION TATA RALLIS", catalog);
        assertTrue(match.isMatched());
        assertEquals("FUSION", match.getMedicine().getName());
    }

    @Test
    public void testUnknownMedicineReturnsNone() {
        MedicineMatcher.prepareIndex(catalog);

        MatchResult match = MedicineMatcher.match("XYZ_UNKNOWN_CHEMICAL_BOTTLE_999", catalog);
        assertFalse(match.isMatched());
        assertNull(match.getMedicine());
    }

    @Test
    public void testCatalogUpdateTriggersReindex() {
        MedicineMatcher.prepareIndex(catalog);

        List<Medicine> updatedCatalog = Arrays.asList(
                new Medicine("1", "CORAGEN", "FMC", null, "Chlorantraniliprole", null, null, null, null, Arrays.asList("coragen"), null, null, null, 1000L),
                new Medicine("2", "CONFIDOR", "Bayer", null, "Imidacloprid", null, null, null, null, Arrays.asList("confidor"), null, null, null, 1000L),
                new Medicine("3", "FUSION", "Tata Rallis", null, "Syngenta Blend", null, null, null, null, Arrays.asList("fusion"), null, null, null, 1000L),
                new Medicine("4", "AMISTAR", "Syngenta", null, "Azoxystrobin", null, null, null, null, Arrays.asList("amistar", "fungicide"), null, null, null, 2000L)
        );

        ScanMetrics metrics = new ScanMetrics("test_scan_3", System.currentTimeMillis());
        MatchResult match = MedicineMatcher.match("AMISTAR 250 SC", updatedCatalog, metrics);

        assertTrue("Should match AMISTAR from updated catalog", match.isMatched());
        assertEquals("AMISTAR", match.getMedicine().getName());
    }
}
