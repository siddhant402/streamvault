package com.streamvault.server.service;

import com.streamvault.server.config.AppProperties;
import com.streamvault.server.model.entity.Movie;
import com.streamvault.server.service.TmdbService.TmdbCountryRelease;
import com.streamvault.server.service.TmdbService.TmdbRelease;
import com.streamvault.server.service.TmdbService.TmdbReleaseDates;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** V10: the right certification is picked from TMDB's release_dates. */
@DisplayName("V10 - TMDB certification")
class TmdbCertificationTest {

    private static final TmdbReleaseDates DATES = new TmdbReleaseDates(List.of(
            new TmdbCountryRelease("GB", List.of(new TmdbRelease("15", 3))),
            new TmdbCountryRelease("US", List.of(
                    new TmdbRelease("", 1),          // premiere, no rating
                    new TmdbRelease("PG", 4),        // digital
                    new TmdbRelease("PG-13", 3)))    // theatrical
    ));

    @Test
    @DisplayName("theatrical rating for the chosen country wins")
    void picksTheatricalForCountry() {
        assertThat(TmdbService.certificationFor(DATES, "US")).isEqualTo("PG-13");
        assertThat(TmdbService.certificationFor(DATES, "GB")).isEqualTo("15");
    }

    @Test
    @DisplayName("no rating for the country, or no data, gives null")
    void missing() {
        assertThat(TmdbService.certificationFor(DATES, "IN")).isNull();
        assertThat(TmdbService.certificationFor(null, "US")).isNull();
        assertThat(TmdbService.certificationFor(new TmdbReleaseDates(null), "US")).isNull();
    }

    @Test
    @DisplayName("the movie gets both the label and the minimum age")
    void appliesToMovie() {
        TmdbService service = new TmdbService(new AppProperties(
                null, null, new AppProperties.Tmdb("", "https://api.themoviedb.org/3", "us"),
                null, null, null));
        Movie movie = Movie.builder().title("x").localPath("x").build();

        assertThat(service.applyCertification(movie, DATES)).isTrue();
        assertThat(movie.getAgeRating()).isEqualTo("PG-13");
        assertThat(movie.getMinimumAge()).isEqualTo(13);
    }
}
