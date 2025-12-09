package com.bootsandcats.oauth2.testing.assertions;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;

import org.assertj.core.api.Assertions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Custom assertions for validating JWT tokens in OAuth2 tests.
 *
 * <p>Provides fluent assertions for verifying JWT structure, claims, and signatures. These
 * assertions generate clear, AI-parseable error messages that help diagnose token validation
 * failures.
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * TokenAssertions.Assertions.assertThat(accessToken)
 *     .isValidJwt()
 *     .hasAlgorithm("ES256")
 *     .hasClaim("sub")
 *     .hasScope("openid")
 *     .isNotExpired();
 * }</pre>
 */
public class TokenAssertions {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String token;
    private JsonNode header;
    private JsonNode payload;

    private TokenAssertions(String token) {
        this.token = token;
    }

    /**
     * Creates a new token assertion instance.
     *
     * @param token the JWT token string
     * @return a new TokenAssertions instance
     */
    public static TokenAssertions assertThat(String token) {
        return new TokenAssertions(token);
    }

    /**
     * Verifies the token has valid JWT structure (header.payload.signature).
     *
     * @return this assertion for chaining
     */
    public TokenAssertions isValidJwt() {
        Assertions.assertThat(token)
                .as("JWT token should not be null or blank")
                .isNotNull()
                .isNotBlank();

        String[] parts = token.split("\\.");
        Assertions.assertThat(parts)
                .as(
                        "JWT should have 3 parts (header.payload.signature), but found %d parts",
                        parts.length)
                .hasSize(3);

        try {
            String headerJson =
                    new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            this.header = OBJECT_MAPPER.readTree(headerJson);

            String payloadJson =
                    new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            this.payload = OBJECT_MAPPER.readTree(payloadJson);
        } catch (Exception e) {
            throw new AssertionError(
                    "Failed to parse JWT: " + e.getMessage() + ". Token may be malformed.", e);
        }

        return this;
    }

    /**
     * Verifies the JWT uses the expected signing algorithm.
     *
     * @param expectedAlgorithm the expected algorithm (e.g., "ES256", "RS256")
     * @return this assertion for chaining
     */
    public TokenAssertions hasAlgorithm(String expectedAlgorithm) {
        ensureParsed();
        String actualAlg = header.path("alg").asText();
        Assertions.assertThat(actualAlg)
                .as(
                        "JWT algorithm should be '%s' but was '%s'. "
                                + "Check JWK configuration and token signing settings.",
                        expectedAlgorithm, actualAlg)
                .isEqualTo(expectedAlgorithm);
        return this;
    }

    /**
     * Verifies the JWT contains a specific claim.
     *
     * @param claimName the name of the claim
     * @return this assertion for chaining
     */
    public TokenAssertions hasClaim(String claimName) {
        ensureParsed();
        Assertions.assertThat(payload.has(claimName))
                .as(
                        "JWT should contain claim '%s'. "
                                + "Available claims: %s. "
                                + "Check token generation configuration.",
                        claimName, payload.fieldNames())
                .isTrue();
        return this;
    }

    /**
     * Verifies the JWT claim has a specific value.
     *
     * @param claimName the name of the claim
     * @param expectedValue the expected value
     * @return this assertion for chaining
     */
    public TokenAssertions hasClaimValue(String claimName, String expectedValue) {
        ensureParsed();
        hasClaim(claimName);
        String actualValue = payload.path(claimName).asText();
        Assertions.assertThat(actualValue)
                .as(
                        "JWT claim '%s' should be '%s' but was '%s'",
                        claimName, expectedValue, actualValue)
                .isEqualTo(expectedValue);
        return this;
    }

    /**
     * Verifies the JWT contains the specified scope.
     *
     * @param expectedScope the scope to check for
     * @return this assertion for chaining
     */
    public TokenAssertions hasScope(String expectedScope) {
        ensureParsed();
        String scopeClaim = payload.path("scope").asText();
        Set<String> scopes = Set.of(scopeClaim.split("\\s+"));
        Assertions.assertThat(scopes)
                .as(
                        "JWT should contain scope '%s'. Actual scopes: %s. "
                                + "Check client scope configuration.",
                        expectedScope, scopes)
                .contains(expectedScope);
        return this;
    }

    /**
     * Verifies the JWT is not expired.
     *
     * @return this assertion for chaining
     */
    public TokenAssertions isNotExpired() {
        ensureParsed();
        hasClaim("exp");
        long exp = payload.path("exp").asLong();
        long now = System.currentTimeMillis() / 1000;
        Assertions.assertThat(exp)
                .as(
                        "JWT should not be expired. exp=%d, now=%d, delta=%d seconds. "
                                + "Check token TTL configuration.",
                        exp, now, exp - now)
                .isGreaterThan(now);
        return this;
    }

    /**
     * Verifies the JWT has the expected issuer.
     *
     * @param expectedIssuer the expected issuer URI
     * @return this assertion for chaining
     */
    public TokenAssertions hasIssuer(String expectedIssuer) {
        ensureParsed();
        return hasClaimValue("iss", expectedIssuer);
    }

    /**
     * Verifies the JWT has the expected audience.
     *
     * @param expectedAudience the expected audience
     * @return this assertion for chaining
     */
    public TokenAssertions hasAudience(String expectedAudience) {
        ensureParsed();
        JsonNode audNode = payload.path("aud");
        if (audNode.isArray()) {
            boolean found = false;
            for (JsonNode aud : audNode) {
                if (expectedAudience.equals(aud.asText())) {
                    found = true;
                    break;
                }
            }
            Assertions.assertThat(found)
                    .as("JWT audience should contain '%s'. Actual: %s", expectedAudience, audNode)
                    .isTrue();
        } else {
            Assertions.assertThat(audNode.asText())
                    .as(
                            "JWT audience should be '%s' but was '%s'",
                            expectedAudience, audNode.asText())
                    .isEqualTo(expectedAudience);
        }
        return this;
    }

    /**
     * Verifies the JWT has a kid (key ID) in the header.
     *
     * @return this assertion for chaining
     */
    public TokenAssertions hasKeyId() {
        ensureParsed();
        Assertions.assertThat(header.has("kid"))
                .as("JWT header should contain 'kid' (key ID). Check JWKS configuration.")
                .isTrue();
        Assertions.assertThat(header.path("kid").asText())
                .as("JWT 'kid' should not be empty")
                .isNotBlank();
        return this;
    }

    /**
     * Verifies the JWT has standard OIDC claims for an ID token.
     *
     * @return this assertion for chaining
     */
    public TokenAssertions hasStandardIdTokenClaims() {
        ensureParsed();
        hasClaim("sub");
        hasClaim("iss");
        hasClaim("aud");
        hasClaim("exp");
        hasClaim("iat");
        return this;
    }

    /**
     * Returns the parsed payload for additional custom assertions.
     *
     * @return the JWT payload as JsonNode
     */
    public JsonNode getPayload() {
        ensureParsed();
        return payload;
    }

    /**
     * Returns the parsed header for additional custom assertions.
     *
     * @return the JWT header as JsonNode
     */
    public JsonNode getHeader() {
        ensureParsed();
        return header;
    }

    private void ensureParsed() {
        if (header == null || payload == null) {
            isValidJwt();
        }
    }
}
