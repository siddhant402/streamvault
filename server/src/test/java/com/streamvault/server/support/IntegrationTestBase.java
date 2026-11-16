package com.streamvault.server.support;

import com.streamvault.server.StreamVaultApplication;
import com.streamvault.server.model.entity.Account;
import com.streamvault.server.model.entity.Movie;
import com.streamvault.server.model.entity.Role;
import com.streamvault.server.repository.AccountRepository;
import com.streamvault.server.repository.MovieRepository;
import com.streamvault.server.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the whole app against an in-memory database. @TestPropertySource outranks
 * application.yml, application-local.yml and environment variables, so tests never touch
 * your real database, movies folder or secrets.
 */
@SpringBootTest(classes = StreamVaultApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.jwt.secret=c3RyZWFtdmF1bHQtdGVzdC1zZWNyZXQtbm90LWZvci1wcm9kdWN0aW9uLXVzZSEh",
        "spring.datasource.url=jdbc:h2:mem:streamvault-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.password=",
        "app.media.root-path=target/no-media-in-tests",
        "app.tmdb.api-key=",
        "logging.level.com.streamvault=INFO"
})
public abstract class IntegrationTestBase {

    @Autowired protected MockMvc mvc;
    @Autowired protected AccountRepository accounts;
    @Autowired protected MovieRepository movies;
    @Autowired protected JwtTokenProvider jwt;
    @Autowired protected PasswordEncoder passwordEncoder;

    protected Account createAccount(Role role) {
        Account account = Account.builder()
                .email(role.name().toLowerCase() + "-" + UUID.randomUUID() + "@test.local")
                .password(passwordEncoder.encode("Password123!"))
                .role(role)
                .build();
        return accounts.save(account);
    }

    protected String loginToken(Account account) {
        return jwt.generateToken(account);
    }

    protected Movie createMovie() {
        return movies.save(Movie.builder()
                .title("Test movie " + UUID.randomUUID())
                .localPath("/does/not/exist/" + UUID.randomUUID() + ".mkv")
                .build());
    }

    /** The request was refused by security: 401 (not logged in) or 403 (not allowed). */
    protected static ResultMatcher rejected() {
        return result -> assertThat(result.getResponse().getStatus())
                .as("expected 401 or 403 but got %s", result.getResponse().getStatus())
                .isIn(401, 403);
    }
}
