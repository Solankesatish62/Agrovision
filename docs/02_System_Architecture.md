# System Architecture Specification

This document details the high-level architecture of the AgroVision ecosystem, describing the interaction between its various components and the underlying technology stack.

---

## 1. High-Level Overview
AgroVision follows a hybrid architecture combining edge computing for real-time computer vision and a cloud-native backend for data management, monitoring, and updates.

### 1.1 The Ecosystem
*   **Edge (Kiosk):** Runs the Android application on Android TV Boxes, performing real-time object detection and OCR.
*   **Cloud (Firebase):** Serves as the central repository for medicine data, media assets, logs, and configuration.
*   **Management (Web):** A React-based dashboard for administrative tasks and network monitoring.

---

## 2. Component Interaction

### 2.1 Android Application (Kiosk)
The Android app is the primary interface. It interacts with the cloud in the following ways:
*   **Data Sync:** Fetches `approved_medicines` and `advertisements` from Firestore.
*   **Media Streaming:** Downloads or streams product images and audio from Firebase Storage.
*   **Telemetry:** Pushes scan results and heartbeat data to `daily_scans`.
*   **Update Listener:** Monitors `app_updates` for new software versions.

### 2.2 React Admin Dashboard
The dashboard provides a control plane for administrators:
*   **CRUD Operations:** Directly manages the Firestore collections for medicines and shops.
*   **Asset Management:** Uploads media files to Firebase Storage.
*   **Monitoring:** Visualizes data from the `daily_scans` collection to provide operational insights.

### 2.3 Firebase Backend
*   **Firestore:** Handles real-time synchronization of state across all kiosks.
*   **Storage:** Stores large files (Images, Audio, APKs) with CDN-like delivery.
*   **Cloud Functions:** (Optional/Planned) To handle complex triggers, such as notifying admins of unmatched medicines or generating automated reports.

---

## 3. The Recognition Engine Architecture
Located within the Android App, the engine is structured as a pipeline:
1.  **Input Layer:** CameraX for frame acquisition.
2.  **Detection Layer:** TFLite (YOLO) for object localization.
3.  **Refinement Layer:** Custom logic for Bounding Box Stability.
4.  **Extraction Layer:** ML Kit OCR for text recognition.
5.  **Matching Layer:** Local/Cloud search logic for identifying the product.
6.  **Presentation Layer:** ResultActivity for UI and Audio feedback.

---

## 4. Technology Stack Summary
*   **Platform:** Android (Java) targeting Android TV Box hardware.
*   **UI/UX:** React (Dashboard), Android XML Layouts (Kiosk).
*   **Database/Backend:** Firebase (Firestore, Storage, Auth, Functions).
*   **Artificial Intelligence:** 
    *   **Object Detection:** YOLO (Running via TFLite).
    *   **Text Recognition:** Google ML Kit OCR.
*   **Hardware Interface:** CameraX.
*   **Updates:** Custom OTA system via Firebase.

---

## 5. Deployment Strategy
Kiosks are deployed in agriculture retail shops. Each kiosk is registered to a specific `shop_id` and `kiosk_id` in the database, allowing for granular control and monitoring of the entire network from the central dashboard.
