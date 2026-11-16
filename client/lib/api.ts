import axios from 'axios';
import { FilterOptions, MovieFilters } from '@/types';
import { goTo } from '@/lib/navigation';

// Default: same address as the web app, which forwards /api/* to the server (next.config.ts).
// Set NEXT_PUBLIC_API_URL only to call a server on another address directly.
const API_BASE = process.env.NEXT_PUBLIC_API_URL ?? '';

const api = axios.create({
  baseURL: API_BASE,
  withCredentials: true,   // send the HttpOnly session cookie
});

/** Id of the chosen profile, sent so the server can apply its age limit. */
export function currentProfileId(): string | null {
  try {
    const stored = localStorage.getItem('sv_profile');
    return stored ? (JSON.parse(stored).id ?? null) : null;
  } catch {
    return null;
  }
}

api.interceptors.request.use((config) => {
  const profileId = currentProfileId();
  if (profileId) config.headers['X-Profile-Id'] = profileId;
  return config;
});

// Signed out or session expired: go to the sign-in page (but not for the sign-in calls themselves)
api.interceptors.response.use(
  (res) => res,
  (err) => {
    const url: string = err.config?.url ?? '';
    if (err.response?.status === 401 && !url.startsWith('/api/auth/') && window.location.pathname !== '/login') {
      try { localStorage.removeItem('sv_profile'); } catch {}
      goTo('/login');
    }
    return Promise.reject(err);
  }
);

// ── Auth ─────────────────────────────────────────────────────────────────────

export const authApi = {
  register: (email: string, password: string, inviteCode?: string) =>
    api.post('/api/auth/register', { email, password, inviteCode: inviteCode || undefined }),
  login: (email: string, password: string) =>
    api.post<{ email: string; role: string }>('/api/auth/login', { email, password }),
  logout: () => api.post('/api/auth/logout'),
  me: () => api.get<{ email: string; role: string }>('/api/auth/me'),
};

// ── Movies ───────────────────────────────────────────────────────────────────

export const moviesApi = {
  list: (page = 0, size = 20, sortBy = 'title', direction = 'asc', search?: string, filters?: MovieFilters) =>
    api.get('/api/movies', {
      params: {
        page, size, sortBy, direction, search,
        genre: filters?.genres?.length ? filters.genres : undefined,
        yearFrom: filters?.yearFrom,
        yearTo: filters?.yearTo,
        actor: filters?.actor || undefined,
      },
      paramsSerializer: { indexes: null }, // genre=a&genre=b
    }),

  filterOptions: () => api.get<FilterOptions>('/api/movies/filters'),

  get: (id: string) =>
    api.get(`/api/movies/${id}`),
};

// ── Profiles ─────────────────────────────────────────────────────────────────

export const profilesApi = {
  list: () => api.get('/api/profiles'),
  create: (name: string, ageRestriction = 18) =>
    api.post('/api/profiles', { name, ageRestriction }),
  update: (id: string, data: { name?: string; avatarUrl?: string }) =>
    api.put(`/api/profiles/${id}`, data),
  delete: (id: string) => api.delete(`/api/profiles/${id}`),
};

// ── Watch ─────────────────────────────────────────────────────────────────────

export const watchApi = {
  recordProgress: (profileId: string, movieId: string, positionSeconds: number) =>
    api.post(`/api/profiles/${profileId}/watch/events`, {
      movieId,
      eventType: 'PROGRESS',
      positionSeconds,
    }),
  recordComplete: (profileId: string, movieId: string, positionSeconds: number) =>
    api.post(`/api/profiles/${profileId}/watch/events`, {
      movieId,
      eventType: 'COMPLETE',
      positionSeconds,
    }),
  getResumePosition: (profileId: string, movieId: string) =>
    api.get(`/api/profiles/${profileId}/watch/resume/${movieId}`),
  getContinueWatching: (profileId: string) =>
    api.get(`/api/profiles/${profileId}/watch/continue`),
};

// ── Streaming ─────────────────────────────────────────────────────────────────

export const streamingApi = {
  // No token in the URL by default: hls.js sends the session cookie instead.
  // streamToken is only for native HLS (Safari / iOS), which cannot send headers.
  getPlaylistUrl: (movieId: string, streamToken?: string) =>
    `${API_BASE}/api/stream/${movieId}/playlist.m3u8` +
    (streamToken ? `?token=${encodeURIComponent(streamToken)}` : ''),
  getStreamToken: (movieId: string) =>
    api.post<{ token: string; expiresAt: string }>(`/api/stream/${movieId}/token`),
  getSubtitles: (movieId: string) =>
    api.get(`/api/stream/${movieId}/subtitles`),
  stopStream: (movieId: string) =>
    api.delete(`/api/stream/${movieId}/session`),
};

export default api;
