'use client';

import { useEffect, useState } from 'react';
import { MovieSummary } from '@/types';
import { formatMeta } from '@/lib/format';

export default function Hero({ movies, onPlay, onInfo }: {
  movies: MovieSummary[];
  onPlay: (m: MovieSummary) => void;
  onInfo: (m: MovieSummary) => void;
}) {
  const [index, setIndex] = useState(0);
  const [paused, setPaused] = useState(false);

  useEffect(() => {
    if (movies.length < 2 || paused) return;
    const t = setInterval(() => setIndex(i => (i + 1) % movies.length), 8000);
    return () => clearInterval(t);
  }, [movies.length, paused]);

  if (!movies.length) return null;

  return (
    <section className="sv-hero" aria-roledescription="carousel" aria-label="Featured movies"
             onMouseEnter={() => setPaused(true)} onMouseLeave={() => setPaused(false)}>
      {movies.map((m, i) => {
        const on = i === index;
        return (
          <div key={m.id} className={`sv-slide${on ? ' on' : ''}`} aria-hidden={!on}>
            {m.posterUrl && <div className="sv-slide-bg" style={{ backgroundImage: `url(${m.posterUrl})` }} />}
            <div className="sv-slide-inner">
              <div className="sv-hero-text">
                {m.genres?.[0] && <p className="sv-kicker">{m.genres[0]}</p>}
                <h1>{m.title}</h1>
                <p className="sv-hero-meta">{formatMeta(m.releaseYear, m.runtimeMinutes)}</p>
                <div className="sv-hero-actions">
                  <button className="sv-btn" tabIndex={on ? 0 : -1} onClick={() => onPlay(m)}>
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M8 5v14l11-7z" /></svg>
                    Play
                  </button>
                  <button className="sv-btn sv-btn-ghost" tabIndex={on ? 0 : -1} onClick={() => onInfo(m)}>Details</button>
                </div>
              </div>
              {m.posterUrl && <img className="sv-hero-poster" src={m.posterUrl} alt="" />}
            </div>
          </div>
        );
      })}

      {movies.length > 1 && (
        <div className="sv-dots">
          {movies.map((m, i) => (
            <button key={m.id} className={i === index ? 'on' : ''} aria-label={`Show ${m.title}`} aria-current={i === index} onClick={() => setIndex(i)} />
          ))}
        </div>
      )}
    </section>
  );
}
