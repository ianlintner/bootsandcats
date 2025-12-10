package com.bootsandcats.oauth2.config;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import com.bootsandcats.oauth2.model.AuditEventResult;
import com.bootsandcats.oauth2.model.AuditEventType;
import com.bootsandcats.oauth2.service.SecurityAuditService;

@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Value("${oauth2.demo-client-secret:demo-secret}")
    private String demoClientSecret;

    @Value("${oauth2.m2m-client-secret:m2m-secret}")
    private String m2mClientSecret;

    @Value("${oauth2.preserve-client-secrets:true}")
    private boolean preserveClientSecrets;

    @Bean
    public CommandLineRunner initializeClients(
            RegisteredClientRepository repository,
            PasswordEncoder passwordEncoder,
            SecurityAuditService securityAuditService) {
        return args -> {
            registerOrUpdateClient(
                    repository,
                    securityAuditService,
                    "demo-client",
                    () ->
                            buildConfidentialClient(
                                    passwordEncoder.encode(demoClientSecret),
                                    UUID.randomUUID().toString()));

            registerOrUpdateClient(
                    repository,
                    securityAuditService,
                    "public-client",
                    () -> buildPublicClient(UUID.randomUUID().toString()));

            registerOrUpdateClient(
                    repository,
                    securityAuditService,
                    "m2m-client",
                    () -> buildMachineToMachineClient(passwordEncoder.encode(m2mClientSecret)));

            registerOrUpdateClient(
                    repository,
                    securityAuditService,
                    "profile-ui",
                    () ->
                            buildProfileUiClient(
                                    passwordEncoder.encode(demoClientSecret),
                                    UUID.randomUUID().toString()));

            registerOrUpdateClient(
                    repository,
                    securityAuditService,
                    "profile-service",
                    () ->
                            buildProfileServiceClient(
                                    passwordEncoder.encode(demoClientSecret),
                                    UUID.randomUUID().toString()));
        };
    }

    private void registerOrUpdateClient(
            RegisteredClientRepository repository,
            SecurityAuditService securityAuditService,
            String clientId,
            Supplier<RegisteredClient> supplier) {
        RegisteredClient existing = repository.findByClientId(clientId);
        RegisteredClient desired = supplier.get();

        if (existing == null) {
            log.info("Registering OAuth client '{}' in database", clientId);
            repository.save(desired);
            auditClientEvent(securityAuditService, AuditEventType.CLIENT_REGISTERED, desired);
            return;
        }

        // If preserveClientSecrets is true, don't update existing clients
        if (preserveClientSecrets) {
            log.info(
                    "OAuth client '{}' already exists, preserving existing configuration "
                            + "(oauth2.preserve-client-secrets=true)",
                    clientId);
            return;
        }

        RegisteredClient.Builder builder = RegisteredClient.from(existing);

        builder.clientId(desired.getClientId());
        builder.clientIdIssuedAt(existing.getClientIdIssuedAt());
        // Preserve the existing client secret - never overwrite it
        // Use existing.getClientSecret() instead of desired.getClientSecret()
        builder.clientSecret(existing.getClientSecret());
        builder.clientSecretExpiresAt(desired.getClientSecretExpiresAt());
        builder.clientName(desired.getClientName());
        builder.clientAuthenticationMethods(
                methods -> {
                    methods.clear();
                    methods.addAll(desired.getClientAuthenticationMethods());
                });
        builder.authorizationGrantTypes(
                grantTypes -> {
                    grantTypes.clear();
                    grantTypes.addAll(desired.getAuthorizationGrantTypes());
                });
        builder.redirectUris(
                uris -> {
                    uris.clear();
                    uris.addAll(desired.getRedirectUris());
                });
        builder.postLogoutRedirectUris(
                uris -> {
                    uris.clear();
                    uris.addAll(desired.getPostLogoutRedirectUris());
                });
        builder.scopes(
                scopes -> {
                    scopes.clear();
                    scopes.addAll(desired.getScopes());
                });
        builder.clientSettings(desired.getClientSettings());
        builder.tokenSettings(desired.getTokenSettings());

        log.info("Updating OAuth client '{}' in database (preserving secret)", clientId);
        RegisteredClient updated = builder.build();
        repository.save(updated);
        auditClientEvent(securityAuditService, AuditEventType.CLIENT_UPDATED, updated);
    }

    private void auditClientEvent(
            SecurityAuditService securityAuditService,
            AuditEventType type,
            RegisteredClient client) {
        try {
            Map<String, Object> details = new HashMap<>();
            details.put("clientId", client.getClientId());
            details.put("scopes", String.join(" ", client.getScopes()));
            details.put("grantTypes", client.getAuthorizationGrantTypes().toString());
            securityAuditService.recordGenericEvent(
                    type, AuditEventResult.SUCCESS, client.getClientId(), null, details);
        } catch (Exception e) {
            log.debug(
                    "Failed to record audit event for client {}: {}",
                    client.getClientId(),
                    e.getMessage());
        }
    }

    private RegisteredClient buildConfidentialClient(String encodedSecret, String id) {
        return RegisteredClient.withId(id)
                .clientId("demo-client")
                .clientIdIssuedAt(Instant.now())
                .clientSecret(encodedSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .redirectUri("http://localhost:8080/callback")
                .redirectUri("http://127.0.0.1:8080/callback")
                .postLogoutRedirectUri("http://localhost:8080/")
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope(OidcScopes.EMAIL)
                .scope("read")
                .scope("write")
                .tokenSettings(
                        TokenSettings.builder()
                                .accessTokenTimeToLive(Duration.ofMinutes(15))
                                .refreshTokenTimeToLive(Duration.ofDays(7))
                                .reuseRefreshTokens(false)
                                .build())
                .clientSettings(
                        ClientSettings.builder()
                                .requireAuthorizationConsent(false)
                                .requireProofKey(false)
                                .build())
                .build();
    }

    private RegisteredClient buildPublicClient(String id) {
        return RegisteredClient.withId(id)
                .clientId("public-client")
                .clientIdIssuedAt(Instant.now())
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("http://localhost:3000/callback")
                .redirectUri("http://127.0.0.1:3000/callback")
                .postLogoutRedirectUri("http://localhost:3000/")
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope(OidcScopes.EMAIL)
                .scope("read")
                .tokenSettings(
                        TokenSettings.builder()
                                .accessTokenTimeToLive(Duration.ofMinutes(15))
                                .refreshTokenTimeToLive(Duration.ofHours(24))
                                .reuseRefreshTokens(false)
                                .build())
                .clientSettings(
                        ClientSettings.builder()
                                .requireAuthorizationConsent(false)
                                .requireProofKey(true)
                                .build())
                .build();
    }

    private RegisteredClient buildMachineToMachineClient(String encodedSecret) {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("m2m-client")
                .clientIdIssuedAt(Instant.now())
                .clientSecret(encodedSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("api:read")
                .scope("api:write")
                .tokenSettings(
                        TokenSettings.builder()
                                .accessTokenTimeToLive(Duration.ofMinutes(5))
                                .build())
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).build())
                .build();
    }

    private RegisteredClient buildProfileUiClient(String encodedSecret, String id) {
        return RegisteredClient.withId(id)
                .clientId("profile-ui")
                .clientIdIssuedAt(Instant.now())
                .clientSecret(encodedSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("http://localhost:8080/login/oauth2/code/oauth2-server")
                .redirectUri("http://127.0.0.1:8080/login/oauth2/code/oauth2-server")
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope(OidcScopes.EMAIL)
                .tokenSettings(
                        TokenSettings.builder()
                                .accessTokenTimeToLive(Duration.ofMinutes(15))
                                .build())
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).build())
                .build();
    }

    private RegisteredClient buildProfileServiceClient(String encodedSecret, String id) {
        return RegisteredClient.withId(id)
                .clientId("profile-service")
                .clientIdIssuedAt(Instant.now())
                .clientSecret(encodedSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("https://profile.cat-herding.net/")
                .postLogoutRedirectUri("https://profile.cat-herding.net/")
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope(OidcScopes.EMAIL)
                .scope("profile:read")
                .scope("profile:write")
                .tokenSettings(
                        TokenSettings.builder()
                                .accessTokenTimeToLive(Duration.ofMinutes(15))
                                .refreshTokenTimeToLive(Duration.ofDays(7))
                                .reuseRefreshTokens(false)
                                .build())
                .clientSettings(
                        ClientSettings.builder()
                                .requireAuthorizationConsent(false)
                                .requireProofKey(false)
                                .build())
                .build();
    }
}
