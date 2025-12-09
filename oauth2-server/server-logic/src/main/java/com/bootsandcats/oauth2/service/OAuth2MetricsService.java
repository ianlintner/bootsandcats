package com.bootsandcats.oauth2.service;

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

    public OAuth2MetricsService(MeterRegistry meterRegistry) {
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
