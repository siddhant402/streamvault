package com.streamvault.server.model.dto.profile;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public class ProfileDtos {

    public record CreateProfileRequest(
            @NotBlank(message = "Name is required")
            @Size(min = 1, max = 50, message = "Name must be between 1 and 50 characters")
            String name,

            String avatarUrl,

            @Min(value = 0,  message = "Age restriction must be between 0 and 18")
            @Max(value = 18, message = "Age restriction must be between 0 and 18")
            Integer ageRestriction
    ) {}

    public record UpdateProfileRequest(
            @Size(min = 1, max = 50, message = "Name must be between 1 and 50 characters")
            String name,

            String avatarUrl,

            @Min(0) @Max(18)
            Integer ageRestriction
    ) {}

    public record ProfileResponse(
            UUID id,
            String name,
            String avatarUrl,
            Integer ageRestriction,
            Instant createdAt
    ) {}
}
