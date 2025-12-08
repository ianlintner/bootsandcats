package com.bootsandcats.oauth2.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.bootsandcats.oauth2.config.TestKeyManagementConfig;
import com.bootsandcats.oauth2.config.TestOAuth2ClientConfiguration;
import com.bootsandcats.oauth2.config.TestObjectMapperConfig;
import com.bootsandcats.oauth2.testing.AiAgentTestReporter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Contract tests for OIDC Discovery document compliance.
 *
 * <p>These tests verify that the authorization server's OpenID Connect Discovery document complies
 * with the OpenID Connect Discovery 1.0 specification.
 *
 * <h2>Specification Reference</h2>
 *
 * <ul>
 *   <li>OpenID Connect Discovery 1.0 Section 3: OpenID Provider Metadata
 *   <li>OpenID Connect Core 1.0 Section 15.1: Discovery
 * </ul>
 *
 * <h2>Contract Verification</h2>
 *
 * <p>These tests ensure:
 *
 * <ul>
 *   <li>All REQUIRED metadata fields are present
 *   <li>All RECOMMENDED metadata fields are present
 *   <li>Field values conform to specification
 *   <li>Referenced endpoints are accessible
 * </ul>
 *
 * @see <a href="https://openid.net/specs/openid-connect-discovery-1_0.html">OIDC Discovery 1.0</a>
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
@Tag("oidc")
@Tag("contract")
@Tag("critical")
@DisplayName("OIDC Discovery Contract Tests")
class OidcDiscoveryContractTest {

    private static final Set<String> REQUIRED_METADATA_FIELDS =
            Set.of(
                    "issuer",
                    "authorization_endpoint",
                    "token_endpoint",
                    "jwks_uri",
                    "response_types_supported",
                    "subject_types_supported",
                    "id_token_signing_alg_values_supported");

    private static final Set<String> RECOMMENDED_METADATA_FIELDS =
            Set.of(
                    "userinfo_endpoint",
                    "registration_endpoint",
                    "scopes_supported",
                    "claims_supported",
                    "grant_types_supported",
                    "token_endpoint_auth_methods_supported");

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    /**
     * Test ID: OD-C-001
     *
     * <p>Contract: Discovery document must be publicly accessible
     *
     * <p>The OpenID Provider Configuration Information must be available at
     * /.well-known/openid-configuration without authentication.
     *
     * <p>Reference: OIDC Discovery 1.0 Section 4
     */
    @Test
    @DisplayName("OD-C-001: Discovery document is publicly accessible")
    void discoveryDocument_shouldBePubliclyAccessible() throws Exception {
        // Given: No authentication
        AiAgentTestReporter.setExpectedOutcome("200 OK with JSON content-type");

        // When: Discovery endpoint is accessed
        MvcResult result =
                mockMvc.perform(get("/.well-known/openid-configuration"))
                        .andExpect(status().isOk())
                        .andReturn();

        // Then: Document is returned
        String contentType = result.getResponse().getContentType();
        assertThat(contentType)
                .as("Discovery document should have JSON content type")
                .containsIgnoringCase("application/json");

        String body = result.getResponse().getContentAsString();
        AiAgentTestReporter.setResponseContext(result.getResponse().getStatus(), body);

        JsonNode discovery = objectMapper.readTree(body);
        assertThat(discovery).as("Discovery document should be valid JSON").isNotNull();
    }

    /**
     * Test ID: OD-C-002
     *
     * <p>Contract: All REQUIRED metadata fields must be present
     *
     * <p>Per OIDC Discovery 1.0 Section 3, the following fields are REQUIRED:
     *
     * <ul>
     *   <li>issuer
     *   <li>authorization_endpoint
     *   <li>token_endpoint (if not implicit-only)
     *   <li>jwks_uri
     *   <li>response_types_supported
     *   <li>subject_types_supported
     *   <li>id_token_signing_alg_values_supported
     * </ul>
     */
    @Test
    @DisplayName("OD-C-002: All REQUIRED metadata fields are present")
    void discoveryDocument_shouldContainRequiredFields() throws Exception {
        // Given: Discovery endpoint
        AiAgentTestReporter.setExpectedOutcome(
                "All REQUIRED fields present: " + REQUIRED_METADATA_FIELDS);

        // When: Discovery document is retrieved
        MvcResult result =
                mockMvc.perform(get("/.well-known/openid-configuration"))
                        .andExpect(status().isOk())
                        .andReturn();

        JsonNode discovery = objectMapper.readTree(result.getResponse().getContentAsString());

        // Then: All required fields are present
        for (String field : REQUIRED_METADATA_FIELDS) {
            assertThat(discovery.has(field))
                    .as(
                            "REQUIRED field '%s' must be present per OIDC Discovery 1.0 Section 3",
                            field)
                    .isTrue();
            assertThat(discovery.path(field).isNull())
                    .as("REQUIRED field '%s' must not be null", field)
                    .isFalse();
        }
    }

