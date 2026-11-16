package com.streamvault.server.security;

import com.streamvault.server.model.entity.Account;
import com.streamvault.server.model.entity.Role;
import com.streamvault.server.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** V6: repeated wrong passwords lock sign-in for a while, per account and per device. */
@DisplayName("V6 - sign-in rate limiting")
class V6LoginRateLimitTest extends IntegrationTestBase {

    private ResultActions login(String email, String password, String ip) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")
                .with(r -> { r.setRemoteAddr(ip); return r; }));
    }

    @Test
    @DisplayName("after 5 wrong passwords the account is locked, even with the right password")
    void accountLockedAfterFiveFailures() throws Exception {
        Account account = createAccount(Role.USER);   // password: Password123!
        String ip = "10.7.0.1";

        for (int i = 0; i < 5; i++) {
            login(account.getEmail(), "wrong-password", ip).andExpect(status().isUnauthorized());
        }
        login(account.getEmail(), "Password123!", ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(r -> assertThat(r.getResponse().getHeader("Retry-After")).isNotBlank());

        // trying from another device does not get around the account lock
        login(account.getEmail(), "Password123!", "10.7.0.2").andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("a correct sign-in before the limit clears the account's failures")
    void successResetsAccountFailures() throws Exception {
        Account account = createAccount(Role.USER);
        String ip = "10.7.1.1";

        for (int i = 0; i < 4; i++) {
            login(account.getEmail(), "wrong-password", ip).andExpect(status().isUnauthorized());
        }
        login(account.getEmail(), "Password123!", ip).andExpect(status().isOk());
        login(account.getEmail(), "wrong-password", ip).andExpect(status().isUnauthorized());
        login(account.getEmail(), "Password123!", ip).andExpect(status().isOk());
    }

    @Test
    @DisplayName("one device guessing many accounts is blocked after 20 failures")
    void deviceLockedAfterManyFailures() throws Exception {
        String ip = "10.7.2.1";
        for (int i = 0; i < 20; i++) {
            login("nobody-" + UUID.randomUUID() + "@test.local", "guess", ip)
                    .andExpect(status().isUnauthorized());
        }
        Account real = createAccount(Role.USER);
        login(real.getEmail(), "Password123!", ip).andExpect(status().isTooManyRequests());

        // the same account from a different device is fine
        login(real.getEmail(), "Password123!", "10.7.2.2").andExpect(status().isOk());
    }
}
