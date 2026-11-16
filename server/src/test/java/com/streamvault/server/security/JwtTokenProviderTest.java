package com.streamvault.server.security;

import com.streamvault.server.config.AppProperties;
import com.streamvault.server.model.entity.Account;
import com.streamvault.server.model.entity.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit tests for the token rules behind V2 and the startup secret check behind V4. */
@DisplayName("V2/V4 - JwtTokenProvider")
class JwtTokenProviderTest {

    private static final String GOOD_SECRET =
            Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    private static JwtTokenProvider provider(String secret, Long streamTtlMs) {
        AppProperties props = new AppProperties(
                new AppProperties.Media("target/none", "target/hls", "ffmpeg"),
                new AppProperties.Jwt(secret, 60_000, streamTtlMs),
                new AppProperties.Tmdb("", "https://api.themoviedb.org/3", null),
                null, null, null);
        return new JwtTokenProvider(props);
    }

    private static Account user() {
        return Account.builder().email("user@test.local").password("x").role(Role.USER).build();
    }

    // ── V4: secret check at startup ──────────────────────────────────────────

    @Test
    @DisplayName("V4: startup fails when the JWT secret is missing")
    void missingSecretFails() {
        assertThatThrownBy(() -> provider("", null).checkSecret())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not set");
        assertThatThrownBy(() -> provider(null, null).checkSecret())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("V4: startup fails on the old placeholder secret")
    void placeholderSecretFails() {
        assertThatThrownBy(() -> provider("CHANGE_ME_run_openssl_rand_base64_32", null).checkSecret())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("V4: startup fails when the secret is shorter than 32 bytes")
    void shortSecretFails() {
        String shortSecret = Base64.getEncoder().encodeToString("too-short".getBytes());
        assertThatThrownBy(() -> provider(shortSecret, null).checkSecret())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("too short");
    }

    @Test
    @DisplayName("V4: a valid 32-byte Base64 secret is accepted")
    void goodSecretAccepted() {
        assertThatCode(() -> provider(GOOD_SECRET, null).checkSecret()).doesNotThrowAnyException();
    }

    // ── V2: token types ──────────────────────────────────────────────────────

    @Test
    @DisplayName("V2: login tokens and stream tokens are distinguishable")
    void tokenTypes() {
        JwtTokenProvider p = provider(GOOD_SECRET, null);
        UUID movie = UUID.randomUUID();

        String login = p.generateToken(user());
        String stream = p.generateStreamToken(user(), movie).token();

        assertThat(p.isStreamToken(p.parse(login))).isFalse();
        assertThat(p.isStreamToken(p.parse(stream))).isTrue();
        assertThat(p.streamMovieId(p.parse(stream))).isEqualTo(movie.toString());
    }

    @Test
    @DisplayName("V2: stream token defaults to a 6-hour lifetime")
    void defaultStreamLifetime() {
        JwtTokenProvider p = provider(GOOD_SECRET, null);
        var token = p.generateStreamToken(user(), UUID.randomUUID());
        long hours = (token.expiresAt().toEpochMilli() - System.currentTimeMillis()) / 3_600_000;
        assertThat(hours).isBetween(5L, 6L);
    }

    @Test
    @DisplayName("V2: an expired stream token is rejected")
    void expiredStreamTokenRejected() throws InterruptedException {
        JwtTokenProvider p = provider(GOOD_SECRET, 1L);
        UUID movie = UUID.randomUUID();
        String stream = p.generateStreamToken(user(), movie).token();

        Thread.sleep(1_500);   // JWT expiry has one-second resolution

        assertThat(p.isStreamTokenFor(stream, movie)).isFalse();
    }

    @Test
    @DisplayName("V2: a token signed with another secret is rejected")
    void foreignSignatureRejected() {
        String otherSecret = Base64.getEncoder().encodeToString("ffffffffffffffffffffffffffffffff".getBytes());
        UUID movie = UUID.randomUUID();
        String forged = provider(otherSecret, null).generateStreamToken(user(), movie).token();

        assertThat(provider(GOOD_SECRET, null).isStreamTokenFor(forged, movie)).isFalse();
    }
}
