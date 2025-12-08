package com.bootsandcats.oauth2.testcontainers;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

/**
 * Mock GitHub OAuth server using WireMock.
 *
 * <p>This class provides a complete mock of GitHub's OAuth2 endpoints: - POST
 * /login/oauth/access_token - Token endpoint - GET /user - User info endpoint - GET /user/emails -
 * User emails endpoint
 *
 * <p>Used for testing federated identity flows without hitting real GitHub APIs.
 *
 * <h2>AI Agent Debugging Notes:</h2>
 *
 * <ul>
 *   <li>If tests fail with connection refused, ensure WireMock server is started
 *   <li>Check that OAuth2 client registration URLs point to WireMock server
 *   <li>Verify mock responses match expected GitHub response formats
 *   <li>GitHub uses 'id' as the user identifier, 'login' as username
 * </ul>
 */
public class MockGitHubOAuthServer {

    private final WireMockServer wireMockServer;

    // Test user attributes
    public static final String TEST_USER_ID = "12345678";
    public static final String TEST_USER_LOGIN = "testuser";
    public static final String TEST_USER_NAME = "Test User";
    public static final String TEST_USER_EMAIL = "testuser@example.com";
    public static final String TEST_USER_AVATAR =
            "https://avatars.githubusercontent.com/u/12345678";
    public static final String TEST_ACCESS_TOKEN = "gho_test_access_token_123456";

    public MockGitHubOAuthServer() {
        this.wireMockServer =
                new WireMockServer(
                        WireMockConfiguration.wireMockConfig()
                                .dynamicPort()
                                .usingFilesUnderClasspath("wiremock"));
    }

    public MockGitHubOAuthServer(int port) {
        this.wireMockServer =
                new WireMockServer(
                        WireMockConfiguration.wireMockConfig()
                                .port(port)
                                .usingFilesUnderClasspath("wiremock"));
    }

    /** Starts the WireMock server and configures all GitHub OAuth endpoint stubs. */
    public void start() {
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
        setupStubs();
    }

    /** Stops the WireMock server. */
    public void stop() {
        wireMockServer.stop();
    }

    /** Resets all stubs and request journals. */
    public void reset() {
        wireMockServer.resetAll();
        setupStubs();
    }

    /** Returns the base URL of the WireMock server. */
    public String getBaseUrl() {
        return "http://localhost:" + wireMockServer.port();
    }

    /** Returns the port the WireMock server is running on. */
    public int getPort() {
        return wireMockServer.port();
    }

    /** Returns the authorization URI for GitHub OAuth. */
    public String getAuthorizationUri() {
        return getBaseUrl() + "/login/oauth/authorize";
    }

    /** Returns the token URI for GitHub OAuth. */
    public String getTokenUri() {
        return getBaseUrl() + "/login/oauth/access_token";
    }

    /** Returns the user info URI for GitHub OAuth. */
    public String getUserInfoUri() {
        return getBaseUrl() + "/user";
    }

    /** Configures all GitHub OAuth endpoint mocks. */
    private void setupStubs() {
        stubTokenEndpoint();
        stubUserEndpoint();
        stubUserEmailsEndpoint();
    }

    /**
     * Mocks GitHub's token endpoint (POST /login/oauth/access_token).
     *
     * <p>GitHub returns tokens in either JSON or URL-encoded format based on Accept header. Spring
     * Security uses JSON format.
     */
    private void stubTokenEndpoint() {
        wireMockServer.stubFor(
                post(urlPathEqualTo("/login/oauth/access_token"))
                        .withHeader("Accept", containing("application/json"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                """
                                {
                                    "access_token": "%s",
                                    "token_type": "bearer",
                                    "scope": "read:user,user:email"
                                }
                                """
                                                        .formatted(TEST_ACCESS_TOKEN))));

        // Also handle URL-encoded Accept header format
        wireMockServer.stubFor(
                post(urlPathEqualTo("/login/oauth/access_token"))
                        .withHeader("Accept", notContaining("application/json"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader(
                                                "Content-Type", "application/x-www-form-urlencoded")
                                        .withBody(
                                                "access_token=%s&token_type=bearer&scope=read%%3Auser%%2Cuser%%3Aemail"
                                                        .formatted(TEST_ACCESS_TOKEN))));
    }

    /**
     * Mocks GitHub's user info endpoint (GET /user).
     *
     * <p>Returns user profile data that Spring Security OAuth2 client parses for federated identity
     * information.
     */
    private void stubUserEndpoint() {
        wireMockServer.stubFor(
                get(urlPathEqualTo("/user"))
                        .withHeader("Authorization", equalTo("Bearer " + TEST_ACCESS_TOKEN))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                """
                                {
                                    "id": %s,
                                    "login": "%s",
                                    "name": "%s",
                                    "email": "%s",
                                    "avatar_url": "%s",
                                    "html_url": "https://github.com/%s",
                                    "type": "User",
                                    "site_admin": false,
                                    "created_at": "2020-01-01T00:00:00Z",
                                    "updated_at": "2024-01-01T00:00:00Z"
                                }
                                """
                                                        .formatted(
                                                                TEST_USER_ID,
                                                                TEST_USER_LOGIN,
                                                                TEST_USER_NAME,
                                                                TEST_USER_EMAIL,
                                                                TEST_USER_AVATAR,
                                                                TEST_USER_LOGIN))));

