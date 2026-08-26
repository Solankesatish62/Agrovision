import { db, storage } from '../firebase/config';
import {
  collection,
  query,
  where,
  getDocs,
  doc,
  setDoc,
  updateDoc,
  serverTimestamp,
  deleteDoc,
  orderBy,
  getDoc
} from 'firebase/firestore';
import { ref, uploadBytes, getDownloadURL, deleteObject } from 'firebase/storage';

export const getAdvertisements = async (shopId) => {
  if (!shopId) {
    console.error("DEBUG: getAdvertisements called without shopId");
    return [];
  }
  console.log("DEBUG: Fetching ads for shop:", shopId);
  try {
    const q = collection(db, `shops/${shopId}/advertisements`);
    const snap = await getDocs(q);
    console.log("DEBUG: Firestore returned", snap.size, "documents");

    return snap.docs
      .map(d => ({ id: d.id, ...d.data() }))
      .filter(ad => ad.deleted !== true);
  } catch (err) {
    console.error("DEBUG: Error fetching ads:", err);
    throw err;
  }
};

export const createAdvertisement = async (shopId, adData, imageFile) => {
  if (!shopId) throw new Error("Missing Shop ID");

  const adRef = doc(collection(db, `shops/${shopId}/advertisements`));
  const adId = adRef.id;

  let imageUrl = '';
  if (imageFile) {
    const fileExt = imageFile.name.split('.').pop();
    const storagePath = `Advertisement/${shopId}_${adId}.${fileExt}`;
    const storageRef = ref(storage, storagePath);
    const snapshot = await uploadBytes(storageRef, imageFile);
    imageUrl = await getDownloadURL(snapshot.ref);
  }

  const newAd = {
    adId,
    shopId,
    ...adData,
    imageUrl: adData.type === 'video' ? '' : imageUrl,
    videoUrl: adData.type === 'video' ? imageUrl : '',
    enabled: adData.enabled !== undefined ? adData.enabled : true,
    deleted: false,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp()
  };

  await setDoc(adRef, newAd);

  try {
    const shopRef = doc(db, 'shops', shopId);
    await updateDoc(shopRef, { configVersion: serverTimestamp() });
  } catch (e) { console.warn("Sync trigger failed", e); }

  return adId;
};

export const updateAdvertisement = async (shopId, adId, adData, imageFile) => {
  const adRef = doc(db, `shops/${shopId}/advertisements`, adId);

  let imageUrl = adData.imageUrl;
  if (imageFile) {
    const fileExt = imageFile.name.split('.').pop();
    const storagePath = `Advertisement/${shopId}_${adId}.${fileExt}`;
    const storageRef = ref(storage, storagePath);
    const snapshot = await uploadBytes(storageRef, imageFile);
    imageUrl = await getDownloadURL(snapshot.ref);
  }

  const updatePayload = {
    ...adData,
    updatedAt: serverTimestamp()
  };

  if (imageFile) {
    const fileExt = imageFile.name.split('.').pop();
    const storagePath = `Advertisement/${shopId}_${adId}.${fileExt}`;
    const storageRef = ref(storage, storagePath);
    const snapshot = await uploadBytes(storageRef, imageFile);
    const downloadUrl = await getDownloadURL(snapshot.ref);

    if (adData.type === 'video') {
      updatePayload.videoUrl = downloadUrl;
      updatePayload.imageUrl = '';
    } else {
      updatePayload.imageUrl = downloadUrl;
      updatePayload.videoUrl = '';
    }
  }

  await updateDoc(adRef, updatePayload);

  try {
    const shopRef = doc(db, 'shops', shopId);
    await updateDoc(shopRef, { configVersion: serverTimestamp() });
  } catch (e) {}
};

export const uploadShopLogo = async (shopId, file) => {
  if (!shopId || !file) return null;
  const fileExt = file.name.split('.').pop();
  const storagePath = `ShopLogos/${shopId}_logo.${fileExt}`;
  const storageRef = ref(storage, storagePath);
  const snapshot = await uploadBytes(storageRef, file);
  return await getDownloadURL(snapshot.ref);
};

export const updateShopProfile = async (shopId, profileData) => {
  const shopRef = doc(db, 'shops', shopId);
  await setDoc(shopRef, {
    ...profileData,
    updatedAt: serverTimestamp(),
    configVersion: serverTimestamp() // Trigger kiosk sync
  }, { merge: true });
};

export const updateShopBranding = async (shopId, brandingData, logoFile) => {
  let logoUrl = brandingData.shopBannerUrl;

  if (logoFile) {
    logoUrl = await uploadShopLogo(shopId, logoFile);
  }

  const shopRef = doc(db, 'shops', shopId);
  await setDoc(shopRef, {
    ...brandingData,
    shopBannerUrl: logoUrl,
    updatedAt: serverTimestamp(),
    configVersion: serverTimestamp() // Trigger kiosk sync
  }, { merge: true });

  return logoUrl;
};

export const deleteAdvertisement = async (shopId, adId) => {
  console.log("DEBUG: Attempting to delete ad:", adId, "for shop:", shopId);
  try {
    const adRef = doc(db, `shops/${shopId}/advertisements`, adId);

    // 1. Get the advertisement data first to find media URLs
    const snap = await getDoc(adRef);
    if (snap.exists()) {
      const data = snap.data();
      const mediaUrl = data.imageUrl || data.videoUrl;

      // 2. Delete the file from Firebase Storage if it exists
      if (mediaUrl && mediaUrl.includes('firebasestorage.googleapis.com')) {
        try {
          const storageRef = ref(storage, mediaUrl);
          await deleteObject(storageRef);
          console.log("DEBUG: Storage media deleted successfully");
        } catch (storageErr) {
          console.warn("DEBUG: Storage deletion skipped or failed (file might already be gone):", storageErr.message);
        }
      }
    }

    // 3. Perform HARD delete in Firestore to prevent garbage accumulation
    await deleteDoc(adRef);

    console.log("DEBUG: Advertisement document deleted successfully");

    // Trigger sync
    try {
      const shopRef = doc(db, 'shops', shopId);
      await updateDoc(shopRef, { configVersion: serverTimestamp() });
    } catch (e) {}

    return true;
  } catch (err) {
    console.error("DEBUG: Delete Error:", err);
    throw err;
  }
};

export const toggleAdStatus = async (shopId, adId, enabled) => {
  const adRef = doc(db, `shops/${shopId}/advertisements`, adId);
  await updateDoc(adRef, {
    enabled,
    updatedAt: serverTimestamp()
  });

  try {
    const shopRef = doc(db, 'shops', shopId);
    await updateDoc(shopRef, { configVersion: serverTimestamp() });
  } catch (e) {}
};
