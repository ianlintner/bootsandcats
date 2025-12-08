package com.bootsandcats.oauth2.behavioral.happy;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.bootsandcats.oauth2.testing.assertions.TokenAssertions;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Test ID: CC-HP-* (Client Credentials Happy Path)
 *
 * <p>Behavioral tests for the OAuth2 Client Credentials flow - Happy Paths.
 *
 * <p>These tests verify that the authorization server correctly issues tokens for valid client
 * credentials requests, following the OAuth 2.0 specification (RFC 6749 Section 4.4).
 *
 * <h2>Scenario Coverage</h2>
 *
 * <ul>
 *   <li>CC-HP-001: Valid client credentials → Access token issued
 *   <li>CC-HP-002: Token with requested scopes → Token contains requested scopes
 *   <li>CC-HP-003: Token introspection → Returns active with correct claims
 *   <li>CC-HP-004: Token has correct structure → JWT with ES256 algorithm
 * </ul>
 *
 * @see <a href="https://tools.ietf.org/html/rfc6749#section-4.4">RFC 6749 Section 4.4</a>
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
@Tag("happy-path")
@Tag("critical")
@DisplayName("Client Credentials Flow - Happy Paths")
class ClientCredentialsHappyPathTest {

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    /**
     * Test ID: CC-HP-001
     *
     * <p>Scenario: Valid client credentials should result in access token issuance
     *
     * <p>Given: A registered client with client_credentials grant type enabled When: The client
     * requests a token with valid credentials Then: An access token is issued with correct
     * token_type and expires_in
     *
     * <p>Security Requirement: Only authenticated clients receive tokens Compliance: RFC 6749
     * Section 4.4.3
     */
    @Test
    @DisplayName("CC-HP-001: Valid credentials should issue access token")
    void validCredentials_shouldIssueAccessToken() throws Exception {
        // Given: Valid client credentials
        AiAgentTestReporter.setRequestContext(
                "/oauth2/token", "POST", java.util.Map.of("grant_type", "client_credentials"));
        AiAgentTestReporter.setExpectedOutcome("200 OK with access_token, token_type, expires_in");

        // When: Token request is made
        MvcResult result =
                mockMvc.perform(
                                post("/oauth2/token")
                                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                        .param("grant_type", "client_credentials")
                                        .param("scope", "api:read api:write")
                                        .with(httpBasic("m2m-client", "m2m-secret")))
                        .andExpect(status().isOk())
                        .andReturn();

        // Then: Access token is issued
        String responseBody = result.getResponse().getContentAsString();
        AiAgentTestReporter.setResponseContext(result.getResponse().getStatus(), responseBody);

        OAuth2ResponseAssertions.assertThat(result.getResponse().getStatus(), responseBody)
                .isSuccessfulTokenResponse()
                .hasAccessToken()
                .hasTokenType("Bearer")
                .hasExpiresIn();
    }

    /**
     * Test ID: CC-HP-002
     *
     * <p>Scenario: Token should contain the requested scopes
     *
     * <p>Given: A client requests specific scopes When: The token is issued Then: The token
     * response includes the granted scopes
     *
     * <p>Security Requirement: Scope should be properly bounded Compliance: RFC 6749 Section 3.3
     */
    @Test
    @DisplayName("CC-HP-002: Token should contain requested scopes")
    void validCredentials_shouldContainRequestedScopes() throws Exception {
        // Given: Request with specific scopes
        AiAgentTestReporter.setExpectedOutcome("Token response includes requested scope");

        // When: Token request is made with scopes
        MvcResult result =
                mockMvc.perform(
                                post("/oauth2/token")
                                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                        .param("grant_type", "client_credentials")
                                        .param("scope", "api:read")
                                        .with(httpBasic("m2m-client", "m2m-secret")))
                        .andExpect(status().isOk())
                        .andReturn();

        // Then: Scopes are included in response
        String responseBody = result.getResponse().getContentAsString();
        OAuth2ResponseAssertions.assertThat(result.getResponse().getStatus(), responseBody)
                .isSuccessfulTokenResponse()
                .hasScope("api:read");
    }

