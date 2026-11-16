package com.streamvault.server.streaming;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V5: segment URLs in the playlist are relative, so the player fetches them through whatever
 * address served the playlist (the HTTPS proxy, a LAN address) instead of http://localhost:8080.
 */
@DisplayName("V5 - relative segment URLs")
class V5PlaylistUrlTest {

    private static final String FFMPEG_PLAYLIST = String.join("\n",
            "#EXTM3U",
            "#EXT-X-VERSION:3",
            "#EXT-X-TARGETDURATION:4",
            "#EXTINF:4.000000,",
            "C:\\Users\\me\\AppData\\Local\\Temp\\streamvault-hls\\abc\\seg0.ts",
            "#EXTINF:4.000000,",
            "/tmp/streamvault-hls/abc/seg1.ts",
            "#EXT-X-ENDLIST");

    @Test
    @DisplayName("no absolute or http:// URLs, and no server paths, in the rewritten playlist")
    void relativeUrlsOnly() {
        String out = StreamingService.rewritePlaylist(FFMPEG_PLAYLIST, UUID.randomUUID(), null);

        assertThat(out).doesNotContain("http://", "https://", "localhost", "Temp", "/tmp/");
        List<String> segments = out.lines().filter(l -> !l.startsWith("#")).filter(l -> !l.isBlank()).toList();
        assertThat(segments).containsExactly("segments/seg0.ts", "segments/seg1.ts");
    }

    @Test
    @DisplayName("a stream token is added to each segment URL, URL-encoded")
    void tokenAppended() {
        String out = StreamingService.rewritePlaylist(FFMPEG_PLAYLIST, UUID.randomUUID(), "a.b+c/d=");

        assertThat(out).contains("segments/seg0.ts?token=a.b%2Bc%2Fd%3D");
        assertThat(out).contains("#EXT-X-ENDLIST");
    }
}
