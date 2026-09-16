package com.localai.workspace.config;

import com.localai.workspace.health.HealthController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthController.class)
@Import(DesktopCorsConfiguration.class)
class DesktopCorsConfigurationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void permitsOnlyTheFixedTauriWindowsOrigin() throws Exception {
        mockMvc.perform(options("/api/health")
                        .header("Origin", DesktopCorsConfiguration.TAURI_WINDOWS_ORIGIN)
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", DesktopCorsConfiguration.TAURI_WINDOWS_ORIGIN));

        mockMvc.perform(options("/api/health")
                        .header("Origin", "https://example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