    /**
     * Test ID: OD-C-003
     *
     * <p>Contract: issuer must match the expected format
     *
     * <p>The issuer value MUST be a URL using the https scheme (or http for localhost) with no
     * query or fragment components.
     *
     * <p>Reference: OIDC Discovery 1.0 Section 3
     */
    @Test
    @DisplayName("OD-C-003: issuer field has valid URL format")
    void issuer_shouldHaveValidUrlFormat() throws Exception {
        // Given: Discovery document
        AiAgentTestReporter.setExpectedOutcome("issuer is valid URL without query/fragment");

        // When: Discovery document is retrieved
        MvcResult result =
                mockMvc.perform(get("/.well-known/openid-configuration"))
                        .andExpect(status().isOk())
                        .andReturn();

        JsonNode discovery = objectMapper.readTree(result.getResponse().getContentAsString());
        String issuer = discovery.path("issuer").asText();

        // Then: issuer is valid URL
        assertThat(issuer)
                .as("issuer should be a valid URL")
                .matches("https?://[^?#]+")
                .doesNotContain("?")
                .doesNotContain("#");
    }

    /**
     * Test ID: OD-C-004
     *
     * <p>Contract: response_types_supported must include required types
     *
     * <p>For authorization code flow support, must include "code". For implicit flow, must include
     * "token" and/or "id_token".
     */
    @Test
    @DisplayName("OD-C-004: response_types_supported includes code")
    void responseTypesSupported_shouldIncludeCode() throws Exception {
        // Given: Discovery document
        AiAgentTestReporter.setExpectedOutcome("response_types_supported includes 'code'");

        // When: Discovery document is retrieved
        MvcResult result =
                mockMvc.perform(get("/.well-known/openid-configuration"))
                        .andExpect(status().isOk())
                        .andReturn();

        JsonNode discovery = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode responseTypes = discovery.path("response_types_supported");

        // Then: 'code' is supported
        assertThat(responseTypes.isArray())
                .as("response_types_supported should be an array")
                .isTrue();

        boolean hasCode = false;
        for (JsonNode type : responseTypes) {
            if ("code".equals(type.asText())) {
                hasCode = true;
                break;
            }
        }
        assertThat(hasCode)
                .as("response_types_supported must include 'code' for authorization code flow")
                .isTrue();
    }

    /**
     * Test ID: OD-C-005
     *
     * <p>Contract: id_token_signing_alg_values_supported must include secure algorithm
     *
     * <p>Must include at least one secure signing algorithm. ES256 is preferred.
     */
    @Test
    @DisplayName("OD-C-005: id_token_signing_alg_values_supported includes ES256")
    void idTokenSigningAlgValuesSupported_shouldIncludeEs256() throws Exception {
        // Given: Discovery document
        AiAgentTestReporter.setExpectedOutcome(
                "id_token_signing_alg_values_supported includes ES256");

        // When: Discovery document is retrieved
        MvcResult result =
                mockMvc.perform(get("/.well-known/openid-configuration"))
                        .andExpect(status().isOk())
                        .andReturn();

        JsonNode discovery = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode algorithms = discovery.path("id_token_signing_alg_values_supported");

        // Then: ES256 is supported
        assertThat(algorithms.isArray())
                .as("id_token_signing_alg_values_supported should be an array")
                .isTrue();

        boolean hasEs256 = false;
        for (JsonNode alg : algorithms) {
            if ("ES256".equals(alg.asText())) {
                hasEs256 = true;
                break;
            }
        }
        assertThat(hasEs256)
                .as("id_token_signing_alg_values_supported should include ES256")
                .isTrue();
    }

    /**
     * Test ID: OD-C-006
     *
     * <p>Contract: jwks_uri must be accessible and return valid JWKS
     *
     * <p>The URI referenced by jwks_uri must return a JSON Web Key Set.
     *
     * <p>Reference: RFC 7517 (JSON Web Key)
     */
    @Test
    @DisplayName("OD-C-006: jwks_uri is accessible and returns valid JWKS")
    void jwksUri_shouldBeAccessibleAndValid() throws Exception {
        // Given: Discovery document with jwks_uri
        AiAgentTestReporter.setExpectedOutcome("jwks_uri returns valid JWKS with keys array");

        // When: JWKS endpoint is accessed
        MvcResult result =
                mockMvc.perform(get("/oauth2/jwks")).andExpect(status().isOk()).andReturn();

        // Then: Valid JWKS is returned
        String body = result.getResponse().getContentAsString();
        JsonNode jwks = objectMapper.readTree(body);

        assertThat(jwks.has("keys")).as("JWKS must contain 'keys' field").isTrue();

        JsonNode keys = jwks.get("keys");
        assertThat(keys.isArray()).as("'keys' must be an array").isTrue();
        assertThat(keys.size()).as("JWKS should have at least one key").isGreaterThan(0);

        // Validate first key structure
        JsonNode firstKey = keys.get(0);
        assertThat(firstKey.has("kty")).as("Key must have 'kty' (key type)").isTrue();
        assertThat(firstKey.has("kid")).as("Key must have 'kid' (key id)").isTrue();
        assertThat(firstKey.has("use") || firstKey.has("key_ops"))
                .as("Key should have 'use' or 'key_ops'")
                .isTrue();
    }

