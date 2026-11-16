package com.streamvault.server.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Authenticates requests from a JWT.
 *
 * - Authorization: Bearer header, or the HttpOnly sv_session cookie set at sign-in:
 *   login tokens only (stream tokens are refused here).
 * - ?token= query param: stream tokens only, and only on the playlist and segment URLs of the
 *   movie the token was issued for. The login token is never accepted from a URL.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    /** The only URLs a ?token= is accepted on: /api/stream/{movieId}/playlist.m3u8 and /segments/{file} */
    private static final Pattern STREAM_MEDIA_PATH = Pattern.compile(
            "^/api/stream/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})"
                    + "/(?:playlist\\.m3u8|segments/[^/]+)$");

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String headerToken = extractFromHeader(request);
            String cookieToken = headerToken == null ? SessionCookie.read(request) : null;
            if (headerToken != null) {
                authenticate(headerToken, null, request);
            } else if (cookieToken != null) {
                authenticate(cookieToken, null, request);   // same rules as the header: login tokens only
            } else {
                String queryToken = request.getParameter("token");
                if (queryToken != null) {
                    String path = request.getRequestURI().substring(request.getContextPath().length());
                    Matcher m = STREAM_MEDIA_PATH.matcher(path);
                    if (m.matches()) {
                        authenticate(queryToken, m.group(1), request);
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * @param streamMovieId null for a header token (must be a login token);
     *                      otherwise the movie in the URL, which a stream token must match.
     */
    private void authenticate(String token, String streamMovieId, HttpServletRequest request) {
        try {
            Claims claims = jwtTokenProvider.parse(token);   // checks signature and expiry
            boolean isStream = jwtTokenProvider.isStreamToken(claims);

            if (streamMovieId == null) {
                if (isStream) return;   // a stream token cannot be used as a login token
            } else if (!isStream || !streamMovieId.equalsIgnoreCase(jwtTokenProvider.streamMovieId(claims))) {
                return;                 // only a stream token for this exact movie is accepted from a URL
            }

            UserDetails userDetails = userDetailsService.loadUserByUsername(claims.getSubject());
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
        } catch (JwtException | IllegalArgumentException | UsernameNotFoundException e) {
            // Invalid token: leave the request unauthenticated
            logger.debug("Rejected JWT: " + e.getMessage());
        }
    }

    private String extractFromHeader(HttpServletRequest request) {
        final String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
