package com.bootsandcats.profileui.security;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.server.exceptions.response.ErrorResponseProcessor;
import io.micronaut.security.authentication.AuthorizationException;
import io.micronaut.security.authentication.DefaultAuthorizationExceptionHandler;
import io.micronaut.security.config.RedirectConfiguration;
import io.micronaut.security.config.RedirectService;
import io.micronaut.security.errors.PriorToLoginPersistence;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Custom authorization exception handler that returns 401/403 for API endpoints
 * instead of redirecting to OAuth2 login.
 * <p>
 * For anonymous endpoints that match our whitelist, we allow the request through.
 * For API endpoints (non-browser), we return a 401 instead of redirecting.
 */
@Singleton
@Replaces(DefaultAuthorizationExceptionHandler.class)
public class ApiAwareAuthorizationExceptionHandler extends DefaultAuthorizationExceptionHandler {

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

    public ApiAwareAuthorizationExceptionHandler(
            ErrorResponseProcessor<?> errorResponseProcessor,
            RedirectConfiguration redirectConfiguration,
            RedirectService redirectService,
            PriorToLoginPersistence priorToLoginPersistence) {
        super(errorResponseProcessor, redirectConfiguration, redirectService, priorToLoginPersistence);
    }

    @Override
    public MutableHttpResponse<?> handle(HttpRequest request, AuthorizationException exception) {
        String path = request.getPath();

        // Check if this is an anonymous endpoint
        if (isAnonymousPath(path)) {
            LOG.debug("Anonymous endpoint {} - returning empty OK response", path);
            // For anonymous endpoints, we should never get here, but if we do,
            // don't redirect - just allow through
            return HttpResponse.ok();
        }

        // Check if this is an API call (Accept header doesn't include HTML)
        String acceptHeader = request.getHeaders().get("Accept");
        boolean isApiCall = acceptHeader != null && !acceptHeader.contains("text/html");

        // Check for Bearer token in Authorization header
        String authHeader = request.getHeaders().get("Authorization");
        boolean hasBearerToken = authHeader != null && authHeader.startsWith("Bearer ");

        if (isApiCall || hasBearerToken || path.startsWith("/api/")) {
            LOG.debug("API request {} - returning 401 instead of redirect", path);
            // For API calls, return 401 instead of redirect
            if (exception.isForbidden()) {
                return HttpResponse.status(HttpStatus.FORBIDDEN);
            }
            return HttpResponse.unauthorized();
        }

        // For browser requests to non-API endpoints, use default behavior (redirect)
        LOG.debug("Browser request {} - using default redirect behavior", path);
        return super.handle(request, exception);
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
