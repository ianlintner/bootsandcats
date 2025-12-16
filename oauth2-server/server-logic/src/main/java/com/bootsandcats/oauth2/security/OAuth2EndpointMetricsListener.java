package com.bootsandcats.oauth2.security;

import org.springframework.context.ApplicationListener;
import org.springframework.lang.Nullable;
import org.springframework.security.authentication.event.AbstractAuthenticationEvent;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenIntrospectionAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenRevocationAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.bootsandcats.oauth2.service.OAuth2MetricsService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Publishes low-cardinality OAuth2 endpoint outcome metrics based on Spring Security authentication
 * events.
 *
 * <p>This intentionally avoids high-cardinality labels (client_id, username, subject, token values,
 * IP addresses). The goal is SLO-style observability and operational dashboards.
 */
@Component
public class OAuth2EndpointMetricsListener
        implements ApplicationListener<AbstractAuthenticationEvent> {

    private final OAuth2MetricsService metricsService;

    public OAuth2EndpointMetricsListener(OAuth2MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @Override
    public void onApplicationEvent(AbstractAuthenticationEvent event) {
        HttpServletRequest request = getCurrentRequest();

        if (event instanceof AuthenticationSuccessEvent successEvent) {
            recordSuccess(successEvent, request);
            return;
        }

        if (event instanceof AbstractAuthenticationFailureEvent failureEvent) {
            recordFailure(failureEvent, request);
        }
    }

    private void recordSuccess(
            AuthenticationSuccessEvent event, @Nullable HttpServletRequest request) {
        String endpoint = resolveEndpoint(event, request);
        String grantType = resolveGrantType(endpoint, request);
        metricsService.recordEndpointRequest(endpoint, "success", grantType, "none");
    }

    private void recordFailure(
            AbstractAuthenticationFailureEvent event, @Nullable HttpServletRequest request) {
        String endpoint = resolveEndpoint(event, request);
        String grantType = resolveGrantType(endpoint, request);
        String error = resolveError(event);
        metricsService.recordEndpointRequest(endpoint, "failure", grantType, error);
    }

    private String resolveEndpoint(
            AbstractAuthenticationEvent event, @Nullable HttpServletRequest request) {
        var authentication = event.getAuthentication();

        if (authentication instanceof OAuth2AccessTokenAuthenticationToken) {
            return "token";
        }
        if (authentication instanceof OAuth2AuthorizationCodeRequestAuthenticationToken) {
            return "authorize";
        }
        if (authentication instanceof OAuth2TokenIntrospectionAuthenticationToken) {
            return "introspect";
        }
        if (authentication instanceof OAuth2TokenRevocationAuthenticationToken) {
            return "revoke";
        }

        // Client authentication happens inside multiple endpoints; try to map to the request URI.
        if (authentication instanceof OAuth2ClientAuthenticationToken) {
            String fromPath = endpointFromRequestPath(request);
            return fromPath != null ? fromPath : "client_auth";
        }

        // Fallback: attempt to infer from request path.
        String fromPath = endpointFromRequestPath(request);
        return fromPath != null ? fromPath : "unknown";
    }

    @Nullable
    private String endpointFromRequestPath(@Nullable HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String path = request.getRequestURI();
        if (path == null) {
            return null;
        }
        if (path.startsWith("/oauth2/token")) {
            return "token";
        }
        if (path.startsWith("/oauth2/authorize")) {
            return "authorize";
        }
        if (path.startsWith("/oauth2/introspect")) {
            return "introspect";
        }
        if (path.startsWith("/oauth2/revoke")) {
            return "revoke";
        }
        if (path.startsWith("/userinfo")) {
            return "userinfo";
        }
        if (path.startsWith("/.well-known/openid-configuration")) {
            return "discovery";
        }
        if (path.startsWith("/.well-known/jwks.json") || path.startsWith("/oauth2/jwks")) {
            return "jwks";
        }
        return null;
    }

    private String resolveGrantType(String endpoint, @Nullable HttpServletRequest request) {
        if (!"token".equals(endpoint) || request == null) {
            return "none";
        }
        // grant_type is a bounded set; safe for labeling.
        String grantType = request.getParameter("grant_type");
        return grantType != null && !grantType.isBlank() ? grantType : "unknown";
    }

    private String resolveError(AbstractAuthenticationFailureEvent event) {
        var exception = event.getException();
        if (exception instanceof OAuth2AuthenticationException oauth2Exception
                && oauth2Exception.getError() != null
                && oauth2Exception.getError().getErrorCode() != null
                && !oauth2Exception.getError().getErrorCode().isBlank()) {
            return oauth2Exception.getError().getErrorCode();
        }
        return exception != null ? "exception" : "unknown";
    }

    @Nullable
    private HttpServletRequest getCurrentRequest() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            return attributes.getRequest();
        } catch (IllegalStateException e) {
            return null;
        }
    }
}
