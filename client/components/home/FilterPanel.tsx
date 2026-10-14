'use client';

import { useEffect, useState } from 'react';
import { FilterOptions, MovieFilters } from '@/types';

export function countFilters(f: MovieFilters): number {
  return f.genres.length + (f.yearFrom || f.yearTo ? 1 : 0) + (f.actor ? 1 : 0);
}

export default function FilterPanel({ options, filters, onChange }: {
  options: FilterOptions | null;
  filters: MovieFilters;
  onChange: (f: MovieFilters) => void;
}) {
  const [actorInput, setActorInput] = useState(filters.actor ?? '');
  useEffect(() => { setActorInput(filters.actor ?? ''); }, [filters.actor]);

  if (!options) return <div className="sv-panel"><span className="sv-muted">Loading filters…</span></div>;

  const years: number[] = [];
  if (options.minYear && options.maxYear) {
    for (let y = options.maxYear; y >= options.minYear; y--) years.push(y);
  }

  const toggleGenre = (g: string) => onChange({
    ...filters,
    genres: filters.genres.includes(g) ? filters.genres.filter(x => x !== g) : [...filters.genres, g],
  });

  const commitActor = (value: string) => {
    const v = value.trim();
    if (v !== (filters.actor ?? '')) onChange({ ...filters, actor: v || undefined });
  };

  return (
    <div className="sv-panel">
      <div>
        <span className="sv-label">Genre</span>
        <div className="sv-chips">
          {options.genres.map(g => {
            const on = filters.genres.includes(g);
            return (
              <button key={g} type="button" className={`sv-chip${on ? ' on' : ''}`} aria-pressed={on} onClick={() => toggleGenre(g)}>
                {g}
              </button>
            );
          })}
        </div>
      </div>

      <div className="sv-panel-row">
        <label className="sv-field">
          <span className="sv-label">Year from</span>
          <select className="sv-select" value={filters.yearFrom ?? ''}
            onChange={e => onChange({ ...filters, yearFrom: e.target.value ? Number(e.target.value) : undefined })}>
            <option value="">Any</option>
            {years.map(y => <option key={y} value={y}>{y}</option>)}
          </select>
        </label>
        <label className="sv-field">
          <span className="sv-label">Year to</span>
          <select className="sv-select" value={filters.yearTo ?? ''}
            onChange={e => onChange({ ...filters, yearTo: e.target.value ? Number(e.target.value) : undefined })}>
            <option value="">Any</option>
            {years.map(y => <option key={y} value={y}>{y}</option>)}
          </select>
        </label>
        <label className="sv-field sv-field-grow">
          <span className="sv-label">Actor</span>
          <input className="sv-input" list="sv-actors" value={actorInput} placeholder="Type a name…"
            onChange={e => { setActorInput(e.target.value); if (options.actors.includes(e.target.value)) commitActor(e.target.value); }}
            onKeyDown={e => { if (e.key === 'Enter') commitActor(actorInput); }}
            onBlur={() => commitActor(actorInput)} />
          <datalist id="sv-actors">
            {options.actors.map(a => <option key={a} value={a} />)}
          </datalist>
        </label>
      </div>
    </div>
  );
}

export function ActiveFilters({ q, filters, onChange, onClearSearch, onClearAll }: {
  q: string;
  filters: MovieFilters;
  onChange: (f: MovieFilters) => void;
  onClearSearch: () => void;
  onClearAll: () => void;
}) {
  const tags: { key: string; label: string; remove: () => void }[] = [];
  if (q) tags.push({ key: 'q', label: `“${q}”`, remove: onClearSearch });
  filters.genres.forEach(g => tags.push({
    key: `g-${g}`, label: g, remove: () => onChange({ ...filters, genres: filters.genres.filter(x => x !== g) }),
  }));
  if (filters.yearFrom || filters.yearTo) {
    tags.push({
      key: 'y',
      label: `${filters.yearFrom ?? '…'} – ${filters.yearTo ?? '…'}`,
      remove: () => onChange({ ...filters, yearFrom: undefined, yearTo: undefined }),
    });
  }
  if (filters.actor) tags.push({ key: 'a', label: filters.actor, remove: () => onChange({ ...filters, actor: undefined }) });

  if (!tags.length) return null;
  return (
    <div className="sv-active">
      {tags.map(t => (
        <span key={t.key} className="sv-tag">
          {t.label}
          <button type="button" aria-label={`Remove ${t.label}`} onClick={t.remove}>×</button>
        </span>
      ))}
      <button type="button" className="sv-link" onClick={onClearAll}>Clear all</button>
    </div>
  );
}
