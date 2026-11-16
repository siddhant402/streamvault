package com.streamvault.server.security;

import com.streamvault.server.config.AppProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenProvider {

    /** Claim that says what a token may be used for. */
    private static final String TYPE_CLAIM = "typ";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_STREAM = "stream";

    /** Claim that holds the one movie a stream token is valid for. */
    private static final String MOVIE_CLAIM = "mid";

    /** Used when app.jwt.stream-expiration-ms is not set. */
    private static final long DEFAULT_STREAM_EXPIRATION_MS = 6 * 60 * 60 * 1000L;

    private final AppProperties appProperties;

    public record StreamToken(String token, Instant expiresAt) {}

    /** Fails startup with a clear message if the JWT secret is missing or too weak. */
    @PostConstruct
    void checkSecret() {
        String secret = appProperties.jwt().secret();
        String howTo = " Set the SV_JWT_SECRET environment variable or app.jwt.secret in application-local.yml"
                + " to the output of: openssl rand -base64 32";
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT secret is not set." + howTo);
        }
        byte[] key;
        try {
            key = Decoders.BASE64.decode(secret);
        } catch (RuntimeException e) {
            throw new IllegalStateException("JWT secret is not valid Base64." + howTo, e);
        }
        if (key.length < 32) {
            throw new IllegalStateException("JWT secret is too short (needs at least 32 bytes)." + howTo);
        }
    }

    /** Login token: sent only in the Authorization header. */
    public String generateToken(UserDetails userDetails) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .claim(TYPE_CLAIM, TYPE_ACCESS)
                .issuedAt(new Date(now))
                .expiration(new Date(now + appProperties.jwt().expirationMs()))
                .signWith(signingKey())
                .compact();
    }

    /**
     * Stream token: valid only for the playlist and segments of one movie, so it can sit in a
     * URL for players that cannot send headers (Safari / iOS native HLS) without exposing the
     * login token. It cannot be used to call any other endpoint.
     */
    public StreamToken generateStreamToken(UserDetails userDetails, UUID movieId) {
        long now = System.currentTimeMillis();
        Long configured = appProperties.jwt().streamExpirationMs();
        long ttl = configured != null && configured > 0 ? configured : DEFAULT_STREAM_EXPIRATION_MS;
        Date expiry = new Date(now + ttl);
        String token = Jwts.builder()
                .subject(userDetails.getUsername())
                .claim(TYPE_CLAIM, TYPE_STREAM)
                .claim(MOVIE_CLAIM, movieId.toString())
                .issuedAt(new Date(now))
                .expiration(expiry)
                .signWith(signingKey())
                .compact();
        return new StreamToken(token, expiry.toInstant());
    }

    /** Verifies the signature and expiry, then returns the claims. Throws JwtException if invalid. */
    public Claims parse(String token) {
        return parseClaims(token);
    }

    public boolean isStreamToken(Claims claims) {
        return TYPE_STREAM.equals(claims.get(TYPE_CLAIM, String.class));
    }

    /** True if the token is a valid, unexpired stream token for this movie. */
    public boolean isStreamTokenFor(String token, UUID movieId) {
        try {
            Claims claims = parseClaims(token);
            return isStreamToken(claims) && movieId.toString().equalsIgnoreCase(claims.get(MOVIE_CLAIM, String.class));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String streamMovieId(Claims claims) {
        return claims.get(MOVIE_CLAIM, String.class);
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean validateToken(String token, UserDetails userDetails) {
        try {
            String username = extractUsername(token);
            return username.equals(userDetails.getUsername()) && !isExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    private boolean isExpired(String token) {
        return parseClaims(token).getExpiration().before(new Date());
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(appProperties.jwt().secret()));
    }
}
