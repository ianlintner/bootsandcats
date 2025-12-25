package com.bootsandcats.oauth2.log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Diagnostic filter that logs incoming OAuth2 token-related requests.
 *
 * <p>Endpoints:
 *
 * <ul>
 *   <li>/oauth2/token
 *   <li>/oauth2/introspect
 *   <li>/oauth2/revoke
 * </ul>
 *
 * <p>This is intended for troubleshooting and MUST NOT log raw credentials or token values.
 */
public class TokenDiagnosticsFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TokenDiagnosticsFilter.class);

    private static final Set<String> TOKEN_ENDPOINTS =
            Set.of("/oauth2/token", "/oauth2/introspect", "/oauth2/revoke");

    private static final Set<String> SENSITIVE_PARAM_NAMES =
            Set.of(
                    "client_secret",
                    "code",
                    "refresh_token",
                    "assertion",
                    "client_assertion",
                    "client_assertion_type",
                    "id_token_hint",
                    "token",
                    "device_code",
                    "user_code");

    private final boolean enabled;
    private final int maskKeepFirst;
    private final int maskKeepLast;

    public TokenDiagnosticsFilter(boolean enabled, int maskKeepFirst, int maskKeepLast) {
        this.enabled = enabled;
        this.maskKeepFirst = maskKeepFirst;
        this.maskKeepLast = maskKeepLast;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled) {
            return true;
        }
        String path = request.getRequestURI();
        return path == null || !TOKEN_ENDPOINTS.contains(path);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        Map<String, String> flattenedParams = flattenAndMask(request.getParameterMap());

        BasicCredentials basicCredentials =
                parseBasicAuthorization(request.getHeader("Authorization"));
        String clientId =
                basicCredentials != null
                        ? basicCredentials.clientId
                        : safeParam(request, "client_id");

        String maskedSecret =
                basicCredentials != null
                        ? MaskingUtils.maskKeepEnds(
                                basicCredentials.secret, maskKeepFirst, maskKeepLast)
                        : null;

        Integer secretLen =
            basicCredentials != null && basicCredentials.secret != null
                ? basicCredentials.secret.length()
                : null;
        String secretSha256 =
            basicCredentials != null && basicCredentials.secret != null
                ? MaskingUtils.sha256Hex(basicCredentials.secret)
                : null;

        String grantType = safeParam(request, "grant_type");

        log.debug(
            "[token] method={} uri={} clientId={} grantType={} auth=basic?{} secret={} secretLen={} secretSha256={} params={} remote={} xff={} ua={} reqId={} traceparent={}",
                request.getMethod(),
                request.getRequestURI(),
                StringUtils.hasText(clientId) ? clientId : "(unknown)",
                StringUtils.hasText(grantType) ? grantType : "(n/a)",
                basicCredentials != null,
                maskedSecret != null ? maskedSecret : "(n/a)",
            secretLen != null ? secretLen : -1,
            StringUtils.hasText(secretSha256) ? secretSha256 : "-",
                flattenedParams,
                request.getRemoteAddr(),
                headerOrDash(request, "X-Forwarded-For"),
                headerOrDash(request, "User-Agent"),
                headerOrDash(request, "X-Request-Id"),
                headerOrDash(request, "traceparent"));

        try {
            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            log.warn(
                    "[token] exception uri={} status={} clientId={} grantType={} msg={} params={}",
                    request.getRequestURI(),
                    response.getStatus(),
                    StringUtils.hasText(clientId) ? clientId : "(unknown)",
                    StringUtils.hasText(grantType) ? grantType : "(n/a)",
                    ex.getMessage(),
                    flattenedParams,
                    ex);
            throw ex;
        }

        if (response.getStatus() >= 400) {
            log.warn(
                    "[token] response status={} uri={} clientId={} grantType={} params={}",
                    response.getStatus(),
                    request.getRequestURI(),
                    StringUtils.hasText(clientId) ? clientId : "(unknown)",
                    StringUtils.hasText(grantType) ? grantType : "(n/a)",
                    flattenedParams);
        } else {
            log.debug(
                    "[token] response status={} uri={} clientId={} grantType={}",
                    response.getStatus(),
                    request.getRequestURI(),
                    StringUtils.hasText(clientId) ? clientId : "(unknown)",
                    StringUtils.hasText(grantType) ? grantType : "(n/a)");
        }
    }

    private Map<String, String> flattenAndMask(Map<String, String[]> parameterMap) {
        return parameterMap.entrySet().stream()
                .collect(
                        Collectors.toMap(
                                Map.Entry::getKey,
                                e -> maskParam(e.getKey(), String.join(",", e.getValue()))));
    }

    private String maskParam(String name, String value) {
        if (!StringUtils.hasText(name)) {
            return value;
        }
        String lower = name.toLowerCase();
        if (SENSITIVE_PARAM_NAMES.contains(lower)
                || lower.contains("secret")
                || lower.contains("token")) {
            return MaskingUtils.maskKeepEnds(value, maskKeepFirst, maskKeepLast);
        }
        return value;
    }

    private String safeParam(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        return StringUtils.hasText(value) ? value : null;
    }

    private String headerOrDash(HttpServletRequest request, String name) {
        String v = request.getHeader(name);
        return StringUtils.hasText(v) ? v : "-";
    }

    private BasicCredentials parseBasicAuthorization(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader)) {
            return null;
        }
        if (!authorizationHeader.regionMatches(true, 0, "Basic ", 0, 6)) {
            return null;
        }
        String token = authorizationHeader.substring(6).trim();
        if (!StringUtils.hasText(token)) {
            log.debug("[token] Authorization: Basic header present but token was empty");
            return null;
        }
        try {
            byte[] decoded = java.util.Base64.getDecoder().decode(token);
            String raw = new String(decoded, StandardCharsets.UTF_8);
            int idx = raw.indexOf(':');
            if (idx <= 0) {
                log.debug(
                        "[token] Authorization: Basic decoded but missing ':' separator (decodedLen={})",
                        raw.length());
                return null;
            }
            String clientId = raw.substring(0, idx);
            String secret = raw.substring(idx + 1);
            if (!StringUtils.hasText(clientId)) {
                log.debug("[token] Authorization: Basic decoded but clientId was blank");
                return null;
            }
            return new BasicCredentials(clientId, secret);
        } catch (IllegalArgumentException ex) {
            // Invalid base64
            log.debug(
                    "[token] Authorization: Basic token was not valid base64 (len={})",
                    token.length());
            return null;
        }
    }

    private static final class BasicCredentials {
        private final String clientId;
        private final String secret;

        private BasicCredentials(String clientId, String secret) {
            this.clientId = clientId;
            this.secret = secret;
        }
    }
}
