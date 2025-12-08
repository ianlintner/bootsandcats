package com.bootsandcats.oauth2.testcontainers;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Mock Google OIDC server using WireMock.
 *
 * <p>This class provides a complete mock of Google's OIDC endpoints:
 * - GET /.well-known/openid-configuration - Discovery document
 * - POST /token - Token endpoint
 * - GET /userinfo - User info endpoint
 * - GET /oauth2/v3/certs - JWKS endpoint
 *
 * <p>Used for testing federated identity flows without hitting real Google APIs.
 *
 * <h2>AI Agent Debugging Notes:</h2>
 * <ul>
 *   <li>Google uses standard OIDC with 'sub' as the user identifier</li>
 *   <li>Discovery document must be available for Spring Security to auto-configure</li>
 *   <li>JWKS endpoint must return valid keys for ID token validation</li>
 *   <li>If token validation fails, check the JWKS keys match the ID token signature</li>
 * </ul>
 */
public class MockGoogleOidcServer {

    private final WireMockServer wireMockServer;
    private KeyPair rsaKeyPair;
    private String keyId;

    // Test user attributes
    public static final String TEST_USER_SUB = "google-user-123456789";
    public static final String TEST_USER_NAME = "Google Test User";
    public static final String TEST_USER_EMAIL = "googleuser@gmail.com";
    public static final String TEST_USER_PICTURE = "https://lh3.googleusercontent.com/test-picture";
    public static final String TEST_ACCESS_TOKEN = "ya29.test_access_token_123456";

    public MockGoogleOidcServer() {
        this.wireMockServer = new WireMockServer(
                WireMockConfiguration.wireMockConfig()
                        .dynamicPort()
                        .usingFilesUnderClasspath("wiremock")
        );
        generateRsaKeyPair();
    }

    public MockGoogleOidcServer(int port) {
        this.wireMockServer = new WireMockServer(
                WireMockConfiguration.wireMockConfig()
                        .port(port)
                        .usingFilesUnderClasspath("wiremock")
        );
        generateRsaKeyPair();
    }

