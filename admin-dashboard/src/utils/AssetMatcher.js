const SYNONYMS = {
    'gn': ['groundnut', 'shenga', 'bhuimug', 'peanut', 'gnut'],
    'kp': ['kapus', 'cotton', 'kps'],
    'mb': ['mealybug', 'mealybugs', 'pithya', 'dhekun'],
    'weed': ['gavat', 'tan', 'herbicide', 'kharpatwar', 'tana', 'ghas'],
    'chilli': ['mirchi', 'chile', 'ch'],
    'soyabean': ['soybean', 'soya', 'sb'],
    'tomato': ['tamatar', 'vel', 'tm'],
    'thrips': ['fulkide', 'bokadya', 'thrips', 'th'],
    'mites': ['oli', 'tambera', 'mites', 'mt'],
    'aphids': ['mava', 'aphids', 'ap'],
    'jassids': ['tudtude', 'jassids', 'js'],
    'bollworm': ['bondali', 'bollworm', 'bw'],
    'whiteflies': ['pandhari', 'mashi', 'whitefly', 'whiteflies', 'wf'],
    'mealybugs': ['pithya', 'dhekun', 'mealybug', 'mealybugs', 'mb'],
    'fungicide': ['burshinashak', 'fungicide', 'fg'],
    'insecticide': ['kitaknashak', 'insecticide', 'is'],
    'bio': ['organic', 'natural', 'biostimulant'],
    'growth': ['vaadh', 'toner', 'promoter', 'gp', 'growth_promoter'],
    // Marathi to English Mappings (Bi-directional)
    'bhuimug': ['groundnut', 'gn'],
    'shenga': ['groundnut', 'gn'],
    'kapus': ['cotton', 'kp'],
    'mirchi': ['chilli', 'pepper', 'ch'],
    'tamatar': ['tomato', 'tm'],
    'gavat': ['weed', 'tan'],
    'tan': ['weed'],
    'fulkide': ['thrips', 'th'],
    'bokadya': ['thrips'],
    'tambera': ['mites'],
    'mava': ['aphids'],
    'tudtude': ['jassids'],
    'bondali': ['bollworm', 'bw'],
    'pandhari': ['whiteflies', 'wf'],
    'pithya': ['mealybugs', 'mb'],
    'burshi': ['fungus', 'fungicide'],
    'kitak': ['insect', 'insecticide'],
    'cotton': ['kapus', 'kp'],
    'groundnut': ['bhuimug', 'shenga', 'gn']
};

const STOP_WORDS = new Set([
    'in', 'on', 'of', 'and', 'the', 'for', 'with', 'at', 'by', 'is', 'a', 'an',
    'varti', 'sathi', 'ani', 'cha', 'chi', 'che', 'la', 'un', 'karun', 'karate',
    'yasarkhya', 'yansarkhya', 'samul', 'kadak', 'ekach', 'veli', 'nuksan', 'karnarya',
    'image', 'photo', 'jpg', 'png', 'jpeg', 'webp', 'mp3', 'wav', 'audio', 'file'
]);

/**
 * Normalizes a string by removing special characters and splitting into words/tokens.
 */
export const tokenize = (str) => {
    if (!str) return [];

    // Devanagari range: \u0900-\u097F
    const clean = str.replace(/[^\u0900-\u097Fa-zA-Z0-9\s]/g, ' ');
    // Split CamelCase
    const withSpaces = clean.replace(/([a-z])([A-Z])/g, '$1 $2');

    return withSpaces
        .toLowerCase()
        .split(/\s+/)
        .filter(word => word.length >= 2 && !STOP_WORDS.has(word));
};

/**
 * Calculates a match score for a file against a set of search terms.
 */
export const calculateMatchScore = (fileName, searchTerms, isMedicineName = false) => {
    if (!fileName || !searchTerms || searchTerms.length === 0) return 0;

    const fileTokens = tokenize(fileName);
    const cleanFileName = fileName.toLowerCase().replace(/[^a-z0-9]/g, '');
    let score = 0;
    let matchedTermsCount = 0;

    const allTermTokens = searchTerms.flatMap(t => tokenize(t));
    const uniqueTermTokens = [...new Set(allTermTokens)];

    // Check raw search terms for normalized alphanumeric matching
    searchTerms.forEach(term => {
        if (!term) return;
        const cleanTerm = term.toLowerCase().replace(/[^a-z0-9]/g, '');
        if (cleanTerm.length < 2) return;

        if (cleanFileName === cleanTerm) {
            score += 150;
            matchedTermsCount++;
        } else if (cleanFileName.startsWith(cleanTerm)) {
            score += 90;
            matchedTermsCount++;
        } else if (cleanFileName.includes(cleanTerm)) {
            score += 50;
            matchedTermsCount++;
        }
    });

    if (uniqueTermTokens.length > 0) {
        // 1. Check tokens for matches and synonyms
        uniqueTermTokens.forEach(tWord => {
            let wordMatched = false;

            // Exact token match
            if (fileTokens.includes(tWord)) {
                score += 50;
                wordMatched = true;
            } else {
                // Synonym match
                for (const [key, list] of Object.entries(SYNONYMS)) {
                    const group = [key, ...list];
                    if (group.includes(tWord)) {
                        if (group.some(member => fileTokens.includes(member))) {
                            score += 45;
                            wordMatched = true;
                            break;
                        }
                        if (fileTokens.some(fToken => group.some(member => fToken.startsWith(member) || member.startsWith(fToken)))) {
                            score += 25;
                            wordMatched = true;
                            break;
                        }
                    }
                }
            }

            // Partial containment match
            if (!wordMatched) {
                if (fileTokens.some(fToken => fToken.includes(tWord) || tWord.includes(fToken))) {
                    score += 15;
                    wordMatched = true;
                }
            }

            if (wordMatched) matchedTermsCount++;
        });
    }

    // Multi-term Bonus
    if (matchedTermsCount > 1) {
        score += (matchedTermsCount * 30);
    }

    // Penalty for Irrelevant Filename Content
    if (uniqueTermTokens.length > 0) {
        const unmatchedTokens = fileTokens.filter(fToken => {
            const isMatch = uniqueTermTokens.some(tWord => {
                if (fToken === tWord) return true;
                if (fToken.includes(tWord) || tWord.includes(fToken)) return true;
                for (const [key, list] of Object.entries(SYNONYMS)) {
                    const group = [key, ...list];
                    if (group.includes(tWord) && group.some(m => fToken.includes(m))) return true;
                }
                return false;
            });
            return !isMatch;
        });

        score -= (unmatchedTokens.length * 5);
    }

    // Bonus for primary product image filenames
    if (fileTokens.includes('bottle') || fileTokens.includes('pack') || fileTokens.includes('primary') || fileTokens.includes('product') || fileTokens.includes('front')) {
        score += 25;
    }

    if (isMedicineName && score > 0) {
        score *= 1.2;
    }

    return Math.max(0, score);
};

