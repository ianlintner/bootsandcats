package com.bootsandcats.oauth2.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bootsandcats.oauth2.dto.UserInfoResponse;

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
                                        schema = @Schema(implementation = UserInfoResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        value =
                                                                """
                                                                {
                                                                    \"sub\": \"user123\",
                                                                    \"name\": \"John Doe\",
                                                                    \"email\": \"john.doe@example.com\",
                                                                    \"preferred_username\": \"johndoe\"
                                                                }
                                                                """))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Unauthorized - Invalid or missing access token",
                        content = @Content)
            })
    public ResponseEntity<UserInfoResponse> userinfo(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return ResponseEntity.status(401).build();
        }

        String sub = jwt.getSubject();
        String name = jwt.getClaim("name");
        String email = jwt.getClaim("email");
        String preferredUsername = jwt.getClaim("preferred_username");

        // Include the raw claims map for compatibility while providing typed fields
        UserInfoResponse response =
                new UserInfoResponse(sub, name, email, preferredUsername, jwt.getClaims());

        return ResponseEntity.ok(response);
    }
}
