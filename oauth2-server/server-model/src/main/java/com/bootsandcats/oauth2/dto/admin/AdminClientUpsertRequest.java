package com.bootsandcats.oauth2.dto.admin;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Create/update request for OAuth2 clients managed by admins. */
public record AdminClientUpsertRequest(
        @NotBlank String clientId,
        String clientName,
        /** Optional: if provided on update, rotates the secret. */
        String clientSecret,
        @NotNull List<String> authorizationGrantTypes,
        @NotNull List<String> clientAuthenticationMethods,
        List<String> redirectUris,
        List<String> postLogoutRedirectUris,
        @NotNull List<String> scopes,
        boolean requireProofKey,
        boolean requireAuthorizationConsent,
        boolean enabled,
        String notes) {}
