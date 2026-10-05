package com.agrovision.kiosk.data.service;

import static org.junit.Assert.*;

import com.agrovision.kiosk.data.model.Medicine;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MedicineSearchServiceTest {

    private List<Medicine> catalog;

    @Before
    public void setUp() {
        catalog = new ArrayList<>();
        catalog.add(createMedicine("1", "CORAGEN", "FMC", "Chlorantraniliprole", Arrays.asList("cora", "insecticide")));
        catalog.add(createMedicine("2", "CORAGEN PLUS", "FMC", "Chlorantraniliprole + Mix", Collections.emptyList()));
        catalog.add(createMedicine("3", "CORAMIN", "Syngenta", "Chemical X", Collections.emptyList()));
        catalog.add(createMedicine("4", "Bango", "Crystal", "Fipronil", Collections.emptyList()));
    }

    private Medicine createMedicine(String id, String name, String company, String chemical, List<String> keywords) {
        return new Medicine(id, name, company, null, chemical, null, null, null, null, keywords, null, null, null, 0);
    }

    @Test
    public void testExactMatch() {
        List<Medicine> results = MedicineSearchService.search("CORAGEN", catalog);
        assertFalse(results.isEmpty());
        assertEquals("CORAGEN", results.get(0).getName());
    }

    @Test
    public void testPrefixMatch() {
        List<Medicine> results = MedicineSearchService.search("CORA", catalog);
        assertEquals(3, results.size());
        // CORAGEN should be first (exact or shorter prefix match often ranks higher)
        assertTrue(results.get(0).getName().startsWith("CORA"));
    }

    @Test
    public void testContainsMatch() {
        List<Medicine> results = MedicineSearchService.search("RAGEN", catalog);
        assertEquals(2, results.size());
        assertTrue(results.get(0).getName().contains("RAGEN"));
    }

    @Test
    public void testFuzzyMatch() {
        // Minor typo: CORAGAN instead of CORAGEN
        List<Medicine> results = MedicineSearchService.search("CORAGAN", catalog);
        assertFalse(results.isEmpty());
        assertEquals("CORAGEN", results.get(0).getName());
    }

    @Test
    public void testChemicalMatch() {
        List<Medicine> results = MedicineSearchService.search("Chloran", catalog);
        assertFalse(results.isEmpty());
        assertTrue(results.get(0).getChemicalName().contains("Chloran"));
    }

    @Test
    public void testCompanyMatch() {
        List<Medicine> results = MedicineSearchService.search("Syngenta", catalog);
        assertEquals(1, results.size());
        assertEquals("CORAMIN", results.get(0).getName());
    }

    @Test
    public void testKeywordMatch() {
        List<Medicine> results = MedicineSearchService.search("insecticide", catalog);
        assertFalse(results.isEmpty());
        assertEquals("CORAGEN", results.get(0).getName());
    }

    @Test
    public void testEmptyQuery() {
        List<Medicine> results = MedicineSearchService.search("", catalog);
        assertTrue(results.isEmpty());
    }
}
