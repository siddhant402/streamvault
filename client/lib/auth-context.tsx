'use client';

import { createContext, useContext, useEffect, useState, ReactNode } from 'react';
import { Profile } from '@/types';
import { authApi } from '@/lib/api';
import { goTo } from '@/lib/navigation';

interface AuthContextType {
  profile: Profile | null;
  setProfile: (profile: Profile | null) => void;
  /** Call after a successful sign-in; the server has already set the session cookie. */
  signIn: () => void;
  logout: () => Promise<void>;
  isAuthenticated: boolean;
}

const AuthContext = createContext<AuthContextType | null>(null);

/**
 * The login token lives in an HttpOnly cookie that page scripts cannot read, so whether we
 * are signed in is asked from the server (GET /api/auth/me) instead of read from storage.
 * Only the chosen profile, which is not secret, is kept in localStorage.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [signedIn, setSignedIn] = useState(false);
  const [profile, setProfileState] = useState<Profile | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    try { localStorage.removeItem('sv_token'); } catch {}   // token stored by older versions
    try {
      const storedProfile = localStorage.getItem('sv_profile');
      if (storedProfile) setProfileState(JSON.parse(storedProfile));
    } catch {}

    authApi.me()
      .then(() => setSignedIn(true))
      .catch(() => setSignedIn(false))
      .finally(() => setReady(true));
  }, []);

  const setProfile = (p: Profile | null) => {
    setProfileState(p);
    try {
      if (p) localStorage.setItem('sv_profile', JSON.stringify(p));
      else localStorage.removeItem('sv_profile');
    } catch {}
  };

  const signIn = () => {
    setProfile(null);   // a profile from a previous account must not carry over
    setSignedIn(true);
  };

  const logout = async () => {
    await authApi.logout().catch(() => {});
    setProfile(null);
    setSignedIn(false);
    goTo('/login');
  };

  return (
    <AuthContext.Provider value={{
      profile, setProfile, signIn, logout,
      isAuthenticated: signedIn,
    }}>
      {/* Wait for the sign-in check so pages never redirect on an empty first render */}
      {ready ? children : null}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
