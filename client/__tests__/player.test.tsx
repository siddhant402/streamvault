import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, waitFor } from '@testing-library/react';

// ── Fakes ────────────────────────────────────────────────────────────────────

const hls = vi.hoisted(() => ({
  supported: true,
  configs: [] as Array<{ xhrSetup?: (xhr: XMLHttpRequest, url: string) => void }>,
  sources: [] as string[],
}));

vi.mock('hls.js', () => {
  class FakeHls {
    static isSupported = () => hls.supported;
    static Events = { MANIFEST_PARSED: 'manifestParsed', ERROR: 'error' };
    static ErrorDetails = { MANIFEST_LOAD_ERROR: 'a', MANIFEST_LOAD_TIMEOUT: 'b' };
    constructor(config: (typeof hls.configs)[number]) { hls.configs.push(config); }
    loadSource(url: string) { hls.sources.push(url); }
    attachMedia() {}
    on() {}
    startLoad() {}
    destroy() {}
  }
  return { default: FakeHls };
});

vi.mock('next/navigation', () => ({
  useParams: () => ({ id: 'movie-1' }),
  useSearchParams: () => new URLSearchParams(),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), back: vi.fn() }),
}));

vi.mock('@/lib/auth-context', () => ({
  useAuth: () => ({ isAuthenticated: true, profile: { id: 'kid-profile', name: 'Kid', ageRestriction: 7 } }),
}));

vi.mock('@/lib/api', () => ({
  moviesApi: { get: vi.fn(async () => ({ data: { id: 'movie-1', title: 'Test', runtimeMinutes: 90 } })) },
  watchApi: { recordProgress: vi.fn(async () => ({})), recordComplete: vi.fn(async () => ({})) },
  streamingApi: {
    getSubtitles: vi.fn(async () => ({ data: [] })),
    stopStream: vi.fn(async () => ({})),
    getStreamToken: vi.fn(async () => ({ data: { token: 'movie-scoped-token', expiresAt: '2026-11-14T18:00:00Z' } })),
    getPlaylistUrl: (id: string, streamToken?: string) =>
      `/api/stream/${id}/playlist.m3u8` + (streamToken ? `?token=${encodeURIComponent(streamToken)}` : ''),
  },
}));

import PlayerPage from '@/app/player/[id]/page';
import { streamingApi } from '@/lib/api';

beforeEach(() => {
  hls.supported = true;
  hls.configs = [];
  hls.sources = [];
  HTMLMediaElement.prototype.play = vi.fn().mockResolvedValue(undefined);
  HTMLMediaElement.prototype.canPlayType = vi.fn().mockReturnValue('');
  // jsdom has no media playback: make seeking a no-op
  Object.defineProperty(HTMLMediaElement.prototype, 'currentTime', {
    configurable: true, get: () => 0, set: () => {},
  });
});

// ── Tests ────────────────────────────────────────────────────────────────────

describe('player (Chrome, Firefox, Edge via hls.js)', () => {
  it('V2/V12: playlist URL has no token; requests carry the cookie, not a token', async () => {
    render(<PlayerPage />);
    await waitFor(() => expect(hls.configs).toHaveLength(1));

    expect(hls.sources[0]).toBe('/api/stream/movie-1/playlist.m3u8');

    const xhr = { withCredentials: false, setRequestHeader: vi.fn() };
    hls.configs[0].xhrSetup!(xhr as unknown as XMLHttpRequest, '/api/stream/movie-1/segments/seg0.ts');

    expect(xhr.withCredentials).toBe(true);
    const headerNames = xhr.setRequestHeader.mock.calls.map(c => c[0]);
    expect(headerNames).not.toContain('Authorization');
  });

  it('V10: every playlist and segment request says which profile is watching', async () => {
    render(<PlayerPage />);
    await waitFor(() => expect(hls.configs).toHaveLength(1));

    const xhr = { withCredentials: false, setRequestHeader: vi.fn() };
    hls.configs[0].xhrSetup!(xhr as unknown as XMLHttpRequest, '/api/stream/movie-1/playlist.m3u8');
    expect(xhr.setRequestHeader).toHaveBeenCalledWith('X-Profile-Id', 'kid-profile');
  });
});

describe('player (Safari / iOS native HLS)', () => {
  it('V2: uses a short-lived movie token, never the login token', async () => {
    hls.supported = false;
    HTMLMediaElement.prototype.canPlayType = vi.fn().mockReturnValue('maybe');

    const { container } = render(<PlayerPage />);
    await waitFor(() => expect(streamingApi.getStreamToken).toHaveBeenCalledWith('movie-1'));

    const video = container.querySelector('video')!;
    await waitFor(() => expect(video.getAttribute('src') ?? video.src).toContain('?token=movie-scoped-token'));
    expect(hls.configs).toHaveLength(0);
  });
});
