package com.streamvault.server.security;

import com.streamvault.server.support.ConfigFiles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** V4: no secrets or machine-specific values in files that Git tracks. */
@DisplayName("V4 - secrets out of tracked config")
class V4SecretsConfigTest {

    @Test
    @DisplayName("application.yml takes secrets and paths from environment variables")
    void appConfigUsesEnvVars() throws Exception {
        Map<String, Object> yml = ConfigFiles.yaml("application.yml");

        assertThat(ConfigFiles.get(yml, "app.jwt.secret")).isEqualTo("${SV_JWT_SECRET:}");
        assertThat(ConfigFiles.get(yml, "app.tmdb.api-key")).isEqualTo("${SV_TMDB_API_KEY:}");
        assertThat(ConfigFiles.get(yml, "app.media.root-path")).asString().startsWith("${SV_MEDIA_ROOT:");
        assertThat(ConfigFiles.get(yml, "app.media.ffmpeg-path")).isEqualTo("${SV_FFMPEG_PATH:ffmpeg}");
        assertThat(ConfigFiles.get(yml, "spring.config.import")).isEqualTo("optional:file:./application-local.yml");
    }

    @Test
    @DisplayName("application.yml no longer contains the old placeholder secrets")
    void noPlaceholderSecrets() throws Exception {
        String text = ConfigFiles.text("application.yml");
        assertThat(text).doesNotContain("CHANGE_ME_run_openssl", "YOUR_TMDB_API_KEY", "USE YOUR OWN PATH");
    }

    @Test
    @DisplayName("application-local.yml and .env are ignored by Git")
    void localFilesIgnored() throws Exception {
        List<String> lines = ConfigFiles.text(".gitignore").lines().map(String::trim).toList();
        assertThat(lines).contains("/application-local.yml", ".env");
    }

    @Test
    @DisplayName("an example local config exists for new setups")
    void exampleExists() throws Exception {
        Map<String, Object> yml = ConfigFiles.yaml("application-local.example.yml");
        assertThat(ConfigFiles.get(yml, "app.jwt.secret")).isNotNull();
    }

    @Test
    @DisplayName("compose.yaml reads the Postgres password from the environment and binds to localhost")
    void composeHasNoPassword() throws Exception {
        String compose = ConfigFiles.text("compose.yaml");

        assertThat(compose).contains("${SV_POSTGRES_PASSWORD:?");
        assertThat(compose).doesNotContain("svpassword");
        assertThat(compose).contains("\"127.0.0.1:5432:5432\"", "\"127.0.0.1:9200:9200\"");
        assertThat(compose).doesNotContain("- \"5432:5432\"", "- \"9200:9200\"");
    }
}
