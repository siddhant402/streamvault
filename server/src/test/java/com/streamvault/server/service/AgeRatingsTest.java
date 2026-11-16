package com.streamvault.server.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** V10: certifications become the minimum viewer age used by profile limits. */
@DisplayName("V10 - age ratings")
class AgeRatingsTest {

    @ParameterizedTest(name = "{0} {1} -> {2}")
    @CsvSource(nullValues = "null", value = {
            "US, G, 0",
            "US, PG, 7",
            "US, PG-13, 13",
            "US, R, 17",
            "US, NC-17, 18",
            "us, pg-13, 13",
            "IN, U, 0",
            "IN, UA, 12",
            "IN, UA 13+, 13",
            "IN, A, 18",
            "GB, 12A, 12",
            "GB, 15, 15",
            "DE, 16, 16",
            "DE, FSK 12, 12",
            "AU, MA15+, 15",
            "US, NR, null",
            "US, Unrated, null",
            "US, '', null",
            "US, null, null"
    })
    void minimumAge(String country, String certification, Integer expected) {
        assertThat(AgeRatings.minimumAge(country, certification)).isEqualTo(expected);
    }
}
