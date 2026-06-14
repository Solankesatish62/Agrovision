import { initializeApp } from "firebase/app";
import { getFirestore } from "firebase/firestore";
import { getAnalytics } from "firebase/analytics";

const firebaseConfig = {
  apiKey: "AIzaSyDTVCJOemGZCF42bYti9yX4bR8uFGvyQw0",
  authDomain: "agrovision-bbafb.firebaseapp.com",
  projectId: "agrovision-bbafb",
  storageBucket: "agrovision-bbafb.firebasestorage.app",
  messagingSenderId: "879191571050",
  appId: "1:879191571050:web:2da8c5eb5b2933ea2c2813",
  measurementId: "G-FF4Y0F3NGE"
};

const app = initializeApp(firebaseConfig);
export const db = getFirestore(app);
const analytics = getAnalytics(app);
