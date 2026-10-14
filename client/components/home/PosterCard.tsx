'use client';

import Link from 'next/link';
import { MovieSummary } from '@/types';
import { formatMeta } from '@/lib/format';

export default function PosterCard({ movie }: { movie: MovieSummary }) {
  return (
    <Link href={`/movie/${movie.id}`} className="sv-card" title={movie.title}>
      <div className="sv-poster">
        {movie.posterUrl ? (
          <img src={movie.posterUrl} alt="" loading="lazy" />
        ) : (
          <div className="sv-poster-empty">{movie.title}</div>
        )}
      </div>
      <p className="sv-card-title">{movie.title}</p>
      <p className="sv-card-meta">{formatMeta(movie.releaseYear, movie.runtimeMinutes) || ' '}</p>
    </Link>
  );
}
