package com.streamvault.server.streaming;

import com.streamvault.server.config.AppProperties;
import com.streamvault.server.exception.TooManyRequestsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** V7: FFmpeg sessions are capped overall and per account. */
@DisplayName("V7 - transcode caps")
class V7TranscodeCapsTest {

    private static TranscodeManager manager(int maxSessions, int maxPerUser) {
        AppProperties props = new AppProperties(
                new AppProperties.Media("target/none", "target/hls", "ffmpeg"),
                new AppProperties.Jwt("unused", 60_000, null),
                new AppProperties.Tmdb("", "", null),
                null,
                new AppProperties.Streaming(maxSessions, maxPerUser),
                null);
        return new TranscodeManager(props, null);
    }

    private static TranscodeSession session(String owner, TranscodeSession.State state) {
        UUID id = UUID.randomUUID();
        TranscodeSession s = new TranscodeSession(id, Path.of("target/hls", id.toString()), "playlist.m3u8");
        s.setOwner(owner);
        s.setState(state);
        return s;
    }

    @Test
    @DisplayName("one account cannot run more than its own limit")
    void perUserCap() {
        TranscodeManager m = manager(5, 1);
        m.track(session("ana@test.local", TranscodeSession.State.STARTING));

        assertThatThrownBy(() -> m.checkCapacity("ana@test.local")).isInstanceOf(TooManyRequestsException.class);
        assertThatCode(() -> m.checkCapacity("raj@test.local")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("nobody can start more than the server-wide limit")
    void globalCap() {
        TranscodeManager m = manager(2, 2);
        m.track(session("ana@test.local", TranscodeSession.State.STARTING));
        m.track(session("raj@test.local", TranscodeSession.State.STARTING));

        assertThatThrownBy(() -> m.checkCapacity("new@test.local"))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("limit");
    }

    @Test
    @DisplayName("finished or failed transcodes do not count")
    void finishedSessionsDoNotCount() {
        TranscodeManager m = manager(1, 1);
        m.track(session("ana@test.local", TranscodeSession.State.DONE));
        m.track(session("ana@test.local", TranscodeSession.State.FAILED));

        assertThatCode(() -> m.checkCapacity("ana@test.local")).doesNotThrowAnyException();
    }
}
