# Recognition Pipeline Specification

The recognition pipeline is the core technical component of AgroVision. It transforms a live camera feed into a recognized product with localized explanations.

---

## 1. Pipeline Stages

### 1.1 Camera Preview
*   **Technology:** Android CameraX API.
*   **Function:** Provides a continuous stream of image frames.
*   **Rule:** The preview must remain fluid and never freeze, even during heavy processing.

### 1.2 YOLO Object Detection
*   **Technology:** TensorFlow Lite (TFLite) running a YOLO model.
*   **Function:** Identifies the presence of a medicine bottle within the frame.
*   **Output:** Bounding box coordinates and object class.

### 1.3 Bounding Box Stability
*   **Function:** A logic layer that monitors the bounding box over several frames.
*   **Purpose:** Ensures the user has placed the bottle steadily in front of the camera before triggering the resource-intensive OCR and matching stages.

### 1.4 OCR (Optical Character Recognition)
*   **Technology:** ML Kit OCR.
*   **Function:** Extracts all visible text from the medicine label within the stabilized bounding box.

### 1.5 Medicine Matcher
*   **Function:** An intelligent engine that compares the extracted OCR text and YOLO class against the `approved_medicines` collection.
*   **Input:** Raw OCR text, keywords from the database.
*   **Mechanism:** String matching, keyword search, and fuzzy matching logic.

### 1.6 Confidence Calculation
*   **Function:** Assigns a numerical score to the match.
*   **Threshold:** A minimum confidence score is required to proceed.
*   **Safety:** If confidence is below the threshold, the system defaults to an "Unrecognized" state to prevent incorrect identification.

### 1.7 Result Presentation (`ResultActivity`)
*   **Components:**
    *   **Marathi Information:** Displaying text data.
    *   **Audio Narration:** Playing the corresponding `.mp3` file from Firebase Storage.
    *   **Image Slides:** Transitioning through product-related visuals.
*   **Purpose:** Explaining the medicine to the farmer in a culturally and linguistically appropriate manner.

### 1.8 Reset (Return Home)
*   **Function:** The system returns to the idle state/Advertisement Mode after a timeout or manual interaction, ready for the next scan.

---

## 2. Error Handling & Fallbacks

*   **Low Confidence:** Trigger "Unrecognized Medicine" screen and log the raw data to `daily_scans` for admin review.
*   **No Text Found:** If YOLO detects a bottle but OCR fails, prompt the user to adjust the bottle position.
*   **Offline State:** The pipeline should prefer local matching logic if the internet connection is unstable, falling back to the cloud for updates.

---

## 3. Future Improvements (Roadmap)
*   **Barcode-first Recognition:** Integrating a dedicated barcode scanner stage to improve accuracy.
*   **Speed Optimization:** Reducing the latency between Bounding Box Stability and Result Presentation.
*   **AI Data Acquisition:** Using the pipeline to help gather data for new, unverified medicines.