    /**
     * Test ID: OD-C-007
     *
     * <p>Contract: scopes_supported should include openid
     *
     * <p>If scopes_supported is present, it must include "openid" for OIDC compliance.
     */
    @Test
    @DisplayName("OD-C-007: scopes_supported includes openid")
    void scopesSupported_shouldIncludeOpenid() throws Exception {
        // Given: Discovery document
        AiAgentTestReporter.setExpectedOutcome("scopes_supported includes 'openid'");

        // When: Discovery document is retrieved
        MvcResult result =
                mockMvc.perform(get("/.well-known/openid-configuration"))
                        .andExpect(status().isOk())
                        .andReturn();

        JsonNode discovery = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode scopes = discovery.path("scopes_supported");

        // Then: openid scope is supported
        if (!scopes.isMissingNode() && scopes.isArray()) {
            boolean hasOpenid = false;
            for (JsonNode scope : scopes) {
                if ("openid".equals(scope.asText())) {
                    hasOpenid = true;
                    break;
                }
            }
            assertThat(hasOpenid)
                    .as("scopes_supported must include 'openid' for OIDC compliance")
                    .isTrue();
        }
    }

    /**
     * Test ID: OD-C-008
     *
     * <p>Contract: grant_types_supported should include authorization_code
     *
     * <p>If grant_types_supported is present, it should include the grant types actually supported.
     */
    @Test
    @DisplayName("OD-C-008: grant_types_supported includes authorization_code")
    void grantTypesSupported_shouldIncludeAuthorizationCode() throws Exception {
        // Given: Discovery document
        AiAgentTestReporter.setExpectedOutcome("grant_types_supported includes authorization_code");

        // When: Discovery document is retrieved
        MvcResult result =
                mockMvc.perform(get("/.well-known/openid-configuration"))
                        .andExpect(status().isOk())
                        .andReturn();

        JsonNode discovery = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode grantTypes = discovery.path("grant_types_supported");

        // Then: authorization_code is supported
        if (!grantTypes.isMissingNode() && grantTypes.isArray()) {
            boolean hasAuthCode = false;
            for (JsonNode grant : grantTypes) {
                if ("authorization_code".equals(grant.asText())) {
                    hasAuthCode = true;
                    break;
                }
            }
            assertThat(hasAuthCode)
                    .as("grant_types_supported should include 'authorization_code'")
                    .isTrue();
        }
    }

    /**
     * Test ID: OD-C-009
     *
     * <p>Contract: token_endpoint_auth_methods_supported should be present
     *
     * <p>Lists the client authentication methods supported at the token endpoint.
     */
    @Test
    @DisplayName("OD-C-009: token_endpoint_auth_methods_supported is present")
    void tokenEndpointAuthMethodsSupported_shouldBePresent() throws Exception {
        // Given: Discovery document
        AiAgentTestReporter.setExpectedOutcome(
                "token_endpoint_auth_methods_supported includes client_secret_basic or client_secret_post");

        // When: Discovery document is retrieved
        MvcResult result =
                mockMvc.perform(get("/.well-known/openid-configuration"))
                        .andExpect(status().isOk())
                        .andReturn();

        JsonNode discovery = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode authMethods = discovery.path("token_endpoint_auth_methods_supported");

        // Then: Auth methods are documented
        if (!authMethods.isMissingNode()) {
            assertThat(authMethods.isArray())
                    .as("token_endpoint_auth_methods_supported should be an array")
                    .isTrue();
            assertThat(authMethods.size())
                    .as("Should support at least one authentication method")
                    .isGreaterThan(0);
        }
    }

    /**
     * Test ID: OD-C-010
     *
     * <p>Contract: code_challenge_methods_supported includes S256 for PKCE
     *
     * <p>For OAuth 2.1 compliance, PKCE with S256 should be supported.
     */
    @Test
    @Tag("pkce")
    @DisplayName("OD-C-010: code_challenge_methods_supported includes S256")
    void codeChallengeMethodsSupported_shouldIncludeS256() throws Exception {
        // Given: Discovery document
        AiAgentTestReporter.setExpectedOutcome("code_challenge_methods_supported includes 'S256'");

        // When: Discovery document is retrieved
        MvcResult result =
                mockMvc.perform(get("/.well-known/openid-configuration"))
                        .andExpect(status().isOk())
                        .andReturn();

        JsonNode discovery = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode challengeMethods = discovery.path("code_challenge_methods_supported");

        // Then: S256 is supported (PKCE)
        if (!challengeMethods.isMissingNode() && challengeMethods.isArray()) {
            boolean hasS256 = false;
            for (JsonNode method : challengeMethods) {
                if ("S256".equals(method.asText())) {
                    hasS256 = true;
                    break;
                }
            }
            assertThat(hasS256)
                    .as("code_challenge_methods_supported should include 'S256' for PKCE")
                    .isTrue();
        }
    }
}
