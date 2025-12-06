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

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
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
                                        e -> maskSensitive(String.join(",", e.getValue()))));

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

    private String maskSensitive(String value) {
        if (value == null) {
            return null;
        }
        if (value.toLowerCase().contains("secret")
                || value.toLowerCase().contains("token")
                || value.toLowerCase().contains("assertion")) {
            return "***";
        }
        return value;
    }
}
