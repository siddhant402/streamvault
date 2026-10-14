'use client';

import { Suspense, useEffect, useMemo, useRef, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { useAuth } from '@/lib/auth-context';
import { moviesApi, watchApi } from '@/lib/api';
import { FilterOptions, MovieFilters, MovieSummary, PageResponse, WatchHistoryEntry } from '@/types';
import HomeNav from '@/components/home/HomeNav';
import Hero from '@/components/home/Hero';
import ContinueRow from '@/components/home/ContinueRow';
import FilterPanel, { ActiveFilters, countFilters } from '@/components/home/FilterPanel';
import PosterCard from '@/components/home/PosterCard';
import Pagination from '@/components/home/Pagination';

const PAGE_SIZE = 24;

const SORTS = [
  { value: 'title:asc', label: 'Title A–Z' },
  { value: 'releaseYear:desc', label: 'Newest first' },
  { value: 'releaseYear:asc', label: 'Oldest first' },
  { value: 'runtimeMinutes:desc', label: 'Longest first' },
];

/** Everything that defines the library view lives in the URL, so back/forward and refresh keep it. */
interface LibraryState {
  q: string;
  page: number;
  sort: string;
  filters: MovieFilters;
}

const toNum = (v: string | null): number | undefined => {
  const n = Number(v);
  return v && Number.isFinite(n) && n > 0 ? n : undefined;
};

function readState(sp: URLSearchParams): LibraryState {
  const sort = sp.get('sort');
  return {
    q: sp.get('q') ?? '',
    page: Math.max(0, Math.floor(Number(sp.get('page')) || 0)),
    sort: SORTS.some(s => s.value === sort) ? (sort as string) : SORTS[0].value,
    filters: {
      genres: sp.getAll('genre'),
      yearFrom: toNum(sp.get('from')),
      yearTo: toNum(sp.get('to')),
      actor: sp.get('actor') ?? undefined,
    },
  };
}

function toQueryString(s: LibraryState): string {
  const p = new URLSearchParams();
  if (s.q) p.set('q', s.q);
  if (s.page > 0) p.set('page', String(s.page));
  if (s.sort !== SORTS[0].value) p.set('sort', s.sort);
  s.filters.genres.forEach(g => p.append('genre', g));
  if (s.filters.yearFrom) p.set('from', String(s.filters.yearFrom));
  if (s.filters.yearTo) p.set('to', String(s.filters.yearTo));
  if (s.filters.actor) p.set('actor', s.filters.actor);
  return p.toString();
}

export default function HomePage() {
  // useSearchParams needs a Suspense boundary for production builds
  return (
    <Suspense fallback={null}>
      <Home />
    </Suspense>
  );
}

function Home() {
  const { isAuthenticated, profile, logout } = useAuth();
  const router = useRouter();
  const searchParams = useSearchParams();
  const libRef = useRef<HTMLElement | null>(null);

  const state = useMemo(() => readState(new URLSearchParams(searchParams.toString())), [searchParams]);
  const key = toQueryString(state);

  const [featured, setFeatured] = useState<MovieSummary[]>([]);
  const [continueWatching, setContinueWatching] = useState<WatchHistoryEntry[]>([]);
  const [options, setOptions] = useState<FilterOptions | null>(null);
  const [movies, setMovies] = useState<MovieSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loadingList, setLoadingList] = useState(true);
  const [panelOpen, setPanelOpen] = useState(false);

  // ── guards
  useEffect(() => {
    if (!isAuthenticated) router.replace('/login');
    else if (!profile) router.replace('/profiles');
  }, [isAuthenticated, profile, router]);

  // ── one-time data: hero, continue watching, filter options
  useEffect(() => {
    if (!isAuthenticated || !profile) return;
    moviesApi.list(0, 6, 'releaseYear', 'desc').then(r => setFeatured(r.data.content)).catch(() => {});
    watchApi.getContinueWatching(profile.id)
      .then(r => setContinueWatching((r.data as WatchHistoryEntry[]).filter(e => !e.completed)))
      .catch(() => {});
    moviesApi.filterOptions().then(r => setOptions(r.data)).catch(() => {});
  }, [isAuthenticated, profile?.id]); // eslint-disable-line react-hooks/exhaustive-deps

  // ── library page, refetched whenever the URL state changes
  useEffect(() => {
    if (!isAuthenticated || !profile) return;
    let cancelled = false;
    setLoadingList(true);
    const [sortBy, direction] = state.sort.split(':');
    moviesApi.list(state.page, PAGE_SIZE, sortBy, direction, state.q || undefined, state.filters)
      .then(res => {
        if (cancelled) return;
        const data: PageResponse<MovieSummary> = res.data;
        setMovies(data.content);
        setTotal(data.totalElements);
        setTotalPages(data.totalPages);
        if (data.totalPages > 0 && state.page >= data.totalPages) update({ page: data.totalPages - 1 }, false);
      })
      .catch(() => {
        if (cancelled) return;
        setMovies([]); setTotal(0); setTotalPages(0);
      })
      .finally(() => { if (!cancelled) setLoadingList(false); });
    return () => { cancelled = true; };
  }, [key, isAuthenticated, profile?.id]); // eslint-disable-line react-hooks/exhaustive-deps

  function update(patch: Partial<LibraryState>, resetPage = true) {
    const next: LibraryState = { ...state, ...patch };
    if (resetPage && patch.page === undefined) next.page = 0;
    const qs = toQueryString(next);
    router.replace(qs ? `/home?${qs}` : '/home', { scroll: false });
  }

  const goToPage = (p: number) => {
    update({ page: p }, false);
    libRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  };

  if (!isAuthenticated || !profile) return null;

  const filterCount = countFilters(state.filters);
  const browsing = !!state.q || filterCount > 0 || state.page > 0;
  const noFilters: MovieFilters = { genres: [] };
  const showSkeleton = loadingList && movies.length === 0;

  return (
    <div className="sv-page">
      <HomeNav
        q={state.q}
        onSearch={q => update({ q })}
        profileName={profile.name}
        onHome={() => router.push('/home')}
        onProfiles={() => router.push('/profiles')}
        onSignOut={logout}
      />

      {!browsing && (
        <Hero
          movies={featured}
          onPlay={m => router.push(`/player/${m.id}`)}
          onInfo={m => router.push(`/movie/${m.id}`)}
        />
      )}

      <main className="sv-main">
        {!browsing && (
          <ContinueRow
            entries={continueWatching}
            onOpen={e => router.push(`/player/${e.movieId}?t=${e.positionSeconds}`)}
          />
        )}

        <section ref={libRef} className="sv-library" aria-labelledby="library-title">
          <div className="sv-lib-head">
            <div className="sv-lib-title">
              <h2 id="library-title" className="sv-section-title">Library</h2>
              <span className="sv-count" aria-live="polite">{total} {total === 1 ? 'movie' : 'movies'}</span>
            </div>
            <div className="sv-lib-controls">
              <button type="button" className="sv-btn-sm" aria-expanded={panelOpen} onClick={() => setPanelOpen(o => !o)}>
                Filters{filterCount > 0 && <span className="sv-badge">{filterCount}</span>}
              </button>
              <label className="sv-sort">
                <span className="sv-sr">Sort by</span>
                <select className="sv-select" value={state.sort} onChange={e => update({ sort: e.target.value })}>
                  {SORTS.map(s => <option key={s.value} value={s.value}>{s.label}</option>)}
                </select>
              </label>
            </div>
          </div>

          {panelOpen && (
            <FilterPanel options={options} filters={state.filters} onChange={f => update({ filters: f })} />
          )}

          <ActiveFilters
            q={state.q}
            filters={state.filters}
            onChange={f => update({ filters: f })}
            onClearSearch={() => update({ q: '' })}
            onClearAll={() => update({ q: '', filters: noFilters })}
          />

          {showSkeleton ? (
            <div className="sv-grid" aria-hidden="true">
              {Array.from({ length: 12 }, (_, i) => <div key={i} className="sv-sk" />)}
            </div>
          ) : movies.length === 0 ? (
            <div className="sv-empty">
              <p>No movies match.</p>
              {(state.q || filterCount > 0) && (
                <button type="button" className="sv-btn-sm" onClick={() => update({ q: '', filters: noFilters })}>Clear search and filters</button>
              )}
            </div>
          ) : (
            <div className={`sv-grid${loadingList ? ' is-loading' : ''}`}>
              {movies.map(m => <PosterCard key={m.id} movie={m} />)}
            </div>
          )}

          <Pagination page={state.page} totalPages={totalPages} total={total} pageSize={PAGE_SIZE} onPage={goToPage} />
        </section>
      </main>
    </div>
  );
}
