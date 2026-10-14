'use client';

import { createContext, useContext, useEffect, useState, ReactNode } from 'react';
import { Profile } from '@/types';

interface AuthContextType {
  token: string | null;
  profile: Profile | null;
  setToken: (token: string | null) => void;
  setProfile: (profile: Profile | null) => void;
  logout: () => void;
  isAuthenticated: boolean;
}

const AuthContext = createContext<AuthContextType | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setTokenState] = useState<string | null>(null);
  const [profile, setProfileState] = useState<Profile | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    const storedToken = localStorage.getItem('sv_token');
    const storedProfile = localStorage.getItem('sv_profile');
    if (storedToken) setTokenState(storedToken);
    if (storedProfile) {
      try { setProfileState(JSON.parse(storedProfile)); } catch {}
    }
    setReady(true);
  }, []);

  const setToken = (t: string | null) => {
    setTokenState(t);
    if (t) localStorage.setItem('sv_token', t);
    else localStorage.removeItem('sv_token');
  };

  const setProfile = (p: Profile | null) => {
    setProfileState(p);
    if (p) localStorage.setItem('sv_profile', JSON.stringify(p));
    else localStorage.removeItem('sv_profile');
  };

  const logout = () => {
    setToken(null);
    setProfile(null);
    window.location.href = '/login';
  };

  return (
    <AuthContext.Provider value={{
      token, profile, setToken, setProfile, logout,
      isAuthenticated: !!token,
    }}>
      {/* Wait for localStorage so pages never redirect on an empty first render */}
      {ready ? children : null}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
