# Database Schema Specification

AgroVision utilizes Google Firebase Firestore as its primary database. The schema is designed to support high-speed recognition, localized content delivery, and remote monitoring.

---

## 1. Firestore Collections Overview

| Collection Name | Description |
| :--- | :--- |
| `approved_medicines` | Master database of verified agriculture medicines. |
| `shops` | Registration and metadata for retail outlets. |
| `kiosks` | Hardware-specific data and status monitoring. |
| `daily_scans` | Logs of all medicine recognition events. |
| `advertisements` | Promotional content for idle mode. |
| `app_updates` | Version control and OTA update metadata. |

---

## 2. Detailed Collection Structures

### 2.1 `approved_medicines`
This collection is the core of the recognition engine.

| Field Name | Type | Description |
| :--- | :--- | :--- |
| `medicine_name` | String | Commercial name of the product. |
| `company` | String | Manufacturing company. |
| `search_keywords` | Array<String> | Keywords used by the Matching Engine. |
| `barcode_prefixes` | Array<String> | Used for barcode-first recognition. |
| `crop` | String/Array | Targeted crops. |
| `disease` | String/Array | Targeted diseases/pests. |
| `usage` | String | Application instructions. |
| `marathi_info` | Map/String | Localized content for display and audio. |
| `image_urls` | Array<String> | URLs to product images in Firebase Storage. |
| `audio_url` | String | URL to the Marathi audio narration file. |
| `created_time` | Timestamp | Record creation date. |
| `updated_time` | Timestamp | Last modification date. |

### 2.2 `daily_scans`
Used for analytics and identifying "Incomplete Medicines" (unmatched scans).

| Field Name | Type | Description |
| :--- | :--- | :--- |
| `kiosk_id` | String | Reference to the scanning hardware. |
| `shop_id` | String | Reference to the retail shop. |
| `timestamp` | Timestamp | Time of scan. |
| `ocr_text_raw` | String | Raw text extracted by ML Kit. |
| `matched_id` | String | ID of the medicine in `approved_medicines` (if found). |
| `confidence` | Float | Recognition confidence score. |
| `status` | String | `success`, `failed`, or `pending_verification`. |

### 2.3 `advertisements`
Controls the content shown when the kiosk is idle.

| Field Name | Type | Description |
| :--- | :--- | :--- |
| `ad_id` | String | Unique identifier. |
| `media_url` | String | URL to the image or video file. |
| `company_id` | String | Reference to the promoting company. |
| `start_date` | Timestamp | Campaign start. |
| `end_date` | Timestamp | Campaign end. |
| `active` | Boolean | Status flag. |

---

## 3. Data Integrity Rules

1.  **Human Verification Required:** No record in `approved_medicines` should be considered final without manual verification by an administrator.
2.  **Redundancy:** `search_keywords` must be populated to include common misspellings or OCR variations to improve matching reliability.
3.  **Audit Trail:** Every change to the medicine database must update the `updated_time` field.
