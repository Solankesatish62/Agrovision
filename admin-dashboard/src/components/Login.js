import React, { useState } from 'react';
import { auth } from '../firebase';
import { signInWithEmailAndPassword } from 'firebase/auth';
import { COLORS, SHADOWS } from './Shared/Styles';

const Login = () => {
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);

    const ALLOWED_ADMINS = [
        'solankesatish62@gmail.com', // Replace with your actual email
         // Replace with your employee's email
    ];

    const handleLogin = async (e) => {
        e.preventDefault();
        setError('');
        setLoading(true);
        try {
            const userCredential = await signInWithEmailAndPassword(auth, email, password);
            const user = userCredential.user;

            // Secondary Security Check: Is this email on our approved list?
            if (!ALLOWED_ADMINS.includes(user.email)) {
                await auth.signOut();
                setError('Access Denied: Your email is not authorized to access this panel.');
            }
        } catch (err) {
            setError('Invalid email or password. Please try again.');
            console.error(err);
        }
        setLoading(false);
    };

    return (
        <div style={{
            display: 'flex',
            justifyContent: 'center',
            alignItems: 'center',
            minHeight: '100vh',
            backgroundColor: COLORS.background,
            fontFamily: 'Inter, system-ui, sans-serif'
        }}>
            <div style={{
                width: '100%',
                maxWidth: '400px',
                padding: '40px',
                backgroundColor: COLORS.white,
                borderRadius: '24px',
                boxShadow: SHADOWS.lg,
                border: `1px solid ${COLORS.border}`
            }}>
                <div style={{ textAlign: 'center', marginBottom: '32px' }}>
                    <div style={{
                        width: '60px',
                        height: '60px',
                        background: `linear-gradient(135deg, ${COLORS.primary}, ${COLORS.primaryLight})`,
                        borderRadius: '16px',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        color: 'white',
                        margin: '0 auto 16px',
                        boxShadow: '0 8px 16px rgba(30, 58, 138, 0.2)'
                    }}>
                        <i className="fas fa-seedling" style={{ fontSize: '28px' }}></i>
                    </div>
                    <h2 style={{ margin: 0, color: COLORS.textMain, fontSize: '24px', fontWeight: '800' }}>Admin Login</h2>
                    <p style={{ margin: '8px 0 0', color: COLORS.textMuted, fontSize: '14px' }}>Secure access to AgroVision Management</p>
                </div>

                <form onSubmit={handleLogin} style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
                    <div>
                        <label style={{ display: 'block', marginBottom: '8px', fontSize: '14px', fontWeight: '600', color: COLORS.textMain }}>Email Address</label>
                        <input
                            type="email"
                            value={email}
                            onChange={(e) => setEmail(e.target.value)}
                            placeholder="admin@agrovision.com"
                            required
                            style={{
                                width: '100%',
                                padding: '12px 16px',
                                borderRadius: '12px',
                                border: `1.5px solid ${COLORS.border}`,
                                outline: 'none',
                                transition: 'border-color 0.2s',
                                fontSize: '15px',
                                boxSizing: 'border-box'
                            }}
                        />
                    </div>
                    <div>
                        <label style={{ display: 'block', marginBottom: '8px', fontSize: '14px', fontWeight: '600', color: COLORS.textMain }}>Password</label>
                        <input
                            type="password"
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            placeholder="••••••••"
                            required
                            style={{
                                width: '100%',
                                padding: '12px 16px',
                                borderRadius: '12px',
                                border: `1.5px solid ${COLORS.border}`,
                                outline: 'none',
                                transition: 'border-color 0.2s',
                                fontSize: '15px',
                                boxSizing: 'border-box'
                            }}
                        />
                    </div>

                    {error && (
                        <div style={{
                            padding: '12px',
                            backgroundColor: '#fef2f2',
                            color: COLORS.danger,
                            borderRadius: '10px',
                            fontSize: '13px',
                            fontWeight: '500',
                            textAlign: 'center',
                            border: '1px solid #fee2e2'
                        }}>
                            {error}
                        </div>
                    )}

                    <button
                        type="submit"
                        disabled={loading}
                        style={{
                            backgroundColor: COLORS.primary,
                            color: 'white',
                            padding: '14px',
                            borderRadius: '12px',
                            border: 'none',
                            fontSize: '16px',
                            fontWeight: '700',
                            cursor: loading ? 'not-allowed' : 'pointer',
                            transition: 'all 0.2s',
                            marginTop: '8px',
                            opacity: loading ? 0.7 : 1
                        }}
                    >
                        {loading ? 'Authenticating...' : 'Sign In'}
                    </button>
                </form>

                <p style={{ textAlign: 'center', marginTop: '32px', fontSize: '12px', color: COLORS.textMuted }}>
                    Authorized Personnel Only. <br/> Protected by Firebase Security.
                </p>
            </div>
        </div>
    );
};

export default Login;
