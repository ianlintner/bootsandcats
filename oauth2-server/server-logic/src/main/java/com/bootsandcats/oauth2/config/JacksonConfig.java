package com.bootsandcats.oauth2.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Jackson configuration for JSON serialization/deserialization.
 *
 * <p>This configuration ensures an ObjectMapper bean is always available, even when Spring Boot's
 * auto-configuration might not be triggered (e.g., in CI smoke tests).
 */
@Configuration
public class JacksonConfig {

    /**
     * Creates a configured ObjectMapper bean if one is not already present.
     *
     * @return configured ObjectMapper instance
     */
    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Register Java 8 time module for proper date/time handling
        mapper.registerModule(new JavaTimeModule());
        // Write dates as ISO-8601 strings instead of timestamps
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
