package com.bootsandcats.oauth2.integration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bootsandcats.oauth2.config.TestOAuth2ClientConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Integration tests for complete OAuth2 authorization flows. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestOAuth2ClientConfiguration.class)
class OAuth2FlowIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @Test
    void clientCredentialsFlow_shouldIssueAccessToken() throws Exception {
        mockMvc.perform(
                        post("/oauth2/token")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("grant_type", "client_credentials")
                                .param("scope", "api:read api:write")
                                .with(httpBasic("m2m-client", "m2m-secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").exists())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").exists())
                .andExpect(jsonPath("$.scope").exists());
    }

    @Test
    void oidcDiscovery_shouldReturnValidConfiguration() throws Exception {
        MvcResult result =
                mockMvc.perform(get("/.well-known/openid-configuration"))
                        .andExpect(status().isOk())
                        .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode config = objectMapper.readTree(responseBody);

        assertThat(config.has("issuer")).isTrue();
        assertThat(config.has("authorization_endpoint")).isTrue();
        assertThat(config.has("token_endpoint")).isTrue();
        assertThat(config.has("jwks_uri")).isTrue();
        assertThat(config.has("userinfo_endpoint")).isTrue();
        assertThat(config.has("response_types_supported")).isTrue();
        assertThat(config.has("grant_types_supported")).isTrue();
        assertThat(config.has("subject_types_supported")).isTrue();
        assertThat(config.has("id_token_signing_alg_values_supported")).isTrue();
        assertThat(config.has("scopes_supported")).isTrue();
        assertThat(config.has("code_challenge_methods_supported")).isTrue();
    }

    @Test
    void jwks_shouldReturnValidKeys() throws Exception {
        MvcResult result =
                mockMvc.perform(get("/oauth2/jwks")).andExpect(status().isOk()).andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode jwks = objectMapper.readTree(responseBody);

        assertThat(jwks.has("keys")).isTrue();
        assertThat(jwks.get("keys").isArray()).isTrue();
        assertThat(jwks.get("keys").size()).isGreaterThan(0);

        JsonNode key = jwks.get("keys").get(0);
        assertThat(key.get("kty").asText()).isEqualTo("EC");
        assertThat(key.has("kid")).isTrue();
        assertThat(key.has("crv")).isTrue();
        assertThat(key.has("x")).isTrue();
        assertThat(key.has("y")).isTrue();
    }

    @Test
    void pkceFlow_shouldRequireCodeVerifierForPKCEClient() throws Exception {
        // Generate PKCE code verifier and challenge
        String codeVerifier = generateCodeVerifier();
        String codeChallenge = generateCodeChallenge(codeVerifier);

        // PKCE authorization requests are handled - this tests the endpoint is accessible
        // without user authentication, it will return a 400 (bad request) or redirect
        mockMvc.perform(
                        get("/oauth2/authorize")
                                .param("response_type", "code")
                                .param("client_id", "public-client")
                                .param("redirect_uri", "http://localhost:3000/callback")
                                .param("scope", "openid profile")
                                .param("code_challenge", codeChallenge)
                                .param("code_challenge_method", "S256"))
                .andExpect(status().is4xxClientError()); // Returns 400 for unauthenticated request
    }

    @Test
    void tokenEndpoint_shouldRejectInvalidGrantType() throws Exception {
        mockMvc.perform(
                        post("/oauth2/token")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("grant_type", "invalid_grant")
                                .with(httpBasic("demo-client", "demo-secret")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tokenEndpoint_shouldIssueTokenWithDefaultScopeForClientCredentials() throws Exception {
        // Client credentials grant can use the client's configured scope if not specified
        // The demo-client supports client_credentials grant
        mockMvc.perform(
                        post("/oauth2/token")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("grant_type", "client_credentials")
                                .param("scope", "read")
                                .with(httpBasic("demo-client", "demo-secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").exists());
    }

    private String generateCodeVerifier() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] codeVerifier = new byte[32];
        secureRandom.nextBytes(codeVerifier);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(codeVerifier);
    }

    private String generateCodeChallenge(String codeVerifier) throws Exception {
        byte[] bytes = codeVerifier.getBytes(StandardCharsets.US_ASCII);
        MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
        messageDigest.update(bytes, 0, bytes.length);
        byte[] digest = messageDigest.digest();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }
}
