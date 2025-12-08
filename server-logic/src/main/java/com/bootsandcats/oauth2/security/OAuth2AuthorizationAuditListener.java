package com.bootsandcats.oauth2.security;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenIntrospectionAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenRevocationAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.bootsandcats.oauth2.service.SecurityAuditService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Event listener for OAuth2 Authorization Server specific authentication events.
 *
 * <p>Captures token issuance, refresh, revocation, and introspection events for audit compliance.
 */
@Component
public class OAuth2AuthorizationAuditListener
        implements ApplicationListener<AuthenticationSuccessEvent> {

    private static final Logger log =
            LoggerFactory.getLogger(OAuth2AuthorizationAuditListener.class);

    private final SecurityAuditService securityAuditService;

    public OAuth2AuthorizationAuditListener(SecurityAuditService securityAuditService) {
        this.securityAuditService = securityAuditService;
    }

    @Override
    public void onApplicationEvent(AuthenticationSuccessEvent event) {
        Authentication authentication = event.getAuthentication();
        HttpServletRequest request = getCurrentRequest();

        if (authentication instanceof OAuth2AccessTokenAuthenticationToken accessTokenAuth) {
            handleAccessTokenAuthentication(accessTokenAuth, request);
        } else if (authentication
                instanceof OAuth2TokenRevocationAuthenticationToken revocationAuth) {
            handleTokenRevocation(revocationAuth, request);
        } else if (authentication
                instanceof OAuth2TokenIntrospectionAuthenticationToken introspectionAuth) {
            handleTokenIntrospection(introspectionAuth, request);
        } else if (authentication instanceof OAuth2ClientAuthenticationToken clientAuth) {
            handleClientAuthentication(clientAuth, request);
        }
    }

    private void handleAccessTokenAuthentication(
            OAuth2AccessTokenAuthenticationToken authToken, HttpServletRequest request) {
        String clientId =
                authToken.getRegisteredClient() != null
                        ? authToken.getRegisteredClient().getClientId()
                        : null;
        String principal = authToken.getName();

        // Determine grant type from the principal authentication
        String grantType = "unknown";
        if (authToken.getPrincipal() instanceof Authentication principalAuth) {
            grantType = principalAuth.getClass().getSimpleName().replace("AuthenticationToken", "");
        }

        Set<String> scopes =
                authToken.getAccessToken() != null
                        ? authToken.getAccessToken().getScopes()
                        : Set.of();
        String scopesStr = String.join(" ", scopes);

        Map<String, Object> details = new HashMap<>();
        if (authToken.getAccessToken() != null) {
            details.put("tokenType", authToken.getAccessToken().getTokenType().getValue());
            if (authToken.getAccessToken().getIssuedAt() != null) {
                details.put("issuedAt", authToken.getAccessToken().getIssuedAt().toString());
            }
            if (authToken.getAccessToken().getExpiresAt() != null) {
                details.put("expiresAt", authToken.getAccessToken().getExpiresAt().toString());
            }
        }
        if (authToken.getRefreshToken() != null) {
            details.put("refreshTokenIssued", true);
        }

        securityAuditService.recordAccessTokenIssued(
                principal, clientId, request, grantType, scopesStr, details);
        log.debug(
                "Recorded access token issuance for client: {}, principal: {}",
                clientId,
                principal);
    }

    private void handleClientAuthentication(
            OAuth2ClientAuthenticationToken authToken, HttpServletRequest request) {
        String clientId =
                authToken.getRegisteredClient() != null
                        ? authToken.getRegisteredClient().getClientId()
                        : authToken.getName();

        Map<String, Object> details = new HashMap<>();
        if (authToken.getClientAuthenticationMethod() != null) {
            details.put(
                    "authenticationMethod", authToken.getClientAuthenticationMethod().getValue());
        }

        securityAuditService.recordClientAuthenticationSuccess(
                clientId,
                request,
                authToken.getClientAuthenticationMethod() != null
                        ? authToken.getClientAuthenticationMethod().getValue()
                        : "unknown",
                details);
        log.debug("Recorded client authentication for client: {}", clientId);
    }

    private void handleTokenRevocation(
            OAuth2TokenRevocationAuthenticationToken authToken, HttpServletRequest request) {
        String clientId = authToken.getName();

        Map<String, Object> details = new HashMap<>();
        if (authToken.getTokenTypeHint() != null) {
            details.put("tokenTypeHint", authToken.getTokenTypeHint());
        }

        String tokenType =
                authToken.getTokenTypeHint() != null ? authToken.getTokenTypeHint() : "unknown";
        securityAuditService.recordTokenRevoked(clientId, clientId, request, tokenType, details);
        log.debug("Recorded token revocation for client: {}", clientId);
    }

    private void handleTokenIntrospection(
            OAuth2TokenIntrospectionAuthenticationToken authToken, HttpServletRequest request) {
        String clientId = authToken.getName();

        boolean isActive =
                authToken.getTokenClaims() != null && authToken.getTokenClaims().isActive();

        Map<String, Object> details = new HashMap<>();
        details.put("tokenActive", isActive);
        if (authToken.getTokenClaims() != null && authToken.getTokenClaims().getSubject() != null) {
            details.put("tokenSubject", authToken.getTokenClaims().getSubject());
        }

        securityAuditService.recordTokenIntrospection(clientId, request, isActive, details);
        log.debug("Recorded token introspection for client: {}, active: {}", clientId, isActive);
    }

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
