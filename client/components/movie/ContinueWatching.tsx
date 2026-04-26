'use client';

import Link from 'next/link';
import Image from 'next/image';
import { WatchHistoryEntry } from '@/types';

function formatTime(secs: number) {
  const m = Math.floor(secs / 60);
  if (m < 60) return `${m}m left`;
  return `${Math.floor(m / 60)}h ${m % 60}m left`;
}

export default function ContinueWatching({ entries }: { entries: WatchHistoryEntry[] }) {
  const active = entries.filter(e => !e.completed).slice(0, 8);
  if (!active.length) return null;

  return (
    <section>
      <h2 className="text-xs tracking-[0.25em] uppercase text-white/40 font-medium mb-5">Continue Watching</h2>
      <div className="flex gap-4 overflow-x-auto pb-2 scrollbar-hide">
        {active.map(e => {
          const pct = Math.min(100, (e.positionSeconds / (e.runtimeMinutes * 60)) * 100);
          const remaining = (e.runtimeMinutes * 60) - e.positionSeconds;
          return (
            <Link key={e.movieId} href={`/player/${e.movieId}?t=${e.positionSeconds}`}
                  className="flex-shrink-0 w-44 group">
              <div className="relative aspect-video rounded-lg overflow-hidden bg-neutral-900 border border-white/5
                              group-hover:border-white/20 transition-all duration-300 group-hover:scale-105">
                {e.posterUrl && (
                  <Image src={e.posterUrl} alt={e.title} fill className="object-cover object-top" sizes="176px" />
                )}
                <div className="absolute inset-0 bg-black/40 group-hover:bg-black/20 transition-colors" />
                <div className="absolute inset-0 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity">
                  <div className="w-10 h-10 rounded-full bg-white/90 flex items-center justify-center">
                    <svg className="w-4 h-4 text-black fill-current ml-0.5" viewBox="0 0 24 24"><path d="M8 5v14l11-7z"/></svg>
                  </div>
                </div>
                {/* Progress bar */}
                <div className="absolute bottom-0 left-0 right-0 h-0.5 bg-white/20">
                  <div className="h-full bg-red-500" style={{ width: `${pct}%` }} />
                </div>
              </div>
              <p className="mt-1.5 text-xs text-white/50 truncate">{e.title}</p>
              {remaining > 0 && <p className="text-xs text-white/25">{formatTime(remaining)}</p>}
            </Link>
          );
        })}
      </div>
    </section>
  );
}
