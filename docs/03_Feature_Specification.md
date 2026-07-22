# Feature Specification

This document provides a detailed specification of the current features and the roadmap for features currently under development in the AgroVision project.

---

## 1. Core Recognition Features

### 1.1 Medicine Identification
*   **YOLO Detection:** Real-time localization of medicine bottles using the YOLO object detection model.
*   **OCR Integration:** High-accuracy text extraction from product labels using ML Kit.
*   **Medicine Matching:** Intelligent comparison logic that maps detected classes and extracted text to the `approved_medicines` database.
*   **Marathi Information:** Delivery of comprehensive medicine details (usage, dosage, target disease) in the Marathi language.
*   **Audio Narration:** Automatic playback of Marathi audio descriptions for better farmer accessibility.
*   **Image Slideshow:** Visual representation of product usage and benefits through interactive image slides.

### 1.2 Idle Mode
*   **Advertisement Engine:** Automatically switches the kiosk into a promotional state when no activity is detected.
*   **Promotional Content:** Displays advertisements for agriculture medicine companies, creating a revenue stream.

---

## 2. Management & Administration

### 2.1 Dashboard Functionality
*   **Medicine Management:** A full suite of tools to add, edit, translate, and verify medicine data.
*   **Shop Registration:** Tools for onboarding new retail outlets into the AgroVision network.
*   **Network Monitoring:** Real-time visibility into kiosk status and scanning activity.
*   **Image/Audio Storage:** Centralized management of media assets linked to products.

### 2.2 Operational Tools
*   **OTA (Over-the-Air) Updates:** Automated system for pushing software improvements and bug fixes to all kiosks.
*   **Incomplete Medicine Tracking:** Automatic flagging of medicines that the system could not identify, allowing admins to prioritize database growth.
*   **Daily Scan Logs:** Detailed audit logs for every interaction at the kiosk.

---

## 3. Features Under Development

### 3.1 Recognition Accuracy & Speed
*   **Barcode Scanner:** Implementation of barcode-based identification to complement or lead the recognition process.
*   **Recognition Speed Improvement:** Algorithmic optimizations to reduce the time from bottle placement to result display.
*   **Performance Optimization:** General system-wide improvements to ensure fluid operation on target hardware.

### 3.2 Automation & Data Entry
*   **AI-assisted Medicine Entry:** Using AI to pre-populate medicine fields, reducing the manual workload for administrators.
*   **Automatic Image Downloader:** Tools to automatically fetch high-quality product imagery from the web.
*   **Automatic Information Collection:** Scrapers and integration tools for building the database more rapidly.

### 3.3 System Utilities
*   **Storage Browser:** An improved interface for managing and organizing media files in Firebase Storage.
*   **Barcode-first Recognition Pipeline:** A refined workflow that prioritizes barcode scanning for near-perfect accuracy when a barcode is visible.

---

## 4. User Interaction Specification
*   **Input:** Medicine bottle placed in camera FOV.
*   **Interaction:** Hands-free recognition and automated audio/visual explanation.
*   **Accessibility:** Focused on Marathi-speaking farmers who may have limited literacy or technical knowledge.
