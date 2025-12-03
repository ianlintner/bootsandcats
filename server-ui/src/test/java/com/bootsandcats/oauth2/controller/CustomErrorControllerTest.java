package com.bootsandcats.oauth2.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bootsandcats.oauth2.config.TestOAuth2ClientConfiguration;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestOAuth2ClientConfiguration.class)
class CustomErrorControllerTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void error_unauthenticatedPath_shouldRedirectToLogin() throws Exception {
        // Unauthenticated requests to protected paths redirect to login
        mockMvc.perform(get("/non-existent-path")).andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser
    void error_authenticatedPath_shouldReturn404() throws Exception {
        // Authenticated requests to non-existent paths return 404
        mockMvc.perform(get("/non-existent-path-that-does-not-exist"))
                .andExpect(status().isNotFound());
    }

    @Test
    void error_shouldReturnJsonErrorResponse() throws Exception {
        mockMvc.perform(get("/error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.error").exists());
    }
}
