'use client';

import { useEffect, useState, useRef } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/lib/auth-context';
import { moviesApi, watchApi } from '@/lib/api';
import { MovieSummary, WatchHistoryEntry, PageResponse } from '@/types';

export default function HomePage() {
  const { isAuthenticated, profile } = useAuth();
  const router = useRouter();
  const [featuredMovies, setFeaturedMovies] = useState<MovieSummary[]>([]);
  const [continueWatching, setContinueWatching] = useState<WatchHistoryEntry[]>([]);
  const [movies, setMovies] = useState<MovieSummary[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [search, setSearch] = useState('');
  const [searchInput, setSearchInput] = useState('');
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [heroIndex, setHeroIndex] = useState(0);
  const [scrollY, setScrollY] = useState(0);
  const heroIntervalRef = useRef<NodeJS.Timeout | null>(null);

  useEffect(() => {
    if (!isAuthenticated) { router.replace('/login'); return; }
    if (!profile) { router.replace('/profiles'); return; }
    loadInitialData();
    const onScroll = () => setScrollY(window.scrollY);
    window.addEventListener('scroll', onScroll);
    return () => window.removeEventListener('scroll', onScroll);
  }, [isAuthenticated, profile]);

  useEffect(() => {
    if (featuredMovies.length < 2) return;
    heroIntervalRef.current = setInterval(() => setHeroIndex(i => (i + 1) % featuredMovies.length), 7000);
    return () => { if (heroIntervalRef.current) clearInterval(heroIntervalRef.current); };
  }, [featuredMovies.length]);

  const loadInitialData = async () => {
    try {
      const [latestRes, continueRes] = await Promise.all([
        moviesApi.list(0, 6, 'releaseYear', 'desc'),
        watchApi.getContinueWatching(profile!.id),
      ]);
      setFeaturedMovies(latestRes.data.content);
      setContinueWatching(continueRes.data.filter((e: WatchHistoryEntry) => !e.completed));
      await loadMovies(0, '');
    } finally {
      setLoading(false);
    }
  };

  const loadMovies = async (p: number, q: string) => {
    const res = await moviesApi.list(p, 24, 'title', 'asc', q || undefined);
    const data: PageResponse<MovieSummary> = res.data;
    if (p === 0) setMovies(data.content);
    else setMovies(prev => [...prev, ...data.content]);
    setTotalPages(data.totalPages);
    setPage(p);
  };

  const handleSearch = async (e: React.FormEvent) => {
    e.preventDefault();
    setSearch(searchInput);
    setLoading(true);
    await loadMovies(0, searchInput);
    setLoading(false);
  };

  const loadMore = async () => {
    if (page + 1 >= totalPages || loadingMore) return;
    setLoadingMore(true);
    await loadMovies(page + 1, search);
    setLoadingMore(false);
  };

  const hero = featuredMovies[heroIndex];
  const navOpacity = Math.min(scrollY / 200, 1);

  if (loading) return (
    <div style={{ minHeight: '100vh', background: '#0a0a0a', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <div style={{ width: 40, height: 40, borderRadius: '50%', border: '2px solid rgba(255,255,255,0.1)', borderTop: '2px solid #e50914', animation: 'spin 0.8s linear infinite' }} />
      <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
    </div>
  );

  return (
    <div style={{ minHeight: '100vh', background: '#0a0a0a', color: 'white', fontFamily: 'var(--font-body)' }}>

      {/* Navbar */}
      <nav style={{
        position: 'fixed', top: 0, left: 0, right: 0, zIndex: 100,
        padding: '0 4%', height: '68px',
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        background: `rgba(10,10,10,${navOpacity})`,
        borderBottom: navOpacity > 0.5 ? '1px solid rgba(255,255,255,0.05)' : 'none',
        transition: 'background 0.3s',
      }}>
        <div style={{ fontFamily: 'var(--font-display)', fontSize: '1.6rem', letterSpacing: '0.2em', cursor: 'pointer' }}
             onClick={() => router.push('/home')}>
          STREAM<span style={{ color: '#e50914' }}>VAULT</span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '24px' }}>
          {profile && (
            <button onClick={() => router.push('/profiles')} style={{
              display: 'flex', alignItems: 'center', gap: '8px',
              background: 'none', border: 'none', cursor: 'pointer', color: 'rgba(255,255,255,0.6)',
              fontSize: '14px', transition: 'color 0.2s',
            }}
              onMouseEnter={e => (e.currentTarget.style.color = 'white')}
              onMouseLeave={e => (e.currentTarget.style.color = 'rgba(255,255,255,0.6)')}
            >
              <div style={{
                width: '32px', height: '32px', borderRadius: '6px',
                background: 'linear-gradient(135deg, #c8080f, #ff6b35)',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                fontSize: '13px', fontWeight: 600, color: 'white',
                fontFamily: 'var(--font-display)',
              }}>
                {profile.name.charAt(0).toUpperCase()}
              </div>
              <span style={{ display: 'none' }}>{profile.name}</span>
            </button>
          )}
        </div>
      </nav>

      {/* Hero */}
      {hero && (
        <div style={{ position: 'relative', height: '85vh', overflow: 'hidden' }}>
          {featuredMovies.map((m, i) => (
            <div key={m.id} style={{
              position: 'absolute', inset: 0,
              transition: 'opacity 1.2s ease',
              opacity: i === heroIndex ? 1 : 0,
            }}>
              {m.posterUrl && (
                <img src={m.posterUrl.replace('w500', 'original')} alt=""
                     style={{ width: '100%', height: '100%', objectFit: 'cover', objectPosition: 'center top' }} />
              )}
            </div>
          ))}
          {/* Gradients */}
          <div style={{ position: 'absolute', inset: 0, background: 'linear-gradient(to right, rgba(0,0,0,0.85) 0%, rgba(0,0,0,0.4) 50%, transparent 100%)' }} />
          <div style={{ position: 'absolute', inset: 0, background: 'linear-gradient(to top, #0a0a0a 0%, rgba(10,10,10,0.2) 40%, transparent 100%)' }} />
          <div style={{ position: 'absolute', inset: 0, background: 'linear-gradient(to bottom, rgba(10,10,10,0.5) 0%, transparent 20%)' }} />

          {/* Content */}
          <div style={{ position: 'absolute', bottom: '18%', left: '4%', maxWidth: '520px' }}>
            {hero.genres?.[0] && (
              <div style={{ fontSize: '11px', letterSpacing: '0.25em', textTransform: 'uppercase', color: '#e50914', marginBottom: '12px', fontWeight: 500 }}>
                {hero.genres[0]}
              </div>
            )}
            <h1 style={{ fontSize: 'clamp(2.2rem, 4vw, 3.8rem)', fontWeight: 300, lineHeight: 1.1, marginBottom: '16px', letterSpacing: '-0.02em' }}>
              {hero.title}
            </h1>
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '28px', color: 'rgba(255,255,255,0.5)', fontSize: '14px' }}>
              {hero.releaseYear && <span>{hero.releaseYear}</span>}
              {hero.runtimeMinutes && <><span style={{ width: 3, height: 3, borderRadius: '50%', background: 'rgba(255,255,255,0.3)', display: 'inline-block' }} /><span>{Math.floor(hero.runtimeMinutes / 60)}h {hero.runtimeMinutes % 60}m</span></>}
            </div>
            <div style={{ display: 'flex', gap: '12px' }}>
              <button onClick={() => router.push(`/player/${hero.id}`)} style={{
                display: 'flex', alignItems: 'center', gap: '8px',
                background: 'white', color: 'black', border: 'none',
                borderRadius: '6px', padding: '12px 28px', fontSize: '15px', fontWeight: 500, cursor: 'pointer',
                transition: 'background 0.2s',
              }}
                onMouseEnter={e => (e.currentTarget.style.background = 'rgba(255,255,255,0.85)')}
                onMouseLeave={e => (e.currentTarget.style.background = 'white')}
              >
                <svg width="16" height="16" viewBox="0 0 24 24" fill="black"><path d="M8 5v14l11-7z"/></svg>
                Play
              </button>
              <button onClick={() => router.push(`/movie/${hero.id}`)} style={{
                display: 'flex', alignItems: 'center', gap: '8px',
                background: 'rgba(255,255,255,0.15)', color: 'white',
                border: '1px solid rgba(255,255,255,0.2)',
                borderRadius: '6px', padding: '12px 24px', fontSize: '15px', fontWeight: 400, cursor: 'pointer',
                backdropFilter: 'blur(10px)', transition: 'background 0.2s',
              }}
                onMouseEnter={e => (e.currentTarget.style.background = 'rgba(255,255,255,0.25)')}
                onMouseLeave={e => (e.currentTarget.style.background = 'rgba(255,255,255,0.15)')}
              >
                <svg width="16" height="16" viewBox="0 0 24 24" fill="white"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z"/></svg>
                More Info
              </button>
            </div>
          </div>

          {/* Hero dots */}
          {featuredMovies.length > 1 && (
            <div style={{ position: 'absolute', bottom: '14%', right: '4%', display: 'flex', gap: '6px' }}>
              {featuredMovies.map((_, i) => (
                <button key={i} onClick={() => { setHeroIndex(i); if (heroIntervalRef.current) clearInterval(heroIntervalRef.current); }}
                  style={{
                    height: '2px', borderRadius: '2px', border: 'none', cursor: 'pointer', padding: 0,
                    width: i === heroIndex ? '24px' : '8px',
                    background: i === heroIndex ? 'white' : 'rgba(255,255,255,0.3)',
                    transition: 'all 0.3s',
                  }} />
              ))}
            </div>
          )}
        </div>
      )}

      {/* Content rows */}
      <div style={{ padding: '0 4% 60px', marginTop: '-60px', position: 'relative', zIndex: 10 }}>

        {/* Continue Watching */}
        {continueWatching.length > 0 && (
          <section style={{ marginBottom: '48px' }}>
            <h2 style={{ fontSize: '13px', letterSpacing: '0.2em', textTransform: 'uppercase', color: 'rgba(255,255,255,0.4)', marginBottom: '18px', fontWeight: 500 }}>
              Continue Watching
            </h2>
            <div style={{ display: 'flex', gap: '12px', overflowX: 'auto', paddingBottom: '8px' }}
                 className="hide-scrollbar">
              {continueWatching.slice(0, 8).map(e => {
                const pct = Math.min(100, (e.positionSeconds / (e.runtimeMinutes * 60)) * 100);
                return (
                  <div key={e.movieId} onClick={() => router.push(`/player/${e.movieId}?t=${e.positionSeconds}`)}
                    style={{ flexShrink: 0, width: '200px', cursor: 'pointer', position: 'relative' }}
                    onMouseEnter={e => (e.currentTarget.style.transform = 'scale(1.04)')}
                    onMouseLeave={e => (e.currentTarget.style.transform = 'scale(1)')}
                  >
                    <div style={{ position: 'relative', aspectRatio: '16/9', borderRadius: '8px', overflow: 'hidden', background: '#1a1a1a' }}>
                      {e.posterUrl && <img src={e.posterUrl} alt={e.title} style={{ width: '100%', height: '100%', objectFit: 'cover', objectPosition: 'top' }} />}
                      <div style={{ position: 'absolute', inset: 0, background: 'rgba(0,0,0,0.3)' }} />
                      <div style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', opacity: 0, transition: 'opacity 0.2s' }}
                           onMouseEnter={e => (e.currentTarget.style.opacity = '1')}
                           onMouseLeave={e => (e.currentTarget.style.opacity = '0')}>
                        <div style={{ width: '44px', height: '44px', borderRadius: '50%', background: 'rgba(255,255,255,0.9)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                          <svg width="16" height="16" viewBox="0 0 24 24" fill="black"><path d="M8 5v14l11-7z"/></svg>
                        </div>
                      </div>
                      {/* Progress */}
                      <div style={{ position: 'absolute', bottom: 0, left: 0, right: 0, height: '3px', background: 'rgba(255,255,255,0.2)' }}>
                        <div style={{ width: `${pct}%`, height: '100%', background: '#e50914' }} />
                      </div>
                    </div>
                    <p style={{ marginTop: '8px', fontSize: '13px', color: 'rgba(255,255,255,0.6)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{e.title}</p>
                  </div>
                );
              })}
            </div>
          </section>
        )}

        {/* All Movies */}
        <section>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '18px' }}>
            <h2 style={{ fontSize: '13px', letterSpacing: '0.2em', textTransform: 'uppercase', color: 'rgba(255,255,255,0.4)', fontWeight: 500 }}>
              {search ? `Results for "${search}"` : 'All Movies'}
            </h2>
            <form onSubmit={handleSearch} style={{ display: 'flex', gap: '8px' }}>
              <input type="text" value={searchInput} onChange={e => setSearchInput(e.target.value)} placeholder="Search…"
                style={{
                  background: 'rgba(255,255,255,0.06)', border: '1px solid rgba(255,255,255,0.1)',
                  borderRadius: '6px', padding: '8px 14px', color: 'white', fontSize: '13px',
                  outline: 'none', width: '180px',
                }}
                onFocus={e => (e.target.style.borderColor = 'rgba(229,9,20,0.5)')}
                onBlur={e => (e.target.style.borderColor = 'rgba(255,255,255,0.1)')}
              />
              <button type="submit" style={{
                background: 'rgba(255,255,255,0.08)', border: '1px solid rgba(255,255,255,0.1)',
                color: 'rgba(255,255,255,0.6)', borderRadius: '6px', padding: '8px 16px', fontSize: '13px', cursor: 'pointer',
              }}>Search</button>
              {search && (
                <button type="button" onClick={() => { setSearchInput(''); setSearch(''); loadMovies(0, ''); }}
                  style={{ background: 'none', border: 'none', color: 'rgba(255,255,255,0.3)', cursor: 'pointer', fontSize: '16px', padding: '0 4px' }}>✕</button>
              )}
            </form>
          </div>

          {movies.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '80px 0', color: 'rgba(255,255,255,0.2)', fontSize: '14px' }}>No movies found</div>
          ) : (
            <>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(140px, 1fr))', gap: '12px' }}>
                {movies.map(movie => (
                  <MovieCard key={movie.id} movie={movie} onClick={() => router.push(`/movie/${movie.id}`)} />
                ))}
              </div>
              {page + 1 < totalPages && (
                <div style={{ display: 'flex', justifyContent: 'center', marginTop: '40px' }}>
                  <button onClick={loadMore} disabled={loadingMore} style={{
                    background: 'transparent', border: '1px solid rgba(255,255,255,0.15)',
                    color: 'rgba(255,255,255,0.4)', borderRadius: '6px', padding: '12px 40px',
                    fontSize: '12px', letterSpacing: '0.15em', textTransform: 'uppercase', cursor: 'pointer',
                    transition: 'all 0.2s',
                  }}
                    onMouseEnter={e => { e.currentTarget.style.borderColor = 'rgba(255,255,255,0.35)'; e.currentTarget.style.color = 'rgba(255,255,255,0.7)'; }}
                    onMouseLeave={e => { e.currentTarget.style.borderColor = 'rgba(255,255,255,0.15)'; e.currentTarget.style.color = 'rgba(255,255,255,0.4)'; }}
                  >
                    {loadingMore ? 'Loading…' : 'Load more'}
                  </button>
                </div>
              )}
            </>
          )}
        </section>
      </div>

      <style>{`
        .hide-scrollbar::-webkit-scrollbar { display: none; }
        .hide-scrollbar { -ms-overflow-style: none; scrollbar-width: none; }
      `}</style>
    </div>
  );
}

