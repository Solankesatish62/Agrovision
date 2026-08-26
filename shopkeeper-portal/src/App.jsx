import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import Login from './pages/Login/Login';
import Dashboard from './pages/Dashboard/Dashboard';
import Kiosks from './pages/Kiosks/Kiosks';
import MyShop from './pages/MyShop/MyShop';
import Advertisements from './pages/Ads/Advertisements';
import { AuthProvider, useAuth } from './context/AuthContext';

const ProtectedRoute = ({ children }) => {
  const { user, loading } = useAuth();

  if (loading) return (
    <div className="flex flex-col items-center justify-center h-screen bg-slate-50">
      <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin mb-4"></div>
      <p className="text-slate-400 font-bold text-sm uppercase tracking-widest animate-pulse">AgroVision</p>
    </div>
  );

  if (!user) return <Navigate to="/login" />;

  return children;
};

function App() {
  return (
    <AuthProvider>
      <Router>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/" element={
            <ProtectedRoute>
              <Dashboard />
            </ProtectedRoute>
          } />
          <Route path="/kiosks" element={
            <ProtectedRoute>
              <Kiosks />
            </ProtectedRoute>
          } />
          <Route path="/shop" element={
            <ProtectedRoute>
              <MyShop />
            </ProtectedRoute>
          } />
          <Route path="/ads" element={
            <ProtectedRoute>
              <Advertisements />
            </ProtectedRoute>
          } />
          {/* Phase 2 Fallbacks */}
          <Route path="*" element={<Navigate to="/" />} />
        </Routes>
      </Router>
    </AuthProvider>
  );
}

export default App;
