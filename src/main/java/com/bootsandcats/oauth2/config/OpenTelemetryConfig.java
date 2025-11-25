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
     * Creates a TimedAspect bean that enables @Timed annotation support across the application.
     *
     * <p>This aspect enables method-level metrics collection when methods are annotated with
     * {@code @Timed}. Metrics include execution time, invocation count, and can be exported to
     * monitoring systems like Prometheus.
     *
     * @param registry MeterRegistry for metrics registration
     * @return TimedAspect configured with the provided MeterRegistry
     */
    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }
}
