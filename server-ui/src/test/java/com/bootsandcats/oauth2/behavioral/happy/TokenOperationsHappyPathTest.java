package com.bootsandcats.oauth2.behavioral.happy;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.bootsandcats.oauth2.config.TestKeyManagementConfig;
import com.bootsandcats.oauth2.config.TestOAuth2ClientConfiguration;
import com.bootsandcats.oauth2.config.TestObjectMapperConfig;
import com.bootsandcats.oauth2.testing.AiAgentTestReporter;
import com.bootsandcats.oauth2.testing.assertions.OAuth2ResponseAssertions;
import com.bootsandcats.oauth2.testing.assertions.TokenAssertions;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Test ID: TK-HP-* (Token Operations Happy Path)
 *
 * <p>Behavioral tests for OAuth2 token operations - Happy Paths.
 *
 * <p>These tests verify that token introspection, revocation, and lifecycle operations work
 * correctly following RFC 7662 (Token Introspection) and RFC 7009 (Token Revocation).
 *
 * <h2>Scenario Coverage</h2>
 *
 * <ul>
 *   <li>TK-HP-001: Token introspection returns active status
 *   <li>TK-HP-002: Token revocation succeeds
 *   <li>TK-HP-003: Revoked token introspection returns inactive
 *   <li>TK-HP-004: Token contains expected claims
 *   <li>TK-HP-005: Token expiry is properly set
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({
    TestOAuth2ClientConfiguration.class,
    TestKeyManagementConfig.class,
    TestObjectMapperConfig.class
})
@ExtendWith(AiAgentTestReporter.class)
@Tag("oauth2")
@Tag("token-operations")
@Tag("happy-path")
@TestMethodOrder(OrderAnnotation.class)
@DisplayName("Token Operations - Happy Paths")
class TokenOperationsHappyPathTest {

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    /**
     * Test ID: TK-HP-001
     *
     * <p>Scenario: Valid token introspection should return active with metadata
     *
     * <p>Given: A valid access token When: The token is introspected Then: Response shows active:
     * true with client_id, scope, exp
     *
     * <p>Compliance: RFC 7662 Section 2.2
     */
    @Test
    @Order(1)
    @Tag("critical")
    @DisplayName("TK-HP-001: Valid token introspection returns active with metadata")
    void validToken_introspection_shouldReturnActiveWithMetadata() throws Exception {
        // Given: Obtain a valid token
        String accessToken = obtainAccessToken("api:read api:write");
        AiAgentTestReporter.setExpectedOutcome(
                "200 OK with active:true, client_id, scope, exp, iat");

        // When: Token is introspected
        MvcResult result =
                mockMvc.perform(
                                post("/oauth2/introspect")
                                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                        .param("token", accessToken)
                                        .with(httpBasic("m2m-client", "m2m-secret")))
                        .andExpect(status().isOk())
                        .andReturn();

        // Then: Token is active with metadata
        String responseBody = result.getResponse().getContentAsString();
        AiAgentTestReporter.setResponseContext(result.getResponse().getStatus(), responseBody);

        OAuth2ResponseAssertions.assertThat(result.getResponse().getStatus(), responseBody)
                .isActiveToken();

        var json = objectMapper.readTree(responseBody);
        org.assertj.core.api.Assertions.assertThat(json.has("client_id"))
                .as("Introspection should include client_id")
                .isTrue();
        org.assertj.core.api.Assertions.assertThat(json.path("client_id").asText())
                .isEqualTo("m2m-client");
        org.assertj.core.api.Assertions.assertThat(json.has("exp"))
                .as("Introspection should include exp")
                .isTrue();
        org.assertj.core.api.Assertions.assertThat(json.has("iat"))
                .as("Introspection should include iat")
                .isTrue();
    }

    /**
     * Test ID: TK-HP-002
     *
     * <p>Scenario: Token revocation should succeed
     *
     * <p>Given: A valid access token When: The token is revoked Then: 200 OK is returned
     *
     * <p>Compliance: RFC 7009 Section 2.1
     */
    @Test
    @Order(2)
    @Tag("critical")
    @DisplayName("TK-HP-002: Token revocation should succeed")
    void tokenRevocation_shouldSucceed() throws Exception {
        // Given: Obtain a valid token
        String accessToken = obtainAccessToken("api:read");
        AiAgentTestReporter.setExpectedOutcome("200 OK or 204 No Content");

        // When: Token is revoked
        MvcResult result =
                mockMvc.perform(
                                post("/oauth2/revoke")
                                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                        .param("token", accessToken)
                                        .param("token_type_hint", "access_token")
                                        .with(httpBasic("m2m-client", "m2m-secret")))
                        .andReturn();

        // Then: Revocation succeeds
        AiAgentTestReporter.setResponseContext(
                result.getResponse().getStatus(), result.getResponse().getContentAsString());

        int status = result.getResponse().getStatus();
        org.assertj.core.api.Assertions.assertThat(status)
                .as("Token revocation should return 200 or 204. Got: %d", status)
                .isIn(200, 204);
    }

