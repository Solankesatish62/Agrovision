import { db } from '../services/firebase/config';
import { collection, getDocs, setDoc, doc, deleteDoc } from 'firebase/firestore';

export const migrateShopkeepers = async () => {
  try {
    const querySnapshot = await getDocs(collection(db, 'shopkeepers'));
    for (const d of querySnapshot.docs) {
      const data = d.data();
      const phone = data.phoneNumber;
      if (phone && d.id !== phone) {
        console.log(`Migrating shopkeeper ${d.id} to ${phone}`);
        await setDoc(doc(db, 'shopkeepers', phone), data);
        await deleteDoc(doc(db, 'shopkeepers', d.id));
      }
    }
    console.log("Migration complete");
  } catch (error) {
    console.error("Migration failed:", error);
  }
};
