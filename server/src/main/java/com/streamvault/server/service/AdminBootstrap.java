package com.streamvault.server.service;

import com.streamvault.server.config.AppProperties;
import com.streamvault.server.model.entity.Role;
import com.streamvault.server.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Makes the accounts listed in SV_ADMIN_EMAILS administrators when the server starts, so an
 * existing install can get an admin without editing the database. The first account
 * registered on a new server is made admin automatically (see AuthService).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrap {

    private final AccountRepository accountRepository;
    private final AppProperties appProperties;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        promoteConfiguredAdmins();
    }

    /** Returns how many accounts were promoted. */
    @Transactional
    public int promoteConfiguredAdmins() {
        int promoted = 0;
        for (String email : appProperties.authOrDefault().adminEmailsOrEmpty()) {
            var account = accountRepository.findByEmail(email);
            if (account.isEmpty()) {
                log.warn("SV_ADMIN_EMAILS lists {}, but no account has that email yet", email);
                continue;
            }
            if (account.get().getRole() != Role.ADMIN) {
                account.get().setRole(Role.ADMIN);
                accountRepository.save(account.get());
                log.info("Made {} an administrator (from SV_ADMIN_EMAILS)", email);
                promoted++;
            }
        }
        return promoted;
    }
}