    /**
     * Test ID: TK-HP-003
     *
     * <p>Scenario: Revoked token introspection should return inactive
     *
     * <p>Given: A token that has been revoked When: The token is introspected Then: Response shows
     * active: false
     *
     * <p>Compliance: RFC 7009 + RFC 7662
     */
    @Test
    @Order(3)
    @Tag("critical")
    @DisplayName("TK-HP-003: Revoked token introspection returns inactive")
    void revokedToken_introspection_shouldReturnInactive() throws Exception {
        // Given: Obtain and revoke a token
        String accessToken = obtainAccessToken("api:read");

        mockMvc.perform(
                        post("/oauth2/revoke")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("token", accessToken)
                                .with(httpBasic("m2m-client", "m2m-secret")))
                .andExpect(status().isOk());

        AiAgentTestReporter.setExpectedOutcome("200 OK with active: false");

        // When: Revoked token is introspected
        MvcResult result =
                mockMvc.perform(
                                post("/oauth2/introspect")
                                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                        .param("token", accessToken)
                                        .with(httpBasic("m2m-client", "m2m-secret")))
                        .andExpect(status().isOk())
                        .andReturn();

        // Then: Token is inactive
        String responseBody = result.getResponse().getContentAsString();
        AiAgentTestReporter.setResponseContext(result.getResponse().getStatus(), responseBody);

        OAuth2ResponseAssertions.assertThat(result.getResponse().getStatus(), responseBody)
                .isInactiveToken();
    }

    /**
     * Test ID: TK-HP-004
     *
     * <p>Scenario: Access token should contain expected JWT claims
     *
     * <p>Given: A valid token request When: Token is issued Then: JWT contains sub, iss, aud, exp,
     * iat, scope
     *
     * <p>Compliance: RFC 9068 (JWT Access Tokens)
     */
    @Test
    @Order(4)
    @DisplayName("TK-HP-004: Access token contains expected JWT claims")
    void accessToken_shouldContainExpectedClaims() throws Exception {
        // Given: Valid token request
        AiAgentTestReporter.setExpectedOutcome(
                "JWT with sub, iss, exp, iat, scope claims");

        // When: Token is issued
        String accessToken = obtainAccessToken("api:read");

        // Then: JWT contains expected claims
        TokenAssertions.assertThat(accessToken)
                .isValidJwt()
                .hasAlgorithm("ES256")
                .hasClaim("sub")
                .hasClaim("iss")
                .hasClaim("exp")
                .hasClaim("iat")
                .hasClaim("scope")
                .isNotExpired();
    }

    /**
     * Test ID: TK-HP-005
     *
     * <p>Scenario: Token expiry should be properly set
     *
     * <p>Given: A valid token request When: Token is issued Then: exp claim is in the future and
     * reasonable
     */
    @Test
    @Order(5)
    @DisplayName("TK-HP-005: Token expiry is properly set")
    void tokenExpiry_shouldBeProperlySet() throws Exception {
        // Given: Valid token request
        AiAgentTestReporter.setExpectedOutcome("exp claim is in future, typically 5-60 minutes");

        // When: Token is issued
        String accessToken = obtainAccessToken("api:read");

        // Then: Expiry is valid
        TokenAssertions assertion = TokenAssertions.assertThat(accessToken).isValidJwt();
        var payload = assertion.getPayload();

        long exp = payload.path("exp").asLong();
        long iat = payload.path("iat").asLong();
        long now = System.currentTimeMillis() / 1000;

        org.assertj.core.api.Assertions.assertThat(exp)
                .as("Token expiry should be in the future")
                .isGreaterThan(now);

        long ttlSeconds = exp - iat;
        org.assertj.core.api.Assertions.assertThat(ttlSeconds)
                .as("Token TTL should be reasonable (1 minute to 24 hours)")
                .isBetween(60L, 86400L);
    }

    /**
     * Test ID: TK-HP-006
     *
     * <p>Scenario: Introspection with token_type_hint should work
     *
     * <p>Given: A valid access token When: Introspection is called with token_type_hint Then:
     * Response is returned (hint is optional but should be accepted)
     *
     * <p>Compliance: RFC 7662 Section 2.1
     */
    @Test
    @Order(6)
    @DisplayName("TK-HP-006: Introspection with token_type_hint works")
    void introspectionWithTypeHint_shouldWork() throws Exception {
        // Given: Valid token
        String accessToken = obtainAccessToken("api:read");
        AiAgentTestReporter.setExpectedOutcome(
                "200 OK with active:true (token_type_hint accepted)");

        // When: Introspection with hint
        MvcResult result =
                mockMvc.perform(
                                post("/oauth2/introspect")
                                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                        .param("token", accessToken)
                                        .param("token_type_hint", "access_token")
                                        .with(httpBasic("m2m-client", "m2m-secret")))
                        .andExpect(status().isOk())
                        .andReturn();

        // Then: Introspection succeeds
        String responseBody = result.getResponse().getContentAsString();
        OAuth2ResponseAssertions.assertThat(result.getResponse().getStatus(), responseBody)
                .isActiveToken();
    }

    /**
     * Helper method to obtain an access token for testing.
     */
    private String obtainAccessToken(String scope) throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post("/oauth2/token")
                                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                        .param("grant_type", "client_credentials")
                                        .param("scope", scope)
                                        .with(httpBasic("m2m-client", "m2m-secret")))
                        .andExpect(status().isOk())
                        .andReturn();

        return OAuth2ResponseAssertions.assertThat(
                        result.getResponse().getStatus(),
                        result.getResponse().getContentAsString())
                .getAccessToken();
    }
}
