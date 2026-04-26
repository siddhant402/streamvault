package com.streamvault.server.controller;

import com.streamvault.server.streaming.StreamingService;
import com.streamvault.server.streaming.SubtitleService.SubtitleFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/stream")
@RequiredArgsConstructor
@Slf4j
public class StreamingController {

    private final StreamingService streamingService;

    @GetMapping("/{movieId}/playlist.m3u8")
    public ResponseEntity<Resource> getPlaylist(
            @PathVariable UUID movieId,
            @RequestParam(required = false) String token)
            throws IOException, InterruptedException {

        log.info("Playlist requested for movie {}", movieId);
        Resource playlist = streamingService.getPlaylist(movieId, token);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/vnd.apple.mpegurl")
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .body(playlist);
    }

    @GetMapping("/{movieId}/segments/{filename}")
    public ResponseEntity<Resource> getSegment(
            @PathVariable UUID movieId,
            @PathVariable String filename) {

        Resource segment = streamingService.getSegment(movieId, filename);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "video/mp2t")
                .header(HttpHeaders.CACHE_CONTROL, "max-age=3600")
                .body(segment);
    }

    @GetMapping("/{movieId}/subtitles")
    public ResponseEntity<List<SubtitleFile>> listSubtitles(@PathVariable UUID movieId) {
        return ResponseEntity.ok(streamingService.getSubtitles(movieId));
    }

    @GetMapping("/{movieId}/subtitles/{filename}")
    public ResponseEntity<Resource> getSubtitle(
            @PathVariable UUID movieId,
            @PathVariable String filename) {

        Resource subtitle = streamingService.getSubtitleFile(movieId, filename);
        String contentType = filename.toLowerCase().endsWith(".vtt")
                ? "text/vtt" : "text/plain; charset=utf-8";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .body(subtitle);
    }

    @DeleteMapping("/{movieId}/session")
    public ResponseEntity<Void> stopStream(@PathVariable UUID movieId) {
        streamingService.stopStream(movieId);
        return ResponseEntity.noContent().build();
    }
}