/**
 * Performs strong, targeted asset search for medicine images or audio files.
 * Does NOT search by crop, disease, or marathi info to prevent inaccurate suggestions.
 */
export const searchAssetsByNameOrQuery = ({
    storageList = [],
    medicineName = '',
    query = '',
    allMedicines = [],
    isAudio = false,
    limit = 20
}) => {
    const cleanQuery = (query || '').trim();
    const cleanMedName = (medicineName || '').trim();

    // If query is an HTTP URL, no suggestions needed
    if (cleanQuery.toLowerCase().startsWith('http')) {
        return [];
    }

    const searchTerms = [];
    const isExplicitSearch = cleanQuery.length > 0;

    if (isExplicitSearch) {
        searchTerms.push(cleanQuery);
    } else if (cleanMedName.length > 0) {
        searchTerms.push(cleanMedName);
    } else {
        // No query and no medicine name provided
        return [];
    }

    const matches = [];

    // 1. Cross-reference existing medicines in DB
    if (allMedicines && Array.isArray(allMedicines) && allMedicines.length > 0) {
        allMedicines.forEach(med => {
            const medName = med.name || med.medicineName || '';
            if (!medName) return;

            const urls = isAudio
                ? (med.audioUrls || med.audiourls ? [med.audioUrls || med.audiourls] : [])
                : (med.imageUrls || med.imageurls || []);

            if (!Array.isArray(urls)) return;

            urls.forEach((url, urlIdx) => {
                if (!url || typeof url !== 'string' || !url.startsWith('http')) return;

                // Score this medicine URL based on search terms against medicine name
                const score = calculateMatchScore(medName, searchTerms, true);
                if (score > 15) {
                    matches.push({
                        name: `${medName} (${isAudio ? 'Audio' : 'DB Image ' + (urlIdx + 1)})`,
                        fullPath: `db_${med.id || medName}_${urlIdx}`,
                        url: url,
                        score: score + 30, // Priority boost for verified DB image
                        isFromDb: true
                    });
                }
            });
        });
    }

    // 2. Search storage files
    if (storageList && Array.isArray(storageList) && storageList.length > 0) {
        storageList.forEach(file => {
            if (!file || !file.name) return;
            const score = calculateMatchScore(file.name, searchTerms, false);
            if (score > 10) {
                matches.push({
                    ...file,
                    score: score
                });
            }
        });
    }

    // Deduplicate by URL or fullPath or name
    const seen = new Set();
    const uniqueMatches = [];

    matches
        .sort((a, b) => b.score - a.score)
        .forEach(item => {
            const key = item.url || item.fullPath || item.name;
            if (!seen.has(key)) {
                seen.add(key);
                uniqueMatches.push(item);
            }
        });

    return uniqueMatches.slice(0, limit);
};

/**
 * Finds the best matches for a given set of medicine metadata.
 */
export const findBestMatches = (storageList, medicineData, limit = 15) => {
    const { name, crop, disease, marathiInfo } = medicineData;

    const marathiKeywords = marathiInfo ? extractKeywordsFromMarathi(marathiInfo) : [];
    const allSearchTerms = [name, crop, disease, ...marathiKeywords].filter(t => t && t.trim() !== '');

    if (allSearchTerms.length === 0) return storageList.slice(0, limit);

    return storageList
        .map(file => {
            const score = calculateMatchScore(file.name, allSearchTerms, true);
            return { ...file, score };
        })
        .filter(f => f.score > 20)
        .sort((a, b) => b.score - a.score)
        .slice(0, limit);
};

// Re-export findBestMatches as findBestImages for compatibility
export const findBestImages = (storageList, medicineData, allMedicines = [], limit = 15) => {
    return findBestMatches(storageList, medicineData, limit);
};

const extractKeywordsFromMarathi = (text) => {
    if (!text) return [];
    const tokens = tokenize(text);
    const agroTerms = Object.keys(SYNONYMS);

    return tokens.filter(t => agroTerms.includes(t) || agroTerms.some(key => SYNONYMS[key].includes(t)));
};
