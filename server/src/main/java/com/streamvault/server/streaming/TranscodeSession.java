package com.streamvault.server.streaming;

import lombok.Data;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

/**
 * Represents an active or completed on-the-fly transcode session.
 * One session per movie per viewer (keyed by movieId).
 */
@Data
public class TranscodeSession {

    public enum State { STARTING, RUNNING, DONE, FAILED }

    private final UUID movieId;
    private final Path outputDir;       // temp dir where .ts segments and .m3u8 are written
    private final String playlistPath;  // absolute path to the .m3u8 file

    private Process ffmpegProcess;
    private State state = State.STARTING;
    private Instant startedAt = Instant.now();
    private Instant lastAccessedAt = Instant.now();

    public void touch() {
        lastAccessedAt = Instant.now();
    }

    public boolean isAlive() {
        return ffmpegProcess != null && ffmpegProcess.isAlive();
    }

    public void kill() {
        if (ffmpegProcess != null && ffmpegProcess.isAlive()) {
            ffmpegProcess.destroyForcibly();
        }
        state = State.DONE;
    }
}
