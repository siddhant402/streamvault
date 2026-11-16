package com.streamvault.server.security;

import com.streamvault.server.support.ConfigFiles;
import com.streamvault.server.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** V5: the app can sit behind the HTTPS proxy, and only trusts the proxy on this computer. */
@TestPropertySource(properties = "app.cors.allowed-origins=https://streamvault.local")
@DisplayName("V5 - HTTPS proxy setup")
class V5HttpsConfigTest extends IntegrationTestBase {

    @Test
    @DisplayName("CORS allows the configured HTTPS address")
    void corsAllowsConfiguredOrigin() throws Exception {
        mvc.perform(options("/api/movies")
                        .header("Origin", "https://streamvault.local")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://streamvault.local"));
    }

    @Test
    @DisplayName("CORS refuses other sites")
    void corsRefusesOtherOrigins() throws Exception {
        mvc.perform(options("/api/movies")
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Caddyfile serves HTTPS and forwards to the app on this computer")
    void caddyfile() throws Exception {
        String caddy = ConfigFiles.text("../Caddyfile");
        assertThat(caddy).contains("tls internal", "reverse_proxy 127.0.0.1:8080", "reverse_proxy 127.0.0.1:3000");
    }

    @Test
    @DisplayName("forwarded headers are trusted only from this computer")
    void forwardedHeadersTrustOnlyLocalhost() throws Exception {
        Map<String, Object> yml = ConfigFiles.yaml("application.yml");
        assertThat(ConfigFiles.get(yml, "server.forward-headers-strategy")).isEqualTo("native");

        String trusted = (String) ConfigFiles.get(yml, "server.tomcat.remoteip.internal-proxies");
        assertThat("127.0.0.1").matches(trusted);
        assertThat("0:0:0:0:0:0:0:1").matches(trusted);
        assertThat("192.168.1.20").doesNotMatch(trusted);
        assertThat("10.0.0.5").doesNotMatch(trusted);
        assertThat(ConfigFiles.get(yml, "app.cors.allowed-origins")).isEqualTo("${SV_CORS_ORIGINS:http://localhost:3000}");
    }
}
