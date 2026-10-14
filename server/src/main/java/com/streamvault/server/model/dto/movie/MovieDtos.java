package com.streamvault.server.model.dto.movie;

import java.util.List;
import java.util.UUID;

public class MovieDtos {

    /** Lightweight — used in paginated list responses */
    public record MovieSummaryDto(
            UUID id,
            String title,
            Integer releaseYear,
            String posterUrl,
            List<String> genres,
            Integer runtimeMinutes
    ) {}

    /** Full detail — used in single movie endpoint */
    public record MovieDetailDto(
            UUID id,
            Integer tmdbId,
            String title,
            String originalTitle,
            Integer releaseYear,
            String synopsis,
            String posterUrl,
            String backdropUrl,
            Integer runtimeMinutes,
            String ageRating,
            List<String> genres,
            List<String> castMembers,
            List<String> directors,
            String folderPath    // useful for frontend to know subs/extras location
    ) {}

    /** Values available for the genre / year / actor filters */
    public record FilterOptionsDto(
            List<String> genres,
            List<String> actors,
            Integer minYear,
            Integer maxYear
    ) {}

    /** Wrapper for paginated list endpoints */
    public record PageResponse<T>(
            List<T> content,
            int currentPage,
            int totalPages,
            long totalElements
    ) {}
}
