package com.streamvault.server.service;

import com.streamvault.server.exception.ResourceNotFoundException;
import com.streamvault.server.model.dto.movie.MovieDtos.*;
import com.streamvault.server.model.entity.Movie;
import com.streamvault.server.repository.MovieRepository;
import com.streamvault.server.scanner.MediaScannerService;
import com.streamvault.server.scanner.MediaScannerService.ScanResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

import java.util.*;

@Service
@RequiredArgsConstructor
public class MovieService {

    private final MovieRepository movieRepository;
    private final MediaScannerService scannerService;

    public PageResponse<MovieSummaryDto> listMovies(String search, List<String> genres,
                                                    Integer yearFrom, Integer yearTo, String actor,
                                                    Pageable pageable) {
        Page<Movie> page = movieRepository.findAll(
                buildSpec(search, genres, yearFrom, yearTo, actor), pageable);

        List<MovieSummaryDto> content = page.getContent().stream()
                .map(this::toSummary)
                .toList();

        return new PageResponse<>(content, page.getNumber(), page.getTotalPages(), page.getTotalElements());
    }

    /** Distinct genres, most-credited actors and the year range present in the library. */
    public FilterOptionsDto getFilterOptions() {
        Set<String> genres = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Map<String, Long> actorCounts = new HashMap<>();
        Integer minYear = null;
        Integer maxYear = null;

        for (Object[] row : movieRepository.findFilterSources()) {
            genres.addAll(splitCsv((String) row[0]));
            for (String actor : splitCsv((String) row[1])) {
                actorCounts.merge(actor, 1L, Long::sum);
            }
            Integer year = (Integer) row[2];
            if (year != null) {
                minYear = minYear == null ? year : Math.min(minYear, year);
                maxYear = maxYear == null ? year : Math.max(maxYear, year);
            }
        }

        List<String> actors = actorCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER)))
                .limit(300)
                .map(Map.Entry::getKey)
                .toList();

        return new FilterOptionsDto(List.copyOf(genres), actors, minYear, maxYear);
    }

    /**
     * genres and cast are stored as comma-separated text, so each genre is matched as a whole
     * token against ",genre1,genre2," (exact, case-insensitive). Actors match by name fragment.
     * All supplied filters are combined with AND.
     */
    private Specification<Movie> buildSpec(String search, List<String> genres,
                                           Integer yearFrom, Integer yearTo, String actor) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (search != null && !search.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("title")),
                        "%" + search.trim().toLowerCase() + "%"));
            }

            if (genres != null) {
                Expression<String> tokens = cb.lower(cb.concat(
                        cb.concat(",", cb.function("replace", String.class,
                                root.<String>get("genres"), cb.literal(", "), cb.literal(","))),
                        ","));
                for (String genre : genres) {
                    if (genre == null || genre.isBlank()) continue;
                    predicates.add(cb.like(tokens, "%," + genre.trim().toLowerCase() + ",%"));
                }
            }

            if (yearFrom != null) predicates.add(cb.greaterThanOrEqualTo(root.get("releaseYear"), yearFrom));
            if (yearTo != null)   predicates.add(cb.lessThanOrEqualTo(root.get("releaseYear"), yearTo));

            if (actor != null && !actor.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("castMembers")),
                        "%" + actor.trim().toLowerCase() + "%"));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public MovieDetailDto getMovie(UUID id) {
        Movie movie = movieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Movie not found with id: " + id));
        return toDetail(movie);
    }

    public ScanResult triggerScan() {
        return scannerService.scan();
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private MovieSummaryDto toSummary(Movie m) {
        return new MovieSummaryDto(
                m.getId(),
                m.getTitle(),
                m.getReleaseYear(),
                m.getPosterUrl(),
                splitCsv(m.getGenres()),
                m.getRuntimeMinutes()
        );
    }

    private MovieDetailDto toDetail(Movie m) {
        return new MovieDetailDto(
                m.getId(),
                m.getTmdbId(),
                m.getTitle(),
                m.getOriginalTitle(),
                m.getReleaseYear(),
                m.getSynopsis(),
                m.getPosterUrl(),
                m.getBackdropUrl(),
                m.getRuntimeMinutes(),
                m.getAgeRating(),
                splitCsv(m.getGenres()),
                splitCsv(m.getCastMembers()),
                splitCsv(m.getDirectors()),
                m.getFolderPath()
        );
    }

    /** Safely split a nullable comma-separated string into a List. */
    private List<String> splitCsv(String value) {
        if (value == null || value.isBlank()) return Collections.emptyList();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
