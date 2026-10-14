'use client';

import { useEffect, useRef, useState } from 'react';

export default function HomeNav({ q, onSearch, profileName, onHome, onProfiles, onSignOut }: {
  q: string;
  onSearch: (q: string) => void;
  profileName: string;
  onHome: () => void;
  onProfiles: () => void;
  onSignOut: () => void;
}) {
  const [value, setValue] = useState(q);
  const committed = useRef(q);

  // Keep the box in sync when the query is cleared elsewhere (chips, "Clear all")
  useEffect(() => {
    if (q !== committed.current) { committed.current = q; setValue(q); }
  }, [q]);

  // Debounced search
  useEffect(() => {
    if (value.trim() === committed.current) return;
    const t = setTimeout(() => { committed.current = value.trim(); onSearch(value.trim()); }, 350);
    return () => clearTimeout(t);
  }, [value]); // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <header className="sv-nav">
      <button className="sv-logo" onClick={onHome} aria-label="StreamVault home">STREAM<span>VAULT</span></button>

      <form className="sv-search" role="search" onSubmit={e => { e.preventDefault(); committed.current = value.trim(); onSearch(value.trim()); }}>
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
          <circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" />
        </svg>
        <input type="search" value={value} onChange={e => setValue(e.target.value)} placeholder="Search movies" aria-label="Search movies" />
      </form>

      <div className="sv-nav-right">
        <button className="sv-avatar-btn" onClick={onProfiles} aria-label={`Switch profile (${profileName})`}>
          <span className="sv-avatar">{profileName.charAt(0).toUpperCase()}</span>
          <span className="sv-profile-name">{profileName}</span>
        </button>
        <button className="sv-signout" onClick={onSignOut}>Sign out</button>
      </div>
    </header>
  );
}
