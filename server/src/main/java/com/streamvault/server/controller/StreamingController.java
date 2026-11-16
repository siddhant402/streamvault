package com.streamvault.server.controller;

import com.streamvault.server.security.JwtTokenProvider.StreamToken;
import com.streamvault.server.service.ViewerAgeLimit;
import com.streamvault.server.streaming.StreamingService;
import com.streamvault.server.streaming.SubtitleService.SubtitleFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
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
    private final ViewerAgeLimit viewerAgeLimit;

    private static final String PROFILE = ViewerAgeLimit.HEADER;

    /**
     * POST /api/stream/{movieId}/token
     * For players that cannot send an Authorization header (Safari / iOS native HLS):
     * returns a short-lived token valid only for this movie's playlist and segments.
     */
    @PostMapping("/{movieId}/token")
    public ResponseEntity<StreamToken> issueStreamToken(
            @PathVariable UUID movieId,
            @RequestHeader(value = PROFILE, required = false) String profileId,
            @AuthenticationPrincipal UserDetails user) {
        int ageLimit = viewerAgeLimit.limitFor(user.getUsername(), profileId);
        return ResponseEntity.ok(streamingService.issueStreamToken(movieId, user, ageLimit));
    }

    @GetMapping("/{movieId}/playlist.m3u8")
    public ResponseEntity<Resource> getPlaylist(
            @PathVariable UUID movieId,
            @RequestParam(required = false) String token,
            @RequestHeader(value = PROFILE, required = false) String profileId,
            @AuthenticationPrincipal UserDetails user)
            throws IOException, InterruptedException {

        log.info("Playlist requested for movie {}", movieId);
        int ageLimit = viewerAgeLimit.limitFor(user.getUsername(), profileId);
        Resource playlist = streamingService.getPlaylist(movieId, token, user.getUsername(), ageLimit);

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
    public ResponseEntity<List<SubtitleFile>> listSubtitles(
            @PathVariable UUID movieId,
            @RequestHeader(value = PROFILE, required = false) String profileId,
            @AuthenticationPrincipal UserDetails user) {
        int ageLimit = viewerAgeLimit.limitFor(user.getUsername(), profileId);
        return ResponseEntity.ok(streamingService.getSubtitles(movieId, ageLimit));
    }

    @GetMapping("/{movieId}/subtitles/{filename}")
    public ResponseEntity<Resource> getSubtitle(
            @PathVariable UUID movieId,
            @PathVariable String filename,
            @RequestHeader(value = PROFILE, required = false) String profileId,
            @AuthenticationPrincipal UserDetails user) {

        int ageLimit = viewerAgeLimit.limitFor(user.getUsername(), profileId);
        Resource subtitle = streamingService.getSubtitleFile(movieId, filename, ageLimit);
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