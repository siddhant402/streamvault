package com.streamvault.server.security;

import com.streamvault.server.model.entity.Account;
import com.streamvault.server.model.entity.Movie;
import com.streamvault.server.model.entity.Role;
import com.streamvault.server.support.IntegrationTestBase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** V12: the web app's session is an HttpOnly, SameSite=Strict cookie, not a token in localStorage. */
@DisplayName("V12 - session cookie")
class V12CookieAuthTest extends IntegrationTestBase {

    private MvcResult login(Account account, String ip, boolean https) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + account.getEmail() + "\",\"password\":\"Password123!\"}")
                        .secure(https)
                        .with(r -> { r.setRemoteAddr(ip); return r; }))
                .andExpect(status().isOk())
                .andReturn();
    }

    @Test
    @DisplayName("sign-in sets an HttpOnly, SameSite=Strict cookie scoped to /api")
    void cookieAttributes() throws Exception {
        String setCookie = login(createAccount(Role.USER), "10.12.0.1", false).getResponse().getHeader("Set-Cookie");

        assertThat(setCookie).startsWith("sv_session=")
                .contains("HttpOnly", "SameSite=Strict", "Path=/api", "Max-Age=86400")
                .doesNotContain("Secure");
    }

    @Test
    @DisplayName("over HTTPS the cookie is also marked Secure")
    void secureOverHttps() throws Exception {
        String setCookie = login(createAccount(Role.USER), "10.12.0.2", true).getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).contains("Secure");
    }

    @Test
    @DisplayName("the cookie alone signs the browser in")
    void cookieAuthenticates() throws Exception {
        Account account = createAccount(Role.USER);
        Cookie session = login(account, "10.12.0.3", false).getResponse().getCookie("sv_session");
        assertThat(session).isNotNull();
        assertThat(session.isHttpOnly()).isTrue();

        mvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(account.getEmail()))
                .andExpect(jsonPath("$.role").value("USER"));
        mvc.perform(get("/api/movies").cookie(session)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("without a session the API answers 401, so the app shows sign-in")
    void signedOutIs401() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/movies")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/movies").cookie(new Cookie("sv_session", "not-a-token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("sign-out clears the cookie")
    void logoutClears() throws Exception {
        String setCookie = mvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).startsWith("sv_session=;").contains("Max-Age=0", "Path=/api");
    }

    @Test
    @DisplayName("a movie stream token in the cookie is not accepted as a session")
    void streamTokenNotASession() throws Exception {
        Account account = createAccount(Role.USER);
        Movie movie = createMovie();
        String stream = jwt.generateStreamToken(account, movie.getId()).token();

        mvc.perform(get("/api/movies").cookie(new Cookie("sv_session", stream)))
                .andExpect(status().isUnauthorized());
    }
}
