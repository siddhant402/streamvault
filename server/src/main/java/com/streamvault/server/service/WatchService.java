package com.streamvault.server.service;

import com.streamvault.server.exception.ResourceNotFoundException;
import com.streamvault.server.model.dto.watch.WatchDtos.*;
import com.streamvault.server.model.entity.Movie;
import com.streamvault.server.model.entity.Profile;
import com.streamvault.server.model.entity.WatchEvent;
import com.streamvault.server.model.entity.WatchEvent.EventType;
import com.streamvault.server.repository.MovieRepository;
import com.streamvault.server.repository.ProfileRepository;
import com.streamvault.server.repository.WatchEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class WatchService {

    private final WatchEventRepository watchEventRepository;
    private final ProfileRepository profileRepository;
    private final MovieRepository movieRepository;

    public void recordEvent(UUID profileId, RecordEventRequest request, String email) {
        Profile profile = verifyProfileOwnership(profileId, email);
        Movie movie = movieRepository.findById(request.movieId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Movie not found: " + request.movieId()));

        WatchEvent event = WatchEvent.builder()
                .profile(profile)
                .movie(movie)
                .eventType(request.eventType())
                .positionSeconds(request.positionSeconds())
                .build();

        watchEventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public ResumePositionResponse getResumePosition(UUID profileId, UUID movieId, String email) {
        verifyProfileOwnership(profileId, email);

        boolean completed = watchEventRepository
                .findLatest(profileId, movieId, EventType.COMPLETE)
                .isPresent();

        Integer position = watchEventRepository
                .findLatest(profileId, movieId, EventType.PROGRESS)
                .map(WatchEvent::getPositionSeconds)
                .orElse(null);

        return new ResumePositionResponse(movieId, position, completed);
    }

    @Transactional(readOnly = true)
    public List<WatchHistoryEntry> getContinueWatching(UUID profileId, String email) {
        verifyProfileOwnership(profileId, email);

        Set<UUID> completedIds = watchEventRepository
                .findCompletedMovieIds(profileId)
                .stream().collect(Collectors.toSet());

        return watchEventRepository.findContinueWatching(profileId)
                .stream()
                .filter(e -> !completedIds.contains(e.getMovie().getId()))
                .map(e -> new WatchHistoryEntry(
                        e.getMovie().getId(),
                        e.getMovie().getTitle(),
                        e.getMovie().getPosterUrl(),
                        e.getPositionSeconds(),
                        e.getMovie().getRuntimeMinutes(),
                        e.getOccurredAt(),
                        false
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WatchHistoryEntry> getWatchHistory(UUID profileId, String email) {
        verifyProfileOwnership(profileId, email);

        Set<UUID> completedIds = watchEventRepository
                .findCompletedMovieIds(profileId)
                .stream().collect(Collectors.toSet());

        return watchEventRepository.findByProfileIdOrderByOccurredAtDesc(profileId)
                .stream()
                .map(e -> new WatchHistoryEntry(
                        e.getMovie().getId(),
                        e.getMovie().getTitle(),
                        e.getMovie().getPosterUrl(),
                        e.getPositionSeconds(),
                        e.getMovie().getRuntimeMinutes(),
                        e.getOccurredAt(),
                        completedIds.contains(e.getMovie().getId())
                ))
                .toList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Profile verifyProfileOwnership(UUID profileId, String email) {
        Profile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Profile not found: " + profileId));
        if (!profile.getAccount().getEmail().equals(email)) {
            throw new AccessDeniedException("You do not own this profile");
        }
        return profile;
    }
}