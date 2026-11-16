package com.streamvault.server.model.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuthDtos {

    public record RegisterRequest(
            @Email(message = "Must be a valid email")
            @NotBlank
            String email,

            @NotBlank
            @Size(min = 8, message = "Password must be at least 8 characters")
            String password,

            /** Needed once the first account exists (SV_INVITE_CODE on the server). */
            @Size(max = 200)
            String inviteCode
    ) {}

    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password
    ) {}

    public record AuthResponse(
            String token,
            String email,
            String role
    ) {}

    /** The signed-in account, from GET /api/auth/me. */
    public record MeResponse(String email, String role) {}
}
