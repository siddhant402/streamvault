'use client';

import { useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import Image from 'next/image';
import { useAuth } from '@/lib/auth-context';
import { moviesApi, watchApi } from '@/lib/api';
import { MovieDetail, ResumePosition } from '@/types';

export default function MovieDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { isAuthenticated, profile } = useAuth();
  const router = useRouter();
  const [movie, setMovie] = useState<MovieDetail | null>(null);
  const [resume, setResume] = useState<ResumePosition | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!isAuthenticated) { router.replace('/login'); return; }
    if (!profile) { router.replace('/profiles'); return; }
    Promise.all([moviesApi.get(id), watchApi.getResumePosition(profile.id, id)])
      .then(([mr, rr]) => { setMovie(mr.data); setResume(rr.data); })
      .finally(() => setLoading(false));
  }, [id, isAuthenticated, profile]);

  const fmt = (mins: number) => `${Math.floor(mins / 60)}h ${mins % 60}m`;
  const resumeHref = () => `/player/${id}${resume?.positionSeconds && resume.positionSeconds > 60 ? `?t=${resume.positionSeconds}` : ''}`;
  const resumeLabel = () => {
    if (!resume) return 'Play';
    if (resume.completed) return 'Play Again';
    if (resume.positionSeconds && resume.positionSeconds > 60) return `Resume · ${Math.floor(resume.positionSeconds / 60)}m`;
    return 'Play';
  };
  const resumePct = () => {
    if (!resume?.positionSeconds || !movie?.runtimeMinutes) return 0;
    return Math.min(100, (resume.positionSeconds / (movie.runtimeMinutes * 60)) * 100);
  };

  if (loading) return (
    <div style={{ minHeight: '100vh', background: '#0a0a0a', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <div style={{ width: 40, height: 40, borderRadius: '50%', border: '2px solid rgba(255,255,255,0.1)', borderTop: '2px solid #e50914', animation: 'spin 0.8s linear infinite' }} />
      <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
    </div>
  );

  if (!movie) return (
    <div style={{ minHeight: '100vh', background: '#0a0a0a', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'rgba(255,255,255,0.3)', fontFamily: 'var(--font-body)' }}>
      Movie not found
    </div>
  );

  return (
    <div style={{ minHeight: '100vh', background: '#0a0a0a', color: 'white', fontFamily: 'var(--font-body)' }}>

      {/* Navbar */}
      <nav style={{
        position: 'fixed', top: 0, left: 0, right: 0, zIndex: 100,
        padding: '0 4%', height: '68px',
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        background: 'linear-gradient(to bottom, rgba(10,10,10,0.95), transparent)',
      }}>
        <div style={{ fontFamily: 'var(--font-display)', fontSize: '1.5rem', letterSpacing: '0.2em', cursor: 'pointer' }}
             onClick={() => router.push('/home')}>
          STREAM<span style={{ color: '#e50914' }}>VAULT</span>
        </div>
        <button onClick={() => router.push('/home')} style={{
          display: 'flex', alignItems: 'center', gap: '6px',
          background: 'none', border: 'none', color: 'rgba(255,255,255,0.5)', cursor: 'pointer',
          fontSize: '13px', letterSpacing: '0.05em',
        }}
          onMouseEnter={e => (e.currentTarget.style.color = 'white')}
          onMouseLeave={e => (e.currentTarget.style.color = 'rgba(255,255,255,0.5)')}
        >
          <svg width="16" height="16" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7"/></svg>
          Back
        </button>
      </nav>

      {/* Full-bleed backdrop */}
      <div style={{ position: 'relative', height: '70vh', overflow: 'hidden' }}>
        {movie.backdropUrl
          ? <Image src={movie.backdropUrl} alt={movie.title} fill style={{ objectFit: 'cover', objectPosition: 'center top' }} priority sizes="100vw" />
          : <div style={{ width: '100%', height: '100%', background: '#111' }} />
        }
        <div style={{ position: 'absolute', inset: 0, background: 'linear-gradient(to right, rgba(10,10,10,0.98) 0%, rgba(10,10,10,0.7) 40%, rgba(10,10,10,0.3) 100%)' }} />
        <div style={{ position: 'absolute', inset: 0, background: 'linear-gradient(to top, #0a0a0a 0%, rgba(10,10,10,0.1) 50%, rgba(10,10,10,0.6) 100%)' }} />
      </div>

      {/* Content — overlaps backdrop */}
      <div style={{ position: 'relative', marginTop: '-42vh', padding: '0 4% 80px', zIndex: 10 }}>
        <div style={{ display: 'flex', gap: '48px', alignItems: 'flex-start', maxWidth: '1100px' }}>

          {/* Left — Poster */}
          <div style={{ flexShrink: 0, width: '200px' }}>
            <div style={{ position: 'relative', aspectRatio: '2/3', borderRadius: '12px', overflow: 'hidden', boxShadow: '0 24px 64px rgba(0,0,0,0.7)', border: '1px solid rgba(255,255,255,0.08)' }}>
              {movie.posterUrl
                ? <Image src={movie.posterUrl} alt={movie.title} fill style={{ objectFit: 'cover' }} sizes="200px" />
                : <div style={{ width: '100%', height: '100%', background: '#1a1a1a', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'rgba(255,255,255,0.15)', fontSize: '12px', textAlign: 'center', padding: '12px' }}>{movie.title}</div>
              }
            </div>
          </div>

          {/* Right — Info */}
          <div style={{ flex: 1, paddingTop: '32px' }}>
            {/* Genre tag */}
            {movie.genres?.[0] && (
              <div style={{ fontSize: '11px', letterSpacing: '0.25em', textTransform: 'uppercase', color: '#e50914', marginBottom: '10px', fontWeight: 500 }}>
                {movie.genres[0]}
              </div>
            )}

            {/* Title */}
            <h1 style={{ fontSize: 'clamp(2rem, 3.5vw, 3rem)', fontWeight: 300, lineHeight: 1.1, marginBottom: '12px', letterSpacing: '-0.02em', fontFamily: 'var(--font-display)' }}>
              {movie.title}
            </h1>
            {movie.originalTitle && movie.originalTitle !== movie.title && (
              <p style={{ color: 'rgba(255,255,255,0.25)', fontSize: '13px', marginBottom: '12px' }}>{movie.originalTitle}</p>
            )}

            {/* Meta row */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '16px', marginBottom: '20px', color: 'rgba(255,255,255,0.45)', fontSize: '14px', flexWrap: 'wrap' }}>
              {movie.releaseYear && <span>{movie.releaseYear}</span>}
              {movie.runtimeMinutes && <><span style={{ color: 'rgba(255,255,255,0.2)' }}>·</span><span>{fmt(movie.runtimeMinutes)}</span></>}
              {movie.ageRating && (
                <span style={{ border: '1px solid rgba(255,255,255,0.2)', borderRadius: '4px', padding: '2px 8px', fontSize: '11px', letterSpacing: '0.05em' }}>
                  {movie.ageRating}
                </span>
              )}
            </div>

            {/* Genres */}
            {movie.genres?.length > 0 && (
              <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', marginBottom: '20px' }}>
                {movie.genres.map(g => (
                  <span key={g} style={{ background: 'rgba(255,255,255,0.06)', border: '1px solid rgba(255,255,255,0.08)', borderRadius: '20px', padding: '4px 14px', fontSize: '12px', color: 'rgba(255,255,255,0.5)' }}>
                    {g}
                  </span>
                ))}
              </div>
            )}

            {/* Synopsis */}
            {movie.synopsis && (
              <p style={{ color: 'rgba(255,255,255,0.6)', fontSize: '14px', lineHeight: 1.7, maxWidth: '560px', marginBottom: '28px' }}>
                {movie.synopsis}
              </p>
            )}

            {/* Progress bar */}
            {resumePct() > 0 && !resume?.completed && (
              <div style={{ width: '160px', height: '2px', background: 'rgba(255,255,255,0.1)', borderRadius: '2px', overflow: 'hidden', marginBottom: '20px' }}>
                <div style={{ width: `${resumePct()}%`, height: '100%', background: '#e50914' }} />
              </div>
            )}

            {/* Play button */}
            <div style={{ display: 'flex', gap: '12px', marginBottom: '36px' }}>
              <button onClick={() => router.push(resumeHref())} style={{
                display: 'flex', alignItems: 'center', gap: '10px',
                background: 'white', color: 'black', border: 'none',
                borderRadius: '8px', padding: '14px 32px',
                fontSize: '15px', fontWeight: 600, cursor: 'pointer',
                transition: 'background 0.2s, transform 0.15s',
              }}
                onMouseEnter={e => { e.currentTarget.style.background = 'rgba(255,255,255,0.87)'; e.currentTarget.style.transform = 'scale(1.02)'; }}
                onMouseLeave={e => { e.currentTarget.style.background = 'white'; e.currentTarget.style.transform = 'scale(1)'; }}
              >
                <svg width="18" height="18" viewBox="0 0 24 24" fill="black"><path d="M8 5v14l11-7z"/></svg>
                {resumeLabel()}
              </button>
            </div>

            {/* Cast / Director grid */}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px', maxWidth: '480px', paddingTop: '24px', borderTop: '1px solid rgba(255,255,255,0.06)' }}>
              {movie.directors?.length > 0 && (
                <div>
                  <p style={{ fontSize: '11px', letterSpacing: '0.18em', textTransform: 'uppercase', color: 'rgba(255,255,255,0.25)', marginBottom: '6px' }}>
                    {movie.directors.length > 1 ? 'Directors' : 'Director'}
                  </p>
                  <p style={{ fontSize: '14px', color: 'rgba(255,255,255,0.7)', lineHeight: 1.5 }}>{movie.directors.join(', ')}</p>
                </div>
              )}
              {movie.castMembers?.length > 0 && (
                <div>
                  <p style={{ fontSize: '11px', letterSpacing: '0.18em', textTransform: 'uppercase', color: 'rgba(255,255,255,0.25)', marginBottom: '6px' }}>Cast</p>
                  <p style={{ fontSize: '14px', color: 'rgba(255,255,255,0.7)', lineHeight: 1.5 }}>{movie.castMembers.slice(0, 5).join(', ')}</p>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}