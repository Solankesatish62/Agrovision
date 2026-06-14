const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

/**
 * Automatically populates missing fields for new documents in the 'approved_medicines' collection.
 * Triggered when a new document is created (1st Gen).
 */
exports.autoPopulateMedicineSchema = functions.firestore
    .document("approved_medicines/{medicineId}")
    .onCreate(async (snapshot, context) => {
        const data = snapshot.data();
        const medicineId = context.params.medicineId;

        // Define the default schema as requested
        const defaults = {
            medicineName: medicineId,
            company: "",
            crop: [],
            disease: [],
            usage: "",
            marathiInfo: "",
            ocrKeywords: [],
            imageUrls: [],
            audioUrls: [],
            updatedAt: admin.firestore.FieldValue.serverTimestamp()
        };

        const updatePayload = {};
        let hasChanges = false;

        // Iterate through defaults and add to updatePayload only if the field is missing
        Object.keys(defaults).forEach((key) => {
            if (data[key] === undefined) {
                updatePayload[key] = defaults[key];
                hasChanges = true;
            }
        });

        // Special check: If medicineName exists but is empty/null, populate it with medicineId
        if (data.medicineName === undefined || data.medicineName === null || data.medicineName === "") {
            if (updatePayload.medicineName === undefined) {
                updatePayload.medicineName = medicineId;
                hasChanges = true;
            }
        }

        if (hasChanges) {
            console.log(`Auto-populating schema for medicine: ${medicineId}`);
            try {
                // Using merge: true to ensure we don't overwrite any fields
                await snapshot.ref.set(updatePayload, { merge: true });
            } catch (error) {
                console.error(`Error updating document ${medicineId}:`, error);
            }
        } else {
            console.log(`Document ${medicineId} already follows the schema.`);
        }

        return null;
    });
