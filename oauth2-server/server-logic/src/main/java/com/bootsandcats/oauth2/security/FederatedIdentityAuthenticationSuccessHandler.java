package com.bootsandcats.oauth2.security;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Component;

import com.bootsandcats.oauth2.model.User;
import com.bootsandcats.oauth2.repository.UserRepository;
import com.bootsandcats.oauth2.service.SecurityAuditService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Handles successful federated identity (GitHub, Google, etc.) authentication. After federated
 * login completes, this handler: 1. Creates or updates the user record in the database 2. Records
 * the authentication event for audit compliance 3. Redirects back to the original saved request
 * (typically /oauth2/authorize for the OAuth2 flow)
 */
@Component
public class FederatedIdentityAuthenticationSuccessHandler
        extends SavedRequestAwareAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final SecurityAuditService securityAuditService;
    private final DenyListService denyListService;
    private final RequestCache requestCache = new HttpSessionRequestCache();

    public FederatedIdentityAuthenticationSuccessHandler(
            UserRepository userRepository,
            SecurityAuditService securityAuditService,
            DenyListService denyListService) {
        this.userRepository = userRepository;
        this.securityAuditService = securityAuditService;
        this.denyListService = denyListService;
        // Ensure we use the session-based request cache
        setRequestCache(requestCache);
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws ServletException, IOException {
        if (authentication instanceof OAuth2AuthenticationToken) {
            OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
            OAuth2User oauthUser = oauthToken.getPrincipal();
            String provider = oauthToken.getAuthorizedClientRegistrationId();
            String providerId = oauthUser.getName();

            // Different providers might use different attributes for ID/Email
            // GitHub uses 'id' (integer) as name, but we might want login
            // Google uses 'sub'

            String email = oauthUser.getAttribute("email");
            String name = oauthUser.getAttribute("name");
            String picture = oauthUser.getAttribute("avatar_url"); // GitHub
            if (picture == null) {
                picture = oauthUser.getAttribute("picture"); // Google
            }

            String username = oauthUser.getAttribute("login"); // GitHub
            if (username == null) {
                username = email; // Fallback
            }

            // Enforce deny-list policy before creating/updating local user record
            try {
                denyListService.assertNotDenied(provider, email, username, providerId);
            } catch (LoginDeniedException denied) {
                Map<String, Object> auditDetails = new HashMap<>();
                auditDetails.put("email", email);
                auditDetails.put("username", username);
                auditDetails.put("providerId", providerId);
                auditDetails.put(
                        "denyRuleId",
                        denied.getDenyRule() != null ? denied.getDenyRule().getId() : null);
                auditDetails.put(
                        "denyRuleProvider",
                        denied.getDenyRule() != null ? denied.getDenyRule().getProvider() : null);
                auditDetails.put(
                        "denyRuleMatchField",
                        denied.getDenyRule() != null ? denied.getDenyRule().getMatchField() : null);
                auditDetails.put(
                        "denyRuleMatchType",
                        denied.getDenyRule() != null ? denied.getDenyRule().getMatchType() : null);

                securityAuditService.recordFederatedLoginDenied(
                        username != null ? username : email,
                        provider,
                        request,
                        denied.getMessage(),
                        auditDetails);

                SecurityContextHolder.clearContext();
                var session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                getRedirectStrategy().sendRedirect(request, response, "/login?denied=1");
                return;
            }

            User user =
                    userRepository
                            .findByProviderAndProviderId(provider, providerId)
                            .orElseGet(
                                    () -> {
                                        User newUser = new User();
                                        newUser.setProvider(provider);
                                        newUser.setProviderId(providerId);
                                        return newUser;
                                    });

            user.setEmail(email);
            user.setName(name);
            user.setPictureUrl(picture);
            user.setUsername(username);
            user.setLastLogin(Instant.now());

            userRepository.save(user);

            // Record successful federated login for audit compliance
            Map<String, Object> auditDetails = new HashMap<>();
            auditDetails.put("email", email);
            auditDetails.put("username", username);
            auditDetails.put("providerId", providerId);
            securityAuditService.recordFederatedLoginSuccess(
                    username != null ? username : email,
                    provider,
                    request,
                    user.getId(),
                    auditDetails);
        }

        // Check if there's a saved OAuth2 authorization request to continue
        SavedRequest savedRequest = requestCache.getRequest(request, response);
        if (savedRequest != null) {
            String savedUrl = savedRequest.getRedirectUrl();
            // If the saved request was an OAuth2 authorization request, redirect to it
            if (savedUrl.contains("/oauth2/authorize")) {
                getRedirectStrategy().sendRedirect(request, response, savedUrl);
                return;
            }
        }

        // Otherwise, use default behavior (redirect to default target or saved request)
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
