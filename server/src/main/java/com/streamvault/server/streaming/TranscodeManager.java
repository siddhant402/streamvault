package com.streamvault.server.streaming;

import com.streamvault.server.config.AppProperties;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Manages on-the-fly FFmpeg transcode sessions.
 *
 * - One session per movieId (shared across viewers of the same movie)
 * - Sessions idle for 30 minutes are killed and their temp files deleted
 * - All sessions are killed on app shutdown
 */
@Component
@RequiredArgsConstructor
@Slf4j
@EnableScheduling
public class TranscodeManager {

    private final AppProperties appProperties;
    private final FfprobeService ffprobeService;

    /** movieId → active session */
    private final Map<UUID, TranscodeSession> sessions = new ConcurrentHashMap<>();

    /** How long a session can be idle before cleanup */
    private static final int IDLE_MINUTES = 30;

    /**
     * Returns an existing session for the movie, or starts a new one.
     * Blocks until the playlist file (.m3u8) is available (up to 60s).
     */
    public TranscodeSession getOrStartSession(UUID movieId, String sourcePath)
            throws IOException, InterruptedException {

        TranscodeSession existing = sessions.get(movieId);
        if (existing != null && existing.isAlive()) {
            existing.touch();
            log.debug("Reusing existing transcode session for movie {}", movieId);
            return existing;
        }

        // Start a new session
        return startSession(movieId, sourcePath);
    }

    private TranscodeSession startSession(UUID movieId, String sourcePath)
            throws IOException, InterruptedException {

        // Create a temp directory for this session's segments
        Path hlsBase = Path.of(appProperties.media().hlsOutputPath());
        Files.createDirectories(hlsBase);
        Path outputDir = hlsBase.resolve(movieId.toString());
        Files.createDirectories(outputDir);

        String playlistPath = outputDir.resolve("playlist.m3u8").toAbsolutePath().toString();

        TranscodeSession session = new TranscodeSession(movieId, outputDir, playlistPath);
        sessions.put(movieId, session);

        // Probe codec — use stream copy if already H.264 (much faster start)
        FfprobeService.ProbeResult probe = ffprobeService.probe(sourcePath);
        log.info("Starting transcode for movie {} — codec={}, streamCopy={}",
                movieId, probe.videoCodec(), probe.isH264());

        List<String> cmd = buildFfmpegCommand(sourcePath, outputDir, playlistPath, probe.isH264());
        log.debug("FFmpeg command: {}", String.join(" ", cmd));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        // Log FFmpeg output to a file in the session dir for debugging
        pb.redirectOutput(outputDir.resolve("ffmpeg.log").toFile());

        Process process = pb.start();
        session.setFfmpegProcess(process);
        session.setState(TranscodeSession.State.RUNNING);

        // Watch the process in a background thread to detect failures
        Thread.ofVirtual().start(() -> {
            try {
                int exitCode = process.waitFor();
                session.setState(exitCode == 0
                        ? TranscodeSession.State.DONE
                        : TranscodeSession.State.FAILED);
                log.info("FFmpeg exited with code {} for movie {}", exitCode, movieId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Wait for the playlist file to appear (player can't start without it)
        waitForPlaylist(playlistPath, 60);

        return session;
    }

    private List<String> buildFfmpegCommand(
            String sourcePath, Path outputDir, String playlistPath, boolean isH264) {

        String ffmpeg = appProperties.media().ffmpegPath();
        List<String> cmd = new ArrayList<>();

        cmd.add(ffmpeg);
        cmd.addAll(List.of("-i", sourcePath));

        if (isH264) {
            // Stream copy — no re-encode, very fast start
            cmd.addAll(List.of(
                    "-c:v", "copy",
                    "-c:a", "aac",
                    "-b:a", "192k"
            ));
        } else {
            // Full re-encode to H.264
            // Note: -movflags +faststart is NOT compatible with HLS output
            cmd.addAll(List.of(
                    "-c:v", "libx264",
                    "-preset", "veryfast",
                    "-crf", "23",
                    "-c:a", "aac",
                    "-b:a", "192k"
            ));
        }

        // HLS output settings
        // Use File.separator-agnostic path construction
        String segPattern = outputDir.toAbsolutePath().toString()
                + java.io.File.separator + "seg%d.ts";

        cmd.addAll(List.of(
                "-f", "hls",
                "-hls_time", "4",
                "-hls_list_size", "0",
                "-hls_segment_type", "mpegts",
                "-hls_flags", "independent_segments",
                "-hls_segment_filename", segPattern,
                playlistPath
        ));

        return cmd;
    }

    private void waitForPlaylist(String playlistPath, int timeoutSeconds)
            throws InterruptedException {
        Path path = Path.of(playlistPath);
        int waited = 0;
        while (!Files.exists(path) && waited < timeoutSeconds) {
            TimeUnit.SECONDS.sleep(1);
            waited++;
        }
        if (!Files.exists(path)) {
            log.warn("Playlist not created after {}s — FFmpeg may have failed. Check ffmpeg.log", timeoutSeconds);
        } else {
            log.info("Playlist ready after {}s: {}", waited, playlistPath);
        }
    }

    /**
     * Kills and cleans up sessions idle for more than IDLE_MINUTES.
     * Runs every 10 minutes.
     */
    @Scheduled(fixedDelay = 600_000)
    public void cleanupIdleSessions() {
        Instant cutoff = Instant.now().minus(IDLE_MINUTES, ChronoUnit.MINUTES);
        sessions.entrySet().removeIf(entry -> {
            TranscodeSession session = entry.getValue();
            if (session.getLastAccessedAt().isBefore(cutoff)) {
                log.info("Cleaning up idle session for movie {}", entry.getKey());
                session.kill();
                deleteSessionFiles(session.getOutputDir());
                return true;
            }
            return false;
        });
    }

    /** Kill all sessions and delete temp files on app shutdown */
    @PreDestroy
    public void shutdownAll() {
        log.info("Shutting down {} transcode session(s)...", sessions.size());
        sessions.values().forEach(session -> {
            session.kill();
            deleteSessionFiles(session.getOutputDir());
        });
        sessions.clear();
    }

    private void deleteSessionFiles(Path dir) {
        try {
            if (Files.exists(dir)) {
                try (var stream = Files.walk(dir)) {
                    stream.sorted(Comparator.reverseOrder())
                            .forEach(p -> {
                                try { Files.delete(p); }
                                catch (IOException e) { log.warn("Could not delete {}", p); }
                            });
                }
                log.debug("Deleted session dir: {}", dir);
            }
        } catch (IOException e) {
            log.warn("Error deleting session dir {}: {}", dir, e.getMessage());
        }
    }

    public void removeSession(UUID movieId) {
        sessions.remove(movieId);
    }

    public Optional<TranscodeSession> getSession(UUID movieId) {
        return Optional.ofNullable(sessions.get(movieId));
    }
}