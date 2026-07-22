# Coding Rules and Engineering Standards

This document defines the mandatory software engineering rules and design principles for the AgroVision project. All developers and AI agents must adhere to these standards to ensure project stability, maintainability, and reliability.

---

## 1. Core Engineering Principles

### 1.1 Professionalism
AgroVision is a production-grade professional kiosk system. All code must follow industry-standard professional software engineering practices.

### 1.2 Stability and Continuity
*   **No Redesigns:** Never redesign the system architecture unless explicitly requested.
*   **Feature Preservation:** Never remove existing features. Every new feature must preserve all existing functionality.
*   **Backward Compatibility:** Never break backward compatibility. Existing kiosk deployments must remain functional after updates.
*   **Incremental Change:** Changes should be surgical and targeted.

### 1.3 Documentation First
Documentation must be updated *before* any major architectural changes are implemented. The code and documentation must remain in sync.

---

## 2. Architectural Rules

### 2.1 Single Responsibility Principle (SRP)
Each class must have a single, clearly defined responsibility. Avoid "God objects" or bloated activities/classes.

### 2.2 Modularity
The architecture must remain modular. Components (Recognition, UI, Database, Advertising) should be decoupled enough to allow independent updates or testing.

### 2.3 Reliability over Speed
Performance improvements are encouraged, but they must **never** reduce recognition accuracy. In the context of agriculture medicine, a fast wrong answer is significantly worse than a slow correct one.

---

## 3. Implementation Rules

### 3.1 Refactoring Policy
Never perform unnecessary refactoring. Refactoring should only be done if it directly supports a new feature or fixes a critical bug, and it must be done with extreme care to avoid regressions.

### 3.2 Error Handling and Fail-safes
*   **Unknown over Wrong:** It is better to show "Medicine Unknown" than to show the "Wrong Medicine". Incorrect identification can lead to crop loss and loss of trust.
*   **UI Responsiveness:** The UI must always remain responsive. Long-running tasks (Recognition, Network) must be performed off the main thread.
*   **Camera Stability:** The camera preview must never freeze. Robust error handling for `CameraX` is mandatory.

---

## 4. AI and Data Integrity

### 4.1 Human Verification
AI never replaces human verification. In the AgroVision ecosystem, AI assists humans, but a human must verify medicine data before it is marked as `approved` in the database.

### 4.2 Data Acquisition
When building tools for automatic database acquisition or image downloading, ensure that the data integrity is maintained and matches the schema defined in `06_Database_Schema.md`.

---

## 5. Summary of Prohibitions

1.  **DO NOT** modify core recognition logic without exhaustive testing.
2.  **DO NOT** change Firestore field names or structures without a migration plan.
3.  **DO NOT** introduce dependencies that break the Android TV Box compatibility.
4.  **DO NOT** remove logging or monitoring code; these are essential for the MVP stage.
