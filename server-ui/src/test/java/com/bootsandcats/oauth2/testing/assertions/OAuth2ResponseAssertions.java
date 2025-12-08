package com.bootsandcats.oauth2.testing.assertions;

import java.util.Set;

import org.assertj.core.api.Assertions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Custom assertions for validating OAuth2 HTTP responses.
 *
 * <p>Provides fluent assertions for verifying OAuth2 response structure, error codes, and headers.
 * Generates clear, AI-parseable error messages for failure diagnosis.
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * OAuth2ResponseAssertions.Assertions.assertThat(status, body)
 *     .isSuccessfulTokenResponse()
 *     .hasAccessToken()
 *     .hasRefreshToken()
 *     .hasTokenType("Bearer");
 * }</pre>
 */
public class OAuth2ResponseAssertions {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> VALID_OAUTH2_ERRORS =
            Set.of(
                    "invalid_request",
                    "invalid_client",
                    "invalid_grant",
                    "unauthorized_client",
                    "unsupported_grant_type",
                    "invalid_scope",
                    "invalid_token",
                    "insufficient_scope",
                    "server_error",
                    "temporarily_unavailable");

    private final int statusCode;
    private final String body;
    private JsonNode json;

    private OAuth2ResponseAssertions(int statusCode, String body) {
        this.statusCode = statusCode;
        this.body = body;
    }

    /**
     * Creates a new response assertion instance.
     *
     * @param statusCode the HTTP status code
     * @param body the response body
     * @return a new OAuth2ResponseAssertions instance
     */
    public static OAuth2ResponseAssertions assertThat(int statusCode, String body) {
        return new OAuth2ResponseAssertions(statusCode, body);
    }

    /**
     * Verifies the response is a successful token response (200 OK with access_token).
     *
     * @return this assertion for chaining
     */
    public OAuth2ResponseAssertions isSuccessfulTokenResponse() {
        Assertions.assertThat(statusCode)
                .as(
                        "Token response should be 200 OK but was %d. Response body: %s",
                        statusCode, truncate(body, 500))
                .isEqualTo(200);

        ensureJsonParsed();
        Assertions.assertThat(json.has("access_token"))
                .as("Token response should contain 'access_token'. Response: %s", truncate(body, 500))
                .isTrue();
        return this;
    }

    /**
     * Verifies the response contains an access token.
     *
     * @return this assertion for chaining
     */
    public OAuth2ResponseAssertions hasAccessToken() {
        ensureJsonParsed();
        Assertions.assertThat(json.has("access_token"))
                .as("Response should contain 'access_token'")
                .isTrue();
        Assertions.assertThat(json.path("access_token").asText())
                .as("access_token should not be empty")
                .isNotBlank();
        return this;
    }

    /**
     * Verifies the response contains a refresh token.
     *
     * @return this assertion for chaining
     */
    public OAuth2ResponseAssertions hasRefreshToken() {
        ensureJsonParsed();
        Assertions.assertThat(json.has("refresh_token"))
                .as(
                        "Response should contain 'refresh_token'. "
                                + "Check client configuration allows refresh_token grant.")
                .isTrue();
        Assertions.assertThat(json.path("refresh_token").asText())
                .as("refresh_token should not be empty")
                .isNotBlank();
        return this;
    }

    /**
     * Verifies the response contains an ID token.
     *
     * @return this assertion for chaining
     */
    public OAuth2ResponseAssertions hasIdToken() {
        ensureJsonParsed();
        Assertions.assertThat(json.has("id_token"))
                .as(
                        "Response should contain 'id_token'. "
                                + "Check that 'openid' scope was requested.")
                .isTrue();
        Assertions.assertThat(json.path("id_token").asText())
                .as("id_token should not be empty")
                .isNotBlank();
        return this;
    }

    /**
     * Verifies the response has the expected token type.
     *
     * @param expectedType the expected token type (usually "Bearer")
     * @return this assertion for chaining
     */
    public OAuth2ResponseAssertions hasTokenType(String expectedType) {
        ensureJsonParsed();
        Assertions.assertThat(json.path("token_type").asText())
                .as("token_type should be '%s'", expectedType)
                .isEqualToIgnoringCase(expectedType);
        return this;
    }

    /**
     * Verifies the response contains an expires_in field.
     *
     * @return this assertion for chaining
     */
    public OAuth2ResponseAssertions hasExpiresIn() {
        ensureJsonParsed();
        Assertions.assertThat(json.has("expires_in")).as("Response should contain 'expires_in'").isTrue();
        Assertions.assertThat(json.path("expires_in").asInt())
                .as("expires_in should be positive")
                .isGreaterThan(0);
        return this;
    }

    /**
     * Verifies the response contains the expected scope.
     *
     * @param expectedScope the scope to check for
     * @return this assertion for chaining
     */
    public OAuth2ResponseAssertions hasScope(String expectedScope) {
        ensureJsonParsed();
        String scopeValue = json.path("scope").asText();
        Set<String> scopes = Set.of(scopeValue.split("\\s+"));
        Assertions.assertThat(scopes)
                .as("Response scope should contain '%s'. Actual: %s", expectedScope, scopes)
                .contains(expectedScope);
        return this;
    }

