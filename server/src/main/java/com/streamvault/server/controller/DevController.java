package com.streamvault.server.controller;

import com.streamvault.server.model.entity.Account;
import com.streamvault.server.model.entity.Profile;
import com.streamvault.server.repository.AccountRepository;
import com.streamvault.server.repository.ProfileRepository;
import com.streamvault.server.repository.WatchEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Dev-only endpoints for testing convenience.
 * Only active when spring.profiles.active=dev (never in prod).
 * The @Profile annotation is used fully qualified to avoid clash with the Profile entity.
 */
@RestController
@RequestMapping("/api/dev")
@RequiredArgsConstructor
@org.springframework.context.annotation.Profile("dev")
public class DevController {

    private final AccountRepository accountRepository;
    private final ProfileRepository profileRepository;
    private final WatchEventRepository watchEventRepository;

    /**
     * DELETE /api/dev/accounts/{email}
     * Deletes account + all child profiles + watch events in correct order.
     */
    @DeleteMapping("/accounts/{email}")
    @Transactional
    public ResponseEntity<Void> deleteAccount(@PathVariable String email) {
        accountRepository.findByEmail(email).ifPresent(account -> {
            // 1. Delete watch events for each profile
            List<Profile> profiles = profileRepository.findByAccountId(account.getId());
            profiles.forEach(profile ->
                    watchEventRepository.findByProfileIdOrderByOccurredAtDesc(profile.getId())
                            .forEach(watchEventRepository::delete)
            );

            // 2. Delete profiles
            profileRepository.deleteAll(profiles);

            // 3. Delete account
            accountRepository.delete(account);
        });
        return ResponseEntity.noContent().build();
    }
}