package com.streamvault.server.security;

import com.streamvault.server.config.AppProperties;
import com.streamvault.server.exception.TooManyRequestsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** V6: the sign-in lock lifts once the window has passed. */
@DisplayName("V6 - AuthRateLimiter timing")
class AuthRateLimiterTest {

    /** A clock the test can move forward. */
    static final class TestClock extends Clock {
        Instant now = Instant.parse("2026-11-14T12:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advance(Duration d) { now = now.plus(d); }
    }

    private final TestClock clock = new TestClock();
    private final AuthRateLimiter limiter =
            new AuthRateLimiter(new AppProperties.Auth(null, 3, 15, 2, null), clock);

    @Test
    @DisplayName("lock lasts for the window, then lifts")
    void lockExpires() {
        for (int i = 0; i < 3; i++) limiter.recordFailedLogin("a@test.local", "1.1.1.1");

        assertThatThrownBy(() -> limiter.checkLogin("A@Test.local", "2.2.2.2"))   // case-insensitive
                .isInstanceOf(TooManyRequestsException.class)
                .satisfies(e -> assertThat(((TooManyRequestsException) e).getRetryAfterSeconds())
                        .isBetween(1L, 15 * 60L));

        clock.advance(Duration.ofMinutes(14));
        assertThatThrownBy(() -> limiter.checkLogin("a@test.local", "1.1.1.1"))
                .isInstanceOf(TooManyRequestsException.class);

        clock.advance(Duration.ofMinutes(2));
        assertThatCode(() -> limiter.checkLogin("a@test.local", "1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("registration limit resets after an hour")
    void registrationLimitResets() {
        limiter.recordRegistration("3.3.3.3");
        limiter.recordRegistration("3.3.3.3");
        assertThatThrownBy(() -> limiter.checkRegistration("3.3.3.3")).isInstanceOf(TooManyRequestsException.class);

        clock.advance(Duration.ofMinutes(61));
        assertThatCode(() -> limiter.checkRegistration("3.3.3.3")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("old entries are cleaned out of memory")
    void evictsExpired() {
        limiter.recordFailedLogin("b@test.local", "4.4.4.4");
        clock.advance(Duration.ofHours(2));
        limiter.evictExpired();
        for (int i = 0; i < 2; i++) limiter.recordFailedLogin("b@test.local", "4.4.4.4");
        assertThatCode(() -> limiter.checkLogin("b@test.local", "4.4.4.4")).doesNotThrowAnyException();
    }
}
