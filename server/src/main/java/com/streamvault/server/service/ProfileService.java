package com.streamvault.server.service;

import com.streamvault.server.exception.ResourceNotFoundException;
import com.streamvault.server.model.dto.profile.ProfileDtos.*;
import com.streamvault.server.model.entity.Account;
import com.streamvault.server.model.entity.Profile;
import com.streamvault.server.repository.AccountRepository;
import com.streamvault.server.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ProfileService {

    private static final int MAX_PROFILES = 5;

    private final ProfileRepository profileRepository;
    private final AccountRepository accountRepository;

    public List<ProfileResponse> listProfiles(String email) {
        Account account = getAccount(email);
        return profileRepository.findByAccountId(account.getId())
                .stream().map(this::toResponse).toList();
    }

    public ProfileResponse getProfile(UUID profileId, String email) {
        Profile profile = findAndVerifyOwnership(profileId, email);
        return toResponse(profile);
    }

    public ProfileResponse createProfile(CreateProfileRequest request, String email) {
        Account account = getAccount(email);

        long count = profileRepository.countByAccountId(account.getId());
        if (count >= MAX_PROFILES) {
            throw new IllegalArgumentException(
                    "Maximum of " + MAX_PROFILES + " profiles allowed per account");
        }

        Profile profile = Profile.builder()
                .account(account)
                .name(request.name())
                .avatarUrl(request.avatarUrl())
                .ageRestriction(request.ageRestriction() != null ? request.ageRestriction() : 18)
                .build();

        return toResponse(profileRepository.save(profile));
    }

    public ProfileResponse updateProfile(UUID profileId, UpdateProfileRequest request, String email) {
        Profile profile = findAndVerifyOwnership(profileId, email);

        if (request.name() != null)           profile.setName(request.name());
        if (request.avatarUrl() != null)      profile.setAvatarUrl(request.avatarUrl());
        if (request.ageRestriction() != null) profile.setAgeRestriction(request.ageRestriction());

        return toResponse(profileRepository.save(profile));
    }

    public void deleteProfile(UUID profileId, String email) {
        Account account = getAccount(email);
        Profile profile = findAndVerifyOwnership(profileId, email);

        long count = profileRepository.countByAccountId(account.getId());
        if (count <= 1) {
            throw new IllegalArgumentException("Cannot delete the only profile on an account");
        }

        profileRepository.delete(profile);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Profile findAndVerifyOwnership(UUID profileId, String email) {
        Profile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Profile not found: " + profileId));

        if (!profile.getAccount().getEmail().equals(email)) {
            throw new AccessDeniedException("You do not own this profile");
        }
        return profile;
    }

    private Account getAccount(String email) {
        return accountRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + email));
    }

    private ProfileResponse toResponse(Profile p) {
        return new ProfileResponse(
                p.getId(), p.getName(), p.getAvatarUrl(),
                p.getAgeRestriction(), p.getCreatedAt());
    }
}