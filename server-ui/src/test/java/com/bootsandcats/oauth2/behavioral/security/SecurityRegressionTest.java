package com.bootsandcats.oauth2.behavioral.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
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

/**
 * Security regression tests for OAuth2 authorization server.
 *
 * <p>These tests are designed to prevent common security vulnerabilities from being reintroduced.
 * They verify:
 *
 * <ul>
 *   <li>Security headers are properly configured
 *   <li>Authentication is required for protected endpoints
 *   <li>CSRF protection is correctly applied
 *   <li>Rate limiting prevents brute-force attacks
 *   <li>Sensitive data is not exposed
 * </ul>
 *
 * <h2>AI Agent Notes</h2>
 *
 * <p>Security regression failures should be treated with HIGH PRIORITY. Do not modify these tests
 * to make them pass - fix the underlying security issue instead.
 *
 * <p>If a security test fails:
 *
 * <ol>
 *   <li>Do NOT disable or skip the test
 *   <li>Investigate why the security control was removed/changed
 *   <li>Restore the security control or provide compensating control
 *   <li>Document any intentional security posture changes
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({
    TestOAuth2ClientConfiguration.class,
    TestKeyManagementConfig.class,
    TestObjectMapperConfig.class
})
@ExtendWith(AiAgentTestReporter.class)
@Tag("security")
@Tag("regression")
@Tag("critical")
@DisplayName("Security Regression Tests")
class SecurityRegressionTest {

    @Autowired private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Nested
    @DisplayName("Security Headers")
    class SecurityHeaders {

        /**
         * Test ID: SEC-R-001
         *
         * <p>Regression: Content-Type-Options header prevents MIME sniffing
         *
         * <p>Reference: OWASP Secure Headers - X-Content-Type-Options
         */
        @Test
        @DisplayName("SEC-R-001: X-Content-Type-Options is set to nosniff")
        void xContentTypeOptions_shouldBeNosniff() throws Exception {
            AiAgentTestReporter.setExpectedOutcome("X-Content-Type-Options: nosniff");
            AiAgentTestReporter.setRegressionIndicator(true);

            mockMvc.perform(get("/.well-known/openid-configuration"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        }

        /**
         * Test ID: SEC-R-002
         *
         * <p>Regression: Frame-Options prevents clickjacking
         *
         * <p>Reference: OWASP Secure Headers - X-Frame-Options
         */
        @Test
        @DisplayName("SEC-R-002: X-Frame-Options prevents clickjacking")
        void xFrameOptions_shouldPreventClickjacking() throws Exception {
            AiAgentTestReporter.setExpectedOutcome("X-Frame-Options: DENY or SAMEORIGIN");
            AiAgentTestReporter.setRegressionIndicator(true);

            MvcResult result =
                    mockMvc.perform(get("/.well-known/openid-configuration"))
                            .andExpect(status().isOk())
                            .andReturn();

            String frameOptions = result.getResponse().getHeader("X-Frame-Options");
            assertThat(frameOptions)
                    .as("X-Frame-Options should be DENY or SAMEORIGIN")
                    .isIn("DENY", "SAMEORIGIN");
        }

        /**
         * Test ID: SEC-R-003
         *
         * <p>Regression: Cache-Control prevents caching of sensitive responses
         *
         * <p>Token responses should not be cached.
         */
        @Test
        @DisplayName("SEC-R-003: Token endpoint responses are not cached")
        void tokenEndpoint_shouldNotBeCached() throws Exception {
            AiAgentTestReporter.setExpectedOutcome("Cache-Control: no-store or no-cache");
            AiAgentTestReporter.setRegressionIndicator(true);

            String credentials = "m2m-client:m2m-secret";
            String encodedCredentials =
                    Base64.getEncoder()
                            .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

            MvcResult result =
                    mockMvc.perform(
                                    post("/oauth2/token")
                                            .header(
                                                    HttpHeaders.AUTHORIZATION,
                                                    "Basic " + encodedCredentials)
                                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                            .param("grant_type", "client_credentials"))
                            .andReturn();

            String cacheControl = result.getResponse().getHeader("Cache-Control");
            assertThat(cacheControl)
                    .as("Token responses must have Cache-Control header")
                    .isNotNull();
            assertThat(cacheControl.toLowerCase())
                    .as("Token responses should not be cached")
                    .containsAnyOf("no-store", "no-cache");
        }
    }

    @Nested
    @DisplayName("Authentication Requirements")
    class AuthenticationRequirements {

        /**
         * Test ID: SEC-R-010
         *
         * <p>Regression: Token introspection requires authentication
         *
         * <p>Unauthenticated requests to introspect tokens must be rejected (redirected to login).
         */
        @Test
        @DisplayName("SEC-R-010: Token introspection requires client authentication")
        void tokenIntrospection_shouldRequireAuthentication() throws Exception {
            AiAgentTestReporter.setExpectedOutcome("302 redirect to login or 401 Unauthorized");
            AiAgentTestReporter.setRegressionIndicator(true);

            MvcResult result =
                    mockMvc.perform(
                                    post("/oauth2/introspect")
                                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                            .param("token", "some-token"))
                            .andReturn();

            // Server redirects to login for unauthenticated requests (form login enabled)
            // or returns 401 for pure API behavior
            int status = result.getResponse().getStatus();
            assertThat(status)
                    .as("Token introspection should require authentication (302 redirect or 401)")
                    .isIn(302, 401);
        }

        /**
         * Test ID: SEC-R-011
         *
         * <p>Regression: Token revocation requires authentication
         *
         * <p>Unauthenticated requests to revoke tokens must be rejected (redirected to login).
         */
        @Test
        @DisplayName("SEC-R-011: Token revocation requires client authentication")
        void tokenRevocation_shouldRequireAuthentication() throws Exception {
            AiAgentTestReporter.setExpectedOutcome("302 redirect to login or 401 Unauthorized");
            AiAgentTestReporter.setRegressionIndicator(true);

            MvcResult result =
                    mockMvc.perform(
                                    post("/oauth2/revoke")
                                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                            .param("token", "some-token"))
                            .andReturn();

            // Server redirects to login for unauthenticated requests (form login enabled)
            // or returns 401 for pure API behavior
            int status = result.getResponse().getStatus();
            assertThat(status)
                    .as("Token revocation should require authentication (302 redirect or 401)")
                    .isIn(302, 401);
        }

        /**
         * Test ID: SEC-R-012
         *
         * <p>Regression: User info endpoint requires authentication
         *
         * <p>The userinfo endpoint must require a valid access token.
         */
        @Test
        @DisplayName("SEC-R-012: UserInfo endpoint requires authentication")
        void userInfoEndpoint_shouldRequireAuthentication() throws Exception {
            AiAgentTestReporter.setExpectedOutcome("302 redirect to login or 401 Unauthorized");
            AiAgentTestReporter.setRegressionIndicator(true);

            MvcResult result = mockMvc.perform(get("/userinfo")).andReturn();

            // Server redirects to login for unauthenticated requests (form login enabled)
            // or returns 401 for pure API behavior
            int status = result.getResponse().getStatus();
            assertThat(status)
                    .as("UserInfo should require authentication (302 redirect or 401)")
                    .isIn(302, 401);
        }
    }

    @Nested
    @DisplayName("Credential Validation")
    class CredentialValidation {

        /**
         * Test ID: SEC-R-020
         *
         * <p>Regression: Invalid client credentials are rejected
         *
         * <p>Token endpoint must properly validate client credentials.
         */
        @Test
        @DisplayName("SEC-R-020: Invalid client credentials are rejected")
        void invalidClientCredentials_shouldBeRejected() throws Exception {
            AiAgentTestReporter.setExpectedOutcome("401 Unauthorized with invalid credentials");
            AiAgentTestReporter.setRegressionIndicator(true);

            String credentials = "fake-client:fake-secret";
            String encodedCredentials =
                    Base64.getEncoder()
                            .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

            mockMvc.perform(
                            post("/oauth2/token")
                                    .header(
                                            HttpHeaders.AUTHORIZATION,
                                            "Basic " + encodedCredentials)
                                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                    .param("grant_type", "client_credentials"))
                    .andExpect(status().isUnauthorized());
        }

        /**
         * Test ID: SEC-R-021
         *
         * <p>Regression: Wrong client secret is rejected
         *
         * <p>Correct client ID with wrong secret must be rejected.
         */
        @Test
        @DisplayName("SEC-R-021: Wrong client secret is rejected")
        void wrongClientSecret_shouldBeRejected() throws Exception {
            AiAgentTestReporter.setExpectedOutcome("401 Unauthorized with wrong secret");
            AiAgentTestReporter.setRegressionIndicator(true);

            String credentials = "m2m-client:wrong-secret";
            String encodedCredentials =
                    Base64.getEncoder()
                            .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

            mockMvc.perform(
                            post("/oauth2/token")
                                    .header(
                                            HttpHeaders.AUTHORIZATION,
                                            "Basic " + encodedCredentials)
                                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                    .param("grant_type", "client_credentials"))
                    .andExpect(status().isUnauthorized());
        }

        /**
         * Test ID: SEC-R-022
         *
         * <p>Regression: Empty credentials are rejected
         *
         * <p>Empty client ID or secret must be rejected with a 4xx error.
         */
        @Test
        @DisplayName("SEC-R-022: Empty credentials are rejected")
        void emptyCredentials_shouldBeRejected() throws Exception {
            AiAgentTestReporter.setExpectedOutcome("4xx error with empty credentials");
            AiAgentTestReporter.setRegressionIndicator(true);

            String credentials = ":";
            String encodedCredentials =
                    Base64.getEncoder()
                            .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

            MvcResult result =
                    mockMvc.perform(
                                    post("/oauth2/token")
                                            .header(
                                                    HttpHeaders.AUTHORIZATION,
                                                    "Basic " + encodedCredentials)
                                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                            .param("grant_type", "client_credentials"))
                            .andReturn();

            // Empty credentials result in 400 Bad Request (invalid_client) or 401 Unauthorized
            int status = result.getResponse().getStatus();
            assertThat(status)
                    .as("Empty credentials should be rejected with 4xx error (400 or 401)")
                    .isIn(400, 401);
        }
    }

    @Nested
    @DisplayName("Sensitive Data Protection")
    class SensitiveDataProtection {

        /**
         * Test ID: SEC-R-030
         *
         * <p>Regression: Error responses do not leak sensitive info
         *
         * <p>Error messages should not reveal internal implementation details.
         */
        @Test
        @DisplayName("SEC-R-030: Error responses do not leak internal details")
        void errorResponse_shouldNotLeakInternalDetails() throws Exception {
            AiAgentTestReporter.setExpectedOutcome(
                    "Error response without stack traces or internals");
            AiAgentTestReporter.setRegressionIndicator(true);

            String credentials = "m2m-client:wrong-secret";
            String encodedCredentials =
                    Base64.getEncoder()
                            .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

            MvcResult result =
                    mockMvc.perform(
                                    post("/oauth2/token")
                                            .header(
                                                    HttpHeaders.AUTHORIZATION,
                                                    "Basic " + encodedCredentials)
                                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                            .param("grant_type", "client_credentials"))
                            .andReturn();

            String body = result.getResponse().getContentAsString();

            assertThat(body)
                    .as("Error response should not contain stack traces")
                    .doesNotContain("Exception")
                    .doesNotContain("at com.")
                    .doesNotContain("at org.springframework")
                    .doesNotContain(".java:");

            assertThat(body)
                    .as("Error response should not leak class names")
                    .doesNotContainPattern("\\w+\\.\\w+\\.\\w+Exception");
        }

        /**
         * Test ID: SEC-R-031
         *
         * <p>Regression: JWKS endpoint does not expose private keys
         *
         * <p>Public JWK endpoint must only return public key components.
         */
        @Test
        @DisplayName("SEC-R-031: JWKS endpoint does not expose private keys")
        void jwksEndpoint_shouldNotExposePrivateKeys() throws Exception {
            AiAgentTestReporter.setExpectedOutcome("JWKS contains only public key components");
            AiAgentTestReporter.setRegressionIndicator(true);

            MvcResult result =
                    mockMvc.perform(get("/oauth2/jwks")).andExpect(status().isOk()).andReturn();

            String body = result.getResponse().getContentAsString();

            // Private key components that should NEVER be present
            assertThat(body)
                    .as("JWKS must not contain 'd' (private exponent)")
                    .doesNotContain("\"d\":");
            assertThat(body)
                    .as("JWKS must not contain 'p' (first prime factor)")
                    .doesNotContain("\"p\":");
            assertThat(body)
                    .as("JWKS must not contain 'q' (second prime factor)")
                    .doesNotContain("\"q\":");
            assertThat(body)
                    .as("JWKS must not contain 'dp' (first factor CRT exponent)")
                    .doesNotContain("\"dp\":");
            assertThat(body)
                    .as("JWKS must not contain 'dq' (second factor CRT exponent)")
                    .doesNotContain("\"dq\":");
            assertThat(body)
                    .as("JWKS must not contain 'qi' (CRT coefficient)")
                    .doesNotContain("\"qi\":");
        }
    }

    @Nested
    @DisplayName("Request Validation")
    class RequestValidation {

        /**
         * Test ID: SEC-R-040
         *
         * <p>Regression: Unsupported HTTP methods are rejected
         *
         * <p>Token endpoint should only accept POST requests. GET redirects to login.
         */
        @Test
        @DisplayName("SEC-R-040: Token endpoint rejects GET requests")
        void tokenEndpoint_shouldRejectGetRequests() throws Exception {
            AiAgentTestReporter.setExpectedOutcome("302 redirect or 405 Method Not Allowed for GET");
            AiAgentTestReporter.setRegressionIndicator(true);

            MvcResult result = mockMvc.perform(get("/oauth2/token")).andReturn();

            // GET request may redirect to login (302) or return 405 Method Not Allowed
            int status = result.getResponse().getStatus();
            assertThat(status)
                    .as("Token endpoint GET should be rejected (302 redirect or 405)")
                    .isIn(302, 405);
        }

        /**
         * Test ID: SEC-R-041
         *
         * <p>Regression: Invalid content type is handled
         *
         * <p>Token endpoint requires form-urlencoded content type.
         */
        @Test
        @DisplayName("SEC-R-041: Token endpoint requires correct content type")
        void tokenEndpoint_shouldRequireCorrectContentType() throws Exception {
            AiAgentTestReporter.setExpectedOutcome("4xx error for unsupported media type");
            AiAgentTestReporter.setRegressionIndicator(true);

            String credentials = "m2m-client:m2m-secret";
            String encodedCredentials =
                    Base64.getEncoder()
                            .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

            MvcResult result =
                    mockMvc.perform(
                                    post("/oauth2/token")
                                            .header(
                                                    HttpHeaders.AUTHORIZATION,
                                                    "Basic " + encodedCredentials)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content("{\"grant_type\": \"client_credentials\"}"))
                            .andReturn();

            // Should be either 415 Unsupported Media Type or 400 Bad Request
            int status = result.getResponse().getStatus();
            assertThat(status).as("Token endpoint should reject JSON content type").isIn(400, 415);
        }
    }

    @Nested
    @DisplayName("Public Endpoint Access")
    class PublicEndpointAccess {

        /**
         * Test ID: SEC-R-050
         *
         * <p>Regression: Discovery endpoint is public
         *
         * <p>OIDC Discovery endpoint must be accessible without authentication.
         */
        @Test
        @DisplayName("SEC-R-050: Discovery endpoint is publicly accessible")
        void discoveryEndpoint_shouldBePublic() throws Exception {
            AiAgentTestReporter.setExpectedOutcome("200 OK without authentication");
            AiAgentTestReporter.setRegressionIndicator(true);

            mockMvc.perform(get("/.well-known/openid-configuration")).andExpect(status().isOk());
        }

        /**
         * Test ID: SEC-R-051
         *
         * <p>Regression: JWKS endpoint is public
         *
         * <p>JSON Web Key Set endpoint must be accessible for token verification.
         */
        @Test
        @DisplayName("SEC-R-051: JWKS endpoint is publicly accessible")
        void jwksEndpoint_shouldBePublic() throws Exception {
            AiAgentTestReporter.setExpectedOutcome("200 OK without authentication");
            AiAgentTestReporter.setRegressionIndicator(true);

            mockMvc.perform(get("/oauth2/jwks")).andExpect(status().isOk());
        }

        /**
         * Test ID: SEC-R-052
         *
         * <p>Regression: Authorization endpoint is accessible
         *
         * <p>Authorization endpoint must be accessible (will redirect to login or return
         * bad request for missing required params like state).
         */
        @Test
        @DisplayName("SEC-R-052: Authorization endpoint is accessible")
        void authorizationEndpoint_shouldBeAccessible() throws Exception {
            AiAgentTestReporter.setExpectedOutcome("302 redirect to login or 400 bad request");
            AiAgentTestReporter.setRegressionIndicator(true);

            MvcResult result =
                    mockMvc.perform(
                                    get("/oauth2/authorize")
                                            .param("response_type", "code")
                                            .param("client_id", "demo-client")
                                            .param("redirect_uri", "http://localhost:8080/callback")
                                            .param("scope", "openid")
                                            .param("state", "test-state"))
                            .andReturn();

            // Should redirect to login (302) or return 400 for validation errors
            int status = result.getResponse().getStatus();
            assertThat(status)
                    .as("Authorization should redirect to login (302), not forbid access (403)")
                    .isIn(302, 303, 400);
        }
    }
}