    private void generateRsaKeyPair() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            this.rsaKeyPair = keyPairGenerator.generateKeyPair();
            this.keyId = UUID.randomUUID().toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate RSA key pair", e);
        }
    }

    /**
     * Starts the WireMock server and configures all Google OIDC endpoint stubs.
     */
    public void start() {
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
        setupStubs();
    }

    /**
     * Stops the WireMock server.
     */
    public void stop() {
        wireMockServer.stop();
    }

    /**
     * Resets all stubs and request journals.
     */
    public void reset() {
        wireMockServer.resetAll();
        setupStubs();
    }

    /**
     * Returns the base URL of the WireMock server.
     */
    public String getBaseUrl() {
        return "http://localhost:" + wireMockServer.port();
    }

    /**
     * Returns the port the WireMock server is running on.
     */
    public int getPort() {
        return wireMockServer.port();
    }

    /**
     * Returns the issuer URI for Google OIDC.
     */
    public String getIssuerUri() {
        return getBaseUrl();
    }

    /**
     * Returns the authorization URI for Google OIDC.
     */
    public String getAuthorizationUri() {
        return getBaseUrl() + "/o/oauth2/v2/auth";
    }

    /**
     * Returns the token URI for Google OIDC.
     */
    public String getTokenUri() {
        return getBaseUrl() + "/token";
    }

    /**
     * Returns the userinfo URI for Google OIDC.
     */
    public String getUserInfoUri() {
        return getBaseUrl() + "/userinfo";
    }

    /**
     * Configures all Google OIDC endpoint mocks.
     */
    private void setupStubs() {
        stubDiscoveryEndpoint();
        stubJwksEndpoint();
        stubTokenEndpoint();
        stubUserInfoEndpoint();
    }

    /**
     * Mocks Google's OIDC discovery endpoint (GET /.well-known/openid-configuration).
     *
     * <p>Spring Security uses this to auto-configure the OIDC client.
     */
    private void stubDiscoveryEndpoint() {
        String baseUrl = getBaseUrl();
        wireMockServer.stubFor(get(urlPathEqualTo("/.well-known/openid-configuration"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "issuer": "%s",
                                    "authorization_endpoint": "%s/o/oauth2/v2/auth",
                                    "token_endpoint": "%s/token",
                                    "userinfo_endpoint": "%s/userinfo",
                                    "jwks_uri": "%s/oauth2/v3/certs",
                                    "revocation_endpoint": "%s/revoke",
                                    "scopes_supported": ["openid", "profile", "email"],
                                    "response_types_supported": ["code", "token", "id_token"],
                                    "grant_types_supported": ["authorization_code", "refresh_token"],
                                    "subject_types_supported": ["public"],
                                    "id_token_signing_alg_values_supported": ["RS256"],
                                    "claims_supported": ["sub", "name", "email", "picture", "email_verified"]
                                }
                                """.formatted(baseUrl, baseUrl, baseUrl, baseUrl, baseUrl, baseUrl))));
    }

    /**
     * Mocks Google's JWKS endpoint (GET /oauth2/v3/certs).
     *
     * <p>Returns the public keys used to verify ID tokens.
     */
    private void stubJwksEndpoint() {
        RSAPublicKey publicKey = (RSAPublicKey) rsaKeyPair.getPublic();
        String n = Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey.getModulus().toByteArray());
        String e = Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey.getPublicExponent().toByteArray());

        wireMockServer.stubFor(get(urlPathEqualTo("/oauth2/v3/certs"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "keys": [
                                        {
                                            "kty": "RSA",
                                            "alg": "RS256",
                                            "use": "sig",
                                            "kid": "%s",
                                            "n": "%s",
                                            "e": "%s"
                                        }
                                    ]
                                }
                                """.formatted(keyId, n, e))));
    }

    /**
     * Mocks Google's token endpoint (POST /token).
     *
     * <p>Returns an access token and ID token. Note: The ID token is a placeholder
     * since proper JWT signing requires more infrastructure.
     */
    private void stubTokenEndpoint() {
        // Create a simple mock ID token (not properly signed, for testing structure only)
        String mockIdToken = createMockIdToken();

        wireMockServer.stubFor(post(urlPathEqualTo("/token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "access_token": "%s",
                                    "token_type": "Bearer",
                                    "expires_in": 3600,
                                    "scope": "openid profile email",
                                    "id_token": "%s"
                                }
                                """.formatted(TEST_ACCESS_TOKEN, mockIdToken))));
    }

    /**
     * Creates a mock ID token for testing.
     *
     * <p>Note: This is a simplified JWT structure for testing OAuth2 flows.
     * For production-grade testing with proper JWT validation, consider using
     * a library like Nimbus JOSE+JWT.
     */
    private String createMockIdToken() {
        // Base64 URL encode header
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString(
                """
                {"alg":"RS256","typ":"JWT","kid":"%s"}
                """.formatted(keyId).getBytes()
        );

        // Base64 URL encode payload
        long now = Instant.now().getEpochSecond();
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                """
                {
                    "iss": "%s",
                    "sub": "%s",
                    "aud": "test-client-id",
                    "exp": %d,
                    "iat": %d,
                    "email": "%s",
                    "email_verified": true,
                    "name": "%s",
                    "picture": "%s"
                }
                """.formatted(
                        getBaseUrl(),
                        TEST_USER_SUB,
                        now + 3600,
                        now,
                        TEST_USER_EMAIL,
                        TEST_USER_NAME,
                        TEST_USER_PICTURE
                ).getBytes()
        );

        // Simplified signature (not cryptographically valid)
        String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "mock-signature".getBytes()
        );

        return header + "." + payload + "." + signature;
    }

    /**
     * Mocks Google's userinfo endpoint (GET /userinfo).
     */
    private void stubUserInfoEndpoint() {
        wireMockServer.stubFor(get(urlPathEqualTo("/userinfo"))
                .withHeader("Authorization", equalTo("Bearer " + TEST_ACCESS_TOKEN))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "sub": "%s",
                                    "name": "%s",
                                    "email": "%s",
                                    "email_verified": true,
                                    "picture": "%s"
                                }
                                """.formatted(
                                TEST_USER_SUB,
                                TEST_USER_NAME,
                                TEST_USER_EMAIL,
                                TEST_USER_PICTURE))));

        // Unauthorized request
        wireMockServer.stubFor(get(urlPathEqualTo("/userinfo"))
                .withHeader("Authorization", absent())
                .willReturn(aResponse()
                        .withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "error": "invalid_token",
                                    "error_description": "Invalid access token"
                                }
                                """)));
    }

    /**
     * Configures a failure response for the token endpoint.
     */
    public void stubTokenEndpointError(String errorCode, String errorDescription) {
        wireMockServer.stubFor(post(urlPathEqualTo("/token"))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "error": "%s",
                                    "error_description": "%s"
                                }
                                """.formatted(errorCode, errorDescription))));
    }

    /**
     * Verifies that the token endpoint was called.
     */
    public void verifyTokenEndpointCalled() {
        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/token")));
    }

    /**
     * Verifies that the userinfo endpoint was called.
     */
    public void verifyUserInfoEndpointCalled() {
        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/userinfo")));
    }

    /**
     * Gets the WireMock server instance for advanced verification.
     */
    public WireMockServer getWireMockServer() {
        return wireMockServer;
    }
}
