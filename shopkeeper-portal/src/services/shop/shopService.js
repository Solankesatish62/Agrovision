import { db } from '../firebase/config';
import { doc, getDoc } from 'firebase/firestore';

export const getShopById = async (shopId) => {
  try {
    const shopDoc = await getDoc(doc(db, 'shops', shopId));
    if (shopDoc.exists()) {
      return { id: shopDoc.id, ...shopDoc.data() };
    }
    return null;
  } catch (error) {
    console.error("Error fetching shop:", error);
    throw error;
  }
};
