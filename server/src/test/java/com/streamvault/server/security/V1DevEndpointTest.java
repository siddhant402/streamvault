package com.streamvault.server.security;

import com.streamvault.server.controller.DevController;
import com.streamvault.server.model.entity.Account;
import com.streamvault.server.model.entity.Role;
import com.streamvault.server.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

/** V1: the account-deletion endpoint must not be open to everyone (default profile). */
@DisplayName("V1 - dev endpoint, default profile")
class V1DevEndpointTest extends IntegrationTestBase {

    @Autowired ApplicationContext context;
    @Autowired Environment environment;

    @Test
    @DisplayName("dev profile is off by default and the dev controller is not loaded")
    void devProfileOffByDefault() {
        assertThat(environment.getActiveProfiles()).doesNotContain("dev");
        assertThat(context.getBeansOfType(DevController.class)).isEmpty();
    }

    @Test
    @DisplayName("anonymous caller cannot delete an account")
    void anonymousCannotDelete() throws Exception {
        Account victim = createAccount(Role.USER);

        mvc.perform(delete("/api/dev/accounts/{email}", victim.getEmail()))
                .andExpect(rejected());

        assertThat(accounts.findByEmail(victim.getEmail())).isPresent();
    }

    @Test
    @DisplayName("logged-in regular user cannot delete an account")
    void regularUserCannotDelete() throws Exception {
        Account victim = createAccount(Role.USER);
        Account attacker = createAccount(Role.USER);

        mvc.perform(delete("/api/dev/accounts/{email}", victim.getEmail())
                        .header("Authorization", "Bearer " + loginToken(attacker)))
                .andExpect(rejected());

        assertThat(accounts.findByEmail(victim.getEmail())).isPresent();
    }
}
