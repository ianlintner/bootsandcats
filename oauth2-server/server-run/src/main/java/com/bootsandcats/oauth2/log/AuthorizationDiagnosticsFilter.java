package com.bootsandcats.oauth2.log;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Diagnostic filter that logs incoming /oauth2/authorize requests with parameters and
 * authentication state. This is intended for troubleshooting and should not log credentials or
 * token values.
 */
public class AuthorizationDiagnosticsFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationDiagnosticsFilter.class);

    private final boolean enabled;
    private final int maskKeepFirst;
    private final int maskKeepLast;

    public AuthorizationDiagnosticsFilter(boolean enabled, int maskKeepFirst, int maskKeepLast) {
        this.enabled = enabled;
        this.maskKeepFirst = maskKeepFirst;
        this.maskKeepLast = maskKeepLast;
    }

    public AuthorizationDiagnosticsFilter() {
        this(false, 0, 0);
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        if (!enabled) {
            return true;
        }
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/oauth2/authorize");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Map<String, String[]> parameterMap = request.getParameterMap();
        Map<String, String> flattenedParams =
                parameterMap.entrySet().stream()
                        .collect(
                                Collectors.toMap(
                                        Map.Entry::getKey,
                        e -> maskParam(e.getKey(), String.join(",", e.getValue()))));

        log.info(
                "[authorize] method={} uri={} status(presend)=? principal={} params={} sessionId={}",
                request.getMethod(),
                request.getRequestURI(),
                auth != null ? auth.getName() : "anonymous",
                flattenedParams,
                request.getRequestedSessionId());
        try {
            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            log.error(
                    "[authorize] exception status={} msg={} params={}",
                    response.getStatus(),
                    ex.getMessage(),
                    flattenedParams,
                    ex);
            throw ex;
        }
        log.info(
                "[authorize] response status={} location={} sessionId={}",
                response.getStatus(),
                response.getHeader("Location"),
                request.getRequestedSessionId());
    }

    private String maskParam(String name, String value) {
        if (name == null) {
            return value;
        }

        String lower = name.toLowerCase();
        // Parameters that are commonly sensitive even though they aren't called "secret".
        boolean sensitive =
                lower.contains("secret")
                        || lower.contains("token")
                        || lower.contains("assertion")
                        || lower.equals("state")
                        || lower.equals("nonce")
                        || lower.equals("code")
                        || lower.equals("code_verifier")
                        || lower.equals("id_token_hint");
        if (!sensitive) {
            return value;
        }

        return MaskingUtils.maskKeepEnds(value, maskKeepFirst, maskKeepLast);
    }
}
