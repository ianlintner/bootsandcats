package com.bootsandcats.oauth2.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Documentation-only controller for OAuth2 endpoints.
 *
 * <p>This controller provides OpenAPI/Swagger documentation for the Spring Authorization Server's
 * built-in OAuth2 endpoints. The actual implementation is provided by Spring Security's
 * Authorization Server framework.
 *
 * <p>Note: The @RestController annotation is not needed as these methods are never invoked. They
 * exist solely to generate OpenAPI documentation for the framework-provided endpoints.
 */
@Tag(
        name = "OAuth2 Endpoints",
        description =
                "Core OAuth2 authorization server endpoints for token issuance, introspection, and revocation")
public class OAuth2EndpointsDocumentation {

    /**
     * Token endpoint for obtaining access tokens.
     *
     * <p>Supports multiple grant types:
     *
     * <ul>
     *   <li>authorization_code - Exchange authorization code for tokens
     *   <li>refresh_token - Refresh an access token
     *   <li>client_credentials - Machine-to-machine authentication
     * </ul>
     *
     * <p><strong>Implementation Note:</strong> This endpoint is implemented by Spring Authorization
     * Server's OAuth2TokenEndpointFilter. This method exists only for documentation purposes.
     */
    @Operation(
            summary = "OAuth2 Token Endpoint",
            description =
                    """
            Exchange authorization code, refresh token, or client credentials for access tokens.
            
            ### Grant Types:
            
            **Authorization Code:**
            - Exchange authorization code for access token
            - Requires: code, redirect_uri, code_verifier (for PKCE)
            
            **Refresh Token:**
            - Obtain new access token using refresh token
            - Requires: refresh_token
            
            **Client Credentials:**
            - Machine-to-machine authentication
            - Requires: client credentials in Authorization header
            
            ### Authentication:
            - Confidential clients: Use HTTP Basic Auth with client_id:client_secret
            - Public clients: Send client_id in request body
            """,
            security = {@SecurityRequirement(name = "oauth2")})
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successfully issued tokens",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = TokenResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "Token Response",
                                                        value =
                                                                """
                        {
                          "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
                          "token_type": "Bearer",
                          "expires_in": 3600,
                          "refresh_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
                          "scope": "openid profile email"
                        }
                        """))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Invalid request",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        examples =
                                                @ExampleObject(
                                                        value =
                                                                """
                        {
                          "error": "invalid_grant",
                          "error_description": "Invalid authorization code"
                        }
                        """))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Invalid client credentials",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        examples =
                                                @ExampleObject(
                                                        value =
                                                                """
                        {
                          "error": "invalid_client",
                          "error_description": "Client authentication failed"
                        }
                        """)))
            })
    @PostMapping("/oauth2/token")
    public void token(
            @Parameter(
                            description = "OAuth2 grant type",
                            required = true,
                            schema =
                                    @Schema(
                                            allowableValues = {
                                                "authorization_code",
                                                "refresh_token",
                                                "client_credentials"
                                            }))
                    @RequestParam("grant_type")
                    String grantType,
            @Parameter(description = "Authorization code (for authorization_code grant)")
                    @RequestParam(required = false)
                    String code,
            @Parameter(description = "Redirect URI (must match authorization request)")
                    @RequestParam(value = "redirect_uri", required = false)
                    String redirectUri,
            @Parameter(description = "PKCE code verifier (for public clients)")
                    @RequestParam(value = "code_verifier", required = false)
                    String codeVerifier,
            @Parameter(description = "Refresh token (for refresh_token grant)")
                    @RequestParam(value = "refresh_token", required = false)
                    String refreshToken,
            @Parameter(description = "Requested scopes (space-separated)")
                    @RequestParam(required = false)
                    String scope,
            @Parameter(description = "Client ID (for public clients)")
                    @RequestParam(value = "client_id", required = false)
                    String clientId) {
        // Implementation provided by Spring Authorization Server
        throw new UnsupportedOperationException("This method is for documentation only");
    }

    /**
     * Token introspection endpoint for validating access tokens.
     *
     * <p><strong>Implementation Note:</strong> This endpoint is implemented by Spring Authorization
     * Server's OAuth2TokenIntrospectionEndpointFilter.
     */
    @Operation(
            summary = "OAuth2 Token Introspection",
            description =
                    """
            Validate and retrieve information about an access token.
            
            Returns token metadata including:
            - Active status
            - Expiration time
            - Token type
            - Scopes
            - Subject (user)
            - Client ID
            
            ### Authentication:
            Requires client credentials in Authorization header (HTTP Basic Auth).
            """,
            security = {@SecurityRequirement(name = "oauth2")})
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Token introspection response",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        examples = {
                                            @ExampleObject(
                                                    name = "Active Token",
                                                    value =
                                                            """
                                {
                                  "active": true,
                                  "scope": "openid profile email",
                                  "client_id": "demo-client",
                                  "username": "user",
                                  "token_type": "Bearer",
                                  "exp": 1735849200,
                                  "iat": 1735845600,
                                  "sub": "user",
                                  "aud": ["demo-client"]
                                }
                                """),
                                            @ExampleObject(
                                                    name = "Inactive Token",
                                                    value =
                                                            """
                                {
                                  "active": false
                                }
                                """)
                                        })),
                @ApiResponse(responseCode = "401", description = "Invalid client credentials")
            })
    @PostMapping("/oauth2/introspect")
    public void introspect(
            @Parameter(description = "Access token to introspect", required = true)
                    @RequestParam
                    String token) {
        // Implementation provided by Spring Authorization Server
        throw new UnsupportedOperationException("This method is for documentation only");
    }

    /**
     * Token revocation endpoint for invalidating access and refresh tokens.
     *
     * <p><strong>Implementation Note:</strong> This endpoint is implemented by Spring Authorization
     * Server's OAuth2TokenRevocationEndpointFilter.
     */
    @Operation(
            summary = "OAuth2 Token Revocation",
            description =
                    """
            Revoke an access token or refresh token.
            
            Once revoked, the token can no longer be used for authentication or authorization.
            
            ### Authentication:
            Requires client credentials in Authorization header (HTTP Basic Auth).
            
            ### Token Type Hint:
            Optional parameter to indicate the type of token being revoked:
            - access_token
            - refresh_token
            """,
            security = {@SecurityRequirement(name = "oauth2")})
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Token successfully revoked or was already invalid"),
                @ApiResponse(responseCode = "401", description = "Invalid client credentials")
            })
    @PostMapping("/oauth2/revoke")
    public void revoke(
            @Parameter(description = "Token to revoke", required = true) @RequestParam String token,
            @Parameter(
                            description = "Hint about the type of token",
                            schema =
                                    @Schema(
                                            allowableValues = {"access_token", "refresh_token"}))
                    @RequestParam(value = "token_type_hint", required = false)
                    String tokenTypeHint) {
        // Implementation provided by Spring Authorization Server
        throw new UnsupportedOperationException("This method is for documentation only");
    }

    /** Response schema for token endpoint. */
    @Schema(description = "OAuth2 token response")
    public static class TokenResponse {
        @Schema(description = "The access token", example = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...")
        public String access_token;

        @Schema(description = "Token type", example = "Bearer")
        public String token_type;

        @Schema(description = "Token expiration time in seconds", example = "3600")
        public Integer expires_in;

        @Schema(
                description = "Refresh token (if requested)",
                example = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...")
        public String refresh_token;

        @Schema(description = "Granted scopes", example = "openid profile email")
        public String scope;

        @Schema(description = "ID token (for OpenID Connect)", example = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...")
        public String id_token;
    }
}
