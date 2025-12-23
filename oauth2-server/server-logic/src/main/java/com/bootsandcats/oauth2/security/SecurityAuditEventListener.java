package com.bootsandcats.oauth2.security;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.bootsandcats.oauth2.service.SecurityAuditService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Event listener for Spring Security authentication events.
 *
 * <p>Captures authentication success, failure, and logout events and records them for audit
 * compliance purposes.
 */
@Component
public class SecurityAuditEventListener {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditEventListener.class);

    private final SecurityAuditService securityAuditService;

    public SecurityAuditEventListener(SecurityAuditService securityAuditService) {
        this.securityAuditService = securityAuditService;
    }

    /**
     * Handle successful authentication events (form login, etc.).
     *
     * <p>Note: OAuth2/federated authentication success is handled separately by
     * FederatedIdentityAuthenticationSuccessHandler to capture provider-specific details.
     *
     * @param event the authentication success event
     */
    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        Authentication authentication = event.getAuthentication();

        // Skip OAuth2 tokens - they're handled by FederatedIdentityAuthenticationSuccessHandler
        if (authentication instanceof OAuth2AuthenticationToken) {
            return;
        }

        String principal = authentication.getName();
        HttpServletRequest request = getCurrentRequest();

        Map<String, Object> details = new HashMap<>();
        details.put("authenticationType", authentication.getClass().getSimpleName());
        if (authentication.getAuthorities() != null) {
            details.put(
                    "authorities",
                    authentication.getAuthorities().stream().map(Object::toString).toList());
        }

        // Add session/request details if available
        if (authentication.getDetails() instanceof WebAuthenticationDetails webDetails) {
            details.put("remoteAddress", webDetails.getRemoteAddress());
            details.put("sessionId", webDetails.getSessionId());
        }

        securityAuditService.recordLoginSuccess(principal, request, null, details);
        log.debug("Recorded authentication success for principal: {}", principal);
    }

    /**
     * Handle authentication failure events.
     *
     * @param event the authentication failure event
     */
    @EventListener
    public void onAuthenticationFailure(AbstractAuthenticationFailureEvent event) {
        Authentication authentication = event.getAuthentication();
        Throwable exception = event.getException();

        String principal =
                authentication != null && authentication.getName() != null
                        ? authentication.getName()
                        : "unknown";
        HttpServletRequest request = getCurrentRequest();

        Map<String, Object> details = new HashMap<>();
        details.put("exceptionType", exception.getClass().getSimpleName());
        if (authentication != null) {
            details.put("authenticationType", authentication.getClass().getSimpleName());
        }

        // Add request metadata for honeypot/audit correlation (do not include secrets)
        if (request != null) {
            details.put("httpMethod", request.getMethod());
            details.put("requestUri", request.getRequestURI());
            String userAgent = request.getHeader("User-Agent");
            if (userAgent != null) {
                details.put("userAgent", userAgent);
            }
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null) {
                details.put("xForwardedFor", forwardedFor);
            }
        }

        // Add session/request details if available
        if (authentication != null
                && authentication.getDetails() instanceof WebAuthenticationDetails webDetails) {
            details.put("remoteAddress", webDetails.getRemoteAddress());
            details.put("sessionId", webDetails.getSessionId());
        }

        // Add OAuth2 specific error details
        if (exception instanceof OAuth2AuthenticationException oauth2Exception) {
            details.put("errorCode", oauth2Exception.getError().getErrorCode());
            if (oauth2Exception.getError().getDescription() != null) {
                details.put("errorDescription", oauth2Exception.getError().getDescription());
            }
        }

        String errorMessage = exception.getMessage();
        securityAuditService.recordLoginFailure(principal, request, errorMessage, details);
        log.debug("Recorded authentication failure for principal: {}", principal);
    }

    /**
     * Handle logout success events.
     *
     * @param event the logout success event
     */
    @EventListener
    public void onLogoutSuccess(LogoutSuccessEvent event) {
        Authentication authentication = event.getAuthentication();
        String principal = authentication != null ? authentication.getName() : "unknown";
        HttpServletRequest request = getCurrentRequest();

        Map<String, Object> details = new HashMap<>();
        if (authentication != null) {
            details.put("authenticationType", authentication.getClass().getSimpleName());
        }

        securityAuditService.recordLogout(principal, request, details);
        log.debug("Recorded logout for principal: {}", principal);
    }

    /**
     * Get the current HTTP request from the request context.
     *
     * @return the current HttpServletRequest or null if not available
     */
    private HttpServletRequest getCurrentRequest() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            return attributes.getRequest();
        } catch (IllegalStateException e) {
            // No request context available (e.g., async processing)
            return null;
        }
    }
}
