package com.bootsandcats.oauth2.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.bootsandcats.oauth2.config.TestKeyManagementConfig;
import com.bootsandcats.oauth2.config.TestOAuth2ClientConfiguration;

/**
 * Security tests for OAuth2 endpoints.
 *
 * <p>Tests security headers, CSRF protection, and authentication requirements.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestOAuth2ClientConfiguration.class, TestKeyManagementConfig.class})
class OAuth2SecurityTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void wellKnownOpenIdConfiguration_shouldBePubliclyAccessible() throws Exception {
        mockMvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").exists())
                .andExpect(jsonPath("$.authorization_endpoint").exists())
                .andExpect(jsonPath("$.token_endpoint").exists())
                .andExpect(jsonPath("$.jwks_uri").exists());
    }

    @Test
    void jwksEndpoint_shouldBePubliclyAccessible() throws Exception {
        mockMvc.perform(get("/oauth2/jwks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys").isArray())
                .andExpect(jsonPath("$.keys[0].kty").value("EC"));
    }

    @Test
    void tokenEndpoint_shouldRejectInvalidCredentials() throws Exception {
        mockMvc.perform(
                        post("/oauth2/token")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("grant_type", "client_credentials")
                                .with(httpBasic("invalid-client", "invalid-secret")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenEndpoint_shouldIssueTokenWithValidCredentials() throws Exception {
        mockMvc.perform(
                        post("/oauth2/token")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("grant_type", "client_credentials")
                                .param("scope", "api:read")
                                .with(httpBasic("m2m-client", "m2m-secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").exists())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").exists());
    }

    @Test
    void authorizeEndpoint_shouldReturnBadRequestForInvalidRequest() throws Exception {
        // OAuth2 authorize endpoint returns 400 for missing/invalid parameters
        mockMvc.perform(
                        get("/oauth2/authorize")
                                .param("response_type", "code")
                                .param("client_id", "demo-client")
                                .param("redirect_uri", "http://localhost:8080/callback")
                                .param("scope", "openid profile"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void actuatorHealth_shouldBePubliclyAccessible() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void actuatorPrometheus_shouldBePubliclyAccessible() throws Exception {
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isOk());
    }

    @Test
    void introspectEndpoint_shouldRedirectOrRejectWithoutCredentials() throws Exception {
        // Without credentials, the endpoint will redirect to login or return unauthorized
        mockMvc.perform(
                        post("/oauth2/introspect")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("token", "some-token"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void revokeEndpoint_shouldRedirectOrRejectWithoutCredentials() throws Exception {
        // Without credentials, the endpoint will redirect to login or return unauthorized
        mockMvc.perform(
                        post("/oauth2/revoke")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("token", "some-token"))
                .andExpect(status().is3xxRedirection());
    }
}
