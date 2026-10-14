'use client';

import { WatchHistoryEntry } from '@/types';

function remaining(e: WatchHistoryEntry): string {
  const left = Math.max(0, Math.round((e.runtimeMinutes * 60 - e.positionSeconds) / 60));
  if (!e.runtimeMinutes) return '';
  return left >= 60 ? `${Math.floor(left / 60)}h ${left % 60}m left` : `${left}m left`;
}

export default function ContinueRow({ entries, onOpen }: {
  entries: WatchHistoryEntry[];
  onOpen: (e: WatchHistoryEntry) => void;
}) {
  if (!entries.length) return null;
  return (
    <section className="sv-continue">
      <h2 className="sv-section-title">Continue watching</h2>
      <div className="sv-row">
        {entries.slice(0, 10).map(e => {
          const pct = e.runtimeMinutes ? Math.min(100, (e.positionSeconds / (e.runtimeMinutes * 60)) * 100) : 0;
          return (
            <button key={e.movieId} className="sv-cw" onClick={() => onOpen(e)}>
              <span className="sv-cw-thumb">
                {e.posterUrl && <img src={e.posterUrl} alt="" loading="lazy" />}
                <span className="sv-cw-bar"><span style={{ width: `${pct}%` }} /></span>
              </span>
              <span className="sv-card-title">{e.title}</span>
              <span className="sv-card-meta">{remaining(e) || ' '}</span>
            </button>
          );
        })}
      </div>
    </section>
  );
}
