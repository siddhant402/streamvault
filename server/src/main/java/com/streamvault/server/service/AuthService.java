package com.streamvault.server.service;

import com.streamvault.server.config.AppProperties;
import com.streamvault.server.exception.ForbiddenException;
import com.streamvault.server.model.dto.auth.AuthDtos.*;
import com.streamvault.server.model.entity.Account;
import com.streamvault.server.model.entity.Role;
import com.streamvault.server.repository.AccountRepository;
import com.streamvault.server.security.AuthRateLimiter;
import com.streamvault.server.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final AuthRateLimiter rateLimiter;
    private final AppProperties appProperties;

    /**
     * The first account on a new server can always register. After that, registering needs
     * the invite code set in SV_INVITE_CODE; with no code set, registration is closed.
     */
    public AuthResponse register(RegisterRequest request, String clientIp) {
        rateLimiter.checkRegistration(clientIp);

        boolean firstAccount = accountRepository.count() == 0;
        if (!firstAccount) {
            String expected = appProperties.authOrDefault().inviteCode();
            if (expected == null || expected.isBlank()) {
                throw new ForbiddenException("Registration is closed. Ask the server owner to set an invite code.");
            }
            if (!constantTimeEquals(expected, request.inviteCode())) {
                rateLimiter.recordRegistration(clientIp);   // wrong guesses count towards the limit
                throw new ForbiddenException("That invite code is not valid.");
            }
        }

        if (accountRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("An account with this email already exists");
        }

        Account account = Account.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                // The first account on a new server owns it, so it can run admin tasks like re-scan
                .role(firstAccount ? Role.ADMIN : Role.USER)
                .build();

        accountRepository.save(account);
        rateLimiter.recordRegistration(clientIp);

        String token = jwtTokenProvider.generateToken(account);
        return new AuthResponse(token, account.getEmail(), account.getRole().name());
    }

    public AuthResponse login(LoginRequest request, String clientIp) {
        rateLimiter.checkLogin(request.email(), clientIp);

        try {
            // Throws BadCredentialsException if wrong — caught by GlobalExceptionHandler
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        } catch (BadCredentialsException e) {
            rateLimiter.recordFailedLogin(request.email(), clientIp);
            throw e;
        }
        rateLimiter.recordSuccessfulLogin(request.email());

        Account account = accountRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        String token = jwtTokenProvider.generateToken(account);
        return new AuthResponse(token, account.getEmail(), account.getRole().name());
    }

    private static boolean constantTimeEquals(String expected, String given) {
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = (given == null ? "" : given.trim()).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }
}
