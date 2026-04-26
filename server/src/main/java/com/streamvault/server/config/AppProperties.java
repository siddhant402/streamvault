package com.streamvault.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Media media,
        Jwt jwt,
        Tmdb tmdb
) {
    public record Media(
            String rootPath,
            String hlsOutputPath,
            String ffmpegPath
    ) {}

    public record Jwt(String secret, long expirationMs) {}

    public record Tmdb(String apiKey, String baseUrl) {}
}