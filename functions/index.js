const functions = require("firebase-functions");
const admin = require("firebase-admin");
const { GoogleGenerativeAI } = require("@google/generative-ai");

admin.initializeApp();

const GLOBAL_MEDICINES_COLLECTION = "approved_medicines";

/**
 * AI Medicine Identity Extraction
 */
exports.identifyMedicine = functions.runWith({ secrets: ["GEMINI_API_KEY"], timeoutSeconds: 60 }).https.onCall(async (data, context) => {
    if (!context.auth) throw new functions.https.HttpsError('unauthenticated', 'Unauthenticated');

    const { imageUrls } = data;
    if (!imageUrls || imageUrls.length === 0) throw new functions.https.HttpsError('invalid-argument', 'Images required');

    const apiKey = process.env.GEMINI_API_KEY;
    if (!apiKey) {
        console.error("[CRITICAL] GEMINI_API_KEY is not set in secrets.");
        return { name: null, error: "API Key Configuration Error." };
    }

    const modelName = "gemini-1.5-flash";

    try {
        console.log(`[DEBUG] Starting identification for ${imageUrls[0]}`);

        const response = await fetch(imageUrls[0]);
        if (!response.ok) throw new Error(`Failed to fetch image: ${response.statusText}`);

        const buffer = await response.arrayBuffer();
        console.log(`[DEBUG] Image fetched. Size: ${buffer.byteLength} bytes`);

        const genAI = new GoogleGenerativeAI(apiKey);
        const model = genAI.getGenerativeModel({ model: modelName });

        const prompt = `Look at this agricultural medicine bottle. Identify the brand name specifically.
        Exclude generic terms like "Bio", "Organic", "Insecticide", "Fungicide" unless they are part of the core brand name.
        Respond with ONLY the main brand name in CAPITAL letters.
        If you cannot find it, respond with 'UNKNOWN'.`;

        const result = await model.generateContent([
            { text: prompt },
            { inlineData: { data: Buffer.from(buffer).toString("base64"), mimeType: "image/jpeg" } }
        ]);

        const name = result.response.text().trim().toUpperCase().replace(/[^A-Z0-9\s-]/g, '');
        console.log(`[DEBUG] AI Response: ${name}`);

        return { name: (name.includes('UNKNOWN') || !name) ? null : name };
    } catch (error) {
        console.error(`[ERROR] Identity Extraction Failed: ${error.message}`);
        return { name: null, error: `AI processing failed: ${error.message}` };
    }
});

/**
 * Backend Search: Central Medicine Database
 */
exports.searchCentralMedicine = functions.https.onCall(async (data, context) => {
    if (!context.auth) throw new functions.https.HttpsError('unauthenticated', 'Unauthenticated');

    const { searchTerm } = data;
    if (!searchTerm) throw new functions.https.HttpsError('invalid-argument', 'Search term required');

    const db = admin.firestore();
    const name = searchTerm.trim();
    const upperName = name.toUpperCase();

    try {
        // 1. Exact ID Match
        let doc = await db.collection(GLOBAL_MEDICINES_COLLECTION).doc(upperName).get();
        if (doc.exists) {
            return { match: { id: doc.id, ...doc.data() }, confidence: "HIGH" };
        }

        // 2. Exact Name Field Match
        const nameSnap = await db.collection(GLOBAL_MEDICINES_COLLECTION)
            .where('name', '==', upperName)
            .limit(1)
            .get();

        if (!nameSnap.empty) {
            const matchDoc = nameSnap.docs[0];
            return { match: { id: matchDoc.id, ...matchDoc.data() }, confidence: "HIGH" };
        }

        // 3. OCR Keyword Match
        const keywordSnap = await db.collection(GLOBAL_MEDICINES_COLLECTION)
            .where('searchKeywords', 'array-contains', name.toLowerCase())
            .limit(1)
            .get();

        if (!keywordSnap.empty) {
            const matchDoc = keywordSnap.docs[0];
            return { match: { id: matchDoc.id, ...matchDoc.data() }, confidence: "MEDIUM" };
        }

        // 4. Fuzzy / Possible Match (Optional: Prefix search)
        const prefixSnap = await db.collection(GLOBAL_MEDICINES_COLLECTION)
            .where('name', '>=', upperName.substring(0, 3))
            .where('name', '<=', upperName.substring(0, 3) + '\uf8ff')
            .limit(5)
            .get();

        if (!prefixSnap.empty) {
            return {
                candidates: prefixSnap.docs.map(d => ({ id: d.id, ...d.data() })),
                confidence: "POSSIBLE_MATCH"
            };
        }

        return { confidence: "NOT_FOUND" };
    } catch (error) {
        console.error("Search Error:", error);
        throw new functions.https.HttpsError('internal', error.message);
    }
});

