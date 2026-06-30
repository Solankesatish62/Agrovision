const GEMINI_API_KEY = "AIzaSyDx-6mA1AQRCPl_rgxQ3OwPfTLQ_A2s5zg";
const GEMINI_URL = `https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=${GEMINI_API_KEY}`;

export const fetchMedicineAiInfo = async (productName, companyName, cropName, activeIngredient, formulation, imageBase64 = null) => {
  let userContent = [];
  const systemPrompt = `Return ONLY a JSON object with keys:
  {
    "extracted_name": "Name", "extracted_company": "Company",
    "extracted_ingredient": "Ingredient", "extracted_formulation": "Form",
    "working_mr": "Marathi info", "problem_mr": "Pests",
    "crops_mr": "Crops", "dosage_mr": "Dosage",
    "ocr_keywords": ["kw1", "kw2"],
    "search_query_product": "query1",
    "search_query_problem": "query2",
    "search_query_solution": "query3"
  }`;

  let promptText = systemPrompt;
  if (imageBase64) {
    promptText += `\n\nAnalyze image. Crop hint: ${cropName || 'General'}`;
  } else {
    promptText += `\n\nDetails: ${productName}, ${companyName}, ${activeIngredient}, ${formulation}`;
  }

  userContent.push({ text: promptText });
  if (imageBase64) {
    userContent.push({ inline_data: { mime_type: "image/jpeg", data: imageBase64 } });
  }

  try {
    const response = await fetch(GEMINI_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ contents: [{ parts: userContent }] })
    });

    const data = await response.json();

    if (data.error) {
        throw new Error(`${data.error.message} (Status: ${data.error.status})`);
    }

    let resultText = data.candidates[0].content.parts[0].text;
    resultText = resultText.replace(/```json/g, '').replace(/```/g, '').trim();
    return JSON.parse(resultText);
  } catch (error) {
    console.error("Gemini Error:", error);
    throw error;
  }
};