    /**
     * Test ID: CC-HP-003
     *
     * <p>Scenario: Token introspection should return active status with claims
     *
     * <p>Given: A valid access token When: The token is introspected Then: Response shows active:
     * true with token metadata
     *
     * <p>Compliance: RFC 7662 (Token Introspection)
     */
    @Test
    @DisplayName("CC-HP-003: Token introspection should return active with claims")
    void tokenIntrospection_shouldReturnActiveWithClaims() throws Exception {
        // Given: Obtain a valid token
        MvcResult tokenResult =
                mockMvc.perform(
                                post("/oauth2/token")
                                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                        .param("grant_type", "client_credentials")
                                        .param("scope", "api:read")
                                        .with(httpBasic("m2m-client", "m2m-secret")))
                        .andExpect(status().isOk())
                        .andReturn();

        String tokenResponse = tokenResult.getResponse().getContentAsString();
        String accessToken =
                OAuth2ResponseAssertions.assertThat(200, tokenResponse).getAccessToken();

        AiAgentTestReporter.setExpectedOutcome(
                "Introspection returns active:true with client_id and scope");

        // When: Token is introspected
        MvcResult introspectResult =
                mockMvc.perform(
                                post("/oauth2/introspect")
                                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                        .param("token", accessToken)
                                        .with(httpBasic("m2m-client", "m2m-secret")))
                        .andExpect(status().isOk())
                        .andReturn();

        // Then: Token is active with correct claims
        String introspectBody = introspectResult.getResponse().getContentAsString();
        AiAgentTestReporter.setResponseContext(
                introspectResult.getResponse().getStatus(), introspectBody);

        OAuth2ResponseAssertions.assertThat(
                        introspectResult.getResponse().getStatus(), introspectBody)
                .isActiveToken();

        // Verify client_id in introspection response
        var json = objectMapper.readTree(introspectBody);
        org.assertj.core.api.Assertions.assertThat(json.path("client_id").asText())
                .isEqualTo("m2m-client");
    }

    /**
     * Test ID: CC-HP-004
     *
     * <p>Scenario: Issued token should be a valid JWT with ES256 algorithm
     *
     * <p>Given: A valid token request When: Token is issued Then: Token is a valid JWT signed with
     * ES256
     *
     * <p>Security Requirement: Tokens must use secure signing algorithm
     */
    @Test
    @DisplayName("CC-HP-004: Token should be valid JWT with ES256")
    void issuedToken_shouldBeValidJwtWithEs256() throws Exception {
        // Given: Valid token request
        AiAgentTestReporter.setExpectedOutcome(
                "JWT token with 3 parts, ES256 algorithm in header");

        // When: Token is issued
        MvcResult result =
                mockMvc.perform(
                                post("/oauth2/token")
                                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                        .param("grant_type", "client_credentials")
                                        .param("scope", "api:read")
                                        .with(httpBasic("m2m-client", "m2m-secret")))
                        .andExpect(status().isOk())
                        .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        String accessToken =
                OAuth2ResponseAssertions.assertThat(200, responseBody).getAccessToken();

        // Then: Token is valid JWT with ES256
        TokenAssertions.assertThat(accessToken)
                .isValidJwt()
                .hasAlgorithm("ES256")
                .hasKeyId()
                .hasClaim("sub")
                .hasClaim("iss")
                .hasClaim("exp")
                .hasClaim("iat")
                .isNotExpired();
    }

    /**
     * Test ID: CC-HP-005
     *
     * <p>Scenario: JWKS endpoint should return valid signing keys
     *
     * <p>Given: The authorization server is running When: JWKS endpoint is accessed Then: Valid EC
     * keys are returned for token verification
     *
     * <p>Compliance: RFC 7517 (JSON Web Key)
     */
    @Test
    @Tag("oidc")
    @DisplayName("CC-HP-005: JWKS endpoint should return valid EC keys")
    void jwksEndpoint_shouldReturnValidEcKeys() throws Exception {
        // Given: Authorization server is running
        AiAgentTestReporter.setExpectedOutcome(
                "JWKS with EC keys including kid, kty, crv, x, y");

        // When: JWKS endpoint is accessed
        MvcResult result =
                mockMvc.perform(get("/oauth2/jwks")).andExpect(status().isOk()).andReturn();

        // Then: Valid JWKS is returned
        String responseBody = result.getResponse().getContentAsString();
        AiAgentTestReporter.setResponseContext(result.getResponse().getStatus(), responseBody);

        var jwks = objectMapper.readTree(responseBody);
        org.assertj.core.api.Assertions.assertThat(jwks.has("keys"))
                .as("JWKS should contain 'keys' array")
                .isTrue();

        var keys = jwks.get("keys");
        org.assertj.core.api.Assertions.assertThat(keys.isArray()).isTrue();
        org.assertj.core.api.Assertions.assertThat(keys.size())
                .as("JWKS should have at least one key")
                .isGreaterThan(0);

        var firstKey = keys.get(0);
        org.assertj.core.api.Assertions.assertThat(firstKey.path("kty").asText())
                .as("Key type should be EC")
                .isEqualTo("EC");
        org.assertj.core.api.Assertions.assertThat(firstKey.has("kid"))
                .as("Key should have kid")
                .isTrue();
        org.assertj.core.api.Assertions.assertThat(firstKey.has("crv"))
                .as("Key should have curve")
                .isTrue();
        org.assertj.core.api.Assertions.assertThat(firstKey.has("x"))
                .as("Key should have x coordinate")
                .isTrue();
        org.assertj.core.api.Assertions.assertThat(firstKey.has("y"))
                .as("Key should have y coordinate")
                .isTrue();
    }
}
