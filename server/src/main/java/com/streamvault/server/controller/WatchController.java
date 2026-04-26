package com.streamvault.server.controller;

import com.streamvault.server.model.dto.watch.WatchDtos.*;
import com.streamvault.server.service.WatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/profiles/{profileId}/watch")
@RequiredArgsConstructor
public class WatchController {

    private final WatchService watchService;

    /**
     * POST /api/profiles/{profileId}/watch/events
     *
     * Called by the frontend during playback:
     * - Every 30s with eventType=PROGRESS and current positionSeconds
     * - Once at the end with eventType=COMPLETE
     *
     * Body: { "movieId": "uuid", "eventType": "PROGRESS", "positionSeconds": 3452 }
     */
    @PostMapping("/events")
    public ResponseEntity<Void> recordEvent(
            @PathVariable UUID profileId,
            @Valid @RequestBody RecordEventRequest request,
            @AuthenticationPrincipal UserDetails user) {
        watchService.recordEvent(profileId, request, user.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * GET /api/profiles/{profileId}/watch/resume/{movieId}
     *
     * Called when the user opens a movie — returns where to resume from.
     * Response: { "movieId": "uuid", "positionSeconds": 3452, "completed": false }
     */
    @GetMapping("/resume/{movieId}")
    public ResponseEntity<ResumePositionResponse> getResumePosition(
            @PathVariable UUID profileId,
            @PathVariable UUID movieId,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(
                watchService.getResumePosition(profileId, movieId, user.getUsername()));
    }

    /**
     * GET /api/profiles/{profileId}/watch/continue
     *
     * "Continue Watching" row — in-progress movies, newest first, excluding completed.
     */
    @GetMapping("/continue")
    public ResponseEntity<List<WatchHistoryEntry>> getContinueWatching(
            @PathVariable UUID profileId,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(
                watchService.getContinueWatching(profileId, user.getUsername()));
    }

    /**
     * GET /api/profiles/{profileId}/watch/history
     *
     * Full watch history for the profile.
     */
    @GetMapping("/history")
    public ResponseEntity<List<WatchHistoryEntry>> getHistory(
            @PathVariable UUID profileId,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(
                watchService.getWatchHistory(profileId, user.getUsername()));
    }
}
