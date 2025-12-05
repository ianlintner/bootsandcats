package com.bootsandcats.oauth2.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.bootsandcats.oauth2.config.TestOAuth2ClientConfiguration;

/** Tests for security headers compliance (OWASP recommendations). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestOAuth2ClientConfiguration.class, TestKeyManagementConfig.class})
class SecurityHeadersTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @WithMockUser
    void response_shouldContainXContentTypeOptions() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    @WithMockUser
    void response_shouldContainXFrameOptions() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Frame-Options"));
    }

    @Test
    @WithMockUser
    void response_shouldContainCacheControlHeaders() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Cache-Control"));
    }
}
