package com.streamvault.server.controller;

import com.streamvault.server.model.dto.movie.MovieDtos.*;
import com.streamvault.server.scanner.MediaScannerService.ScanResult;
import com.streamvault.server.service.MovieService;
import lombok.RequiredArgsConstructor;
import com.streamvault.server.service.ViewerAgeLimit;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MovieController {

    private final MovieService movieService;
    private final ViewerAgeLimit viewerAgeLimit;

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SORTABLE_FIELDS =
            new TreeSet<>(Set.of("title", "releaseYear", "runtimeMinutes", "createdAt"));

    /**
     * GET /api/movies?page=0&size=20&sortBy=title&direction=asc&search=inception
     *     &genre=Drama&genre=Crime&yearFrom=1990&yearTo=2010&actor=pacino
     * Repeated genre values must all match; every filter is optional.
     */
    @GetMapping("/movies")
    public ResponseEntity<PageResponse<MovieSummaryDto>> listMovies(
            @RequestParam(defaultValue = "0")     int page,
            @RequestParam(defaultValue = "20")    int size,
            @RequestParam(defaultValue = "title") String sortBy,
            @RequestParam(defaultValue = "asc")   String direction,
            @RequestParam(required = false)        String search,
            @RequestParam(required = false)        List<String> genre,
            @RequestParam(required = false)        Integer yearFrom,
            @RequestParam(required = false)        Integer yearTo,
            @RequestParam(required = false)        String actor,
            @RequestHeader(value = ViewerAgeLimit.HEADER, required = false) String profileId,
            @AuthenticationPrincipal UserDetails user
    ) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be 0 or more");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (!SORTABLE_FIELDS.contains(sortBy)) {
            throw new IllegalArgumentException("sortBy must be one of " + SORTABLE_FIELDS);
        }
        if (!direction.equalsIgnoreCase("asc") && !direction.equalsIgnoreCase("desc")) {
            throw new IllegalArgumentException("direction must be asc or desc");
        }

        Sort sort = direction.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        int ageLimit = viewerAgeLimit.limitFor(user.getUsername(), profileId);
        return ResponseEntity.ok(movieService.listMovies(search, genre, yearFrom, yearTo, actor, ageLimit, pageable));
    }

    /**
     * GET /api/movies/filters  — genres, actors and year range available for filtering
     */
    @GetMapping("/movies/filters")
    public ResponseEntity<FilterOptionsDto> filterOptions() {
        return ResponseEntity.ok(movieService.getFilterOptions());
    }

    /**
     * GET /api/movies/{id}
     */
    @GetMapping("/movies/{id}")
    public ResponseEntity<MovieDetailDto> getMovie(
            @PathVariable UUID id,
            @RequestHeader(value = ViewerAgeLimit.HEADER, required = false) String profileId,
            @AuthenticationPrincipal UserDetails user) {
        int ageLimit = viewerAgeLimit.limitFor(user.getUsername(), profileId);
        return ResponseEntity.ok(movieService.getMovie(id, ageLimit));
    }

    /**
     * POST /api/admin/scan  — triggers re-scan of M:\movies  (ADMIN only)
     */
    @PostMapping("/admin/scan")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ScanResult> triggerScan() {
        return ResponseEntity.ok(movieService.triggerScan());
    }

}