package com.streamvault.server.security;

import com.streamvault.server.model.entity.Movie;
import com.streamvault.server.model.entity.Role;
import com.streamvault.server.support.ConfigFiles;
import com.streamvault.server.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** V9: API responses and error pages do not reveal server paths or internals. */
@DisplayName("V9 - information disclosure")
class V9InfoDisclosureTest extends IntegrationTestBase {

    @TempDir Path folder;
    Movie movie;
    String auth;

    @BeforeEach
    void setUp() throws Exception {
        Files.writeString(folder.resolve("Film.en.srt"), "1\n00:00:01,000 --> 00:00:02,000\nHi\n");
        movie = movies.save(Movie.builder()
                .title("Info test")
                .localPath(folder.resolve("Film.mkv").toString())
                .folderPath(folder.toString())
                .build());
        auth = "Bearer " + loginToken(createAccount(Role.USER));
    }

    @Test
    @DisplayName("movie details do not include the server folder")
    void movieDetailsHaveNoPath() throws Exception {
        String body = mvc.perform(get("/api/movies/{id}", movie.getId()).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.folderPath").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(folder.getFileName().toString());
    }

    @Test
    @DisplayName("subtitle list gives file names only, no paths")
    void subtitleListHasNoPath() throws Exception {
        String body = mvc.perform(get("/api/stream/{id}/subtitles", movie.getId()).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].filename").value("Film.en.srt"))
                .andExpect(jsonPath("$[0].path").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(folder.getFileName().toString());
    }

    @Test
    @DisplayName("not-found errors do not echo the requested file name")
    void notFoundIsGeneric() throws Exception {
        mvc.perform(get("/api/stream/{id}/subtitles/{f}", movie.getId(), "missing-file.srt").header("Authorization", auth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Subtitle file not found"));
    }

    @Test
    @DisplayName("config: no error details to browsers, INFO logging by default")
    void quietConfig() throws Exception {
        Map<String, Object> yml = ConfigFiles.yaml("application.yml");
        assertThat(ConfigFiles.get(yml, "server.error.include-message")).isEqualTo("never");
        assertThat(ConfigFiles.get(yml, "server.error.include-binding-errors")).isEqualTo("never");
        assertThat(ConfigFiles.get(yml, "logging.level.com.streamvault")).isEqualTo("INFO");
    }
}