function MovieCard({ movie, onClick }: { movie: MovieSummary; onClick: () => void }) {
  const [hovered, setHovered] = useState(false);
  return (
    <div onClick={onClick} onMouseEnter={() => setHovered(true)} onMouseLeave={() => setHovered(false)}
      style={{ cursor: 'pointer', transition: 'transform 0.2s', transform: hovered ? 'scale(1.05)' : 'scale(1)' }}>
      <div style={{ position: 'relative', aspectRatio: '2/3', borderRadius: '8px', overflow: 'hidden', background: '#1a1a1a', border: hovered ? '1px solid rgba(255,255,255,0.25)' : '1px solid transparent', transition: 'border-color 0.2s' }}>
        {movie.posterUrl
          ? <img src={movie.posterUrl} alt={movie.title} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
          : <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'rgba(255,255,255,0.15)', fontSize: '12px', textAlign: 'center', padding: '8px' }}>{movie.title}</div>
        }
        {hovered && (
          <div style={{ position: 'absolute', inset: 0, background: 'linear-gradient(to top, rgba(0,0,0,0.9) 0%, transparent 50%)', display: 'flex', flexDirection: 'column', justifyContent: 'flex-end', padding: '12px' }}>
            <p style={{ fontSize: '12px', fontWeight: 500, color: 'white', lineHeight: 1.3, marginBottom: '4px' }}>{movie.title}</p>
            {movie.releaseYear && <p style={{ fontSize: '11px', color: 'rgba(255,255,255,0.4)' }}>{movie.releaseYear}</p>}
          </div>
        )}
      </div>
    </div>
  );
}