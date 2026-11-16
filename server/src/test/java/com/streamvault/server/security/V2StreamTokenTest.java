package com.streamvault.server.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamvault.server.model.entity.Account;
import com.streamvault.server.model.entity.Movie;
import com.streamvault.server.model.entity.Role;
import com.streamvault.server.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V2: the login token must never be accepted from a URL, and the movie-scoped stream token
 * must only unlock that one movie's playlist and segments.
 *
 * A 404 on a segment URL means the request got past security (there is simply no transcode
 * session in tests); 401/403 means security refused it.
 */
@DisplayName("V2 - tokens in URLs")
class V2StreamTokenTest extends IntegrationTestBase {

    @Autowired ObjectMapper objectMapper;

    Account user;
    String login;
    Movie movie;
    Movie otherMovie;

    @BeforeEach
    void setUp() {
        user = createAccount(Role.USER);
        login = loginToken(user);
        movie = createMovie();
        otherMovie = createMovie();
    }

    private String streamTokenFor(Movie m) {
        return jwt.generateStreamToken(user, m.getId()).token();
    }

    private String segmentUrl(Movie m) {
        return "/api/stream/" + m.getId() + "/segments/seg0.ts";
    }

    // ── The old behaviour must be gone ───────────────────────────────────────

    @Test
    @DisplayName("login token in the playlist URL is rejected")
    void loginTokenInPlaylistUrlRejected() throws Exception {
        mvc.perform(get("/api/stream/{id}/playlist.m3u8", movie.getId()).param("token", login))
                .andExpect(rejected());
    }

    @Test
    @DisplayName("login token in a segment URL is rejected")
    void loginTokenInSegmentUrlRejected() throws Exception {
        mvc.perform(get(segmentUrl(movie)).param("token", login))
                .andExpect(rejected());
    }

    @Test
    @DisplayName("login token in the URL of any other endpoint is rejected")
    void loginTokenInApiUrlRejected() throws Exception {
        mvc.perform(get("/api/movies").param("token", login))
                .andExpect(rejected());
    }

    @Test
    @DisplayName("login token is never treated as a stream token (so it is not copied into playlists)")
    void loginTokenIsNotAStreamToken() {
        assertThat(jwt.isStreamTokenFor(login, movie.getId())).isFalse();
    }

    // ── Header login still works ─────────────────────────────────────────────

    @Test
    @DisplayName("login token in the Authorization header still reaches the segment endpoint")
    void loginTokenInHeaderWorks() throws Exception {
        mvc.perform(get(segmentUrl(movie)).header("Authorization", "Bearer " + login))
                .andExpect(status().isNotFound());
    }

    // ── Stream tokens are tightly scoped ─────────────────────────────────────

    @Test
    @DisplayName("stream token in the URL works for its own movie")
    void streamTokenWorksForItsMovie() throws Exception {
        mvc.perform(get(segmentUrl(movie)).param("token", streamTokenFor(movie)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("stream token for one movie is rejected on another movie")
    void streamTokenRejectedForOtherMovie() throws Exception {
        mvc.perform(get(segmentUrl(otherMovie)).param("token", streamTokenFor(movie)))
                .andExpect(rejected());
        mvc.perform(get("/api/stream/{id}/playlist.m3u8", otherMovie.getId()).param("token", streamTokenFor(movie)))
                .andExpect(rejected());
    }

    @Test
    @DisplayName("stream token cannot be used as a login token in the header")
    void streamTokenRejectedAsLogin() throws Exception {
        String stream = streamTokenFor(movie);
        mvc.perform(get("/api/movies").header("Authorization", "Bearer " + stream))
                .andExpect(rejected());
        mvc.perform(get(segmentUrl(movie)).header("Authorization", "Bearer " + stream))
                .andExpect(rejected());
        // and it cannot mint more stream tokens
        mvc.perform(post("/api/stream/{id}/token", movie.getId()).header("Authorization", "Bearer " + stream))
                .andExpect(rejected());
    }

    @Test
    @DisplayName("stream token in the URL of other endpoints is rejected")
    void streamTokenRejectedOnOtherEndpoints() throws Exception {
        String stream = streamTokenFor(movie);
        mvc.perform(get("/api/movies").param("token", stream)).andExpect(rejected());
        mvc.perform(get("/api/stream/{id}/subtitles", movie.getId()).param("token", stream)).andExpect(rejected());
        mvc.perform(get("/api/profiles").param("token", stream)).andExpect(rejected());
    }

    @Test
    @DisplayName("a tampered stream token is rejected")
    void tamperedStreamTokenRejected() throws Exception {
        String stream = streamTokenFor(movie);
        String tampered = stream.substring(0, stream.length() - 4) + "AAAA";
        mvc.perform(get(segmentUrl(movie)).param("token", tampered)).andExpect(rejected());
    }

    // ── Issuing stream tokens ────────────────────────────────────────────────

    @Test
    @DisplayName("POST /token returns a 6-hour token scoped to that movie")
    void issueStreamToken() throws Exception {
        String body = mvc.perform(post("/api/stream/{id}/token", movie.getId())
                        .header("Authorization", "Bearer " + login))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        String token = json.get("token").asText();
        Instant expiresAt = Instant.parse(json.get("expiresAt").asText());

        assertThat(jwt.isStreamTokenFor(token, movie.getId())).isTrue();
        assertThat(jwt.isStreamTokenFor(token, otherMovie.getId())).isFalse();
        assertThat(Duration.between(Instant.now(), expiresAt))
                .isBetween(Duration.ofHours(5), Duration.ofHours(6).plusMinutes(1));
    }

    @Test
    @DisplayName("POST /token needs a logged-in user")
    void issueStreamTokenNeedsLogin() throws Exception {
        mvc.perform(post("/api/stream/{id}/token", movie.getId())).andExpect(rejected());
    }

    @Test
    @DisplayName("POST /token for an unknown movie returns 404")
    void issueStreamTokenUnknownMovie() throws Exception {
        mvc.perform(post("/api/stream/{id}/token", UUID.randomUUID())
                        .header("Authorization", "Bearer " + login))
                .andExpect(status().isNotFound());
    }
}
