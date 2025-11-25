package com.bootsandcats.oauth2.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** User Info Controller for OIDC userinfo endpoint. */
@RestController
@Tag(name = "User Info", description = "OpenID Connect UserInfo endpoint")
public class UserInfoController {

    /**
     * Get user information for authenticated user.
     *
     * @param jwt JWT token from authentication
     * @return User information claims
     */
    @GetMapping("/userinfo")
    @Timed(value = "oauth2.userinfo", description = "Time taken for userinfo request")
    @Operation(
            summary = "Get user information",
            description =
                    "Returns claims about the authenticated user. Requires a valid access token "
                            + "with 'openid' scope. This endpoint is part of the OpenID Connect specification.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "User information retrieved successfully",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = Map.class),
                                        examples =
                                                @ExampleObject(
                                                        value =
                                                                """
                                                                {
                                                                    "sub": "user123",
                                                                    "name": "John Doe",
                                                                    "email": "john.doe@example.com",
                                                                    "preferred_username": "johndoe"
                                                                }
                                                                """))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Unauthorized - Invalid or missing access token",
                        content = @Content)
            })
    public ResponseEntity<Map<String, Object>> userinfo(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return ResponseEntity.status(401).build();
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", jwt.getSubject());

        if (jwt.getClaim("name") != null) {
            claims.put("name", jwt.getClaim("name"));
        }
        if (jwt.getClaim("email") != null) {
            claims.put("email", jwt.getClaim("email"));
        }
        if (jwt.getClaim("preferred_username") != null) {
            claims.put("preferred_username", jwt.getClaim("preferred_username"));
        }

        return ResponseEntity.ok(claims);
    }
}
