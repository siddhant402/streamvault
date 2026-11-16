package com.streamvault.server.security;

import com.streamvault.server.config.AppProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * The browser keeps the login token in an HttpOnly cookie, so page scripts (and any injected
 * script) cannot read it. SameSite=Strict stops other sites from sending it with their
 * requests, which is what protects the cookie-based API from cross-site request forgery.
 */
@Component
@RequiredArgsConstructor
public class SessionCookie {

    public static final String NAME = "sv_session";
    private static final String PATH = "/api";

    private final AppProperties appProperties;

    public ResponseCookie create(String token, HttpServletRequest request) {
        return ResponseCookie.from(NAME, token)
                .httpOnly(true)
                .secure(request.isSecure())   // true behind the HTTPS proxy
                .sameSite("Strict")
                .path(PATH)
                .maxAge(Duration.ofMillis(appProperties.jwt().expirationMs()))
                .build();
    }

    public ResponseCookie clear(HttpServletRequest request) {
        return ResponseCookie.from(NAME, "")
                .httpOnly(true)
                .secure(request.isSecure())
                .sameSite("Strict")
                .path(PATH)
                .maxAge(Duration.ZERO)
                .build();
    }

    public static String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (NAME.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                return c.getValue();
            }
        }
        return null;
    }
}
