package com.bootsandcats.oauth2.integration;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bootsandcats.oauth2.config.TestOAuth2ClientConfiguration;

/** Integration tests for actuator endpoints and metrics. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestOAuth2ClientConfiguration.class)
class ActuatorIntegrationTest {

    @Autowired private MockMvc mockMvc;

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
        assertThat(responseBody).contains("jvm_");
        assertThat(responseBody).contains("http_server_requests");
    }

    @Test
    void infoEndpoint_shouldReturnAppInfo() throws Exception {
        mockMvc.perform(get("/actuator/info")).andExpect(status().isOk());
    }
}
