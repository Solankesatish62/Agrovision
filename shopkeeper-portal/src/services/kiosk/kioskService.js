import { db } from '../firebase/config';
import {
  collection,
  query,
  where,
  getDocs,
  doc,
  updateDoc,
  serverTimestamp
} from 'firebase/firestore';

export const getKiosksByShopId = async (shopId) => {
  const q = query(
    collection(db, 'kiosks'),
    where('shopId', '==', shopId)
  );
  const querySnapshot = await getDocs(q);
  return querySnapshot.docs.map(doc => ({
    id: doc.id,
    ...doc.data()
  }));
};

export const pairKiosk = async (kioskId, pairingCode, shopId) => {
  const q = query(
    collection(db, 'kiosks'),
    where('kioskId', '==', kioskId)
  );
  const querySnapshot = await getDocs(q);

  if (querySnapshot.empty) {
    throw new Error('Kiosk not found');
  }

  const kioskDoc = querySnapshot.docs[0];
  const kioskData = kioskDoc.data();

  if (kioskData.shopId) {
    throw new Error('Kiosk already paired');
  }

  if (kioskData.pairingCode !== pairingCode) {
    throw new Error('Invalid pairing code');
  }

  await updateDoc(doc(db, 'kiosks', kioskDoc.id), {
    shopId: shopId,
    linkedAt: serverTimestamp(),
    status: 'PAIRED',
    pairingCode: null // Invalidate after use
  });

  return true;
};
