package com.streamvault.server.controller;

import com.streamvault.server.model.dto.profile.ProfileDtos.*;
import com.streamvault.server.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/profiles")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    /** GET /api/profiles — list all profiles for the logged-in account */
    @GetMapping
    public ResponseEntity<List<ProfileResponse>> listProfiles(
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(profileService.listProfiles(user.getUsername()));
    }

    /** GET /api/profiles/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<ProfileResponse> getProfile(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(profileService.getProfile(id, user.getUsername()));
    }

    /** POST /api/profiles */
    @PostMapping
    public ResponseEntity<ProfileResponse> createProfile(
            @Valid @RequestBody CreateProfileRequest request,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(profileService.createProfile(request, user.getUsername()));
    }

    /** PUT /api/profiles/{id} */
    @PutMapping("/{id}")
    public ResponseEntity<ProfileResponse> updateProfile(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(profileService.updateProfile(id, request, user.getUsername()));
    }

    /** DELETE /api/profiles/{id} */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProfile(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails user) {
        profileService.deleteProfile(id, user.getUsername());
        return ResponseEntity.noContent().build();
    }
}
