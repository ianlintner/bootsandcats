package com.bootsandcats.profileui.security;

import java.util.Set;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.security.rules.SecurityRuleResult;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

/**
 * Security rule that allows anonymous access to specific endpoints. This rule runs BEFORE the
 * OAuth2 login filter by having a higher order (lower number).
 */
@Singleton
public class AnonymousEndpointSecurityRule implements SecurityRule<HttpRequest<?>> {

    private static final Logger LOG = LoggerFactory.getLogger(AnonymousEndpointSecurityRule.class);

    /** Anonymous paths (exact match). */
    private static final Set<String> ANONYMOUS_PATHS =
            Set.of(
                    "/api/status",
                    "/actuator/health",
                    "/actuator/prometheus",
                    "/health",
                    "/profile-app.js",
                    "/favicon.ico");

    /** Anonymous path prefixes. */
    private static final Set<String> ANONYMOUS_PATH_PREFIXES = Set.of("/public/", "/actuator/");

    @Override
    public int getOrder() {
        // Run before all other security rules (default is 0, we want to run first)
        return HIGHEST_PRECEDENCE;
    }

    @Override
    public Publisher<SecurityRuleResult> check(
            HttpRequest<?> request, @Nullable Authentication authentication) {
        String path = request.getPath();

        if (isAnonymousPath(path)) {
            LOG.debug("Allowing anonymous access to: {}", path);
            return Mono.just(SecurityRuleResult.ALLOWED);
        }

        // Let other rules handle it
        return Mono.just(SecurityRuleResult.UNKNOWN);
    }

    private boolean isAnonymousPath(String path) {
        // Check exact matches
        if (ANONYMOUS_PATHS.contains(path)) {
            return true;
        }

        // Check prefix matches
        for (String prefix : ANONYMOUS_PATH_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }

        return false;
    }
}
