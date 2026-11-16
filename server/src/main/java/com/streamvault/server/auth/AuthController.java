package com.streamvault.server.auth;

import com.streamvault.server.model.dto.auth.AuthDtos.*;
import com.streamvault.server.security.SessionCookie;
import com.streamvault.server.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SessionCookie sessionCookie;

    /**
     * Signs in and sets the HttpOnly session cookie the web app uses. The token is also in
     * the body for scripts and other API clients that send it as a Bearer header.
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
                                                 HttpServletRequest http) {
        AuthResponse auth = authService.register(request, http.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, sessionCookie.create(auth.token(), http).toString())
                .body(auth);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest http) {
        AuthResponse auth = authService.login(request, http.getRemoteAddr());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, sessionCookie.create(auth.token(), http).toString())
                .body(auth);
    }

    /** Clears the session cookie. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest http) {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, sessionCookie.clear(http).toString())
                .build();
    }

    /** Who is signed in; the web app calls this on load instead of reading a stored token. */
    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(@AuthenticationPrincipal UserDetails user) {
        String role = user.getAuthorities().stream()
                .map(a -> a.getAuthority().replaceFirst("^ROLE_", ""))
                .findFirst().orElse("USER");
        return ResponseEntity.ok(new MeResponse(user.getUsername(), role));
    }
}
