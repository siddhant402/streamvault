package com.streamvault.server.security;

import com.streamvault.server.model.entity.Account;
import com.streamvault.server.model.entity.Role;
import com.streamvault.server.service.AdminBootstrap;
import com.streamvault.server.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** V11: there is a supported way to get an administrator. Uses its own empty database. */
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:streamvault-v11;DB_CLOSE_DELAY=-1",
        "app.auth.invite-code=v11-code",
        "app.auth.admin-emails=boss@test.local, ,Missing@Test.local"
})
@DisplayName("V11 - admin accounts")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class V11AdminTest extends IntegrationTestBase {

    @Autowired AdminBootstrap adminBootstrap;
    @Autowired PasswordEncoder encoder;

    private String registerBody(String email, String code) {
        return "{\"email\":\"" + email + "\",\"password\":\"Password123!\""
                + (code != null ? ",\"inviteCode\":\"" + code + "\"" : "") + "}";
    }

    @Test
    @Order(1)   // needs the empty database
    @DisplayName("the first account registered is an admin; later ones are not")
    void firstAccountIsAdmin() throws Exception {
        assertThat(accounts.count()).isZero();

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("owner@test.local", null))
                        .with(r -> { r.setRemoteAddr("10.11.0.1"); return r; }))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("family@test.local", "v11-code"))
                        .with(r -> { r.setRemoteAddr("10.11.0.2"); return r; }))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    @Order(2)
    @DisplayName("SV_ADMIN_EMAILS promotes an existing account, which can then re-scan")
    void configuredAdminPromoted() throws Exception {
        Account boss = accounts.save(Account.builder()
                .email("boss@test.local").password(encoder.encode("Password123!")).role(Role.USER).build());
        Account regular = createAccount(Role.USER);

        mvc.perform(post("/api/admin/scan").header("Authorization", "Bearer " + loginToken(boss)))
                .andExpect(status().isForbidden());

        assertThat(adminBootstrap.promoteConfiguredAdmins()).isEqualTo(1);
        assertThat(accounts.findByEmail("boss@test.local").orElseThrow().getRole()).isEqualTo(Role.ADMIN);
        assertThat(adminBootstrap.promoteConfiguredAdmins()).isZero();   // already done

        mvc.perform(post("/api/admin/scan").header("Authorization", "Bearer " + loginToken(boss)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/admin/scan").header("Authorization", "Bearer " + loginToken(regular)))
                .andExpect(status().isForbidden());
    }
}
