package com.bootsandcats.profileui.security;

import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.security.oauth2.client.DefaultOpenIdProviderMetadata;
import io.micronaut.security.oauth2.endpoint.authorization.request.DefaultAuthorizationRedirectHandler;
import io.micronaut.security.oauth2.endpoint.authorization.request.OauthAuthorizationRequest;
import io.micronaut.security.oauth2.endpoint.authorization.state.StateFactory;
import io.micronaut.security.handlers.UnauthorizedRejectionUriProvider;
import jakarta.inject.Singleton;
import java.util.Optional;

/**
 * Disables OAuth2 authorization redirects for unauthorized requests.
 * Instead of redirecting to the OAuth2 authorization endpoint, this provider
 * returns an empty Optional, causing Micronaut to return a 401 Unauthorized response.
 */
@Singleton
@Primary
public class NoRedirectUnauthorizedRejectionUriProvider implements UnauthorizedRejectionUriProvider {

    @Override
    public Optional<String> getUnauthorizedRedirectUri(HttpRequest<?> request) {
        // Return empty to prevent redirect - will result in 401 Unauthorized
        return Optional.empty();
    }
}
