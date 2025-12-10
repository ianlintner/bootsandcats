package com.bootsandcats.profileui.security;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.security.authentication.AuthorizationException;
import io.micronaut.security.authentication.DefaultAuthorizationExceptionHandler;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

/**
 * Custom authorization exception handler that returns 401/403 for API endpoints
 * instead of redirecting to OAuth2 login.
 * <p>
 * This handler is designed for bearer token authentication (JWT) and does not
 * require OAuth2 login redirect functionality.
 */
@Singleton
@Produces
@Replaces(DefaultAuthorizationExceptionHandler.class)
public class ApiAwareAuthorizationExceptionHandler
        implements io.micronaut.http.server.exceptions.ExceptionHandler<AuthorizationException, MutableHttpResponse<?>> {

    private static final Logger LOG = LoggerFactory.getLogger(ApiAwareAuthorizationExceptionHandler.class);

    /**
     * Anonymous paths that should never trigger OAuth2 redirect.
     */
    private static final Set<String> ANONYMOUS_PATHS = Set.of(
            "/api/status",
            "/actuator/health",
            "/actuator/prometheus",
            "/health"
    );

    /**
     * Anonymous path prefixes.
     */
    private static final Set<String> ANONYMOUS_PATH_PREFIXES = Set.of(
            "/public/",
            "/actuator/"
    );

    @Override
    public MutableHttpResponse<?> handle(HttpRequest request, AuthorizationException exception) {
        String path = request.getPath();

        // Check if this is an anonymous endpoint - should not be rejected
        if (isAnonymousPath(path)) {
            LOG.debug("Anonymous endpoint {} - returning empty OK response", path);
            return HttpResponse.ok();
        }

        boolean forbidden = exception.isForbidden();
        LOG.debug("Rejecting request to {} - forbidden={}", path, forbidden);
        
        if (forbidden) {
            return HttpResponse.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "status", HttpStatus.FORBIDDEN.getCode(),
                            "error", "Forbidden",
                            "message", "Access Denied"));
        }
        
        return HttpResponse.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of(
                        "status", HttpStatus.UNAUTHORIZED.getCode(),
                        "error", "Unauthorized",
                        "message", "Authentication required"));
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
