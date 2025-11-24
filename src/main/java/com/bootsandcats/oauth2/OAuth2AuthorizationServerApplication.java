package com.bootsandcats.oauth2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main Spring Boot Application for OAuth2 Authorization Server.
 *
 * <p>This server provides: - OAuth2 Authorization with PKCE support - OpenID Connect (OIDC) -
 * JWT-based tokens - OpenTelemetry metrics and tracing - Prometheus metrics endpoint
 */
@SpringBootApplication
public class OAuth2AuthorizationServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(OAuth2AuthorizationServerApplication.class, args);
    }
}
