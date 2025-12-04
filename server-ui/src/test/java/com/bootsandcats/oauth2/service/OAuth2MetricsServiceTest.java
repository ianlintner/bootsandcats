package com.bootsandcats.oauth2.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.bootsandcats.oauth2.config.TestKeyManagementConfig;
import com.bootsandcats.oauth2.config.TestOAuth2ClientConfiguration;

@SpringBootTest
@ActiveProfiles("test")
@Import({TestOAuth2ClientConfiguration.class, TestKeyManagementConfig.class})
class OAuth2MetricsServiceTest {

    @Autowired private OAuth2MetricsService metricsService;

    @Test
    void recordTokenIssued_shouldIncrementCounter() {
        double initialCount = metricsService.getTokensIssued();

        metricsService.recordTokenIssued();

        assertThat(metricsService.getTokensIssued()).isEqualTo(initialCount + 1);
    }

    @Test
    void recordTokenRevoked_shouldIncrementCounter() {
        double initialCount = metricsService.getTokensRevoked();

        metricsService.recordTokenRevoked();

        assertThat(metricsService.getTokensRevoked()).isEqualTo(initialCount + 1);
    }

    @Test
    void recordAuthorizationRequest_shouldIncrementCounter() {
        double initialCount = metricsService.getAuthorizationRequests();

        metricsService.recordAuthorizationRequest();

        assertThat(metricsService.getAuthorizationRequests()).isEqualTo(initialCount + 1);
    }

    @Test
    void recordAuthorizationFailed_shouldIncrementCounter() {
        double initialCount = metricsService.getAuthorizationFailures();

        metricsService.recordAuthorizationFailed();

        assertThat(metricsService.getAuthorizationFailures()).isEqualTo(initialCount + 1);
    }
}
