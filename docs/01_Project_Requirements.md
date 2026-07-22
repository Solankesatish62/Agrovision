# Project Requirements Specification: AgroVision

**Project Name:** AgroVision  
**Domain:** AgroVision.shop  
**Status:** MVP (Minimum Viable Product)  

---

## 1. Project Overview
AgroVision is an AI-powered agriculture medicine recognition kiosk designed specifically for agriculture input shops. It leverages advanced computer vision to help shopkeepers instantly identify products. Unlike traditional mobile applications, AgroVision is a dedicated professional kiosk system installed inside the physical retail environment. 

When a medicine bottle is placed in front of the kiosk’s camera, the system automatically recognizes the product and provides a comprehensive explanation to the farmer in Marathi using a combination of high-quality images and audio narration.

## 2. Vision and Mission

### 2.1 Vision
AgroVision aims to become the definitive operating system for agriculture retail shops, creating a comprehensive ecosystem that connects kiosks, farmers, companies, and distributors through a unified AI-driven platform.

### 2.2 Mission
To reduce dependency on the shopkeeper's memory while improving trust, speed, and customer experience in agriculture retail through automated recognition and localized information delivery.

## 3. Problem Statement
The agriculture retail sector faces several critical challenges that AgroVision is designed to solve:

### 3.1 Shopkeeper Constraints
*   **Inventory Complexity:** Shops sell thousands of products, making it impossible for a single individual to remember every medicine, crop compatibility, disease target, dosage, active ingredient, and precaution.
*   **Knowledge Gap:** Many shops depend on helpers who often lack the deep expertise of an experienced shopkeeper.
*   **Operational Inefficiency:** Manual information retrieval leads to slow service and customer waiting times.

### 3.2 Farmer Challenges
*   **Language Barrier:** Most farmers cannot understand technical labels printed in English.
*   **Identification Issues:** Difficulty in identifying specific medicines or comparing similar products.
*   **Information Accessibility:** Farmers require visual and auditory explanations to truly understand product usage and benefits.

### 3.3 Consequences
*   Wrong medicine explanations and recommendations.
*   Loss of customer trust.
*   Potential for crop damage due to incorrect application.

## 4. Objectives
*   **Automated Recognition:** Eliminate manual searching by using YOLO-based object detection and OCR.
*   **Localized Communication:** Provide information in Marathi to ensure farmer comprehension.
*   **Multi-modal Interaction:** Use audio narration and image slides to cater to different literacy levels.
*   **Revenue Generation:** Provide an idle advertisement mode for medicine companies.
*   **Operational Transparency:** Enable shop monitoring and remote management via a dedicated dashboard.

## 5. User Personas

### 5.1 Primary Users
*   **Agriculture Shopkeepers:** Daily operators who use the kiosk to serve customers efficiently.

### 5.2 Secondary Users
*   **Farmers:** The end-consumers who view and listen to medicine information to make informed decisions.

### 5.3 Future Personas
*   **Medicine Companies:** Interested in promotion and market analytics.
*   **Distributors:** Users involved in the supply chain.
*   **Researchers:** Users analyzing agricultural trends and product efficacy.
*   **Administrators:** Centralized management of the AgroVision network.

## 6. Current Architecture Overview
The system is built on a distributed architecture combining edge intelligence with cloud management:

*   **Android Application:** The primary interface and recognition engine running on the kiosk.
*   **React Admin Dashboard:** Web-based management tool for shops and medicine data.
*   **Firebase Suite:** 
    *   **Firestore:** Real-time database for medicine metadata, shop records, and logs.
    *   **Storage:** Hosting for product images, audio files, and app updates.
    *   **Authentication:** Secure access for shopkeepers and admins.
    *   **Cloud Functions:** Backend logic for data processing and monitoring.
*   **Recognition Engine:** Combines YOLO Object Detection, Bounding Box Stability algorithms, and a Medicine Matching Engine.
*   **Advertisement Engine:** Manages transitions to promotional content when the kiosk is idle.
*   **OTA System:** Ensures all kiosks run the latest software versions.

## 7. Technology Stack
*   **Mobile/Kiosk:** Android (Java), CameraX, Android TV Box.
*   **AI/ML:** TensorFlow Lite (TFLite), YOLO (Object Detection), ML Kit OCR (Text Recognition).
*   **Web Dashboard:** React, JavaScript.
*   **Backend/Cloud:** Firebase (Firestore, Storage, Auth, Cloud Functions).
*   **IDE:** Android Studio.

## 8. Current Features
The following features are fully implemented and functional:

*   **Medicine Recognition Pipeline:**
    *   YOLO-based detection.
    *   OCR for label reading.
    *   Intelligent Medicine Matching against a cloud-synced database.
*   **Information Delivery:**
    *   Marathi-language product details.
    *   Automated audio narration.
    *   Supportive image slideshows.
*   **Business Tools:**
    *   Idle Advertisement Mode for promotions.
    *   Shop Monitoring and Registration.
    *   Medicine Management Dashboard for admins.
