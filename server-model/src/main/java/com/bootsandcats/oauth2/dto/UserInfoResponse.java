package com.bootsandcats.oauth2.dto;

import java.util.Map;

/** User info response DTO aligned with OIDC userinfo claims. */
public record UserInfoResponse(
        String sub,
        String name,
        String email,
        String preferredUsername,
        Map<String, Object> claims) {}
