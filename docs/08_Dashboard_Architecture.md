# Dashboard Architecture Specification

The AgroVision Admin Dashboard is the centralized management hub for the entire kiosk network. It allows administrators to manage product data, monitor shop performance, and oversee the advertisement network.

---

## 1. Technology Stack
*   **Frontend Framework:** React.
*   **Language:** JavaScript.
*   **State Management:** Standard React hooks/context (as per existing implementation).
*   **Backend Integration:** Firebase JavaScript SDK.
    *   **Authentication:** Firebase Auth for secure admin login.
    *   **Database:** Firestore for real-time data management.
    *   **Storage:** Firebase Storage for uploading medicine images and audio files.

---

## 2. Core Functional Modules

### 2.1 Medicine Management Dashboard
*   **Function:** Interface for adding, editing, and approving agriculture medicines.
*   **Key Tasks:** 
    *   Entry of product details (Name, Company, Crops, Diseases).
    *   Localized content entry (Marathi translation).
    *   Media association (Image and Audio uploads).
    *   Keyword optimization for the Matching Engine.

### 2.2 Shop & Kiosk Monitoring
*   **Function:** Real-time visibility into the kiosk network.
*   **Key Tasks:**
    *   Shop registration and kiosk assignment.
    *   Monitoring "Heartbeat" or status of active kiosks.
    *   Reviewing daily scan logs.

### 2.3 Advertisement Management
*   **Function:** Control center for the Advertisement Engine.
*   **Key Tasks:**
    *   Uploading promotional media.
    *   Scheduling ad campaigns.
    *   Assigning ads to specific shops or regions.

### 2.4 OTA Management
*   **Function:** Deployment of software updates.
*   **Key Tasks:**
    *   Uploading new Android APKs to Firebase Storage.
    *   Updating `app_updates` metadata to trigger kiosk updates.

---

## 3. Data Interaction & Workflows

### 3.1 Medicine Approval Workflow
1.  **Discovery:** Unmatched medicines from `daily_scans` are flagged.
2.  **Entry:** Admin enters technical details via the Dashboard.
3.  **Localization:** Marathi information and audio are added.
4.  **Verification:** Admin checks all fields for accuracy.
5.  **Activation:** The `approved` flag is set to true, making the medicine available to all kiosks instantly via Firestore sync.

### 3.2 Monitoring Workflow
*   The dashboard fetches data from the `daily_scans` collection to display charts and tables of activity.
*   Kiosks are grouped by `shop_id` for organized monitoring.

---

## 4. Development Principles
*   **Responsiveness:** The dashboard must be usable on both desktop and mobile browsers for on-the-go management.
*   **Security:** Access is restricted to authorized personnel via Firebase Authentication roles.
*   **Efficiency:** Implement pagination and efficient queries to handle the growing volume of scan logs.
