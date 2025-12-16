package com.bootsandcats.oauth2.service;

import java.util.Locale;

import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * OAuth2 Metrics Service.
 *
 * <p>Provides custom metrics for OAuth2 authorization operations.
 */
@Service
public class OAuth2MetricsService {

    private final Counter tokenIssuedCounter;
    private final Counter tokenRevokedCounter;
    private final Counter authorizationRequestCounter;
    private final Counter authorizationFailedCounter;

    private final MeterRegistry meterRegistry;

    public OAuth2MetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.tokenIssuedCounter =
                Counter.builder("oauth2.tokens.issued")
                        .description("Number of tokens issued")
                        .register(meterRegistry);

        this.tokenRevokedCounter =
                Counter.builder("oauth2.tokens.revoked")
                        .description("Number of tokens revoked")
                        .register(meterRegistry);

        this.authorizationRequestCounter =
                Counter.builder("oauth2.authorization.requests")
                        .description("Number of authorization requests")
                        .register(meterRegistry);

        this.authorizationFailedCounter =
                Counter.builder("oauth2.authorization.failed")
                        .description("Number of failed authorization attempts")
                        .register(meterRegistry);
    }

    /**
     * Record a request outcome for an OAuth2/OIDC endpoint.
     *
     * <p>This metric is designed for SLO-style dashboards and must remain low-cardinality.
     *
     * <p>Do not include per-client, per-user, token, or IP labels.
     *
     * @param endpoint logical endpoint name (e.g. token, authorize, introspect, revoke, userinfo)
     * @param outcome success or failure
     * @param grantType OAuth2 grant type (token endpoint only) or null
     * @param error OAuth2 error code (e.g. invalid_grant) or exception type; null means none
     */
    public void recordEndpointRequest(
            String endpoint,
            String outcome,
            @Nullable String grantType,
            @Nullable String error) {
        String safeEndpoint = normalize(endpoint, "unknown");
        String safeOutcome = normalize(outcome, "unknown");
        String safeGrantType = normalize(grantType, "none");
        String safeError = normalize(error, "none");

        Counter.builder("oauth2.endpoint.requests")
                .description("OAuth2/OIDC endpoint requests by endpoint, outcome, grant_type, and error")
                .tag("endpoint", safeEndpoint)
                .tag("outcome", safeOutcome)
                .tag("grant_type", safeGrantType)
                .tag("error", safeError)
                .register(meterRegistry)
                .increment();
    }

    private String normalize(@Nullable String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return fallback;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    @Timed(value = "oauth2.token.issue", description = "Time to issue token")
    public void recordTokenIssued() {
        tokenIssuedCounter.increment();
    }

    public void recordTokenRevoked() {
        tokenRevokedCounter.increment();
    }

    public void recordAuthorizationRequest() {
        authorizationRequestCounter.increment();
    }

    public void recordAuthorizationFailed() {
        authorizationFailedCounter.increment();
    }

    public double getTokensIssued() {
        return tokenIssuedCounter.count();
    }

    public double getTokensRevoked() {
        return tokenRevokedCounter.count();
    }

    public double getAuthorizationRequests() {
        return authorizationRequestCounter.count();
    }

    public double getAuthorizationFailures() {
        return authorizationFailedCounter.count();
    }
}
