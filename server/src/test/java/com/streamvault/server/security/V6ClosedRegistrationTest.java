package com.streamvault.server.security;

import com.streamvault.server.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** V6: with no invite code set, only the very first account can register. */
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:streamvault-v6-closed;DB_CLOSE_DELAY=-1",
        "app.auth.invite-code="
})
@DisplayName("V6 - registration closed without an invite code")
class V6ClosedRegistrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("first account registers, the second is refused")
    void closedAfterFirstAccount() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"owner@test.local\",\"password\":\"Password123!\"}")
                        .with(r -> { r.setRemoteAddr("10.6.2.1"); return r; }))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"stranger@test.local\",\"password\":\"Password123!\",\"inviteCode\":\"\"}")
                        .with(r -> { r.setRemoteAddr("10.6.2.2"); return r; }))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Registration is closed. Ask the server owner to set an invite code."));
    }
}
