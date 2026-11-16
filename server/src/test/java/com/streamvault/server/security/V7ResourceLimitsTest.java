package com.streamvault.server.security;

import com.streamvault.server.model.entity.Role;
import com.streamvault.server.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** V7: the movie list refuses huge pages and sorting by hidden fields. */
@DisplayName("V7 - movie list limits")
class V7ResourceLimitsTest extends IntegrationTestBase {

    String auth;

    @BeforeEach
    void setUp() {
        auth = "Bearer " + loginToken(createAccount(Role.USER));
    }

    private void expect(String query, int status) throws Exception {
        mvc.perform(get("/api/movies" + query).header("Authorization", auth))
                .andExpect(status().is(status));
    }

    @Test
    @DisplayName("page size above 100, or below 1, is refused")
    void pageSizeLimited() throws Exception {
        expect("?size=100000", 400);
        expect("?size=101", 400);
        expect("?size=0", 400);
        expect("?page=-1", 400);
        expect("?size=100", 200);
        expect("?size=24", 200);
    }

    @Test
    @DisplayName("only the four public fields can be sorted on")
    void sortFieldsAllowListed() throws Exception {
        expect("?sortBy=localPath", 400);
        expect("?sortBy=folderPath", 400);
        expect("?sortBy=title", 200);
        expect("?sortBy=releaseYear&direction=desc", 200);
        expect("?sortBy=runtimeMinutes", 200);
        expect("?sortBy=createdAt", 200);
        expect("?direction=sideways", 400);
    }
}
