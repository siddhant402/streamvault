package com.streamvault.server.security;

import com.streamvault.server.model.entity.Account;
import com.streamvault.server.model.entity.Role;
import com.streamvault.server.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** V1: even with the dev profile switched on, only an ADMIN can use the dev endpoint. */
@ActiveProfiles("dev")
@DisplayName("V1 - dev endpoint, dev profile on")
class V1DevEndpointDevProfileTest extends IntegrationTestBase {

    @Test
    @DisplayName("anonymous caller is rejected")
    void anonymousRejected() throws Exception {
        Account victim = createAccount(Role.USER);

        mvc.perform(delete("/api/dev/accounts/{email}", victim.getEmail()))
                .andExpect(rejected());

        assertThat(accounts.findByEmail(victim.getEmail())).isPresent();
    }

    @Test
    @DisplayName("regular user is rejected")
    void regularUserRejected() throws Exception {
        Account victim = createAccount(Role.USER);
        Account attacker = createAccount(Role.USER);

        mvc.perform(delete("/api/dev/accounts/{email}", victim.getEmail())
                        .header("Authorization", "Bearer " + loginToken(attacker)))
                .andExpect(rejected());

        assertThat(accounts.findByEmail(victim.getEmail())).isPresent();
    }

    @Test
    @DisplayName("admin can still use it")
    void adminAllowed() throws Exception {
        Account target = createAccount(Role.USER);
        Account admin = createAccount(Role.ADMIN);

        mvc.perform(delete("/api/dev/accounts/{email}", target.getEmail())
                        .header("Authorization", "Bearer " + loginToken(admin)))
                .andExpect(status().isNoContent());

        assertThat(accounts.findByEmail(target.getEmail())).isEmpty();
    }
}
