import axios from 'axios';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

const api = axios.create({
  baseURL: API_BASE,
});

// Attach JWT token to every request
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('sv_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Redirect to login on 401
api.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      localStorage.removeItem('sv_token');
      localStorage.removeItem('sv_profile');
      window.location.href = '/login';
    }
    return Promise.reject(err);
  }
);

// ── Auth ─────────────────────────────────────────────────────────────────────

export const authApi = {
  register: (email: string, password: string) =>
    api.post('/api/auth/register', { email, password }),
  login: (email: string, password: string) =>
    api.post<{ token: string; email: string; role: string }>(
      '/api/auth/login', { email, password }
    ),
};

// ── Movies ───────────────────────────────────────────────────────────────────

export const moviesApi = {
  list: (page = 0, size = 20, sortBy = 'title', direction = 'asc', search?: string) =>
    api.get('/api/movies', { params: { page, size, sortBy, direction, search } }),

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
  getPlaylistUrl: (movieId: string, token: string) =>
    `${API_BASE}/api/stream/${movieId}/playlist.m3u8?token=${token}`,
  getSubtitles: (movieId: string) =>
    api.get(`/api/stream/${movieId}/subtitles`),
  stopStream: (movieId: string) =>
    api.delete(`/api/stream/${movieId}/session`),
};

export default api;
