package com.bootsandcats.oauth2.security;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.bootsandcats.oauth2.service.SecurityAuditService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Applies deny-list policy for form-login users (local login).
 *
 * <p>This is intentionally enforced in the success handler (post-authentication) to keep the demo
 * in-memory user store simple.
 */
@Component
public class FormLoginDenyListSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final DenyListService denyListService;
    private final SecurityAuditService securityAuditService;

    public FormLoginDenyListSuccessHandler(
            DenyListService denyListService, SecurityAuditService securityAuditService) {
        this.denyListService = denyListService;
        this.securityAuditService = securityAuditService;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws ServletException, IOException {
        String username = authentication != null ? authentication.getName() : null;
        try {
            denyListService.assertNotDenied("local", null, username, null);
        } catch (LoginDeniedException denied) {
            Map<String, Object> details = new HashMap<>();
            details.put("provider", "local");
            details.put("username", username);
            details.put("denyRuleId", denied.getDenyRule() != null ? denied.getDenyRule().getId() : null);
            details.put(
                    "denyRuleProvider",
                    denied.getDenyRule() != null ? denied.getDenyRule().getProvider() : null);
            details.put(
                    "denyRuleMatchField",
                    denied.getDenyRule() != null ? denied.getDenyRule().getMatchField() : null);
            details.put(
                    "denyRuleMatchType",
                    denied.getDenyRule() != null ? denied.getDenyRule().getMatchType() : null);

            securityAuditService.recordLoginDenied(username, request, denied.getMessage(), details);

            // Clear auth and session
            SecurityContextHolder.clearContext();
            var session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            getRedirectStrategy().sendRedirect(request, response, "/login?denied=1");
            return;
        }

        super.onAuthenticationSuccess(request, response, authentication);
    }
}
