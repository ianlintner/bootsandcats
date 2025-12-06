package com.bootsandcats.oauth2.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.bootsandcats.oauth2.config.TestKeyManagementConfig;
import com.bootsandcats.oauth2.config.TestOAuth2ClientConfiguration;

/** Integration tests for actuator endpoints and metrics. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// @AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestOAuth2ClientConfiguration.class, TestKeyManagementConfig.class})
class ActuatorIntegrationTest {

    @Autowired private WebApplicationContext context;
    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void healthEndpoint_shouldReturnUp() throws Exception {
        MvcResult result =
                mockMvc.perform(get("/actuator/health")).andExpect(status().isOk()).andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).contains("UP");
    }

    @Test
    void livenessEndpoint_shouldReturnUp() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
    }

    @Test
    void readinessEndpoint_shouldReturnUp() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }

    @Test
    void prometheusEndpoint_shouldReturnMetrics() throws Exception {
        MvcResult result =
                mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isOk()).andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).isNotBlank();
        assertThat(responseBody).contains("jvm_");
        assertThat(responseBody)
                .containsAnyOf(
                        "process_uptime_seconds", "process_start_time_seconds", "system_cpu_usage");
    }

    @Test
    void infoEndpoint_shouldReturnAppInfo() throws Exception {
        mockMvc.perform(get("/actuator/info")).andExpect(status().isOk());
    }
}