    /**
     * Verifies the response is an OAuth2 error response with the expected error code.
     *
     * @param expectedError the expected OAuth2 error code
     * @return this assertion for chaining
     */
    public OAuth2ResponseAssertions isErrorResponse(String expectedError) {
        ensureJsonParsed();
        Assertions.assertThat(json.has("error"))
                .as(
                        "Response should contain 'error' field for error response. "
                                + "Response: %s",
                        truncate(body, 500))
                .isTrue();

        String actualError = json.path("error").asText();
        Assertions.assertThat(actualError)
                .as(
                        "OAuth2 error should be '%s' but was '%s'. "
                                + "Description: %s",
                        expectedError, actualError, json.path("error_description").asText())
                .isEqualTo(expectedError);
        return this;
    }

    /**
     * Verifies the response is a valid OAuth2 error response (contains error field with valid
     * code).
     *
     * @return this assertion for chaining
     */
    public OAuth2ResponseAssertions isValidOAuth2Error() {
        ensureJsonParsed();
        Assertions.assertThat(json.has("error")).as("Error response should contain 'error' field").isTrue();

        String error = json.path("error").asText();
        Assertions.assertThat(VALID_OAUTH2_ERRORS)
                .as(
                        "Error code '%s' should be a standard OAuth2 error. Valid codes: %s",
                        error, VALID_OAUTH2_ERRORS)
                .contains(error);
        return this;
    }

    /**
     * Verifies the HTTP status code is as expected.
     *
     * @param expectedStatus the expected HTTP status code
     * @return this assertion for chaining
     */
    public OAuth2ResponseAssertions hasStatus(int expectedStatus) {
        Assertions.assertThat(statusCode)
                .as(
                        "HTTP status should be %d but was %d. Response: %s",
                        expectedStatus, statusCode, truncate(body, 500))
                .isEqualTo(expectedStatus);
        return this;
    }

    /**
     * Verifies the HTTP status is a client error (4xx).
     *
     * @return this assertion for chaining
     */
    public OAuth2ResponseAssertions isClientError() {
        Assertions.assertThat(statusCode)
                .as("HTTP status should be 4xx client error but was %d", statusCode)
                .isBetween(400, 499);
        return this;
    }

    /**
     * Verifies the HTTP status is unauthorized (401).
     *
     * @return this assertion for chaining
     */
    public OAuth2ResponseAssertions isUnauthorized() {
        return hasStatus(401);
    }

    /**
     * Verifies the HTTP status is bad request (400).
     *
     * @return this assertion for chaining
     */
    public OAuth2ResponseAssertions isBadRequest() {
        return hasStatus(400);
    }

    /**
     * Verifies the token introspection response shows the token as active.
     *
     * @return this assertion for chaining
     */
    public OAuth2ResponseAssertions isActiveToken() {
        ensureJsonParsed();
        Assertions.assertThat(json.path("active").asBoolean())
                .as("Token should be active. Response: %s", truncate(body, 500))
                .isTrue();
        return this;
    }

    /**
     * Verifies the token introspection response shows the token as inactive.
     *
     * @return this assertion for chaining
     */
    public OAuth2ResponseAssertions isInactiveToken() {
        ensureJsonParsed();
        Assertions.assertThat(json.path("active").asBoolean())
                .as(
                        "Token should be inactive. Response: %s. "
                                + "If expecting active, check token expiry or revocation status.",
                        truncate(body, 500))
                .isFalse();
        return this;
    }

    /**
     * Returns the access token from the response.
     *
     * @return the access token string
     */
    public String getAccessToken() {
        ensureJsonParsed();
        return json.path("access_token").asText();
    }

    /**
     * Returns the refresh token from the response.
     *
     * @return the refresh token string
     */
    public String getRefreshToken() {
        ensureJsonParsed();
        return json.path("refresh_token").asText();
    }

    /**
     * Returns the ID token from the response.
     *
     * @return the ID token string
     */
    public String getIdToken() {
        ensureJsonParsed();
        return json.path("id_token").asText();
    }

    /**
     * Returns the parsed JSON body.
     *
     * @return the JSON body as JsonNode
     */
    public JsonNode getJson() {
        ensureJsonParsed();
        return json;
    }

    private void ensureJsonParsed() {
        if (json == null) {
            try {
                json = OBJECT_MAPPER.readTree(body);
            } catch (Exception e) {
                throw new AssertionError(
                        "Failed to parse response body as JSON: "
                                + truncate(body, 200)
                                + ". Error: "
                                + e.getMessage(),
                        e);
            }
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) return "null";
        if (value.length() <= maxLength) return value;
        return value.substring(0, maxLength) + "...[truncated]";
    }
}
