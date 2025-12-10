package com.bootsandcats.profileui.security;

import java.util.Set;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.filter.ServerFilterPhase;
import jakarta.inject.Singleton;

/**
 * Filter that marks requests to anonymous endpoints so they bypass OAuth2 login redirect. This
 * filter runs with high priority (ORDER = -200) to set the attribute before the OAuth2 login
 * handler can redirect the request.
 */
@Filter("/**")
@Singleton
public class AnonymousEndpointFilter implements HttpServerFilter {

    private static final Logger LOG = LoggerFactory.getLogger(AnonymousEndpointFilter.class);

    /** Attribute key to mark anonymous requests. */
    public static final String ANONYMOUS_REQUEST_ATTR = "io.micronaut.security.ANONYMOUS_REQUEST";

    /** List of anonymous endpoint paths (exact match or prefix). */
    private static final Set<String> ANONYMOUS_PATHS =
            Set.of("/api/status", "/actuator/health", "/actuator/prometheus", "/health");

    /** List of anonymous path prefixes. */
    private static final Set<String> ANONYMOUS_PATH_PREFIXES = Set.of("/public/", "/actuator/");

    @Override
    public int getOrder() {
        // Run before security filters (SECURITY = 0, this runs at -200)
        return ServerFilterPhase.SECURITY.order() - 200;
    }

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(
            HttpRequest<?> request, ServerFilterChain chain) {
        String path = request.getPath();

        if (isAnonymousPath(path)) {
            LOG.debug("Anonymous endpoint detected: {}", path);
            // Mark the request as anonymous
            request.setAttribute(ANONYMOUS_REQUEST_ATTR, Boolean.TRUE);
        }

        return chain.proceed(request);
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
