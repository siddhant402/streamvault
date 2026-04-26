package com.streamvault.server.model.dto.watch;

import com.streamvault.server.model.entity.WatchEvent.EventType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public class WatchDtos {

    /** Posted by the frontend periodically during playback */
    public record RecordEventRequest(
            @NotNull(message = "movieId is required")
            UUID movieId,

            @NotNull(message = "eventType is required")
            EventType eventType,

            @Min(value = 0, message = "positionSeconds must be >= 0")
            Integer positionSeconds
    ) {}

    /** Resume position for a specific movie */
    public record ResumePositionResponse(
            UUID movieId,
            Integer positionSeconds,   // null if never watched
            boolean completed
    ) {}

    /** One entry in the "Continue Watching" or history list */
    public record WatchHistoryEntry(
            UUID movieId,
            String title,
            String posterUrl,
            Integer positionSeconds,
            Integer runtimeMinutes,
            Instant lastWatchedAt,
            boolean completed
    ) {}
}
