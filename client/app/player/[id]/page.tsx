'use client';

import { useEffect, useRef, useState, useCallback } from 'react';
import { useParams, useSearchParams, useRouter } from 'next/navigation';
import Hls from 'hls.js';
import { useAuth } from '@/lib/auth-context';
import { moviesApi, watchApi, streamingApi } from '@/lib/api';
import { MovieDetail, SubtitleFile } from '@/types';

const PROGRESS_INTERVAL = 30;

export default function PlayerPage() {
  const { id } = useParams<{ id: string }>();
  const searchParams = useSearchParams();
  const startTime = Number(searchParams.get('t') || 0);
  const { isAuthenticated, profile, token } = useAuth();
  const router = useRouter();
  const videoRef = useRef<HTMLVideoElement>(null);
  const hlsRef = useRef<Hls | null>(null);
  const lastSavedRef = useRef(0);
  const controlsTimerRef = useRef<NodeJS.Timeout | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  const [movie, setMovie] = useState<MovieDetail | null>(null);
  const [subtitles, setSubtitles] = useState<SubtitleFile[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [showControls, setShowControls] = useState(true);
  const [playing, setPlaying] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [buffered, setBuffered] = useState(0);
  const [volume, setVolume] = useState(1);
  const [muted, setMuted] = useState(false);
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [showSubMenu, setShowSubMenu] = useState(false);
  const [selectedSub, setSelectedSub] = useState<string | null>(null);
  const [skipFlash, setSkipFlash] = useState<'back' | 'forward' | null>(null);

  useEffect(() => {
    if (!isAuthenticated) { router.replace('/login'); return; }
    if (!profile || !token) { router.replace('/profiles'); return; }
    loadAndPlay();
    return () => cleanup();
  }, [id]);

  const loadAndPlay = async () => {
    try {
      const [movieRes, subsRes] = await Promise.all([moviesApi.get(id), streamingApi.getSubtitles(id)]);
      setMovie(movieRes.data);
      setSubtitles(subsRes.data);
    } catch { setError('Failed to load movie.'); }
    finally { setLoading(false); }
  };

  useEffect(() => {
    if (!loading && !error && token && videoRef.current) initHls();
  }, [loading, token]);

  const initHls = () => {
    if (!videoRef.current || !token) return;
    const playlistUrl = `http://localhost:8080/api/stream/${id}/playlist.m3u8?token=${token}`;
    if (Hls.isSupported()) {
      const hls = new Hls({ autoStartLoad: false, startLevel: -1, maxBufferLength: 30, maxMaxBufferLength: 60 });
      hls.loadSource(playlistUrl);
      hls.attachMedia(videoRef.current);
      hls.on(Hls.Events.MANIFEST_PARSED, () => {
        hls.startLoad(0);
        if (videoRef.current) {
          videoRef.current.currentTime = startTime > 0 ? startTime : 0;
          videoRef.current.play().catch(console.error);
        }
      });
      hls.on(Hls.Events.ERROR, (_, data) => {
        if (data.fatal) {
          if (data.details === Hls.ErrorDetails.MANIFEST_LOAD_ERROR || data.details === Hls.ErrorDetails.MANIFEST_LOAD_TIMEOUT) hls.startLoad();
          else setError(`Stream error: ${data.details}`);
        }
      });
      hlsRef.current = hls;
    } else if (videoRef.current.canPlayType('application/vnd.apple.mpegurl')) {
      videoRef.current.src = playlistUrl;
      videoRef.current.currentTime = startTime > 0 ? startTime : 0;
      videoRef.current.play().catch(console.error);
    } else { setError('HLS not supported. Try Chrome.'); }
  };

  const cleanup = () => {
    if (controlsTimerRef.current) clearTimeout(controlsTimerRef.current);
    if (videoRef.current && !videoRef.current.paused) saveProgress(videoRef.current.currentTime);
    if (hlsRef.current) { hlsRef.current.destroy(); hlsRef.current = null; }
    streamingApi.stopStream(id).catch(() => {});
  };

  const saveProgress = async (pos: number) => {
    if (!profile || pos < 5) return;
    lastSavedRef.current = pos;
    await watchApi.recordProgress(profile.id, id, Math.floor(pos)).catch(() => {});
  };

  const handleTimeUpdate = () => {
    if (!videoRef.current) return;
    const pos = videoRef.current.currentTime;
    setCurrentTime(pos);
    if (videoRef.current.buffered.length > 0) setBuffered(videoRef.current.buffered.end(videoRef.current.buffered.length - 1));
    // Update duration from movie runtime if video element reports wrong duration (HLS rolling window)
    if (movie?.runtimeMinutes && videoRef.current.duration !== movie.runtimeMinutes * 60) {
      setDuration(movie.runtimeMinutes * 60);
    }
    if (pos - lastSavedRef.current >= PROGRESS_INTERVAL) saveProgress(pos);
  };

  const handleEnded = async () => {
    if (!profile) return;
    await watchApi.recordComplete(profile.id, id, Math.floor(duration)).catch(() => {});
    router.push(`/movie/${id}`);
  };

  const togglePlay = () => { if (!videoRef.current) return; videoRef.current.paused ? videoRef.current.play() : videoRef.current.pause(); };

  const skipSeconds = (secs: number) => {
    if (!videoRef.current) return;
    videoRef.current.currentTime = Math.max(0, Math.min(duration, videoRef.current.currentTime + secs));
    setSkipFlash(secs > 0 ? 'forward' : 'back');
    setTimeout(() => setSkipFlash(null), 600);
  };

  const seek = (pct: number) => {
    const t = pct * duration;
    if (videoRef.current) videoRef.current.currentTime = t;
    setCurrentTime(t);
  };

  const toggleMute = () => {
    if (!videoRef.current) return;
    const nm = !muted; setMuted(nm); videoRef.current.muted = nm;
  };

  const toggleFullscreen = () => {
    if (!containerRef.current) return;
    if (document.fullscreenElement) document.exitFullscreen();
    else containerRef.current.requestFullscreen();
  };

  useEffect(() => {
    const onFs = () => setIsFullscreen(!!document.fullscreenElement);
    document.addEventListener('fullscreenchange', onFs);
    return () => document.removeEventListener('fullscreenchange', onFs);
  }, []);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.target instanceof HTMLInputElement) return;
      switch (e.key) {
        case ' ': case 'k': e.preventDefault(); togglePlay(); break;
        case 'ArrowRight': e.preventDefault(); skipSeconds(10); break;
        case 'ArrowLeft': e.preventDefault(); skipSeconds(-10); break;
        case 'f': toggleFullscreen(); break;
        case 'm': toggleMute(); break;
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [muted, playing]);

  const showControlsTemporarily = useCallback(() => {
    setShowControls(true);
    if (controlsTimerRef.current) clearTimeout(controlsTimerRef.current);
    controlsTimerRef.current = setTimeout(() => { if (playing) setShowControls(false); }, 3500);
  }, [playing]);

  const formatTime = (s: number) => {
    if (!s || isNaN(s)) return '0:00';
    const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), sec = Math.floor(s % 60);
    if (h > 0) return `${h}:${String(m).padStart(2, '0')}:${String(sec).padStart(2, '0')}`;
    return `${m}:${String(sec).padStart(2, '0')}`;
  };

  const progressPct = duration > 0 ? (currentTime / duration) * 100 : 0;
  const bufferedPct = duration > 0 ? (buffered / duration) * 100 : 0;
  const visible = showControls || !playing;

  if (loading) return (
    <div style={{ minHeight: '100vh', background: 'black', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 16, fontFamily: 'var(--font-body)' }}>
      <div style={{ width: 48, height: 48, borderRadius: '50%', border: '2px solid rgba(255,255,255,0.1)', borderTop: '2px solid #e50914', animation: 'spin 0.8s linear infinite' }} />
      <p style={{ color: 'rgba(255,255,255,0.3)', fontSize: 12, letterSpacing: '0.2em', textTransform: 'uppercase' }}>Starting stream</p>
      <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
    </div>
  );

  if (error) return (
    <div style={{ minHeight: '100vh', background: 'black', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 16, fontFamily: 'var(--font-body)', padding: 24 }}>
      <div style={{ width: 48, height: 48, borderRadius: '50%', background: 'rgba(229,9,20,0.1)', border: '1px solid rgba(229,9,20,0.2)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <svg width="20" height="20" fill="none" stroke="#e50914" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M12 9v3m0 3h.01M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/></svg>
      </div>
      <p style={{ color: 'rgba(255,255,255,0.5)', fontSize: 14, textAlign: 'center', maxWidth: 360 }}>{error}</p>
      <button onClick={() => router.back()} style={{ background: 'none', border: '1px solid rgba(255,255,255,0.15)', color: 'rgba(255,255,255,0.4)', borderRadius: 6, padding: '10px 24px', fontSize: 12, letterSpacing: '0.15em', textTransform: 'uppercase', cursor: 'pointer' }}>← Back</button>
    </div>
  );

  return (
    <div
      ref={containerRef}
      onMouseMove={showControlsTemporarily}
      onMouseLeave={() => playing && setShowControls(false)}
      style={{ position: 'relative', width: '100%', height: '100vh', background: 'black', overflow: 'hidden', userSelect: 'none', cursor: visible ? 'default' : 'none', fontFamily: 'var(--font-body)' }}
    >
      {/* Video */}
      <video
        ref={videoRef}
        style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: 'contain', zIndex: 1 }}
        onTimeUpdate={handleTimeUpdate}
        onLoadedMetadata={() => { if (videoRef.current) { const movieDuration = movie?.runtimeMinutes ? movie.runtimeMinutes * 60 : videoRef.current.duration; setDuration(movieDuration); } }}
        onPlay={() => setPlaying(true)}
        onPause={() => setPlaying(false)}
        onEnded={handleEnded}
        onClick={togglePlay}
        playsInline
      />

      {/* Controls overlay */}
      <div style={{ position: 'absolute', inset: 0, zIndex: 10, display: 'flex', flexDirection: 'column', justifyContent: 'space-between', opacity: visible ? 1 : 0, transition: 'opacity 0.3s ease', pointerEvents: visible ? 'auto' : 'none' }}>

        {/* Top bar */}
        <div style={{ padding: '20px 24px 60px', background: 'linear-gradient(to bottom, rgba(0,0,0,0.85) 0%, transparent 100%)' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <button onClick={() => router.push(`/movie/${id}`)} style={{ background: 'none', border: 'none', color: 'white', cursor: 'pointer', padding: 8, display: 'flex', alignItems: 'center', gap: 6, fontSize: 14, opacity: 0.8 }}
              onMouseEnter={e => (e.currentTarget.style.opacity = '1')} onMouseLeave={e => (e.currentTarget.style.opacity = '0.8')}>
              <svg width="20" height="20" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7"/></svg>
            </button>
            <span style={{ color: 'white', fontSize: 15, fontWeight: 400, letterSpacing: '0.02em', opacity: 0.9 }}>{movie?.title}</span>
            <div style={{ width: 36 }} />
          </div>
        </div>

        {/* Center controls */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 48 }}>
          {/* Skip back */}
          <button onClick={() => skipSeconds(-10)} style={{ background: 'none', border: 'none', cursor: 'pointer', display: 'flex', flexDirection: 'column', alignItems: 'center', opacity: skipFlash === 'back' ? 1 : 0.85, transform: skipFlash === 'back' ? 'scale(1.15)' : 'scale(1)', transition: 'all 0.15s' }}>
            <div style={{ width: 60, height: 60, borderRadius: '50%', border: '2px solid rgba(255,255,255,0.7)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexDirection: 'column', gap: 1 }}>
              <svg width="22" height="16" viewBox="0 0 22 16" fill="white">
                <path d="M10 0 L0 8 L10 16 L10 11 C15 11 19 13 21 16 C21 10 18 4 10 4 Z"/>
              </svg>
              <span style={{ fontSize: 11, color: 'white', fontWeight: 700, lineHeight: 1 }}>10</span>
            </div>
          </button>

          {/* Play/Pause */}
          <button onClick={togglePlay} style={{ background: 'none', border: 'none', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', transition: 'transform 0.15s' }}
            onMouseEnter={e => (e.currentTarget.style.transform = 'scale(1.08)')}
            onMouseLeave={e => (e.currentTarget.style.transform = 'scale(1)')}>
            {playing ? (
              <svg width="64" height="64" fill="white" viewBox="0 0 24 24"><path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/></svg>
            ) : (
              <svg width="64" height="64" fill="white" viewBox="0 0 24 24" style={{ marginLeft: 4 }}><path d="M8 5v14l11-7z"/></svg>
            )}
          </button>

          {/* Skip forward */}
          <button onClick={() => skipSeconds(10)} style={{ background: 'none', border: 'none', cursor: 'pointer', display: 'flex', flexDirection: 'column', alignItems: 'center', opacity: skipFlash === 'forward' ? 1 : 0.85, transform: skipFlash === 'forward' ? 'scale(1.15)' : 'scale(1)', transition: 'all 0.15s' }}>
            <div style={{ width: 60, height: 60, borderRadius: '50%', border: '2px solid rgba(255,255,255,0.7)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexDirection: 'column', gap: 1 }}>
              <svg width="22" height="16" viewBox="0 0 22 16" fill="white">
                <path d="M12 0 L22 8 L12 16 L12 11 C7 11 3 13 1 16 C1 10 4 4 12 4 Z"/>
              </svg>
              <span style={{ fontSize: 11, color: 'white', fontWeight: 700, lineHeight: 1 }}>10</span>
            </div>
          </button>
        </div>

        {/* Bottom controls */}
        <div style={{ padding: '60px 24px 24px', background: 'linear-gradient(to top, rgba(0,0,0,0.9) 0%, transparent 100%)' }}>
          {/* Scrubber */}
          <div style={{ position: 'relative', display: 'flex', alignItems: 'center', marginBottom: 16, cursor: 'pointer' }}
            onClick={e => { const rect = e.currentTarget.getBoundingClientRect(); seek((e.clientX - rect.left) / rect.width); }}>
            {/* Track */}
            <div style={{ flex: 1, height: 3, background: 'rgba(255,255,255,0.2)', borderRadius: 3, position: 'relative', overflow: 'visible' }}>
              {/* Buffered */}
              <div style={{ position: 'absolute', inset: 0, width: `${bufferedPct}%`, background: 'rgba(255,255,255,0.35)', borderRadius: 3 }} />
              {/* Played */}
              <div style={{ position: 'absolute', inset: 0, width: `${progressPct}%`, background: '#e50914', borderRadius: 3 }} />
              {/* Red dot thumb */}
              <div style={{ position: 'absolute', top: '50%', left: `${progressPct}%`, transform: 'translate(-50%, -50%)', width: 14, height: 14, borderRadius: '50%', background: '#e50914', boxShadow: '0 0 8px rgba(229,9,20,0.6)' }} />
            </div>
            {/* Time right */}
            <span style={{ color: 'white', fontSize: 15, fontWeight: 500, marginLeft: 16, minWidth: 52, textAlign: 'right', fontVariantNumeric: 'tabular-nums' }}>
              {formatTime(duration > 0 ? duration - currentTime : 0)}
            </span>
          </div>

          {/* Bottom row */}
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            {/* Left: volume + time */}
            <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
              <button onClick={toggleMute} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'white', opacity: 0.8, padding: 4 }}>
                {muted || volume === 0
                  ? <svg width="20" height="20" fill="white" viewBox="0 0 24 24"><path d="M16.5 12A4.5 4.5 0 0014 7.97v2.21l2.45 2.45c.03-.2.05-.41.05-.63zm2.5 0c0 .94-.2 1.82-.54 2.64l1.51 1.51A8.796 8.796 0 0021 12c0-4.28-2.99-7.86-7-8.77v2.06c2.89.86 5 3.54 5 6.71zM4.27 3L3 4.27 7.73 9H3v6h4l5 5v-6.73l4.25 4.25c-.67.52-1.42.93-2.25 1.18v2.06A8.99 8.99 0 0019 19.73L20.73 21 22 19.73 5.27 2 4.27 3zM12 4L9.91 6.09 12 8.18V4z"/></svg>
                  : <svg width="20" height="20" fill="white" viewBox="0 0 24 24"><path d="M3 9v6h4l5 5V4L7 9H3zm13.5 3A4.5 4.5 0 0014 7.97v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z"/></svg>
                }
              </button>
              <span style={{ color: 'rgba(255,255,255,0.6)', fontSize: 13, fontVariantNumeric: 'tabular-nums' }}>
                {formatTime(currentTime)} <span style={{ opacity: 0.4 }}>/ {formatTime(duration)}</span>
              </span>
            </div>

            {/* Right: subtitles + fullscreen */}
            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              {subtitles.length > 0 && (
                <div style={{ position: 'relative' }}>
                  <button onClick={() => setShowSubMenu(!showSubMenu)} style={{
                    background: 'none', border: '1px solid rgba(255,255,255,0.25)', color: 'rgba(255,255,255,0.7)',
                    borderRadius: 4, padding: '4px 10px', fontSize: 11, letterSpacing: '0.1em', textTransform: 'uppercase', cursor: 'pointer',
                  }}>
                    {selectedSub ? `CC · ${selectedSub}` : 'CC'}
                  </button>
                  {showSubMenu && (
                    <div style={{ position: 'absolute', bottom: '100%', right: 0, marginBottom: 8, background: 'rgba(20,20,20,0.97)', border: '1px solid rgba(255,255,255,0.1)', borderRadius: 10, overflow: 'hidden', minWidth: 130, boxShadow: '0 8px 32px rgba(0,0,0,0.6)' }}>
                      <button onClick={() => { setSelectedSub(null); setShowSubMenu(false); }} style={{ width: '100%', textAlign: 'left', padding: '10px 16px', fontSize: 12, background: 'none', border: 'none', cursor: 'pointer', color: !selectedSub ? '#e50914' : 'rgba(255,255,255,0.5)' }}>Off</button>
                      {subtitles.map(sub => (
                        <button key={sub.filename} onClick={() => { setSelectedSub(sub.language); setShowSubMenu(false); }} style={{ width: '100%', textAlign: 'left', padding: '10px 16px', fontSize: 12, background: 'none', border: 'none', cursor: 'pointer', color: selectedSub === sub.language ? '#e50914' : 'rgba(255,255,255,0.5)' }}>
                          {sub.language}
                        </button>
                      ))}
                    </div>
                  )}
                </div>
              )}
              <button onClick={toggleFullscreen} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'white', opacity: 0.8, padding: 4 }}>
                {isFullscreen
                  ? <svg width="20" height="20" fill="white" viewBox="0 0 24 24"><path d="M5 16h3v3h2v-5H5v2zm3-8H5v2h5V5H8v3zm6 11h2v-3h3v-2h-5v5zm2-11V5h-2v5h5V8h-3z"/></svg>
                  : <svg width="20" height="20" fill="white" viewBox="0 0 24 24"><path d="M7 14H5v5h5v-2H7v-3zm-2-4h2V7h3V5H5v5zm12 7h-3v2h5v-5h-2v3zM14 5v2h3v3h2V5h-5z"/></svg>
                }
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}