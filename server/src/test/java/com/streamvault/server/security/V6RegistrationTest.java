package com.streamvault.server.security;

import com.streamvault.server.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** V6: after the first account, registering needs the invite code. Uses its own empty database. */
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:streamvault-v6-invite;DB_CLOSE_DELAY=-1",
        "app.auth.invite-code=family-code-123"
})
@DisplayName("V6 - registration needs an invite code")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class V6RegistrationTest extends IntegrationTestBase {

    private ResultActions register(String inviteCode, String ip) throws Exception {
        String email = "reg-" + UUID.randomUUID() + "@test.local";
        String body = "{\"email\":\"" + email + "\",\"password\":\"Password123!\""
                + (inviteCode != null ? ",\"inviteCode\":\"" + inviteCode + "\"" : "") + "}";
        return mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON).content(body)
                .with(r -> { r.setRemoteAddr(ip); return r; }));
    }

    @Test
    @Order(1)   // needs the empty database
    @DisplayName("first account needs no code; later ones need the right code")
    void inviteCodeFlow() throws Exception {
        assertThat(accounts.count()).isZero();

        register(null, "10.6.0.1").andExpect(status().isCreated());           // first account

        register(null, "10.6.0.2").andExpect(status().isForbidden());         // no code
        register("wrong-code", "10.6.0.3").andExpect(status().isForbidden())  // wrong code
                .andExpect(jsonPath("$.error").value("That invite code is not valid."));
        register("family-code-123", "10.6.0.4").andExpect(status().isCreated());

        assertThat(accounts.count()).isEqualTo(2);
    }

    @Test
    @Order(2)
    @DisplayName("one device can create at most 5 accounts an hour")
    void registrationRateLimited() throws Exception {
        String ip = "10.6.1.1";
        for (int i = 0; i < 5; i++) {
            register("family-code-123", ip).andExpect(status().isCreated());
        }
        register("family-code-123", ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(result -> assertThat(result.getResponse().getHeader("Retry-After")).isNotBlank());

        register("family-code-123", "10.6.1.2").andExpect(status().isCreated());   // other device unaffected
    }
}
