package com.streamvault.server.repository;

import com.streamvault.server.model.entity.WatchEvent;
import com.streamvault.server.model.entity.WatchEvent.EventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WatchEventRepository extends JpaRepository<WatchEvent, UUID> {

    /**
     * Get the latest event of a given type for a profile+movie combo.
     * Used to find resume position (latest PROGRESS) or completion status.
     */
    @Query("""
            SELECT w FROM WatchEvent w
            WHERE w.profile.id = :profileId
              AND w.movie.id   = :movieId
              AND w.eventType  = :type
            ORDER BY w.occurredAt DESC
            LIMIT 1
            """)
    Optional<WatchEvent> findLatest(
            @Param("profileId") UUID profileId,
            @Param("movieId")   UUID movieId,
            @Param("type")      EventType type);

    /**
     * All events for a profile, newest first — used for watch history list.
     */
    @Query("""
            SELECT w FROM WatchEvent w
            WHERE w.profile.id = :profileId
            ORDER BY w.occurredAt DESC
            """)
    List<WatchEvent> findByProfileIdOrderByOccurredAtDesc(@Param("profileId") UUID profileId);

    /**
     * Completed movies for a profile.
     */
    @Query("""
            SELECT DISTINCT w.movie.id FROM WatchEvent w
            WHERE w.profile.id = :profileId
              AND w.eventType  = 'COMPLETE'
            """)
    List<UUID> findCompletedMovieIds(@Param("profileId") UUID profileId);

    /**
     * Latest PROGRESS event per movie for a profile — used for "Continue Watching".
     */
    @Query("""
            SELECT w FROM WatchEvent w
            WHERE w.profile.id = :profileId
              AND w.eventType  = 'PROGRESS'
              AND w.occurredAt = (
                  SELECT MAX(w2.occurredAt) FROM WatchEvent w2
                  WHERE w2.profile.id = w.profile.id
                    AND w2.movie.id   = w.movie.id
                    AND w2.eventType  = 'PROGRESS'
              )
            ORDER BY w.occurredAt DESC
            """)
    List<WatchEvent> findContinueWatching(@Param("profileId") UUID profileId);
}
