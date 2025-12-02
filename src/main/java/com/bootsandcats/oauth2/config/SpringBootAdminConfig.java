package com.bootsandcats.oauth2.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

import de.codecentric.boot.admin.server.config.EnableAdminServer;

/**
 * Configuration for Spring Boot Admin Server.
 *
 * <p>Provides a web UI for managing and monitoring Spring Boot applications at /admin.
 *
 * <p>Features:
 *
 * <ul>
 *   <li>Application health and metrics visualization
 *   <li>Environment and configuration properties
 *   <li>Logging configuration
 *   <li>JVM and thread management
 *   <li>HTTP trace and audit events
 * </ul>
 *
 * <p>Access: http://localhost:9000/admin
 *
 * <p>Security: Currently allows unrestricted access. In production, configure authentication.
 */
@Configuration
@EnableAdminServer
public class SpringBootAdminConfig {

    /**
     * Configure security for Spring Boot Admin endpoints.
     *
     * <p>WARNING: This configuration allows unrestricted access to the admin UI. In production,
     * configure proper authentication and authorization.
     *
     * @param http the HttpSecurity to configure
     * @return the configured SecurityFilterChain
     * @throws Exception if configuration fails
     */
    // Note: Spring Boot Admin has its own security configuration
    // We rely on the main SecurityConfig for overall security
}
