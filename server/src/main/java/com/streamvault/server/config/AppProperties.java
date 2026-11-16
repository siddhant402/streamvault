package com.streamvault.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Media media,
        Jwt jwt,
        Tmdb tmdb,
        Auth auth,
        Streaming streaming,
        Cors cors
) {
    public record Media(
            String rootPath,
            String hlsOutputPath,
            String ffmpegPath
    ) {}

    /** streamExpirationMs: lifetime of movie-scoped stream tokens (null = 6 hours). */
    public record Jwt(String secret, long expirationMs, Long streamExpirationMs) {}

    /** certificationCountry: which country's age ratings to use, e.g. US, IN, GB (default US). */
    public record Tmdb(String apiKey, String baseUrl, String certificationCountry) {
        public String certificationCountryOrDefault() {
            return certificationCountry != null && !certificationCountry.isBlank()
                    ? certificationCountry.trim().toUpperCase() : "US";
        }
    }

    /**
     * inviteCode: needed to register once the first account exists (blank = registration closed).
     * The other limits fall back to their defaults when not set.
     */
    public record Auth(
            String inviteCode,
            Integer maxFailedLogins,
            Integer failedLoginWindowMinutes,
            Integer maxRegistrationsPerHour,
            List<String> adminEmails
    ) {
        /** Accounts made ADMIN at startup (SV_ADMIN_EMAILS, comma-separated), lower-cased. */
        public List<String> adminEmailsOrEmpty() {
            return adminEmails == null ? List.of() : adminEmails.stream()
                    .filter(e -> e != null && !e.isBlank())
                    .map(e -> e.trim().toLowerCase())
                    .toList();
        }

        public int maxFailedLoginsOrDefault()        { return maxFailedLogins != null ? maxFailedLogins : 5; }
        public int failedLoginWindowMinutesOrDefault() { return failedLoginWindowMinutes != null ? failedLoginWindowMinutes : 15; }
        public int maxRegistrationsPerHourOrDefault() { return maxRegistrationsPerHour != null ? maxRegistrationsPerHour : 5; }
    }

    /** Caps on concurrent FFmpeg transcodes, overall and per account. */
    public record Streaming(Integer maxSessions, Integer maxSessionsPerUser) {
        public int maxSessionsOrDefault()        { return maxSessions != null ? maxSessions : 3; }
        public int maxSessionsPerUserOrDefault() { return maxSessionsPerUser != null ? maxSessionsPerUser : 2; }
    }

    /** Browser origins allowed to call the API (the web app's address). */
    public record Cors(List<String> allowedOrigins) {}

    public Auth authOrDefault() {
        return auth != null ? auth : new Auth(null, null, null, null, null);
    }

    public Streaming streamingOrDefault() {
        return streaming != null ? streaming : new Streaming(null, null);
    }

    public List<String> allowedOriginsOrDefault() {
        return cors != null && cors.allowedOrigins() != null && !cors.allowedOrigins().isEmpty()
                ? cors.allowedOrigins()
                : List.of("http://localhost:3000");
    }
}
