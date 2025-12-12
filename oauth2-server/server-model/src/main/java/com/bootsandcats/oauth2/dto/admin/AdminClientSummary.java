package com.bootsandcats.oauth2.dto.admin;

import java.util.List;

/** Summary view of an OAuth2 registered client for admin management. */
public record AdminClientSummary(
        String clientId,
        String clientName,
        boolean enabled,
        boolean system,
        List<String> scopes,
        List<String> authorizationGrantTypes,
        List<String> clientAuthenticationMethods,
        List<String> redirectUris,
        List<String> postLogoutRedirectUris,
        boolean requireProofKey,
        boolean requireAuthorizationConsent,
        String notes) {}
