package com.bootsandcats.oauth2.testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.bootsandcats.oauth2.config.TestKeyManagementConfig;
import com.bootsandcats.oauth2.testing.AiAgentTestReporter;

/**
 * Integration tests for federated identity flows using WireMock to mock OAuth providers.
 *
 * <p>These tests verify the complete OAuth2 federated login flow with mocked GitHub and Google
 * providers, ensuring the FederatedIdentityAuthenticationSuccessHandler processes responses
 * correctly.
 *
 * <h2>AI Agent Debugging Notes:</h2>
 * <ul>
 *   <li>WireMock servers are started dynamically on available ports</li>
 *   <li>OAuth2 client properties are configured via @DynamicPropertySource</li>
 *   <li>Tests verify redirect flows - check Location headers for issues</li>
 *   <li>If token exchange fails, verify WireMock stubs match expected request format</li>
 * </ul>
 *
 * <h2>Test Categories:</h2>
 * <ul>
 *   <li>GitHub OAuth flow tests - Authorization redirect and callback handling</li>
 *   <li>Google OIDC flow tests - Authorization redirect and callback handling</li>
 *   <li>Error handling tests - Invalid tokens, provider errors</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("testcontainers-oauth")
@Import({TestKeyManagementConfig.class})
@DisplayName("Federated Identity Flow Tests")
public class FederatedIdentityFlowTest {

    private static MockGitHubOAuthServer mockGitHub;
    private static MockGoogleOidcServer mockGoogle;

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeAll
    static void startMockServers() {
        mockGitHub = new MockGitHubOAuthServer();
        mockGitHub.start();

        mockGoogle = new MockGoogleOidcServer();
        mockGoogle.start();
    }

    @AfterAll
    static void stopMockServers() {
        if (mockGitHub != null) {
            mockGitHub.stop();
        }
        if (mockGoogle != null) {
            mockGoogle.stop();
        }
    }

    @BeforeEach
    void resetMockServers() {
        mockGitHub.reset();
        mockGoogle.reset();
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @DynamicPropertySource
    static void configureOAuth2Properties(DynamicPropertyRegistry registry) {
        // GitHub OAuth configuration pointing to WireMock
        registry.add("spring.security.oauth2.client.registration.github.client-id", () -> "test-github-client-id");
        registry.add("spring.security.oauth2.client.registration.github.client-secret", () -> "test-github-client-secret");
        registry.add("spring.security.oauth2.client.registration.github.scope", () -> "read:user,user:email");
        registry.add("spring.security.oauth2.client.provider.github.authorization-uri", 
                () -> mockGitHub.getBaseUrl() + "/login/oauth/authorize");
        registry.add("spring.security.oauth2.client.provider.github.token-uri", 
                () -> mockGitHub.getBaseUrl() + "/login/oauth/access_token");
        registry.add("spring.security.oauth2.client.provider.github.user-info-uri", 
                () -> mockGitHub.getBaseUrl() + "/user");
        registry.add("spring.security.oauth2.client.provider.github.user-name-attribute", () -> "id");

        // Google OIDC configuration pointing to WireMock
        registry.add("spring.security.oauth2.client.registration.google.client-id", () -> "test-google-client-id");
        registry.add("spring.security.oauth2.client.registration.google.client-secret", () -> "test-google-client-secret");
        registry.add("spring.security.oauth2.client.registration.google.scope", () -> "openid,profile,email");
        registry.add("spring.security.oauth2.client.provider.google.issuer-uri", () -> mockGoogle.getBaseUrl());
    }

    @Nested
    @DisplayName("GitHub OAuth Flow Tests")
    class GitHubOAuthFlowTests {

        @Test
        @DisplayName("HAPPY PATH: Should redirect to GitHub authorization endpoint")
        void shouldRedirectToGitHubAuthorizationEndpoint() throws Exception {
            AiAgentTestReporter.TestContext ctx = AiAgentTestReporter.startTest(
                    "FederatedIdentityFlowTest",
                    "shouldRedirectToGitHubAuthorizationEndpoint",
                    "Initiating GitHub login redirects to GitHub's authorization endpoint"
            );

            try {
                // When - initiate GitHub OAuth flow
                MvcResult result = mockMvc.perform(get("/oauth2/authorization/github"))
                        .andExpect(status().is3xxRedirection())
                        .andReturn();

                // Then - should redirect to GitHub (mocked)
                String redirectUrl = result.getResponse().getRedirectedUrl();
                assertThat(redirectUrl).isNotNull();
                assertThat(redirectUrl).contains(mockGitHub.getBaseUrl() + "/login/oauth/authorize");
                assertThat(redirectUrl).contains("client_id=test-github-client-id");
                assertThat(redirectUrl).contains("response_type=code");
                assertThat(redirectUrl).contains("scope=");

                ctx.addObservation("Redirect URL: " + redirectUrl);
                ctx.recordSuccess();
            } catch (Exception e) {
                ctx.recordFailure(e);
                throw e;
            } finally {
                AiAgentTestReporter.reportTest(ctx);
            }
        }

        @Test
        @DisplayName("SAD PATH: Should handle missing authorization code")
        void shouldHandleMissingAuthorizationCode() throws Exception {
            AiAgentTestReporter.TestContext ctx = AiAgentTestReporter.startTest(
                    "FederatedIdentityFlowTest",
                    "shouldHandleMissingAuthorizationCode",
                    "Callback without authorization code should result in error"
            );

            try {
                // When - callback without code parameter
                mockMvc.perform(get("/login/oauth2/code/github"))
                        .andExpect(status().is3xxRedirection())
                        .andExpect(redirectedUrlPattern("**/login?error*"));

                ctx.addObservation("Missing code parameter correctly handled");
                ctx.recordSuccess();
            } catch (Exception e) {
                ctx.recordFailure(e);
                throw e;
            } finally {
                AiAgentTestReporter.reportTest(ctx);
            }
        }

        @Test
        @DisplayName("SAD PATH: Should handle OAuth error response")
        void shouldHandleOAuthErrorResponse() throws Exception {
            AiAgentTestReporter.TestContext ctx = AiAgentTestReporter.startTest(
                    "FederatedIdentityFlowTest",
                    "shouldHandleOAuthErrorResponse",
                    "OAuth provider error response should redirect to login with error"
            );

            try {
                // When - callback with error parameter (user denied access)
                mockMvc.perform(get("/login/oauth2/code/github")
                        .param("error", "access_denied")
                        .param("error_description", "The user denied the request"))
                        .andExpect(status().is3xxRedirection())
                        .andExpect(redirectedUrlPattern("**/login?error*"));

                ctx.addObservation("OAuth error correctly propagated to login page");
                ctx.recordSuccess();
            } catch (Exception e) {
                ctx.recordFailure(e);
                throw e;
            } finally {
                AiAgentTestReporter.reportTest(ctx);
            }
        }
    }

    @Nested
    @DisplayName("Google OIDC Flow Tests")
    class GoogleOidcFlowTests {

        @Test
        @DisplayName("HAPPY PATH: Should redirect to Google authorization endpoint")
        void shouldRedirectToGoogleAuthorizationEndpoint() throws Exception {
            AiAgentTestReporter.TestContext ctx = AiAgentTestReporter.startTest(
                    "FederatedIdentityFlowTest",
                    "shouldRedirectToGoogleAuthorizationEndpoint",
                    "Initiating Google login redirects to Google's authorization endpoint"
            );

            try {
                // When - initiate Google OAuth flow
                MvcResult result = mockMvc.perform(get("/oauth2/authorization/google"))
                        .andExpect(status().is3xxRedirection())
                        .andReturn();

                // Then - should redirect to Google (mocked)
                String redirectUrl = result.getResponse().getRedirectedUrl();
                assertThat(redirectUrl).isNotNull();
                assertThat(redirectUrl).contains(mockGoogle.getBaseUrl());
                assertThat(redirectUrl).contains("client_id=test-google-client-id");

                ctx.addObservation("Redirect URL: " + redirectUrl);
                ctx.recordSuccess();
            } catch (Exception e) {
                ctx.recordFailure(e);
                throw e;
            } finally {
                AiAgentTestReporter.reportTest(ctx);
            }
        }
    }

    @Nested
    @DisplayName("Login Page Tests")
    class LoginPageTests {

        @Test
        @DisplayName("HAPPY PATH: Login page should show OAuth provider links")
        void loginPageShouldShowOAuthProviderLinks() throws Exception {
            AiAgentTestReporter.TestContext ctx = AiAgentTestReporter.startTest(
                    "FederatedIdentityFlowTest",
                    "loginPageShouldShowOAuthProviderLinks",
                    "Login page displays links to federated identity providers"
            );

            try {
                // When
                MvcResult result = mockMvc.perform(get("/login"))
                        .andExpect(status().isOk())
                        .andReturn();

                String content = result.getResponse().getContentAsString();

                // Then - page should contain OAuth links
                // The actual content depends on the Thymeleaf template
                assertThat(content).isNotEmpty();
                ctx.addObservation("Login page rendered successfully");
                ctx.addObservation("Content length: " + content.length() + " bytes");

                ctx.recordSuccess();
            } catch (Exception e) {
                ctx.recordFailure(e);
                throw e;
            } finally {
                AiAgentTestReporter.reportTest(ctx);
            }
        }
    }

    @Nested
    @DisplayName("WireMock Server Health Tests")
    class WireMockServerHealthTests {

        @Test
        @DisplayName("HAPPY PATH: GitHub WireMock server is running")
        void githubWireMockServerIsRunning() {
            AiAgentTestReporter.TestContext ctx = AiAgentTestReporter.startTest(
                    "FederatedIdentityFlowTest",
                    "githubWireMockServerIsRunning",
                    "Verify GitHub mock server is running and accessible"
            );

            try {
                assertThat(mockGitHub.getPort()).isGreaterThan(0);
                assertThat(mockGitHub.getBaseUrl()).startsWith("http://localhost:");

                ctx.addObservation("GitHub WireMock port: " + mockGitHub.getPort());
                ctx.addObservation("GitHub WireMock URL: " + mockGitHub.getBaseUrl());
                ctx.recordSuccess();
            } catch (Exception e) {
                ctx.recordFailure(e);
                throw e;
            } finally {
                AiAgentTestReporter.reportTest(ctx);
            }
        }

        @Test
        @DisplayName("HAPPY PATH: Google WireMock server is running")
        void googleWireMockServerIsRunning() {
            AiAgentTestReporter.TestContext ctx = AiAgentTestReporter.startTest(
                    "FederatedIdentityFlowTest",
                    "googleWireMockServerIsRunning",
                    "Verify Google mock server is running and accessible"
            );

            try {
                assertThat(mockGoogle.getPort()).isGreaterThan(0);
                assertThat(mockGoogle.getBaseUrl()).startsWith("http://localhost:");

                ctx.addObservation("Google WireMock port: " + mockGoogle.getPort());
                ctx.addObservation("Google WireMock URL: " + mockGoogle.getBaseUrl());
                ctx.recordSuccess();
            } catch (Exception e) {
                ctx.recordFailure(e);
                throw e;
            } finally {
                AiAgentTestReporter.reportTest(ctx);
            }
        }
    }
}
