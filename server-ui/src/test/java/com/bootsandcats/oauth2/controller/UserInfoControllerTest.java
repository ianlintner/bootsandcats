package com.bootsandcats.oauth2.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.bootsandcats.oauth2.config.TestKeyManagementConfig;
import com.bootsandcats.oauth2.config.TestOAuth2ClientConfiguration;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestOAuth2ClientConfiguration.class, TestKeyManagementConfig.class})
class UserInfoControllerTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void userinfo_shouldRedirectToLoginWhenNotAuthenticated() throws Exception {
        // OAuth2 protected endpoints redirect to login when not authenticated
        mockMvc.perform(get("/userinfo")).andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    void userinfo_shouldReturn401WithoutJwtToken() throws Exception {
        // UserInfo endpoint requires JWT authentication, not form login
        mockMvc.perform(get("/userinfo")).andExpect(status().isUnauthorized());
    }
}
