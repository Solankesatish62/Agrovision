# AI Agent Guidelines

This document provides specific instructions for AI agents working on the AgroVision project. These guidelines ensure that AI contributions align with the project's professional standards and safety requirements.

---

## 1. Scope of Authority

### 1.1 Architectural Restraint
AI agents are prohibited from initiating large-scale architectural redesigns. If a structural improvement is identified, it must be proposed as a recommendation rather than implemented autonomously.

### 1.2 Preservation of Logic
Existing features and business logic must be treated as immutable unless a bug fix or feature enhancement is explicitly requested. The AI must ensure that new code integrates seamlessly without breaking existing functionality.

---

## 2. Decision Making Hierarchy

### 2.1 The "Accuracy First" Rule
When optimizing code, especially within the `Recognition Pipeline`, the AI must prioritize accuracy over performance. If a change might reduce the confidence of medicine matching, it must be discarded.

### 2.2 Safety and Trust
AI agents must adhere to the principle: **"Unknown medicine is better than wrong medicine."** Logic that attempts to "guess" a medicine with low confidence should be avoided in favor of a fallback "Unrecognized" state that triggers human verification.

---

## 3. Coding and Modification Standards

### 3.1 Surgical Edits
Prefer surgical edits using `replace_file_content` or `multi_replace_file_content` over rewriting entire files. This maintains the user's existing coding style and reduces the risk of side effects.

### 3.2 Responsibility Check
Before creating a new class, ensure it follows the **Single Responsibility Principle**. If a task can be handled by an existing module in a clean way, avoid creating redundant components.

### 3.3 Dependency Management
When adding new libraries to `build.gradle.kts`, check for compatibility with existing versions (e.g., CameraX, TFLite, and Firebase) and ensure they are compatible with the target Android TV Box environment.

---

## 4. Documentation and Context

### 4.1 Update Cycle
The AI must check if a change requires an update to the `docs/` folder. If a new feature is added, the corresponding `.md` file (e.g., `03_Feature_Specification.md`) must be updated to reflect the change.

### 4.2 Error Reporting
If the AI encounters a limitation in the current architecture that prevents the implementation of a request, it should report the limitation clearly rather than attempting a "hacky" workaround that violates design principles.

---

## 5. Development Philosophy for AI
*   **Assist, Don't Replace:** AI assists in code generation and data management, but it never bypasses the mandatory human verification for medicine data.
*   **Maintain Modularity:** Keep the codebase clean and modular.
*   **Respect the MVP:** Focus on the current goals (performance, accuracy, barcode integration) rather than speculative future features unless directed.
