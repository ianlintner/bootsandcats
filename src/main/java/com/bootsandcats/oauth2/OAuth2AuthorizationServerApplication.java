package com.bootsandcats.oauth2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main Spring Boot Application for OAuth2 Authorization Server.
 *
 * <p>This server provides:
 *
 * <ul>
 *   <li>OAuth2 Authorization with PKCE support
 *   <li>OpenID Connect (OIDC)
 *   <li>JWT-based tokens
 *   <li>OpenTelemetry metrics and tracing
 *   <li>Prometheus metrics endpoint
 * </ul>
 */
@SpringBootApplication
public class OAuth2AuthorizationServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(OAuth2AuthorizationServerApplication.class, args);
    }
}
