# Implementation Plan - Phase 2: AI-Assisted Medicine Cataloging

This plan transforms the medicine addition flow into a production-ready, AI-assisted system that respects the existing AgroVision schema and prioritizes cost-effective database-first matching.

## Schema Discovery & Mapping

Based on the Admin Dashboard and Android Room model, the official schema is:

| Field Name | Type | Dashboard Label | Description |
| :--- | :--- | :--- | :--- |
| `name` | String | Medicine Name | Unique ID (usually Uppercase) |
| `company` | String | Manufacturing Company | Manufacturer |
| `cibNo` | String | CIB&RC Reg. Number | Registration Number |
| `chemicalName` | String | Chemical Composition | Active ingredients |
| `crop` | String | Target Crops | CSV/Text for Marathi crops |
| `disease` | String | Target Diseases | CSV/Text for Marathi pests |
| `marathiInfo` | String | Marathi Information | Detailed Marathi description (maps to `warnings` in Kiosk) |
| `usage` | String | Usage Instructions | Technical instructions in English |
| `searchKeywords` | Array | OCR Keywords | Variations for `MedicineMatcher.java` |
| `barcodePrefixes`| Array | Barcode/QR Prefixes | GS1/Specific identifiers |
| `imageUrls` | Array | Image URLs | Paths to Firebase Storage |
| `audioUrls` | Array | Audio URL | Narration from Sarvam AI |

---

## Proposed Changes

### 1. Backend: Cloud Functions (Firebase)

Objective: Centralize AI logic, security, and complex matching.

#### [index.js](file:///C:/Users/solan/AndroidStudioProjects/Agrovision/functions/index.js)
- **`identifyMedicine`**: Update to use `gemini-1.5-flash` with a stricter prompt for brand name extraction.
- **`generateMedicineInfo`**:
    - Implement the **Strict Prompting** as per requirements (No invention, "माहिती उपलब्ध नाही." fallback).
    - Map output directly to the schema fields listed above.
    - Implement **OCR Keyword Generation**: 30-50 high-quality variations (common substitutions like 1 for I, 0 for O, etc.).
- **`searchCentralMedicine` (NEW)**:
    - Moves search logic to the backend to handle massive datasets.
    - Implements layered matching:
        1. Exact Name/ID match.
        2. Alias/OCR Keyword `array-contains` match.
        3. (Optional) Fuzzy candidate matching on the top 10 results.
    - Returns `confidence`: `HIGH`, `MEDIUM`, or `POSSIBLE_MATCH`.
- **`generateMedicineAudio`**:
    - Integrate **Sarvam AI** for Marathi text-to-speech.
    - Generate a script following the paragraph requirement (25-30 seconds).
    - Store the generated `.mp3` in Firebase Storage and return the URL.

### 2. Frontend: Shopkeeper Portal (React)

Objective: Provide a seamless, tiered workflow.

#### [AddMedicineFlow.jsx](file:///C:/Users/solan/AndroidStudioProjects/Agrovision/shopkeeper-portal/src/pages/Medicines/AddMedicineFlow.jsx)
- **Central Database-First Integration**:
    - After identity extraction, call `searchCentralMedicine`.
    - If `HIGH_CONFIDENCE`: Show "Medicine already available" and an "Add To My Kiosk" button. **No AI generated.**
    - If `POSSIBLE_MATCH`: Show the existing medicine card and ask "Is this the same medicine?".
- **Job Recovery**:
    - Use `localStorage` or a Firebase listener to resume processing if the page refreshes.
- **Review Screen**:
    - Use exact field labels from the Admin Dashboard.
    - Allow editing of all generated fields before approval.
- **Audio Trigger**:
    - Trigger `generateMedicineAudio` only after the shopkeeper approves the textual data.

#### [medicineService.js](file:///C:/Users/solan/AndroidStudioProjects/Agrovision/shopkeeper-portal/src/services/medicine/medicineService.js)
- Refactor to route search requests through the new Cloud Function.
- Implement relationship-only addition: `shops/{shopId}/medicines/{medicineId}`.

---

## Verification Plan

### Automated Tests
- **Cloud Function Unit Tests**: Verify JSON output structure and fallback logic for AI generation.
- **Matching Algorithm Tests**: Test the search service with known variants (e.g., "ULALA", "ULALA 50", "UL4LA") against a test dataset.

### Manual Verification
1.  **Test 1 (Existing):** Upload "Fusion". Verify central match found, no Gemini call, added to shop.
2.  **Test 2 (New):** Upload a new product. Verify AI generation maps to correct fields (Marathi in Marathi fields, etc.).
3.  **Test 3 (Fuzzy):** Upload "B10 R303". Verify it suggests "Bio-R303" as a POSSIBLE_MATCH.
4.  **Test 4 (Security):** Attempt to call the Sarvam API function without a valid shopkeeper session. Verify rejection.
5.  **Test 5 (Kiosk):** Perform a scan on the Android Kiosk for a medicine added via the portal. Verify it matches and shows the correct Marathi info.

---

## Development Order

1.  **Step 1:** Define the exact JSON mapping in a technical note.
2.  **Step 2:** Update Cloud Functions (Search & Identity).
3.  **Step 3:** Implement UI logic for tiered matching (High/Possible/New).
4.  **Step 4:** Update Cloud Functions (Generation & OCR Keywords).
5.  **Step 5:** Implement Review & Edit UI.
6.  **Step 6:** Implement Sarvam Audio generation.
7.  **Step 7:** Security Rules & Cost Tracking.
