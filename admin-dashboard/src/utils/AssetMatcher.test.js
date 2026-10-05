import { calculateMatchScore, searchAssetsByNameOrQuery } from './AssetMatcher';

describe('AssetMatcher - searchAssetsByNameOrQuery', () => {
    const mockStorageList = [
        { name: 'bio_r303_bottle.jpg', fullPath: 'medicine-images/bio_r303_bottle.jpg' },
        { name: 'bior303_pack.png', fullPath: 'medicine-images/bior303_pack.png' },
        { name: 'coragen_18.5_sc.jpg', fullPath: 'medicine-images/coragen_18.5_sc.jpg' },
        { name: 'cotton_thrips_pest.jpg', fullPath: 'medicine-images/cotton_thrips_pest.jpg' },
        { name: 'bayer_confidor.jpg', fullPath: 'medicine-images/bayer_confidor.jpg' }
    ];

    const mockMedicines = [
        {
            id: 'med_1',
            medicineName: 'Bio-R303+',
            imageUrls: ['https://firebasestorage.googleapis.com/v0/b/app/o/bio_r303.jpg']
        },
        {
            id: 'med_2',
            medicineName: 'Coragen',
            imageUrls: ['https://firebasestorage.googleapis.com/v0/b/app/o/coragen.jpg']
        }
    ];

    test('suggests images by medicineName when query is empty', () => {
        const results = searchAssetsByNameOrQuery({
            storageList: mockStorageList,
            medicineName: 'Bio-R303+',
            query: '',
            allMedicines: mockMedicines,
            isAudio: false
        });

        expect(results.length).toBeGreaterThan(0);
        // DB image or matching storage image should be top
        expect(results[0].name).toMatch(/Bio-R303|bio_r303/i);

        // Crucial requirement: Should NOT match pest or crop images like cotton_thrips_pest.jpg
        const pestMatch = results.find(r => r.name === 'cotton_thrips_pest.jpg');
        expect(pestMatch).toBeUndefined();
    });

    test('searches images accurately when query is typed by user', () => {
        const results = searchAssetsByNameOrQuery({
            storageList: mockStorageList,
            medicineName: 'Bio-R303+',
            query: 'coragen',
            allMedicines: mockMedicines,
            isAudio: false
        });

        expect(results.length).toBeGreaterThan(0);
        expect(results[0].name).toMatch(/coragen/i);
    });

    test('returns empty results when query is a full HTTP URL', () => {
        const results = searchAssetsByNameOrQuery({
            storageList: mockStorageList,
            medicineName: 'Bio-R303+',
            query: 'https://firebasestorage.googleapis.com/v0/b/app/o/some_image.jpg',
            allMedicines: mockMedicines,
            isAudio: false
        });

        expect(results).toEqual([]);
    });

    test('returns empty results when both medicineName and query are empty', () => {
        const results = searchAssetsByNameOrQuery({
            storageList: mockStorageList,
            medicineName: '',
            query: '',
            allMedicines: mockMedicines,
            isAudio: false
        });

        expect(results).toEqual([]);
    });
});

describe('AssetMatcher - calculateMatchScore', () => {
    test('scores exact and substring matches correctly', () => {
        const score = calculateMatchScore('bio_r303_bottle.jpg', ['bior303']);
        expect(score).toBeGreaterThan(50);
    });

    test('handles tokenized matching for multi-word queries', () => {
        const score = calculateMatchScore('bayer_confidor.jpg', ['bayer confidor']);
        expect(score).toBeGreaterThan(100);
    });
});
