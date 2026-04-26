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
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MovieService {

    private final MovieRepository movieRepository;
    private final MediaScannerService scannerService;

    public PageResponse<MovieSummaryDto> listMovies(String search, Pageable pageable) {
        Page<Movie> page = (search != null && !search.isBlank())
                ? movieRepository.findByTitleContainingIgnoreCase(search, pageable)
                : movieRepository.findAll(pageable);

        List<MovieSummaryDto> content = page.getContent().stream()
                .map(this::toSummary)
                .toList();

        return new PageResponse<>(content, page.getNumber(), page.getTotalPages(), page.getTotalElements());
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
