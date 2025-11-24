package com.bootsandcats.oauth2.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * OpenTelemetry and Metrics Configuration.
 *
 * <p>Configures OpenTelemetry tracing and Prometheus metrics integration.
 */
@Configuration
public class OpenTelemetryConfig {

    /**
     * TimedAspect for @Timed annotation support.
     *
     * @param registry MeterRegistry for metrics
     * @return TimedAspect
     */
    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }
}
