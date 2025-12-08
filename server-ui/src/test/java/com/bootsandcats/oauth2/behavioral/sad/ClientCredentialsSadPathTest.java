package com.bootsandcats.oauth2.behavioral.sad;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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

/**
 * Test ID: CC-SP-* (Client Credentials Sad Path)
 *
 * <p>Behavioral tests for the OAuth2 Client Credentials flow - Sad Paths.
 *
 * <p>These tests verify that the authorization server correctly rejects invalid client credentials
 * requests with appropriate error responses, following the OAuth 2.0 specification.
 *
 * <h2>Scenario Coverage</h2>
 *
 * <ul>
 *   <li>CC-SP-001: Invalid client credentials → 401 Unauthorized
 *   <li>CC-SP-002: Unknown client ID → 401 Unauthorized
 *   <li>CC-SP-003: Missing grant_type → 400 Bad Request
 *   <li>CC-SP-004: Invalid grant_type → 400 Bad Request with unsupported_grant_type
 *   <li>CC-SP-005: Invalid scope → 400 Bad Request with invalid_scope
 *   <li>CC-SP-006: Client not authorized for grant type → unauthorized_client
 * </ul>
 *
 * @see <a href="https://tools.ietf.org/html/rfc6749#section-5.2">RFC 6749 Section 5.2 - Error
 *     Response</a>
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
@Tag("client-credentials")
@Tag("sad-path")
@Tag("security")
@DisplayName("Client Credentials Flow - Sad Paths")
class ClientCredentialsSadPathTest {

    @Autowired private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    /**
     * Test ID: CC-SP-001
     *
     * <p>Scenario: Invalid client secret should be rejected
     *
     * <p>Given: A registered client ID with wrong secret When: Token request is made Then: 401
     * Unauthorized is returned
     *
     * <p>Security Requirement: Client authentication must be enforced Compliance: RFC 6749 Section
     * 2.3.1
     *
     * <p>Common Causes of Test Failure:
     *
     * <ul>
     *   <li>Client authentication disabled in config
     *   <li>Password encoder mismatch
     *   <li>Client secret not properly hashed
     * </ul>
     */
    @Test
    @Tag("critical")
    @DisplayName("CC-SP-001: Invalid client secret should be rejected")
    void invalidClientSecret_shouldBeRejected() throws Exception {
        // Given: Valid client ID with wrong secret
        AiAgentTestReporter.setRequestContext(
                "/oauth2/token",
                "POST",
                java.util.Map.of(
                        "grant_type", "client_credentials", "client_id", "m2m-client"));
        AiAgentTestReporter.setExpectedOutcome("401 Unauthorized - invalid_client error");

        // When: Token request is made with wrong secret
        MvcResult result =
                mockMvc.perform(
                                post("/oauth2/token")
                                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                        .param("grant_type", "client_credentials")
                                        .with(httpBasic("m2m-client", "wrong-secret")))
                        .andExpect(status().isUnauthorized())
                        .andReturn();

        // Then: Unauthorized response
        String responseBody = result.getResponse().getContentAsString();
        AiAgentTestReporter.setResponseContext(result.getResponse().getStatus(), responseBody);

        OAuth2ResponseAssertions.assertThat(result.getResponse().getStatus(), responseBody)
                .isUnauthorized();
    }

    /**
     * Test ID: CC-SP-002
     *
     * <p>Scenario: Unknown client ID should be rejected
     *
     * <p>Given: An unregistered client ID When: Token request is made Then: 401 Unauthorized is
     * returned
     *
     * <p>Security Requirement: Only registered clients can obtain tokens
     */
    @Test
    @Tag("critical")
    @DisplayName("CC-SP-002: Unknown client ID should be rejected")
    void unknownClientId_shouldBeRejected() throws Exception {
        // Given: Unknown client ID
        AiAgentTestReporter.setRequestContext(
                "/oauth2/token",
                "POST",
                java.util.Map.of(
                        "grant_type", "client_credentials",
                        "client_id", "unknown-client"));
        AiAgentTestReporter.setExpectedOutcome("401 Unauthorized");

        // When: Token request is made with unknown client
        MvcResult result =
                mockMvc.perform(
                                post("/oauth2/token")
                                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                        .param("grant_type", "client_credentials")
                                        .with(httpBasic("unknown-client", "any-secret")))
                        .andExpect(status().isUnauthorized())
                        .andReturn();

        // Then: Unauthorized response
        AiAgentTestReporter.setResponseContext(
                result.getResponse().getStatus(), result.getResponse().getContentAsString());

        OAuth2ResponseAssertions.assertThat(
                        result.getResponse().getStatus(),
                        result.getResponse().getContentAsString())
                .isUnauthorized();
    }

    /**
     * Test ID: CC-SP-003
     *
     * <p>Scenario: Missing grant_type parameter should return error
     *
     * <p>Given: A valid client When: Token request is made without grant_type Then: 400 Bad Request
     * with invalid_request error
     *
     * <p>Compliance: RFC 6749 Section 4.4.2 - grant_type is REQUIRED
     */
    @Test
    @DisplayName("CC-SP-003: Missing grant_type should return invalid_request")
    void missingGrantType_shouldReturnError() throws Exception {
        // Given: Request without grant_type
        AiAgentTestReporter.setRequestContext(
                "/oauth2/token", "POST", java.util.Map.of("client_id", "m2m-client"));
        AiAgentTestReporter.setExpectedOutcome("400 Bad Request with invalid_request error");

        // When: Token request is made without grant_type
        MvcResult result =
                mockMvc.perform(
                                post("/oauth2/token")
                                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                        .with(httpBasic("m2m-client", "m2m-secret")))
                        .andExpect(status().isBadRequest())
                        .andReturn();

        // Then: Error response
        String responseBody = result.getResponse().getContentAsString();
        AiAgentTestReporter.setResponseContext(result.getResponse().getStatus(), responseBody);

        OAuth2ResponseAssertions.assertThat(result.getResponse().getStatus(), responseBody)
                .isBadRequest()
                .isValidOAuth2Error();
    }

    /**
     * Test ID: CC-SP-004
     *
     * <p>Scenario: Invalid grant_type should return unsupported_grant_type
     *
     * <p>Given: A valid client When: Token request is made with invalid grant_type Then: 400 Bad
     * Request with unsupported_grant_type error
     *
     * <p>Compliance: RFC 6749 Section 5.2
     */
    @Test
    @DisplayName("CC-SP-004: Invalid grant_type should return unsupported_grant_type")
    void invalidGrantType_shouldReturnError() throws Exception {
        // Given: Request with invalid grant_type
        AiAgentTestReporter.setRequestContext(
                "/oauth2/token",
                "POST",
                java.util.Map.of("grant_type", "invalid_grant_type"));
        AiAgentTestReporter.setExpectedOutcome(
                "400 Bad Request with unsupported_grant_type error");

        // When: Token request is made with invalid grant_type
        MvcResult result =
                mockMvc.perform(
                                post("/oauth2/token")
                                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                        .param("grant_type", "invalid_grant_type")
                                        .with(httpBasic("m2m-client", "m2m-secret")))
                        .andExpect(status().isBadRequest())
                        .andReturn();

        // Then: Error response with unsupported_grant_type
        String responseBody = result.getResponse().getContentAsString();
        AiAgentTestReporter.setResponseContext(result.getResponse().getStatus(), responseBody);

        OAuth2ResponseAssertions.assertThat(result.getResponse().getStatus(), responseBody)
                .isBadRequest()
                .isErrorResponse("unsupported_grant_type");
    }

    /**
     * Test ID: CC-SP-005
     *
     * <p>Scenario: Request for scope not allowed for client should return invalid_scope
     *
     * <p>Given: A client with limited scopes configured When: Token request is made with
     * unauthorized scope Then: 400 Bad Request with invalid_scope error
     *
     * <p>Compliance: RFC 6749 Section 3.3
     */
    @Test
    @DisplayName("CC-SP-005: Unauthorized scope should return invalid_scope")
    void unauthorizedScope_shouldReturnError() throws Exception {
        // Given: Request with scope not allowed for client
        AiAgentTestReporter.setRequestContext(
                "/oauth2/token",
                "POST",
                java.util.Map.of(
                        "grant_type", "client_credentials",
                        "scope", "admin:delete"));
        AiAgentTestReporter.setExpectedOutcome("400 Bad Request with invalid_scope error");

        // When: Token request is made with unauthorized scope
        MvcResult result =
                mockMvc.perform(
                                post("/oauth2/token")
                                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                        .param("grant_type", "client_credentials")
                                        .param("scope", "admin:delete super:secret")
                                        .with(httpBasic("m2m-client", "m2m-secret")))
                        .andExpect(status().isBadRequest())
                        .andReturn();

        // Then: Error response with invalid_scope
        String responseBody = result.getResponse().getContentAsString();
        AiAgentTestReporter.setResponseContext(result.getResponse().getStatus(), responseBody);

        OAuth2ResponseAssertions.assertThat(result.getResponse().getStatus(), responseBody)
                .isBadRequest()
                .isErrorResponse("invalid_scope");
    }

    /**
     * Test ID: CC-SP-006
     *
     * <p>Scenario: Token introspection without credentials should be rejected
     *
     * <p>Given: A valid token When: Introspection request is made without client credentials Then:
     * Request is rejected (401 or redirect to login)
     *
     * <p>Security Requirement: Introspection endpoint requires authentication Compliance: RFC 7662
     * Section 2.1
     */
    @Test
    @Tag("token-operations")
    @DisplayName("CC-SP-006: Introspection without credentials should be rejected")
    void introspectionWithoutCredentials_shouldBeRejected() throws Exception {
        // Given: Request without authentication
        AiAgentTestReporter.setRequestContext("/oauth2/introspect", "POST", java.util.Map.of());
        AiAgentTestReporter.setExpectedOutcome("401 Unauthorized or 302 redirect to login");

        // When: Introspection request without credentials
        MvcResult result =
                mockMvc.perform(
                                post("/oauth2/introspect")
                                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                        .param("token", "some-token"))
                        .andReturn();

        // Then: Request is rejected
        AiAgentTestReporter.setResponseContext(
                result.getResponse().getStatus(), result.getResponse().getContentAsString());

        int status = result.getResponse().getStatus();
        org.assertj.core.api.Assertions.assertThat(status)
                .as(
                        "Introspection without auth should be rejected (401 or 302). Got: %d",
                        status)
                .isIn(401, 302, 303);
    }

    /**
     * Test ID: CC-SP-007
     *
     * <p>Scenario: Token revocation without credentials should be rejected
     *
     * <p>Given: A valid token When: Revocation request is made without client credentials Then:
     * Request is rejected
     *
     * <p>Security Requirement: Revocation endpoint requires authentication Compliance: RFC 7009
     * Section 2.1
     */
    @Test
    @Tag("token-operations")
    @DisplayName("CC-SP-007: Revocation without credentials should be rejected")
    void revocationWithoutCredentials_shouldBeRejected() throws Exception {
        // Given: Request without authentication
        AiAgentTestReporter.setRequestContext("/oauth2/revoke", "POST", java.util.Map.of());
        AiAgentTestReporter.setExpectedOutcome("401 Unauthorized or 302 redirect to login");

        // When: Revocation request without credentials
        MvcResult result =
                mockMvc.perform(
                                post("/oauth2/revoke")
                                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                        .param("token", "some-token"))
                        .andReturn();

        // Then: Request is rejected
        AiAgentTestReporter.setResponseContext(
                result.getResponse().getStatus(), result.getResponse().getContentAsString());

        int status = result.getResponse().getStatus();
        org.assertj.core.api.Assertions.assertThat(status)
                .as("Revocation without auth should be rejected (401 or 302). Got: %d", status)
                .isIn(401, 302, 303);
    }

    /**
     * Test ID: CC-SP-008
     *
     * <p>Scenario: Introspection of invalid token should return inactive
     *
     * <p>Given: A malformed or invalid token When: Token is introspected Then: Response shows
     * active: false
     *
     * <p>Compliance: RFC 7662 Section 2.2
     */
    @Test
    @Tag("token-operations")
    @DisplayName("CC-SP-008: Invalid token introspection should return inactive")
    void invalidTokenIntrospection_shouldReturnInactive() throws Exception {
        // Given: An invalid/malformed token
        String invalidToken = "this-is-not-a-valid-jwt-token";
        AiAgentTestReporter.setExpectedOutcome("200 OK with active: false");

        // When: Token is introspected
        MvcResult result =
                mockMvc.perform(
                                post("/oauth2/introspect")
                                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                        .param("token", invalidToken)
                                        .with(httpBasic("m2m-client", "m2m-secret")))
                        .andExpect(status().isOk())
                        .andReturn();

        // Then: Response indicates token is not active
        String responseBody = result.getResponse().getContentAsString();
        AiAgentTestReporter.setResponseContext(result.getResponse().getStatus(), responseBody);

        OAuth2ResponseAssertions.assertThat(result.getResponse().getStatus(), responseBody)
                .isInactiveToken();
    }
}
