package com.streamvault.server.streaming;

import com.streamvault.server.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** V8: file names from URLs cannot reach anything outside the expected folder. */
@DisplayName("V8 - path containment")
class V8PathContainmentTest {

    @TempDir Path root;
    Path sessionDir;
    Path movieFolder;
    final SubtitleService subtitles = new SubtitleService();

    @BeforeEach
    void setUp() throws IOException {
        sessionDir = Files.createDirectories(root.resolve("hls").resolve("movie-1"));
        Files.writeString(sessionDir.resolve("seg0.ts"), "video");
        Files.writeString(sessionDir.resolve("ffmpeg.log"), "C:\\Movies\\secret path");
        Files.writeString(root.resolve("hls").resolve("seg9.ts"), "outside");

        movieFolder = Files.createDirectories(root.resolve("movies").resolve("Inception (2010)"));
        Files.writeString(movieFolder.resolve("Inception.en.srt"), "1\n00:00:01,000 --> 00:00:02,000\nHi\n");
        Files.writeString(movieFolder.resolve("notes.txt"), "not a subtitle");
        Path other = Files.createDirectories(root.resolve("movies").resolve("Other"));
        Files.writeString(other.resolve("secret.srt"), "other movie");
        Files.writeString(root.resolve("secret.srt"), "outside");
    }

    // ── Segments ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("a real segment name resolves inside the session folder")
    void validSegment() {
        Path p = StreamingService.resolveSegment(sessionDir, "seg0.ts");
        assertThat(p).isEqualTo(sessionDir.toAbsolutePath().normalize().resolve("seg0.ts"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "../seg9.ts", "..\\seg9.ts", "seg0.ts/../../seg9.ts", "..", ".", "",
            "ffmpeg.log", "playlist.m3u8", "seg0.TS", "seg.ts", "seg0.ts.bak", "xseg0.ts",
            "/etc/passwd", "C:\\Windows\\win.ini", "seg0.ts\u0000.log", "seg1234567.ts"
    })
    @DisplayName("anything that is not seg<number>.ts is refused")
    void invalidSegmentsRefused(String name) {
        assertThatThrownBy(() -> StreamingService.resolveSegment(sessionDir, name))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── Subtitles ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("a subtitle file in the movie folder is found")
    void validSubtitle() {
        assertThat(subtitles.findSubtitleFile(movieFolder.toString(), "Inception.en.srt")).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "../secret.srt", "..\\secret.srt", "../../secret.srt", "../Other/secret.srt",
            "..\\Other\\secret.srt", "notes.txt", "C:secret.srt", "", " ", "Inception.en.srt\u0000",
            "sub/Inception.en.srt", "Inception.en.srt/.", "..srt"
    })
    @DisplayName("paths outside the folder and non-subtitle files are refused")
    void invalidSubtitlesRefused(String name) {
        assertThat(subtitles.findSubtitleFile(movieFolder.toString(), name)).isEmpty();
    }

    @Test
    @DisplayName("an absolute path to a subtitle elsewhere is refused")
    void absoluteSubtitlePathRefused() {
        String absolute = root.resolve("secret.srt").toAbsolutePath().toString();
        assertThat(subtitles.findSubtitleFile(movieFolder.toString(), absolute)).isEmpty();
    }
}