        // Also handle request without authorization (should fail)
        wireMockServer.stubFor(
                get(urlPathEqualTo("/user"))
                        .withHeader("Authorization", absent())
                        .willReturn(
                                aResponse()
                                        .withStatus(401)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                """
                                {
                                    "message": "Requires authentication",
                                    "documentation_url": "https://docs.github.com/rest"
                                }
                                """)));
    }

    /**
     * Mocks GitHub's user emails endpoint (GET /user/emails).
     *
     * <p>Returns user email addresses. Used when primary email isn't in the user profile.
     */
    private void stubUserEmailsEndpoint() {
        wireMockServer.stubFor(
                get(urlPathEqualTo("/user/emails"))
                        .withHeader("Authorization", equalTo("Bearer " + TEST_ACCESS_TOKEN))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                """
                                [
                                    {
                                        "email": "%s",
                                        "primary": true,
                                        "verified": true,
                                        "visibility": "public"
                                    },
                                    {
                                        "email": "secondary@example.com",
                                        "primary": false,
                                        "verified": true,
                                        "visibility": null
                                    }
                                ]
                                """
                                                        .formatted(TEST_USER_EMAIL))));
    }

    /**
     * Configures a failure response for the token endpoint.
     *
     * <p>Use this to test error handling when token exchange fails.
     */
    public void stubTokenEndpointError(String errorCode, String errorDescription) {
        wireMockServer.stubFor(
                post(urlPathEqualTo("/login/oauth/access_token"))
                        .willReturn(
                                aResponse()
                                        .withStatus(400)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                """
                                {
                                    "error": "%s",
                                    "error_description": "%s"
                                }
                                """
                                                        .formatted(errorCode, errorDescription))));
    }

    /**
     * Mocks a successful authentication with custom user attributes.
     *
     * @param userId GitHub user ID
     * @param login GitHub username
     * @param email User email
     * @param name Display name
     * @param avatarUrl Avatar URL
     */
    public void mockSuccessfulAuth(
            String userId, String login, String email, String name, String avatarUrl) {
        stubCustomUserResponse(userId, login, name, email);
    }

    /**
     * Mocks an authentication error.
     *
     * @param errorCode OAuth error code
     * @param description Error description
     */
    public void mockAuthError(String errorCode, String description) {
        stubTokenEndpointError(errorCode, description);
    }

    /**
     * Configures a custom user response.
     *
     * <p>Use this to test different user attributes or scenarios.
     */
    public void stubCustomUserResponse(String userId, String login, String name, String email) {
        wireMockServer.stubFor(
                get(urlPathEqualTo("/user"))
                        .withHeader("Authorization", equalTo("Bearer " + TEST_ACCESS_TOKEN))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                """
                                {
                                    "id": %s,
                                    "login": "%s",
                                    "name": "%s",
                                    "email": "%s",
                                    "avatar_url": "https://avatars.githubusercontent.com/u/%s",
                                    "type": "User"
                                }
                                """
                                                        .formatted(
                                                                userId, login, name, email,
                                                                userId))));
    }

    /** Verifies that the token endpoint was called. */
    public void verifyTokenEndpointCalled() {
        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/login/oauth/access_token")));
    }

    /** Verifies that the user endpoint was called. */
    public void verifyUserEndpointCalled() {
        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/user")));
    }

    /** Gets the WireMock server instance for advanced verification. */
    public WireMockServer getWireMockServer() {
        return wireMockServer;
    }
}
