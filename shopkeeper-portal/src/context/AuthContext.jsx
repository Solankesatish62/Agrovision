import React, { createContext, useContext, useState, useEffect } from 'react';
import { auth, db } from '../services/firebase/config';
import { onAuthStateChanged, signOut } from 'firebase/auth';
import { collection, query, where, getDocs, doc, onSnapshot } from 'firebase/firestore';

const AuthContext = createContext();

export const useAuth = () => useContext(AuthContext);

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [shopkeeperData, setShopkeeperData] = useState(null);
  const [loading, setLoading] = useState(true);

  const logout = () => {
    localStorage.removeItem('shopkeeper_session');
    signOut(auth);
    setUser(null);
    setShopkeeperData(null);
  };

  const manualLogin = async (phoneNumber) => {
    try {
        const q = query(
          collection(db, 'shopkeepers'),
          where('phoneNumber', '==', phoneNumber)
        );
        const snap = await getDocs(q);
        if (!snap.empty) {
            const data = snap.docs[0].data();
            const id = snap.docs[0].id;
            const session = { ...data, id, loginTime: Date.now() };
            localStorage.setItem('shopkeeper_session', JSON.stringify(session));
            setShopkeeperData(session);
            setUser({ phoneNumber }); // Mock user object
            return true;
        }
        return false;
    } catch (e) {
        console.error("Manual login failed:", e);
        return false;
    }
  };

  useEffect(() => {
    // 1. Check LocalStorage for manual session immediately
    const savedSession = localStorage.getItem('shopkeeper_session');
    if (savedSession) {
        const data = JSON.parse(savedSession);
        setShopkeeperData(data);
        setUser({ phoneNumber: data.phoneNumber });
        setLoading(false);
        return; // Don't need firebase listener if manually logged in
    }

    // 2. Fallback to Firebase Auth listener
    const unsubscribe = onAuthStateChanged(auth, async (firebaseUser) => {
        if (firebaseUser) {
            const phoneNumber = firebaseUser.phoneNumber;
            try {
                const q = query(collection(db, 'shopkeepers'), where('phoneNumber', '==', phoneNumber));
                const querySnapshot = await getDocs(q);
                if (!querySnapshot.empty) {
                    const shopkeeperDoc = querySnapshot.docs[0];
                    setShopkeeperData({ ...shopkeeperDoc.data(), id: shopkeeperDoc.id });
                    setUser(firebaseUser);
                }
            } catch (error) { console.error(error); }
        }
        setLoading(false);
    });

    return () => unsubscribe();
  }, []);

  const value = {
    user,
    shopkeeperData,
    loading,
    logout,
    manualLogin
  };

  return (
    <AuthContext.Provider value={value}>
      {!loading && children}
    </AuthContext.Provider>
  );
};
