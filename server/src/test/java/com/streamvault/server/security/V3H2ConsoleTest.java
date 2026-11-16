package com.streamvault.server.security;

import com.streamvault.server.support.ConfigFiles;
import com.streamvault.server.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

/** V3: the H2 console must be off unless the dev profile is on, and then only local. */
@DisplayName("V3 - H2 console")
class V3H2ConsoleTest extends IntegrationTestBase {

    @Autowired Environment environment;

    @Test
    @DisplayName("console is disabled in the running app by default")
    void consoleDisabledAtRuntime() throws Exception {
        assertThat(environment.getProperty("spring.h2.console.enabled", Boolean.class, false)).isFalse();

        mvc.perform(get("/h2-console")).andExpect(rejected());
        mvc.perform(get("/h2-console/login.jsp")).andExpect(rejected());
    }

    @Test
    @DisplayName("pages can no longer be framed (X-Frame-Options: DENY)")
    void framingDenied() throws Exception {
        mvc.perform(get("/api/movies")).andExpect(header().string("X-Frame-Options", "DENY"));
    }

    @Test
    @DisplayName("application.yml: console off, no AUTO_SERVER port, password from SV_DB_PASSWORD")
    void baseConfig() throws Exception {
        Map<String, Object> yml = ConfigFiles.yaml("application.yml");

        assertThat(ConfigFiles.get(yml, "spring.h2.console.enabled")).isEqualTo(false);
        assertThat((String) ConfigFiles.get(yml, "spring.datasource.url")).doesNotContain("AUTO_SERVER");
        assertThat(ConfigFiles.get(yml, "spring.datasource.password")).isEqualTo("${SV_DB_PASSWORD:}");
        assertThat(ConfigFiles.get(yml, "spring.profiles.active")).isNull();
    }

    @Test
    @DisplayName("application-dev.yml: console reachable from this computer only")
    void devConfig() throws Exception {
        Map<String, Object> yml = ConfigFiles.yaml("application-dev.yml");

        assertThat(ConfigFiles.get(yml, "spring.h2.console.enabled")).isEqualTo(true);
        assertThat(ConfigFiles.get(yml, "spring.h2.console.settings.web-allow-others")).isEqualTo(false);
    }
}