*   **Infrastructure:**
    *   Over-the-Air (OTA) application updates.
    *   Firebase backend integration for data and media.
    *   Tracking system for "Incomplete Medicines" (scanned but not matched).

## 9. Current Recognition Pipeline (Workflow)
The kiosk follows a strict sequential pipeline for recognition:

1.  **Camera Preview:** Continuous feed monitoring.
2.  **YOLO Detection:** Identifies the presence of a medicine bottle.
3.  **Bounding Box Stability:** Ensures the bottle is stationary before processing.
4.  **OCR (Optical Character Recognition):** Extracts text from the medicine label.
5.  **Medicine Matcher:** Compares OCR results and YOLO classes against the database.
6.  **Confidence Calculation:** Determines the reliability of the match.
7.  **Result Display:** Launches `ResultActivity` if confidence is high.
8.  **Farmer Interaction:** Marathi audio and image slides explain the medicine.
9.  **Return Home:** System resets to idle/ad mode after interaction.

## 10. Functional Requirements

### 10.1 Recognition & Interaction
*   The system shall detect medicine bottles using YOLO object detection.
*   The system shall extract textual information from labels using ML Kit OCR.
*   The system shall provide product details (Crop, Disease, Usage, Precautions) in Marathi.
*   The system shall play audio descriptions corresponding to the recognized medicine.
*   The system shall display a slideshow of images for visual aid.

### 10.2 Idle State
*   The system shall automatically switch to Advertisement Mode when no bottle is detected.
*   The system shall cycle through approved advertisements from the `advertisements` collection.

### 10.3 Management & Monitoring
*   The system shall allow for remote shop registration and monitoring.
*   The system shall log every scan in the `daily_scans` collection for analytics.
*   The system shall notify admins of unmatched medicines for manual entry.

## 11. Non-Functional Requirements

### 11.1 Reliability & Accuracy
*   **Trustworthiness:** Recognition must be highly reliable. Unknown medicine is better than wrong medicine; the system must never show incorrect information.
*   **Stability:** The camera preview must never freeze.
*   **Human-in-the-loop:** AI assists humans, but human verification is mandatory before any new medicine is added to the production database.

### 11.2 Performance
*   **Responsiveness:** The UI must remain responsive at all times.
*   **Latency:** Recognition speed is a priority for optimization.
*   **Connectivity:** Offline recognition is preferred where possible to ensure stability in rural areas.

### 11.3 Architecture
*   **Modularity:** The project must follow a modular design where each class has a single responsibility.
*   **Maintainability:** Code must be maintainable and follow established software engineering rules.

## 12. Business Model
The project sustains itself through multiple revenue streams:

*   **Kiosk Subscription:** Recurring fees from agriculture shops.
*   **Advertisement Revenue:** Charging companies to display ads during idle time.
*   **Promotions:** Targeted medicine company promotions.
*   **Future Streams:** Analytics, specialized Company Dashboards, Premium features, and a Marketplace.

## 13. Database Schema (Firestore)
The system utilizes the following primary collections:

*   **approved_medicines:** The master database of verified products.
    *   *Fields:* Name, Company, Keywords, Barcode Prefixes, Crop, Disease, Usage, Marathi Info, Media URLs, Timestamps.
*   **shops:** Information about registered retail outlets.
*   **kiosks:** Specific hardware identifiers and status.
*   **daily_scans:** Audit log of all recognition events.
*   **advertisements:** Media and metadata for promotional content.
*   **app_updates:** Metadata for the OTA update system.

## 14. Design Principles & Development Philosophy
*   **Safety First:** Never provide incorrect agricultural advice.
*   **Backward Compatibility:** Never break existing features or perform unnecessary refactoring.
*   **Incremental Progress:** Every new feature must preserve all existing functionality.
*   **Accuracy vs. Speed:** Performance improvements must never come at the cost of recognition accuracy.
*   **Documentation-Driven:** Major architectural changes must be documented before implementation.

## 15. Roadmap

### 15.1 Features Under Development
*   **Barcode Scanner:** Secondary recognition method for high-accuracy matching.
*   **Storage Browser:** Improved media management.
*   **AI-assisted Medicine Entry:** Streamlining the database growth.
*   **Automation:** Automatic image downloading and information collection.
*   **Optimization:** Continuous improvement of recognition speed and accuracy.

### 15.2 Long Term Vision (Ecosystem)
*   Development of a dedicated Farmer Mobile App.
*   Advanced Dashboards for Companies and Distributors.
*   Integration with Shop Inventory systems.
*   Voice Assistant for natural language queries.
*   Marketplace and Survey platforms.

## 16. Current Limitations
*   Recognition speed requires further optimization for a "seamless" feel.
*   The database, while growing, does not yet cover every available product.
*   Barcode integration is currently in the development phase.
*   Dependency on human verification limits the speed of database expansion (though necessary for trust).

## 17. Success Criteria
*   Zero tolerance for "Wrong Medicine" errors.
*   Measurable reduction in farmer wait time at participating shops.
*   High adoption rate among shopkeepers and their helpers.
*   Successful conversion of idle time into advertisement revenue.
*   Consistent system uptime and responsive UI performance.
