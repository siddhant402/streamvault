'use client';

import Link from 'next/link';
import Image from 'next/image';
import { MovieSummary } from '@/types';

export default function MovieCard({ movie }: { movie: MovieSummary }) {
  return (
    <Link href={`/movie/${movie.id}`} className="group block">
      <div className="relative aspect-[2/3] rounded-lg overflow-hidden bg-neutral-900 border border-white/5
                      group-hover:border-white/20 transition-all duration-300 group-hover:scale-105 group-hover:shadow-2xl">
        {movie.posterUrl ? (
          <Image src={movie.posterUrl} alt={movie.title} fill className="object-cover" sizes="160px" />
        ) : (
          <div className="w-full h-full flex items-center justify-center text-white/15 text-xs text-center p-2">
            {movie.title}
          </div>
        )}
        <div className="absolute inset-0 bg-gradient-to-t from-black/90 via-transparent to-transparent
                        opacity-0 group-hover:opacity-100 transition-opacity duration-300 flex flex-col justify-end p-2.5">
          <p className="text-xs font-medium text-white leading-tight line-clamp-2">{movie.title}</p>
          {movie.releaseYear && <p className="text-xs text-white/40 mt-0.5">{movie.releaseYear}</p>}
        </div>
      </div>
    </Link>
  );
}
