import { describe, it, expect, beforeEach, vi } from 'vitest';
import type { AxiosResponse, InternalAxiosRequestConfig } from 'axios';

vi.mock('@/lib/navigation', () => ({ goTo: vi.fn() }));

import api, { authApi, streamingApi, currentProfileId } from '@/lib/api';
import { goTo } from '@/lib/navigation';

/** Replaces the network: records each request and answers with the given status. */
let sent: InternalAxiosRequestConfig[] = [];
function respondWith(status: number, data: unknown = {}) {
  api.defaults.adapter = async (config) => {
    sent.push(config);
    const response = { data, status, statusText: '', headers: {}, config } as AxiosResponse;
    if (status >= 400) {
      throw Object.assign(new Error(`HTTP ${status}`), { config, response, isAxiosError: true });
    }
    return response;
  };
}
const last = () => sent[sent.length - 1];
const body = (c: InternalAxiosRequestConfig) => JSON.parse(c.data as string);

beforeEach(() => {
  sent = [];
  window.history.pushState({}, '', '/home');
});

describe('V12 - session cookie, no stored token', () => {
  it('sends the session cookie and no Authorization header', async () => {
    localStorage.setItem('sv_token', 'old-token-from-previous-version');
    respondWith(200);
    await api.get('/api/movies');

    expect(last().withCredentials).toBe(true);
    expect(last().headers.Authorization).toBeUndefined();
  });

  it('login and register do not expect a token to store', async () => {
    respondWith(200, { email: 'a@b.c', role: 'USER' });
    await authApi.login('a@b.c', 'pw');
    expect(body(last())).toEqual({ email: 'a@b.c', password: 'pw' });
  });

  it('has sign-out and who-am-I calls', async () => {
    respondWith(204);
    await authApi.logout();
    expect(last().method).toBe('post');
    expect(last().url).toBe('/api/auth/logout');

    respondWith(200, { email: 'a@b.c', role: 'USER' });
    await authApi.me();
    expect(last().method).toBe('get');
    expect(last().url).toBe('/api/auth/me');
  });

  it('a 401 from the API sends the user to sign in and forgets the profile', async () => {
    localStorage.setItem('sv_profile', JSON.stringify({ id: 'p-1' }));
    respondWith(401);
    await expect(api.get('/api/movies')).rejects.toThrow();

    expect(goTo).toHaveBeenCalledWith('/login');
    expect(localStorage.getItem('sv_profile')).toBeNull();
  });

  it('a 401 from the sign-in calls themselves does not redirect (no loop)', async () => {
    respondWith(401);
    await expect(authApi.me()).rejects.toThrow();
    await expect(authApi.login('a@b.c', 'wrong')).rejects.toThrow();
    expect(goTo).not.toHaveBeenCalled();
  });

  it('a 401 while already on the sign-in page does not redirect', async () => {
    window.history.pushState({}, '', '/login');
    respondWith(401);
    await expect(api.get('/api/movies')).rejects.toThrow();
    expect(goTo).not.toHaveBeenCalled();
  });
});

describe('V10 - profile sent with every request', () => {
  it('adds X-Profile-Id for the chosen profile', async () => {
    localStorage.setItem('sv_profile', JSON.stringify({ id: 'kid-profile', name: 'Kid' }));
    respondWith(200);
    await api.get('/api/movies');
    expect(last().headers['X-Profile-Id']).toBe('kid-profile');
  });

  it('sends no profile header before a profile is chosen', async () => {
    respondWith(200);
    await api.get('/api/profiles');
    expect(last().headers['X-Profile-Id']).toBeUndefined();
  });

  it('ignores a corrupt stored profile instead of crashing', () => {
    localStorage.setItem('sv_profile', '{not json');
    expect(currentProfileId()).toBeNull();
  });
});

describe('V6 - invite code on registration', () => {
  it('sends the invite code when given', async () => {
    respondWith(201, {});
    await authApi.register('a@b.c', 'Password123!', 'family-code');
    expect(body(last())).toEqual({ email: 'a@b.c', password: 'Password123!', inviteCode: 'family-code' });
  });

  it('leaves it out when empty', async () => {
    respondWith(201, {});
    await authApi.register('a@b.c', 'Password123!', '');
    expect(body(last())).not.toHaveProperty('inviteCode');
  });
});

describe('V2 - no login token in playback URLs', () => {
  it('playlist URL has no token by default', () => {
    const url = streamingApi.getPlaylistUrl('movie-1');
    expect(url).toMatch(/\/api\/stream\/movie-1\/playlist\.m3u8$/);
    expect(url).not.toContain('token');
  });

  it('a movie stream token is URL-encoded', () => {
    expect(streamingApi.getPlaylistUrl('movie-1', 'a+b/c=')).toMatch(/\?token=a%2Bb%2Fc%3D$/);
  });
});
