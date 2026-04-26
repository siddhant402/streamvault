'use client';

import Link from 'next/link';
import Image from 'next/image';
import { MovieSummary } from '@/types';

interface Props {
  movies: MovieSummary[];
}

export default function HeroCarousel({ movies }: Props) {
  if (!movies.length) return null;
  const top5 = movies.slice(0, 5);

  return (
    <div className="relative w-full">
      <div className="carousel-track px-6 gap-4 pb-4">
        {top5.map((movie, i) => (
          <Link
            key={movie.id}
            href={`/movie/${movie.id}`}
            className="carousel-item relative flex-shrink-0 w-72 h-44 rounded-xl overflow-hidden
                       group border border-sv-border hover:border-sv-accent transition-all"
          >
            {movie.posterUrl ? (
              <Image
                src={movie.posterUrl.replace('w500', 'w780')}
                alt={movie.title}
                fill
                className="object-cover group-hover:scale-105 transition-transform duration-500"
                sizes="288px"
              />
            ) : (
              <div className="w-full h-full bg-sv-card" />
            )}
            {/* Gradient overlay */}
            <div className="absolute inset-0 bg-gradient-to-t from-black via-black/30 to-transparent" />

            {/* Badge */}
            <div className="absolute top-3 left-3 bg-sv-accent text-white text-xs
                            font-bold px-2 py-1 rounded tracking-wider">
              #{i + 1} NEW
            </div>

            {/* Info */}
            <div className="absolute bottom-0 left-0 right-0 p-3">
              <h3 className="font-display text-xl tracking-wide text-white leading-tight
                             group-hover:text-sv-accent transition-colors">
                {movie.title}
              </h3>
              <div className="flex items-center gap-2 mt-1">
                {movie.releaseYear && (
                  <span className="text-xs text-sv-muted">{movie.releaseYear}</span>
                )}
                {movie.genres?.[0] && (
                  <span className="text-xs text-sv-muted">· {movie.genres[0]}</span>
                )}
                {movie.runtimeMinutes && (
                  <span className="text-xs text-sv-muted">
                    · {Math.floor(movie.runtimeMinutes / 60)}h {movie.runtimeMinutes % 60}m
                  </span>
                )}
              </div>
            </div>
          </Link>
        ))}
      </div>
    </div>
  );
}