/**
 * AI Medicine Information Generation
 */
exports.generateMedicineInfo = functions.runWith({ secrets: ["GEMINI_API_KEY"], timeoutSeconds: 120 }).https.onCall(async (data, context) => {
    if (!context.auth) throw new functions.https.HttpsError('unauthenticated', 'Unauthenticated');

    const { medicineName, imageUrls } = data;
    if (!medicineName) throw new functions.https.HttpsError('invalid-argument', 'Medicine name is required.');

    const apiKey = process.env.GEMINI_API_KEY;
    if (!apiKey) {
        console.error("[CRITICAL] GEMINI_API_KEY is not set in secrets.");
        throw new functions.https.HttpsError('failed-precondition', "API Key Configuration Error.");
    }

    const modelName = "gemini-1.5-flash";

    try {
        console.log(`[DEBUG] Generating info for: ${medicineName}`);
        const genAI = new GoogleGenerativeAI(apiKey);
        const model = genAI.getGenerativeModel({ model: modelName });

        const prompt = `
            You are an agricultural medicine information assistant for Maharashtra, India.
            Provide accurate, legally compliant information about: ${medicineName}.

            SOURCES: Official CIB&RC data, manufacturer labels, ICAR guidelines only.
            FALLBACK: If reliable data is unavailable for any field, use "माहिती उपलब्ध नाही."
            LANGUAGE: Simple Marathi for farmers.

            OCR KEYWORDS: Generate 30-50 high-quality technical variations for OCR matching.
            Include common character substitutions (1 for I, 0 for O, 5 for S, 8 for B, etc.),
            common spelling errors, merged/split words, and brand+manufacturer combinations.

            Return ONLY a valid JSON object with these EXACT fields:
            {
                "name": "BRAND NAME IN CAPS",
                "company": "Manufacturer Name",
                "cibNo": "Registration Number or 'माहिती उपलब्ध नाही.'",
                "chemicalName": "Active ingredients + %",
                "crop": "Marathi list of supported crops",
                "disease": "Marathi list of pests/diseases",
                "marathiInfo": "Simple 1-paragraph summary for farmers in Marathi",
                "usage": "Detailed instructions in English (Dosage, Method)",
                "searchKeywords": ["variation1", "variation2", "..."]
            }
        `;

        const result = await model.generateContent(prompt);
        const text = result.response.text();
        console.log(`[DEBUG] Raw AI Response: ${text}`);

        // Robust JSON Extraction (handles markdown code blocks)
        let jsonStr = text;
        if (text.includes('```')) {
            const match = text.match(/```(?:json)?\s*([\s\S]*?)\s*```/);
            if (match) jsonStr = match[1];
        } else {
            const match = text.match(/\{[\s\S]*\}/);
            if (match) jsonStr = match[0];
        }

        let raw;
        try {
            raw = JSON.parse(jsonStr);
        } catch (parseErr) {
            console.error(`[ERROR] JSON Parse Failed. Raw text: ${text}`);
            throw new Error("AI returned invalid data format. Please try again.");
        }

        return {
            ...raw,
            name: raw.name || medicineName,
            imageUrls: imageUrls || [],
            audioUrls: "", // Generated in separate step
            status: "SHOP_SUBMITTED",
            updatedAt: admin.firestore.FieldValue.serverTimestamp()
        };
    } catch (error) {
        console.error(`Generation Failed: ${error.message}`);
        throw new functions.https.HttpsError('internal', "AI processing failed.");
    }
});

/**
 * AI Medicine Audio Generation (Sarvam AI)
 */
exports.generateMedicineAudio = functions.runWith({ secrets: ["SARVAM_API_KEY"] }).https.onCall(async (data, context) => {
    if (!context.auth) throw new functions.https.HttpsError('unauthenticated', 'Unauthenticated');

    const { medicineData } = data;
    if (!medicineData || !medicineData.marathiInfo) throw new functions.https.HttpsError('invalid-argument', 'Marathi Info required');

    // Sarvam AI Integration Logic
    // 1. Construct script from medicineData
    // 2. Call Sarvam API for TTS
    // 3. Upload to Firebase Storage
    // 4. Return URL

    return {
        audioUrl: "", // Logic to be implemented with API credentials
        script: `उत्पादनाचे नाव ${medicineData.name} आहे...`
    };
});

exports.autoPopulateMedicineSchema = functions.firestore
    .document("approved_medicines/{medicineId}")
    .onCreate(async (snapshot, context) => {
        await snapshot.ref.set({ updatedAt: admin.firestore.FieldValue.serverTimestamp() }, { merge: true });
        return null;
    });
