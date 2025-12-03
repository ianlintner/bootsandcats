package com.bootsandcats.oauth2.config;

import org.springframework.context.annotation.Configuration;

import de.codecentric.boot.admin.server.config.EnableAdminServer;

@Configuration
@EnableAdminServer
public class SpringBootAdminConfig {
    // Spring Boot Admin config is handled by its own starter; security is managed via main security
    // setup.
}
