package com.bootsandcats.oauth2.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.micrometer.core.annotation.Timed;

/** User Info Controller for OIDC userinfo endpoint. */
@RestController
public class UserInfoController {

    /**
     * Get user information for authenticated user.
     *
     * @param jwt JWT token from authentication
     * @return User information claims
     */
    @GetMapping("/userinfo")
    @Timed(value = "oauth2.userinfo", description = "Time taken for userinfo request")
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
