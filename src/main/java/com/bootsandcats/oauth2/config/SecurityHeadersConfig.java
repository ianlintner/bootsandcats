package com.bootsandcats.oauth2.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Security Headers Configuration for OWASP compliance.
 *
 * <p>Configures security headers including CSP, HSTS, X-Frame-Options, and CORS.
 */
@Configuration
public class SecurityHeadersConfig {

    /**
     * Configure security headers for HTTP responses.
     *
     * @param http HttpSecurity builder
     */
    public void configureSecurityHeaders(HttpSecurity http) throws Exception {
        http.headers(
                headers ->
                        headers
                                // X-Content-Type-Options
                                .contentTypeOptions(contentTypeOptions -> {})
                                // X-Frame-Options
                                .frameOptions(frameOptions -> frameOptions.deny())
                                // X-XSS-Protection
                                .xssProtection(
                                        xss ->
                                                xss.headerValue(
                                                        org.springframework.security.web.header
                                                                .writers.XXssProtectionHeaderWriter
                                                                .HeaderValue.ENABLED_MODE_BLOCK))
                                // Content-Security-Policy
                                .contentSecurityPolicy(
                                        csp ->
                                                csp.policyDirectives(
                                                        "default-src 'self'; "
                                                                + "script-src 'self'; "
                                                                + "style-src 'self' 'unsafe-inline'; "
                                                                + "img-src 'self' data:; "
                                                                + "font-src 'self'; "
                                                                + "frame-ancestors 'none'; "
                                                                + "form-action 'self'"))
                                // Referrer-Policy
                                .referrerPolicy(
                                        referrer ->
                                                referrer.policy(
                                                        ReferrerPolicyHeaderWriter.ReferrerPolicy
                                                                .STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                                // Permissions-Policy
                                .permissionsPolicy(
                                        permissions ->
                                                permissions.policy(
                                                        "geolocation=(), "
                                                                + "camera=(), "
                                                                + "microphone=(), "
                                                                + "payment=()")));
    }

    /**
     * CORS configuration source.
     *
     * @return CorsConfigurationSource
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(
                Arrays.asList("http://localhost:8080", "http://localhost:3000"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(
                List.of("Authorization", "Content-Type", "X-Requested-With"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
