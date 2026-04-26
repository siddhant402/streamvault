'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/lib/auth-context';
import { authApi } from '@/lib/api';

export default function LoginPage() {
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const { setToken } = useAuth();
  const router = useRouter();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      if (mode === 'register') await authApi.register(email, password);
      const res = await authApi.login(email, password);
      setToken(res.data.token);
      router.push('/profiles');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Invalid credentials');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{
      minHeight: '100vh',
      background: 'linear-gradient(135deg, #0a0a0a 0%, #1a0505 50%, #0a0a0a 100%)',
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'center',
      fontFamily: 'var(--font-body)',
      padding: '20px',
    }}>
      {/* Background grid */}
      <div style={{
        position: 'fixed', inset: 0, pointerEvents: 'none',
        backgroundImage: 'linear-gradient(rgba(255,255,255,0.02) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.02) 1px, transparent 1px)',
        backgroundSize: '80px 80px'
      }} />
      {/* Red glow */}
      <div style={{
        position: 'fixed', top: '30%', left: '50%', transform: 'translate(-50%,-50%)',
        width: '600px', height: '400px', borderRadius: '50%', pointerEvents: 'none',
        background: 'radial-gradient(ellipse, rgba(229,9,20,0.12) 0%, transparent 70%)'
      }} />

      {/* Logo */}
      <div style={{
        fontFamily: 'var(--font-display)',
        fontSize: '2rem',
        letterSpacing: '0.25em',
        color: 'white',
        marginBottom: '40px',
        position: 'relative',
      }}>
        STREAM<span style={{ color: '#e50914' }}>VAULT</span>
      </div>

      {/* Card */}
      <div style={{
        width: '100%',
        maxWidth: '420px',
        background: 'rgba(0,0,0,0.75)',
        borderRadius: '16px',
        padding: '48px 48px 40px',
        border: '1px solid rgba(255,255,255,0.06)',
        backdropFilter: 'blur(20px)',
        position: 'relative',
      }}>
        <h1 style={{
          color: 'white',
          fontSize: '2rem',
          fontWeight: 400,
          marginBottom: '8px',
          letterSpacing: '-0.02em',
        }}>
          {mode === 'login' ? 'Sign in' : 'Register'}
        </h1>
        <p style={{ color: 'rgba(255,255,255,0.35)', fontSize: '13px', marginBottom: '32px' }}>
          {mode === 'login' ? 'Access your personal vault' : 'Create your StreamVault account'}
        </p>

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
            <label style={{ color: 'rgba(255,255,255,0.4)', fontSize: '11px', letterSpacing: '0.12em', textTransform: 'uppercase' }}>
              Email
            </label>
            <input
              type="email" value={email} onChange={e => setEmail(e.target.value)}
              required placeholder="you@example.com" autoComplete="email"
              style={{
                background: 'rgba(255,255,255,0.07)',
                border: '1px solid rgba(255,255,255,0.1)',
                borderRadius: '8px', padding: '14px 16px',
                color: 'white', fontSize: '15px', outline: 'none',
                transition: 'border-color 0.2s',
              }}
              onFocus={e => (e.target.style.borderColor = 'rgba(229,9,20,0.6)')}
              onBlur={e => (e.target.style.borderColor = 'rgba(255,255,255,0.1)')}
            />
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
            <label style={{ color: 'rgba(255,255,255,0.4)', fontSize: '11px', letterSpacing: '0.12em', textTransform: 'uppercase' }}>
              Password
            </label>
            <input
              type="password" value={password} onChange={e => setPassword(e.target.value)}
              required placeholder="••••••••" autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
              style={{
                background: 'rgba(255,255,255,0.07)',
                border: '1px solid rgba(255,255,255,0.1)',
                borderRadius: '8px', padding: '14px 16px',
                color: 'white', fontSize: '15px', outline: 'none',
                transition: 'border-color 0.2s',
              }}
              onFocus={e => (e.target.style.borderColor = 'rgba(229,9,20,0.6)')}
              onBlur={e => (e.target.style.borderColor = 'rgba(255,255,255,0.1)')}
            />
          </div>

          {error && (
            <div style={{
              background: 'rgba(229,9,20,0.1)', border: '1px solid rgba(229,9,20,0.2)',
              borderRadius: '8px', padding: '12px 16px',
              color: 'rgba(255,120,120,0.9)', fontSize: '13px',
            }}>
              {error}
            </div>
          )}

          <button
            type="submit" disabled={loading}
            style={{
              background: '#e50914', color: 'white', border: 'none',
              borderRadius: '8px', padding: '15px',
              fontSize: '15px', fontWeight: 500, cursor: loading ? 'not-allowed' : 'pointer',
              opacity: loading ? 0.7 : 1, marginTop: '4px',
              transition: 'background 0.2s',
            }}
            onMouseEnter={e => !loading && ((e.target as HTMLElement).style.background = '#c8080f')}
            onMouseLeave={e => !loading && ((e.target as HTMLElement).style.background = '#e50914')}
          >
            {loading ? 'Please wait…' : mode === 'login' ? 'Sign in' : 'Create account'}
          </button>
        </form>

        <div style={{ display: 'flex', alignItems: 'center', gap: '12px', margin: '24px 0' }}>
          <div style={{ flex: 1, height: '1px', background: 'rgba(255,255,255,0.07)' }} />
          <span style={{ color: 'rgba(255,255,255,0.2)', fontSize: '12px' }}>or</span>
          <div style={{ flex: 1, height: '1px', background: 'rgba(255,255,255,0.07)' }} />
        </div>

        <button
          onClick={() => { setMode(m => m === 'login' ? 'register' : 'login'); setError(''); }}
          style={{
            width: '100%', background: 'transparent', border: '1px solid rgba(255,255,255,0.1)',
            borderRadius: '8px', padding: '13px',
            color: 'rgba(255,255,255,0.45)', fontSize: '14px', cursor: 'pointer',
            transition: 'all 0.2s',
          }}
          onMouseEnter={e => { (e.target as HTMLElement).style.borderColor = 'rgba(255,255,255,0.25)'; (e.target as HTMLElement).style.color = 'rgba(255,255,255,0.75)'; }}
          onMouseLeave={e => { (e.target as HTMLElement).style.borderColor = 'rgba(255,255,255,0.1)'; (e.target as HTMLElement).style.color = 'rgba(255,255,255,0.45)'; }}
        >
          {mode === 'login' ? "New to StreamVault? Register" : 'Already have an account? Sign in'}
        </button>
      </div>
    </div>
  );
}