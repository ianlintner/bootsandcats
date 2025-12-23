package com.bootsandcats.oauth2.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.util.StringUtils;

import com.bootsandcats.oauth2.model.AuditEventResult;
import com.bootsandcats.oauth2.model.AuditEventType;
import com.bootsandcats.oauth2.service.SecurityAuditService;

@Configuration
@EnableScheduling
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

        private final RegisteredClientRepository repository;
        private final PasswordEncoder passwordEncoder;
        private final SecurityAuditService securityAuditService;

        public DataInitializer(
                        RegisteredClientRepository repository,
                        PasswordEncoder passwordEncoder,
                        SecurityAuditService securityAuditService) {
                this.repository = repository;
                this.passwordEncoder = passwordEncoder;
                this.securityAuditService = securityAuditService;
        }

    @Value("${oauth2.demo-client-secret:demo-secret}")
    private String demoClientSecret;

    @Value("${oauth2.m2m-client-secret:m2m-secret}")
    private String m2mClientSecret;

    @Value("${oauth2.profile-ui-client-secret:demo-profile-ui-client-secret}")
    private String profileUiClientSecret;

    @Value("${oauth2.profile-service-client-secret:demo-profile-service-client-secret}")
    private String profileServiceClientSecret;

    @Value("${oauth2.github-review-service-client-secret:demo-github-review-service-client-secret}")
    private String githubReviewServiceClientSecret;

    @Value("${oauth2.chat-service-client-secret:demo-chat-service-client-secret}")
    private String chatServiceClientSecret;

    @Value("${oauth2.slop-detector-client-secret:demo-slop-detector-client-secret}")
    private String slopDetectorClientSecret;

    @Value("${oauth2.security-agency-client-secret:demo-security-agency-client-secret}")
    private String securityAgencyClientSecret;

    @Value("${oauth2.secure-subdomain-client-secret:demo-secure-subdomain-client-secret}")
    private String secureSubdomainClientSecret;

    /**
     * Prefer reading the secure-subdomain OAuth client secret from a mounted file (e.g. CSI Key
     * Vault secret) so rotation can be picked up without relying on static env vars.
     */
    @Value(
            "${oauth2.secure-subdomain-client-secret-file:/mnt/secrets-store/secure-subdomain-client-secret}")
    private String secureSubdomainClientSecretFile;

    @Value("${oauth2.preserve-client-secrets:true}")
    private boolean preserveClientSecrets;

    /**
     * When true, allow syncing a small allowlist of client secrets from environment-provided
     * values. This is meant for production where secrets are managed externally (e.g., Key Vault)
     * and rotated without re-seeding the database.
     */
    @Value("${oauth2.sync-client-secrets:false}")
    private boolean syncClientSecrets;

    @Bean
        public CommandLineRunner initializeClients() {
                return args -> {
            String resolvedSecureSubdomainClientSecret = resolveSecureSubdomainClientSecret();
            registerOrUpdateClient(
                    repository,
                    passwordEncoder,
                    securityAuditService,
                    "demo-client",
                    () ->
                            buildConfidentialClient(
                                    passwordEncoder.encode(demoClientSecret),
                                    UUID.randomUUID().toString()));

            registerOrUpdateClient(
                    repository,
                    passwordEncoder,
                    securityAuditService,
                    "public-client",
                    () -> buildPublicClient(UUID.randomUUID().toString()));

            registerOrUpdateClient(
                    repository,
                    passwordEncoder,
                    securityAuditService,
                    "m2m-client",
                    m2mClientSecret,
                    () -> buildMachineToMachineClient(passwordEncoder.encode(m2mClientSecret)));

            registerOrUpdateClient(
                    repository,
                    passwordEncoder,
                    securityAuditService,
                    "profile-ui",
                    () ->
                            buildProfileUiClient(
                                    passwordEncoder.encode(profileUiClientSecret),
                                    UUID.randomUUID().toString()));

            registerOrUpdateClient(
                    repository,
                    passwordEncoder,
                    securityAuditService,
                    "profile-service",
                    profileServiceClientSecret,
                    () ->
                            buildProfileServiceClient(
                                    passwordEncoder.encode(profileServiceClientSecret),
                                    UUID.randomUUID().toString()));

            registerOrUpdateClient(
                    repository,
                    passwordEncoder,
                    securityAuditService,
                    "github-review-service",
                    () ->
                            buildGithubReviewServiceClient(
                                    passwordEncoder.encode(githubReviewServiceClientSecret),
                                    UUID.randomUUID().toString()));

            registerOrUpdateClient(
                    repository,
                    passwordEncoder,
                    securityAuditService,
                    "chat-backend",
                    () ->
                            buildChatBackendClient(
                                    passwordEncoder.encode(chatServiceClientSecret),
                                    UUID.randomUUID().toString()));

            registerOrUpdateClient(
                    repository,
                    passwordEncoder,
                    securityAuditService,
                    "slop-detector",
                    () ->
                            buildSlopDetectorClient(
                                    passwordEncoder.encode(slopDetectorClientSecret),
                                    UUID.randomUUID().toString()));

            registerOrUpdateClient(
                    repository,
                    passwordEncoder,
                    securityAuditService,
                    "security-agency",
                    () ->
                            buildSecurityAgencyClient(
                                    passwordEncoder.encode(securityAgencyClientSecret),
                                    UUID.randomUUID().toString()));

            registerOrUpdateClient(
                    repository,
                    passwordEncoder,
                    securityAuditService,
                    "secure-subdomain-client",
                                        resolvedSecureSubdomainClientSecret,
                    () ->
                            buildSecureSubdomainClient(
                                                                        passwordEncoder.encode(resolvedSecureSubdomainClientSecret),
                                    UUID.randomUUID().toString()));
        };
    }

        /**
         * Periodically reconcile the secure-subdomain client secret in the DB so that if the
         * externally-managed secret rotates (e.g. Key Vault), the token exchange from the ingress
         * gateway does not fail with invalid_client.
         */
        @Scheduled(fixedDelayString = "${oauth2.client-secret-sync-interval-ms:300000}")
        public void reconcileSecureSubdomainClientSecret() {
                if (!syncClientSecrets) {
                        return;
                }

                String rawSecret = resolveSecureSubdomainClientSecret();
                if (!StringUtils.hasText(rawSecret)) {
                        return;
                }

                RegisteredClient existing = repository.findByClientId("secure-subdomain-client");
                if (existing == null) {
                        log.warn(
                                        "OAuth client 'secure-subdomain-client' not found during periodic reconcile; registering");
                        registerOrUpdateClient(
                                        repository,
                                        passwordEncoder,
                                        securityAuditService,
                                        "secure-subdomain-client",
                                        rawSecret,
                                        () ->
                                                        buildSecureSubdomainClient(
                                                                        passwordEncoder.encode(rawSecret), UUID.randomUUID().toString()));
                        return;
                }

                if (passwordEncoder.matches(rawSecret, existing.getClientSecret())) {
                        return;
                }

                RegisteredClient updatedSecretOnly =
                                RegisteredClient.from(existing).clientSecret(passwordEncoder.encode(rawSecret)).build();
                log.warn(
                                "OAuth client 'secure-subdomain-client' secret mismatch detected (periodic check); updating stored secret from mounted file");
                repository.save(updatedSecretOnly);
                auditClientEvent(securityAuditService, AuditEventType.CLIENT_UPDATED, updatedSecretOnly);
        }

        String resolveSecureSubdomainClientSecret() {
                String fromFile = readSecretFromFile(secureSubdomainClientSecretFile);
                if (StringUtils.hasText(fromFile)) {
                        return fromFile;
                }
                return secureSubdomainClientSecret;
        }

        String readSecretFromFile(String filePath) {
                if (!StringUtils.hasText(filePath)) {
                        return null;
                }

                Path path = Paths.get(filePath);
                if (!Files.isRegularFile(path)) {
                        return null;
                }

                try {
                        // Strip trailing newlines/spaces that often appear in mounted secret files.
                        String value = Files.readString(path, StandardCharsets.UTF_8).trim();
                        return StringUtils.hasText(value) ? value : null;
                } catch (IOException e) {
                        log.warn("Failed to read secure-subdomain client secret from {}", filePath, e);
                        return null;
                }
        }

    private void registerOrUpdateClient(
            RegisteredClientRepository repository,
            PasswordEncoder passwordEncoder,
            SecurityAuditService securityAuditService,
            String clientId,
            Supplier<RegisteredClient> supplier) {
        registerOrUpdateClient(
                repository, passwordEncoder, securityAuditService, clientId, null, supplier);
    }

    private void registerOrUpdateClient(
            RegisteredClientRepository repository,
            PasswordEncoder passwordEncoder,
            SecurityAuditService securityAuditService,
            String clientId,
            String rawClientSecret,
            Supplier<RegisteredClient> supplier) {
        RegisteredClient existing = repository.findByClientId(clientId);
        RegisteredClient desired = supplier.get();

        if (existing == null) {
            log.info("Registering OAuth client '{}' in database", clientId);
            repository.save(desired);
            auditClientEvent(securityAuditService, AuditEventType.CLIENT_REGISTERED, desired);
            return;
        }

        boolean allowSecretSyncForClient =
                syncClientSecrets
                        && ("m2m-client".equals(clientId)
                                || "profile-service".equals(clientId)
                                || "secure-subdomain-client".equals(clientId));

        if (preserveClientSecrets) {
            if (!allowSecretSyncForClient) {
                log.info(
                        "OAuth client '{}' already exists, preserving existing configuration "
                                + "(oauth2.preserve-client-secrets=true)",
                        clientId);
                return;
            }

            if (!StringUtils.hasText(rawClientSecret)) {
                log.info(
                        "OAuth client '{}' already exists; secret sync enabled but no secret value "
                                + "provided (skipping)",
                        clientId);
                return;
            }

            if (passwordEncoder.matches(rawClientSecret, existing.getClientSecret())) {
                log.info(
                        "OAuth client '{}' already exists; secret sync enabled and secret matches "
                                + "(no update)",
                        clientId);
                return;
            }

            RegisteredClient updatedSecretOnly =
                    RegisteredClient.from(existing).clientSecret(desired.getClientSecret()).build();
            log.warn(
                    "OAuth client '{}' secret mismatch detected; updating stored secret from environment",
                    clientId);
            repository.save(updatedSecretOnly);
            auditClientEvent(
                    securityAuditService, AuditEventType.CLIENT_UPDATED, updatedSecretOnly);
            return;
        }

        RegisteredClient.Builder builder = RegisteredClient.from(existing);

        builder.clientId(desired.getClientId());
        builder.clientIdIssuedAt(existing.getClientIdIssuedAt());
        // By default, preserve secrets (avoids accidental rotation).
        // If sync-client-secrets is enabled for this client and a mismatch is detected, update.
        boolean shouldSyncSecret = allowSecretSyncForClient && StringUtils.hasText(rawClientSecret);
        boolean shouldUpdateSecret =
                shouldSyncSecret
                        && !passwordEncoder.matches(rawClientSecret, existing.getClientSecret());
        builder.clientSecret(
                shouldUpdateSecret ? desired.getClientSecret() : existing.getClientSecret());
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

        log.info(
                "Updating OAuth client '{}' in database ({})",
                clientId,
                shouldUpdateSecret ? "secret updated" : "secret preserved");
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
                .redirectUri("https://profile.cat-herding.net/oauth/callback/oauth2server")
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

    private RegisteredClient buildGithubReviewServiceClient(String encodedSecret, String id) {
        return RegisteredClient.withId(id)
                .clientId("github-review-service")
                .clientIdIssuedAt(Instant.now())
                .clientSecret(encodedSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("api:read")
                .scope("api:write")
                .tokenSettings(
                        TokenSettings.builder()
                                .accessTokenTimeToLive(Duration.ofMinutes(15))
                                .build())
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).build())
                .build();
    }

    private RegisteredClient buildChatBackendClient(String encodedSecret, String id) {
        return RegisteredClient.withId(id)
                .clientId("chat-backend")
                .clientIdIssuedAt(Instant.now())
                .clientSecret(encodedSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("https://chat.cat-herding.net/_oauth2/callback")
                .postLogoutRedirectUri("https://chat.cat-herding.net/")
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope(OidcScopes.EMAIL)
                .tokenSettings(
                        TokenSettings.builder()
                                .accessTokenTimeToLive(Duration.ofHours(1))
                                .refreshTokenTimeToLive(Duration.ofDays(1))
                                .reuseRefreshTokens(true)
                                .build())
                .clientSettings(
                        ClientSettings.builder()
                                .requireAuthorizationConsent(false)
                                .requireProofKey(false)
                                .build())
                .build();
    }

    private RegisteredClient buildSlopDetectorClient(String encodedSecret, String id) {
        return RegisteredClient.withId(id)
                .clientId("slop-detector")
                .clientIdIssuedAt(Instant.now())
                .clientSecret(encodedSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("api:read")
                .scope("api:write")
                .redirectUri("https://slop.cat-herding.net/_oauth2/callback")
                .postLogoutRedirectUri("https://slop.cat-herding.net/")
                .tokenSettings(
                        TokenSettings.builder()
                                .accessTokenTimeToLive(Duration.ofMinutes(15))
                                .build())
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).build())
                .build();
    }

    private RegisteredClient buildSecurityAgencyClient(String encodedSecret, String id) {
        return RegisteredClient.withId(id)
                .clientId("security-agency")
                .clientIdIssuedAt(Instant.now())
                .clientSecret(encodedSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .redirectUri("https://security-agency.cat-herding.net/_oauth2/callback")
                .postLogoutRedirectUri("https://security-agency.cat-herding.net/")
                .scope("api:read")
                .scope("api:write")
                .tokenSettings(
                        TokenSettings.builder()
                                .accessTokenTimeToLive(Duration.ofMinutes(15))
                                .build())
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).build())
                .build();
    }

    private RegisteredClient buildSecureSubdomainClient(String encodedSecret, String id) {
        return RegisteredClient.withId(id)
                .clientId("secure-subdomain-client")
                .clientIdIssuedAt(Instant.now())
                .clientSecret(encodedSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("https://*.secure.cat-herding.net/_oauth2/callback")
                .redirectUri("http://localhost:*/_oauth2/callback")
                .postLogoutRedirectUri("https://*.secure.cat-herding.net/_oauth2/logout")
                .postLogoutRedirectUri("http://localhost:*/_oauth2/logout")
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope(OidcScopes.EMAIL)
                .tokenSettings(
                        TokenSettings.builder()
                                .accessTokenTimeToLive(Duration.ofMinutes(15))
                                .refreshTokenTimeToLive(Duration.ofHours(1))
                                .reuseRefreshTokens(false)
                                .authorizationCodeTimeToLive(Duration.ofMinutes(5))
                                .build())
                .clientSettings(
                        ClientSettings.builder()
                                .requireAuthorizationConsent(false)
                                .requireProofKey(true)
                                .build())
                .build();
    }
}
