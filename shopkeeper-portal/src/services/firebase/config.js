import { initializeApp } from "firebase/app";
import { getFirestore } from "firebase/firestore";
import { getAuth } from "firebase/auth";
import { getStorage } from "firebase/storage";

const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY || "AIzaSyDTVCJOemGZCF42bYti9yX4bR8uFGvyQw0",
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN || "agrovision-bbafb.firebaseapp.com",
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID || "agrovision-bbafb",
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET || "agrovision-bbafb.firebasestorage.app",
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID || "879191571050",
  appId: import.meta.env.VITE_FIREBASE_APP_ID || "1:879191571050:web:2da8c5eb5b2933ea2c2813",
  measurementId: import.meta.env.VITE_FIREBASE_MEASUREMENT_ID || "G-FF4Y0F3NGE"
};

const app = initializeApp(firebaseConfig);
export const db = getFirestore(app);
export const auth = getAuth(app);
export const storage = getStorage(app);

export default app;
