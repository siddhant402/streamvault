package com.streamvault.server.security;

import com.streamvault.server.config.AppProperties;
import com.streamvault.server.exception.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory limits on login and registration attempts.
 *
 * - Failed logins: at most N per account, and 4N per IP address, within the window. Once the
 *   limit is reached, even the right password is refused until the window has passed.
 * - Registrations: at most M per IP address per hour.
 *
 * State lives in memory, so a restart clears it. That is enough for a home server.
 */
@Component
public class AuthRateLimiter {

    private static final int IP_MULTIPLIER = 4;

    private final AppProperties.Auth config;
    private final Clock clock;

    private final Map<String, Deque<Instant>> failedByAccount = new ConcurrentHashMap<>();
    private final Map<String, Deque<Instant>> failedByIp = new ConcurrentHashMap<>();
    private final Map<String, Deque<Instant>> registrationsByIp = new ConcurrentHashMap<>();

    @Autowired
    public AuthRateLimiter(AppProperties appProperties) {
        this(appProperties.authOrDefault(), Clock.systemUTC());
    }

    AuthRateLimiter(AppProperties.Auth config, Clock clock) {
        this.config = config;
        this.clock = clock;
    }

    // ── Login ────────────────────────────────────────────────────────────────

    public void checkLogin(String email, String ip) {
        Duration window = loginWindow();
        int max = config.maxFailedLoginsOrDefault();
        long waitAccount = waitSeconds(failedByAccount, accountKey(email), max, window);
        long waitIp = waitSeconds(failedByIp, ipKey(ip), max * IP_MULTIPLIER, window);
        long wait = Math.max(waitAccount, waitIp);
        if (wait > 0) {
            throw new TooManyRequestsException(
                    "Too many failed sign-in attempts. Try again in " + minutes(wait) + ".", wait);
        }
    }

    public void recordFailedLogin(String email, String ip) {
        record(failedByAccount, accountKey(email));
        record(failedByIp, ipKey(ip));
    }

    /** A successful login clears the account's failures (not the IP's). */
    public void recordSuccessfulLogin(String email) {
        failedByAccount.remove(accountKey(email));
    }

    // ── Registration ─────────────────────────────────────────────────────────

    public void checkRegistration(String ip) {
        long wait = waitSeconds(registrationsByIp, ipKey(ip),
                config.maxRegistrationsPerHourOrDefault(), Duration.ofHours(1));
        if (wait > 0) {
            throw new TooManyRequestsException(
                    "Too many accounts created from this device. Try again in " + minutes(wait) + ".", wait);
        }
    }

    public void recordRegistration(String ip) {
        record(registrationsByIp, ipKey(ip));
    }

    // ── Housekeeping ─────────────────────────────────────────────────────────

    @Scheduled(fixedDelay = 600_000)
    public void evictExpired() {
        Instant now = clock.instant();
        evict(failedByAccount, now.minus(loginWindow()));
        evict(failedByIp, now.minus(loginWindow()));
        evict(registrationsByIp, now.minus(Duration.ofHours(1)));
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private Duration loginWindow() {
        return Duration.ofMinutes(config.failedLoginWindowMinutesOrDefault());
    }

    /** Seconds until the key is under its limit again; 0 if it already is. */
    private long waitSeconds(Map<String, Deque<Instant>> map, String key, int max, Duration window) {
        Deque<Instant> events = map.get(key);
        if (events == null) return 0;
        Instant now = clock.instant();
        synchronized (events) {
            prune(events, now.minus(window));
            if (events.size() < max) return 0;
            Instant oldest = events.peekFirst();
            return Math.max(1, Duration.between(now, oldest.plus(window)).toSeconds());
        }
    }

    private void record(Map<String, Deque<Instant>> map, String key) {
        Deque<Instant> events = map.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (events) {
            events.addLast(clock.instant());
        }
    }

    private static void prune(Deque<Instant> events, Instant cutoff) {
        while (!events.isEmpty() && events.peekFirst().isBefore(cutoff)) {
            events.pollFirst();
        }
    }

    private static void evict(Map<String, Deque<Instant>> map, Instant cutoff) {
        map.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                prune(e.getValue(), cutoff);
                return e.getValue().isEmpty();
            }
        });
    }

    private static String accountKey(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String ipKey(String ip) {
        return ip == null ? "unknown" : ip;
    }

    private static String minutes(long seconds) {
        long m = (seconds + 59) / 60;
        return m <= 1 ? "a minute" : m + " minutes";
    }
}
