package com.streamvault.server.service;

import com.streamvault.server.model.dto.auth.AuthDtos.*;
import com.streamvault.server.model.entity.Account;
import com.streamvault.server.model.entity.Role;
import com.streamvault.server.repository.AccountRepository;
import com.streamvault.server.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;

    public AuthResponse register(RegisterRequest request) {
        if (accountRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("An account with this email already exists");
        }

        Account account = Account.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(Role.USER)
                .build();

        accountRepository.save(account);

        String token = jwtTokenProvider.generateToken(account);
        return new AuthResponse(token, account.getEmail(), account.getRole().name());
    }

    public AuthResponse login(LoginRequest request) {
        // Throws BadCredentialsException if wrong — caught by GlobalExceptionHandler
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        Account account = accountRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        String token = jwtTokenProvider.generateToken(account);
        return new AuthResponse(token, account.getEmail(), account.getRole().name());
    }
}
