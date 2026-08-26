import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { Sprout, Phone, ArrowRight } from 'lucide-react';

const Login = () => {
  const [phoneNumber, setPhoneNumber] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const { manualLogin } = useAuth();
  const navigate = useNavigate();

  const handleLogin = async (e) => {
    e.preventDefault();
    setError('');

    let formattedNumber = phoneNumber.trim();
    if (!formattedNumber.startsWith('+')) {
      formattedNumber = `+91${formattedNumber.replace(/\D/g, '')}`;
    }

    if (formattedNumber.length < 13) {
      setError('Please enter a valid 10-digit mobile number.');
      return;
    }

    setLoading(true);

    try {
      const success = await manualLogin(formattedNumber);
      if (success) {
        navigate('/');
      } else {
        setError('Number not registered. Please contact support.');
      }
    } catch (err) {
      console.error("LOGIN ERROR:", err);
      setError('Something went wrong. Please try again later.');
    }
    setLoading(false);
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-slate-50 px-4 py-12">
      <div className="max-w-md w-full bg-white rounded-3xl shadow-xl overflow-hidden border border-slate-100">
        <div className="bg-primary p-8 text-white text-center">
          <div className="w-16 h-16 bg-white/20 rounded-2xl flex items-center justify-center mx-auto mb-4 backdrop-blur-sm">
            <Sprout size={32} />
          </div>
          <h2 className="text-2xl font-bold">AgroVision</h2>
          <p className="text-primary-foreground/80 text-sm mt-1">Shopkeeper Portal</p>
        </div>

        <div className="p-8">
          <form onSubmit={handleLogin} className="space-y-6">
            <div className="text-center">
              <h3 className="text-xl font-semibold text-slate-800">Welcome Back</h3>
              <p className="text-slate-500 text-sm mt-1">Enter your registered mobile number</p>
            </div>

            <div>
              <label className="block text-sm font-medium text-slate-700 mb-2">Mobile Number</label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-slate-400">
                  <Phone size={18} />
                </div>
                <input
                  type="tel"
                  value={phoneNumber}
                  onChange={(e) => setPhoneNumber(e.target.value)}
                  placeholder="9876543210"
                  required
                  className="block w-full pl-11 pr-4 py-3 bg-slate-50 border border-slate-200 rounded-xl focus:ring-2 focus:ring-primary outline-none"
                />
              </div>
            </div>

            {error && (
              <div className="p-4 bg-red-50 border border-red-100 text-red-600 rounded-xl text-sm font-medium text-center">
                {error}
              </div>
            )}

            <button
              type="submit"
              disabled={loading}
              className="w-full bg-primary hover:bg-primary-dark text-white font-bold py-4 rounded-xl transition-all disabled:opacity-50 flex items-center justify-center gap-2"
            >
              {loading ? 'Logging in...' : 'Access Dashboard'}
              {!loading && <ArrowRight size={18} />}
            </button>

            <p className="text-[10px] text-slate-400 text-center uppercase tracking-widest font-bold">
               No OTP Required for Testing Phase
            </p>
          </form>
        </div>
      </div>
    </div>
  );
};

export default Login;
