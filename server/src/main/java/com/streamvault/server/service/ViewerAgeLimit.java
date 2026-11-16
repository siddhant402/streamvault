package com.streamvault.server.service;

import com.streamvault.server.exception.ResourceNotFoundException;
import com.streamvault.server.model.entity.Movie;
import com.streamvault.server.model.entity.Profile;
import com.streamvault.server.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Works out the age limit of whoever is watching, from the X-Profile-Id header the web app
 * sends with every request. 18 means no limit.
 *
 * This is a parental control, not a security boundary: the account owner can always choose
 * an unrestricted profile.
 */
@Component
@RequiredArgsConstructor
public class ViewerAgeLimit {

    public static final String HEADER = "X-Profile-Id";
    public static final int NO_LIMIT = 18;

    private final ProfileRepository profileRepository;

    /**
     * @param email         the signed-in account
     * @param profileHeader value of X-Profile-Id, or null when the caller sent none
     */
    @Transactional(readOnly = true)
    public int limitFor(String email, String profileHeader) {
        if (profileHeader == null || profileHeader.isBlank()) return NO_LIMIT;

        UUID profileId;
        try {
            profileId = UUID.fromString(profileHeader.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(HEADER + " is not a valid profile id");
        }

        Profile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found"));
        if (!profile.getAccount().getEmail().equals(email)) {
            throw new AccessDeniedException("You do not own this profile");
        }
        Integer limit = profile.getAgeRestriction();
        return limit == null ? NO_LIMIT : Math.max(0, Math.min(NO_LIMIT, limit));
    }

    /**
     * Below 18, only rated movies at or under the limit are shown; unrated movies are hidden
     * because nothing is known about them.
     */
    public static boolean allows(int limit, Movie movie) {
        if (limit >= NO_LIMIT) return true;
        return movie.getMinimumAge() != null && movie.getMinimumAge() <= limit;
    }

    /** Throws 404 (not 403, so the title is not revealed) when the profile may not see the movie. */
    public static void require(int limit, Movie movie) {
        if (!allows(limit, movie)) {
            throw new ResourceNotFoundException("Movie not found");
        }
    }
}
