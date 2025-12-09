package com.bootsandcats.oauth2.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

@Configuration
public class OpenApiConfig {

    @Value("${oauth2.issuer-url:http://localhost:9000}")
    private String issuerUrl;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("OAuth2 Authorization Server API")
                                .version("1.0.0")
                                .description(
                                        "Spring Boot OAuth2 Authorization Server with OpenID Connect (OIDC), "
                                                + "PKCE, and JWT support. This server provides OAuth2 2.1 compliant "
                                                + "authorization services including Authorization Code, Client Credentials, "
                                                + "and Refresh Token grant types.")
                                .contact(
                                        new Contact()
                                                .name("Bootsandcats Team")
                                                .url("https://github.com/ianlintner/bootsandcats"))
                                .license(
                                        new License()
                                                .name("MIT License")
                                                .url("https://opensource.org/licenses/MIT")))
                .externalDocs(
                        new ExternalDocumentation()
                                .description("Full Documentation")
                                .url("https://ianlintner.github.io/bootsandcats/"))
                .servers(
                        List.of(
                                new Server()
                                        .url(issuerUrl)
                                        .description("OAuth2 Authorization Server")))
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        "oauth2",
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.OAUTH2)
                                                .description("OAuth2 Authentication")
                                                .flows(
                                                        new OAuthFlows()
                                                                .authorizationCode(
                                                                        new OAuthFlow()
                                                                                .authorizationUrl(
                                                                                        issuerUrl
                                                                                                + "/oauth2/authorize")
                                                                                .tokenUrl(
                                                                                        issuerUrl
                                                                                                + "/oauth2/token")
                                                                                .scopes(
                                                                                        new Scopes()
                                                                                                .addString(
                                                                                                        "openid",
                                                                                                        "OpenID Connect scope")
                                                                                                .addString(
                                                                                                        "profile",
                                                                                                        "Access user profile")
                                                                                                .addString(
                                                                                                        "email",
                                                                                                        "Access user email")
                                                                                                .addString(
                                                                                                        "read",
                                                                                                        "Read access")
                                                                                                .addString(
                                                                                                        "write",
                                                                                                        "Write access")))
                                                                .clientCredentials(
                                                                        new OAuthFlow()
                                                                                .tokenUrl(
                                                                                        issuerUrl
                                                                                                + "/oauth2/token")
                                                                                .scopes(
                                                                                        new Scopes()
                                                                                                .addString(
                                                                                                        "api:read",
                                                                                                        "API read access")
                                                                                                .addString(
                                                                                                        "api:write",
                                                                                                        "API write access")))))
                                .addSecuritySchemes(
                                        "bearerAuth",
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")
                                                .description("JWT Bearer Token Authentication")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
