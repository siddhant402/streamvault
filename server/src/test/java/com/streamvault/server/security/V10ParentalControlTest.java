package com.streamvault.server.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamvault.server.model.entity.Account;
import com.streamvault.server.model.entity.Movie;
import com.streamvault.server.model.entity.Profile;
import com.streamvault.server.model.entity.Role;
import com.streamvault.server.repository.ProfileRepository;
import com.streamvault.server.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** V10: a profile's age limit is applied to the library, movie pages and playback. */
@DisplayName("V10 - profile age limits")
class V10ParentalControlTest extends IntegrationTestBase {

    @Autowired ProfileRepository profiles;
    @Autowired ObjectMapper objectMapper;

    String prefix;
    String auth;
    Account account;
    Profile kid, teen, adult;
    Movie ratedG, rated13, ratedR, unrated;

    @BeforeEach
    void setUp() {
        prefix = "V10-" + UUID.randomUUID().toString().substring(0, 8);
        account = createAccount(Role.USER);
        auth = "Bearer " + loginToken(account);
        kid = profile("Kid", 7);
        teen = profile("Teen", 13);
        adult = profile("Adult", 18);
        ratedG = movie("G", "G", 0);
        rated13 = movie("PG-13", "PG-13", 13);
        ratedR = movie("R", "R", 17);
        unrated = movie("Unrated", null, null);
    }

    private Profile profile(String name, int age) {
        return profiles.save(Profile.builder().account(account).name(name).ageRestriction(age).build());
    }

    private Movie movie(String suffix, String rating, Integer minAge) {
        return movies.save(Movie.builder()
                .title(prefix + " " + suffix)
                .localPath("/does/not/exist/" + UUID.randomUUID() + ".mkv")
                .ageRating(rating).minimumAge(minAge)
                .build());
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder req, Profile p) {
        req.header("Authorization", auth);
        return p == null ? req : req.header("X-Profile-Id", p.getId().toString());
    }

    private List<String> titlesFor(Profile p) throws Exception {
        String body = mvc.perform(as(get("/api/movies").param("search", prefix).param("size", "100"), p))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> titles = new ArrayList<>();
        for (JsonNode n : objectMapper.readTree(body).get("content")) {
            titles.add(n.get("title").asText().substring(prefix.length() + 1));
        }
        return titles;
    }

    @Test
    @DisplayName("library shows only movies within the profile's limit; unrated is hidden below 18")
    void libraryFiltered() throws Exception {
        assertThat(titlesFor(kid)).containsExactlyInAnyOrder("G");
        assertThat(titlesFor(teen)).containsExactlyInAnyOrder("G", "PG-13");
        assertThat(titlesFor(adult)).containsExactlyInAnyOrder("G", "PG-13", "R", "Unrated");
        assertThat(titlesFor(null)).containsExactlyInAnyOrder("G", "PG-13", "R", "Unrated");
    }

    @Test
    @DisplayName("movie page of a too-old movie is not found for a kid, fine for an adult")
    void detailBlocked() throws Exception {
        mvc.perform(as(get("/api/movies/{id}", ratedR.getId()), kid)).andExpect(status().isNotFound());
        mvc.perform(as(get("/api/movies/{id}", unrated.getId()), teen)).andExpect(status().isNotFound());
        mvc.perform(as(get("/api/movies/{id}", ratedG.getId()), kid)).andExpect(status().isOk());
        mvc.perform(as(get("/api/movies/{id}", ratedR.getId()), adult)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("playback of a too-old movie is refused before anything starts")
    void playbackBlocked() throws Exception {
        mvc.perform(as(get("/api/stream/{id}/playlist.m3u8", rated13.getId()), kid)).andExpect(status().isNotFound());
        mvc.perform(as(post("/api/stream/{id}/token", rated13.getId()), kid)).andExpect(status().isNotFound());
        mvc.perform(as(get("/api/stream/{id}/subtitles", ratedR.getId()), teen)).andExpect(status().isNotFound());
        mvc.perform(as(post("/api/stream/{id}/token", rated13.getId()), teen)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("another account's profile cannot be used, and a bad profile id is refused")
    void foreignOrInvalidProfile() throws Exception {
        Account other = createAccount(Role.USER);
        Profile othersKid = profiles.save(Profile.builder().account(other).name("Theirs").ageRestriction(7).build());

        mvc.perform(as(get("/api/movies"), othersKid)).andExpect(status().isForbidden());
        mvc.perform(get("/api/movies").header("Authorization", auth).header("X-Profile-Id", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }
}
