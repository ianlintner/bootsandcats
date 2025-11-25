package com.bootsandcats.oauth2.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Tests for JWT token generation and validation. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JwtTokenTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @Test
    void clientCredentials_shouldIssueValidJwt() throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post("/oauth2/token")
                                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                        .param("grant_type", "client_credentials")
                                        .param("scope", "api:read")
                                        .with(httpBasic("m2m-client", "m2m-secret")))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.access_token").exists())
                        .andExpect(jsonPath("$.token_type").value("Bearer"))
                        .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        String accessToken = jsonNode.get("access_token").asText();

        // Verify JWT structure (header.payload.signature)
        String[] jwtParts = accessToken.split("\\.");
        org.assertj.core.api.Assertions.assertThat(jwtParts).hasSize(3);
    }

    @Test
    void tokenIntrospection_shouldValidateToken() throws Exception {
        // First, get a token
        MvcResult tokenResult =
                mockMvc.perform(
                                post("/oauth2/token")
                                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                        .param("grant_type", "client_credentials")
                                        .param("scope", "api:read")
                                        .with(httpBasic("m2m-client", "m2m-secret")))
                        .andExpect(status().isOk())
                        .andReturn();

        String responseBody = tokenResult.getResponse().getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        String accessToken = jsonNode.get("access_token").asText();

        // Then introspect it
        mockMvc.perform(
                        post("/oauth2/introspect")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("token", accessToken)
                                .with(httpBasic("m2m-client", "m2m-secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.client_id").value("m2m-client"));
    }

    @Test
    void tokenRevocation_shouldInvalidateToken() throws Exception {
        // First, get a token
        MvcResult tokenResult =
                mockMvc.perform(
                                post("/oauth2/token")
                                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                        .param("grant_type", "client_credentials")
                                        .param("scope", "api:read")
                                        .with(httpBasic("m2m-client", "m2m-secret")))
                        .andExpect(status().isOk())
                        .andReturn();

        String responseBody = tokenResult.getResponse().getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        String accessToken = jsonNode.get("access_token").asText();

        // Then revoke it
        mockMvc.perform(
                        post("/oauth2/revoke")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("token", accessToken)
                                .with(httpBasic("m2m-client", "m2m-secret")))
                .andExpect(status().isOk());

        // Verify the token is no longer active
        mockMvc.perform(
                        post("/oauth2/introspect")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("token", accessToken)
                                .with(httpBasic("m2m-client", "m2m-secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }
}
