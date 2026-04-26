'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/lib/auth-context';
import { profilesApi } from '@/lib/api';
import { Profile } from '@/types';

const AVATAR_CONFIGS = [
  { bg: 'linear-gradient(135deg, #c8080f 0%, #ff6b35 100%)', glow: 'rgba(229,9,20,0.5)' },
  { bg: 'linear-gradient(135deg, #6a11cb 0%, #9b59f5 100%)', glow: 'rgba(106,17,203,0.5)' },
  { bg: 'linear-gradient(135deg, #f7971e 0%, #ffd200 100%)', glow: 'rgba(247,151,30,0.5)' },
  { bg: 'linear-gradient(135deg, #11998e 0%, #38ef7d 100%)', glow: 'rgba(17,153,142,0.5)' },
  { bg: 'linear-gradient(135deg, #1a78c2 0%, #00c6ff 100%)', glow: 'rgba(26,120,194,0.5)' },
];

export default function ProfilesPage() {
  const [profiles, setProfiles] = useState<Profile[]>([]);
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);
  const [newName, setNewName] = useState('');
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [fadeOut, setFadeOut] = useState(false);
  const { setProfile, logout, isAuthenticated } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (!isAuthenticated) { router.replace('/login'); return; }
    profilesApi.list().then(r => setProfiles(r.data)).finally(() => setLoading(false));
  }, [isAuthenticated]);

  const selectProfile = (p: Profile) => {
    setSelectedId(p.id);
    setTimeout(() => setFadeOut(true), 80);
    setTimeout(() => {
      setProfile(p);
      router.push('/home');
    }, 600);
  };

  const createProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newName.trim()) return;
    const res = await profilesApi.create(newName.trim());
    setProfiles(prev => [...prev, res.data]);
    setNewName('');
    setCreating(false);
  };

  if (loading) return (
    <div style={{ minHeight: '100vh', background: '#0a0a0a', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <div style={{ width: 40, height: 40, borderRadius: '50%', border: '2px solid rgba(255,255,255,0.1)', borderTop: '2px solid #e50914', animation: 'spin 0.8s linear infinite' }} />
      <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
    </div>
  );

  return (
    <div style={{
      minHeight: '100vh',
      background: 'radial-gradient(ellipse at 50% 40%, #1a0505 0%, #0a0a0a 60%)',
      display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
      fontFamily: 'var(--font-body)',
      transition: 'opacity 0.5s ease, transform 0.5s ease',
      opacity: fadeOut ? 0 : 1,
      transform: fadeOut ? 'scale(1.04)' : 'scale(1)',
    }}>
      {/* Grid texture */}
      <div style={{
        position: 'fixed', inset: 0, pointerEvents: 'none',
        backgroundImage: 'linear-gradient(rgba(255,255,255,0.015) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.015) 1px, transparent 1px)',
        backgroundSize: '80px 80px',
      }} />

      {/* Logo */}
      <div style={{ fontFamily: 'var(--font-display)', fontSize: '1.6rem', letterSpacing: '0.2em', color: 'white', marginBottom: '48px', position: 'relative' }}>
        STREAM<span style={{ color: '#e50914' }}>VAULT</span>
      </div>

      {/* Title */}
      <h1 style={{ color: 'white', fontSize: '2.4rem', fontWeight: 300, letterSpacing: '-0.01em', marginBottom: '48px', textAlign: 'center' }}>
        Who's watching?
      </h1>

      {/* Profile grid */}
      <div style={{ display: 'flex', gap: '24px', flexWrap: 'wrap', justifyContent: 'center', maxWidth: '800px' }}>
        {profiles.map((p, i) => {
          const cfg = AVATAR_CONFIGS[i % AVATAR_CONFIGS.length];
          const isSelected = selectedId === p.id;
          return (
            <button key={p.id} onClick={() => selectProfile(p)}
              style={{
                display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '14px',
                background: 'none', border: 'none', cursor: 'pointer', padding: '0',
                transition: 'transform 0.2s ease',
              }}
              onMouseEnter={e => (e.currentTarget.style.transform = 'scale(1.06)')}
              onMouseLeave={e => (e.currentTarget.style.transform = isSelected ? 'scale(1.06)' : 'scale(1)')}
            >
              {/* Avatar */}
              <div style={{
                width: '140px', height: '140px', borderRadius: '14px',
                background: cfg.bg,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                fontSize: '4rem', fontWeight: 300, color: 'white',
                fontFamily: 'var(--font-display)',
                letterSpacing: '0.05em',
                boxShadow: isSelected ? `0 0 0 3px white, 0 8px 32px ${cfg.glow}` : `0 4px 24px ${cfg.glow}`,
                transition: 'box-shadow 0.2s ease',
                position: 'relative', overflow: 'hidden',
              }}>
                {/* Shine overlay */}
                <div style={{
                  position: 'absolute', top: '-30%', left: '-30%', width: '60%', height: '60%',
                  background: 'radial-gradient(circle, rgba(255,255,255,0.2) 0%, transparent 70%)',
                  borderRadius: '50%',
                }} />
                {p.name.charAt(0).toUpperCase()}
              </div>
              {/* Name */}
              <span style={{ color: 'rgba(255,255,255,0.7)', fontSize: '15px', letterSpacing: '0.02em' }}>
                {p.name}
              </span>
            </button>
          );
        })}

        {/* Add profile */}
        {!creating && profiles.length < 5 && (
          <button onClick={() => setCreating(true)} style={{
            display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '14px',
            background: 'none', border: 'none', cursor: 'pointer', padding: '0',
            transition: 'transform 0.2s ease',
          }}
            onMouseEnter={e => (e.currentTarget.style.transform = 'scale(1.06)')}
            onMouseLeave={e => (e.currentTarget.style.transform = 'scale(1)')}
          >
            <div style={{
              width: '140px', height: '140px', borderRadius: '14px',
              background: 'rgba(255,255,255,0.05)',
              border: '2px dashed rgba(255,255,255,0.15)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              transition: 'border-color 0.2s, background 0.2s',
            }}
              onMouseEnter={e => { (e.currentTarget as HTMLElement).style.borderColor = 'rgba(255,255,255,0.35)'; (e.currentTarget as HTMLElement).style.background = 'rgba(255,255,255,0.08)'; }}
              onMouseLeave={e => { (e.currentTarget as HTMLElement).style.borderColor = 'rgba(255,255,255,0.15)'; (e.currentTarget as HTMLElement).style.background = 'rgba(255,255,255,0.05)'; }}
            >
              <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="rgba(255,255,255,0.3)" strokeWidth="1.5">
                <path d="M12 4v16M4 12h16" strokeLinecap="round"/>
              </svg>
            </div>
            <span style={{ color: 'rgba(255,255,255,0.3)', fontSize: '15px' }}>Add Profile</span>
          </button>
        )}
      </div>

      {/* Create form */}
      {creating && (
        <form onSubmit={createProfile} style={{ marginTop: '32px', display: 'flex', gap: '10px', alignItems: 'center' }}>
          <input autoFocus type="text" value={newName} onChange={e => setNewName(e.target.value)}
            placeholder="Profile name"
            style={{
              background: 'rgba(255,255,255,0.07)', border: '1px solid rgba(255,255,255,0.12)',
              borderRadius: '8px', padding: '12px 16px', color: 'white', fontSize: '14px',
              outline: 'none', width: '180px',
            }}
            onFocus={e => (e.target.style.borderColor = 'rgba(229,9,20,0.5)')}
            onBlur={e => (e.target.style.borderColor = 'rgba(255,255,255,0.12)')}
          />
          <button type="submit" style={{
            background: '#e50914', color: 'white', border: 'none', borderRadius: '8px',
            padding: '12px 20px', fontSize: '14px', fontWeight: 500, cursor: 'pointer',
          }}>Create</button>
          <button type="button" onClick={() => setCreating(false)} style={{
            background: 'transparent', color: 'rgba(255,255,255,0.35)', border: '1px solid rgba(255,255,255,0.1)',
            borderRadius: '8px', padding: '12px 16px', fontSize: '14px', cursor: 'pointer',
          }}>Cancel</button>
        </form>
      )}

      {/* Sign out */}
      <button onClick={logout} style={{
        marginTop: '56px', background: 'transparent', border: '1px solid rgba(255,255,255,0.1)',
        color: 'rgba(255,255,255,0.25)', borderRadius: '6px', padding: '10px 28px',
        fontSize: '11px', letterSpacing: '0.15em', textTransform: 'uppercase', cursor: 'pointer',
        transition: 'all 0.2s',
      }}
        onMouseEnter={e => { (e.target as HTMLElement).style.color = 'rgba(255,255,255,0.6)'; (e.target as HTMLElement).style.borderColor = 'rgba(255,255,255,0.25)'; }}
        onMouseLeave={e => { (e.target as HTMLElement).style.color = 'rgba(255,255,255,0.25)'; (e.target as HTMLElement).style.borderColor = 'rgba(255,255,255,0.1)'; }}
      >
        Sign out
      </button>
    </div>
  );
}