package com.streamvault.server.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.streamvault.server.config.AppProperties;
import com.streamvault.server.model.entity.Movie;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Calls TMDB REST API to fetch movie metadata.
 *
 * If no TMDB API key is configured ("YOUR_TMDB_API_KEY"), TMDB calls are
 * silently skipped — the movie is still saved with local-only data.
 */
@Service
@Slf4j
public class TmdbService {

    private final RestClient restClient;
    private final String apiKey;

    private static final String IMAGE_BASE = "https://image.tmdb.org/t/p/w500";

    public TmdbService(AppProperties appProperties) {
        this.apiKey = appProperties.tmdb().apiKey();
        this.restClient = RestClient.builder()
                .baseUrl(appProperties.tmdb().baseUrl())
                .build();
    }

    /**
     * Enriches a Movie entity with TMDB metadata in-place.
     * Safe to call even if the API key isn't set — falls back gracefully.
     */
    public void enrich(Movie movie, String title, Integer year) {
        if (isApiKeyMissing()) {
            log.debug("TMDB API key not configured — skipping metadata fetch for '{}'", title);
            return;
        }

        try {
            Optional<TmdbSearchResult> result = searchMovie(title, year);
            result.ifPresentOrElse(
                    r -> fetchAndApplyDetails(movie, r.id()),
                    () -> log.warn("TMDB: no results for '{}' ({})", title, year)
            );
        } catch (RestClientException e) {
            log.warn("TMDB API error for '{}': {}", title, e.getMessage());
        }
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private Optional<TmdbSearchResult> searchMovie(String title, Integer year) {
        String uri = year != null
                ? "/search/movie?query={q}&year={y}&api_key={k}&language=en-US&page=1"
                : "/search/movie?query={q}&api_key={k}&language=en-US&page=1";

        TmdbSearchResponse response = year != null
                ? restClient.get().uri(uri, title, year, apiKey)
                    .retrieve().body(TmdbSearchResponse.class)
                : restClient.get().uri(uri, title, apiKey)
                    .retrieve().body(TmdbSearchResponse.class);

        if (response == null || response.results() == null || response.results().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(response.results().get(0));
    }

    private void fetchAndApplyDetails(Movie movie, int tmdbId) {
        TmdbMovieDetail detail = restClient.get()
                .uri("/movie/{id}?api_key={k}&append_to_response=credits&language=en-US",
                        tmdbId, apiKey)
                .retrieve()
                .body(TmdbMovieDetail.class);

        if (detail == null) return;

        movie.setTmdbId(detail.id());
        movie.setTitle(detail.title() != null ? detail.title() : movie.getTitle());
        movie.setOriginalTitle(detail.originalTitle());
        movie.setSynopsis(detail.overview());
        movie.setRuntimeMinutes(detail.runtime());
        movie.setAgeRating(detail.ageRating());

        if (detail.posterPath() != null) {
            movie.setPosterUrl(IMAGE_BASE + detail.posterPath());
        }
        if (detail.backdropPath() != null) {
            movie.setBackdropUrl("https://image.tmdb.org/t/p/w1280" + detail.backdropPath());
        }
        if (detail.releaseDate() != null && detail.releaseDate().length() >= 4) {
            try {
                movie.setReleaseYear(Integer.parseInt(detail.releaseDate().substring(0, 4)));
            } catch (NumberFormatException ignored) {}
        }
        if (detail.genres() != null) {
            movie.setGenres(detail.genres().stream()
                    .map(TmdbGenre::name).collect(Collectors.joining(",")));
        }
        if (detail.credits() != null) {
            if (detail.credits().cast() != null) {
                movie.setCastMembers(detail.credits().cast().stream()
                        .limit(10)
                        .map(TmdbCastMember::name)
                        .collect(Collectors.joining(",")));
            }
            if (detail.credits().crew() != null) {
                movie.setDirectors(detail.credits().crew().stream()
                        .filter(c -> "Director".equals(c.job()))
                        .map(TmdbCrewMember::name)
                        .collect(Collectors.joining(",")));
            }
        }

        log.debug("TMDB enriched '{}' (tmdbId={})", movie.getTitle(), tmdbId);
    }

    private boolean isApiKeyMissing() {
        return apiKey == null || apiKey.isBlank() || apiKey.equals("YOUR_TMDB_API_KEY");
    }

    // ── TMDB Response Records ─────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TmdbSearchResponse(List<TmdbSearchResult> results) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TmdbSearchResult(int id, String title) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TmdbMovieDetail(
            int id,
            String title,
            @JsonProperty("original_title") String originalTitle,
            String overview,
            @JsonProperty("poster_path") String posterPath,
            @JsonProperty("backdrop_path") String backdropPath,
            @JsonProperty("release_date") String releaseDate,
            Integer runtime,
            @JsonProperty("release_dates") Object releaseDates,  // simplified — parsed below
            List<TmdbGenre> genres,
            TmdbCredits credits
    ) {
        // Age rating from release_dates is complex — keep null for now
        public String ageRating() { return null; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TmdbGenre(int id, String name) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TmdbCredits(
            List<TmdbCastMember> cast,
            List<TmdbCrewMember> crew
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TmdbCastMember(String name, Integer order) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TmdbCrewMember(String name, String job) {}
}
