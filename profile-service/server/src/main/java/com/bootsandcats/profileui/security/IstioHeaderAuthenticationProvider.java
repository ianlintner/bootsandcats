package com.bootsandcats.profileui.security;

import java.util.*;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.http.HttpRequest;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.authentication.ServerAuthentication;
import io.micronaut.security.filters.AuthenticationFetcher;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

/**
 * Authentication provider that reads JWT claims from Istio-injected headers.
 *
 * <p>Istio's EnvoyFilter extracts JWT claims and adds them as headers:
 *
 * <ul>
 *   <li>x-jwt-sub - Subject (user ID)
 *   <li>x-jwt-username - Preferred username
 *   <li>x-jwt-email - Email address
 *   <li>x-jwt-name - Display name
 *   <li>x-jwt-scope - Space-separated scopes
 * </ul>
 *
 * <p>JWT validation is handled by Istio, so this provider only reads the headers.
 */
@Singleton
public class IstioHeaderAuthenticationProvider implements AuthenticationFetcher<HttpRequest<?>> {

    private static final Logger LOG =
            LoggerFactory.getLogger(IstioHeaderAuthenticationProvider.class);

    private static final String HEADER_SUB = "x-jwt-sub";
    private static final String HEADER_USERNAME = "x-jwt-username";
    private static final String HEADER_EMAIL = "x-jwt-email";
    private static final String HEADER_NAME = "x-jwt-name";
    private static final String HEADER_SCOPE = "x-jwt-scope";

    @Override
    public Publisher<Authentication> fetchAuthentication(HttpRequest<?> request) {
        String subject = request.getHeaders().get(HEADER_SUB);

        if (subject == null || subject.isBlank()) {
            LOG.trace("No x-jwt-sub header found, request is unauthenticated");
            return Mono.empty();
        }

        LOG.debug("Found Istio JWT headers for subject: {}", subject);

        // Extract all claims from headers
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", subject);

        String username = request.getHeaders().get(HEADER_USERNAME);
        if (username != null && !username.isBlank()) {
            attributes.put("preferred_username", username);
        }

        String email = request.getHeaders().get(HEADER_EMAIL);
        if (email != null && !email.isBlank()) {
            attributes.put("email", email);
        }

        String name = request.getHeaders().get(HEADER_NAME);
        if (name != null && !name.isBlank()) {
            attributes.put("name", name);
        }

        // Parse scopes from header
        List<String> roles = new ArrayList<>();
        String scopeHeader = request.getHeaders().get(HEADER_SCOPE);
        if (scopeHeader != null && !scopeHeader.isBlank()) {
            String[] scopes = scopeHeader.split(" ");
            for (String scope : scopes) {
                if (!scope.isBlank()) {
                    roles.add(scope.trim());
                }
            }
            attributes.put("scope", scopeHeader);
        }

        Authentication authentication = new ServerAuthentication(subject, roles, attributes);

        LOG.debug("Created authentication for {} with {} scopes", subject, roles.size());
        return Mono.just(authentication);
    }
}
