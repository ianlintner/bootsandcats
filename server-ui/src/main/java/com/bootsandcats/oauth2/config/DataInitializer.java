package com.bootsandcats.oauth2.config;

import java.time.Duration;
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

@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Value("${oauth2.demo-client-secret:demo-secret}")
    private String demoClientSecret;

    @Value("${oauth2.m2m-client-secret:m2m-secret}")
    private String m2mClientSecret;

    @Bean
    public CommandLineRunner initializeClients(
            RegisteredClientRepository repository, PasswordEncoder passwordEncoder) {
        return args -> {
            registerClientIfMissing(
                    repository,
                    "demo-client",
                    () ->
                            buildConfidentialClient(
                                    passwordEncoder.encode(demoClientSecret),
                                    UUID.randomUUID().toString()));

            registerClientIfMissing(
                    repository,
                    "public-client",
                    () -> buildPublicClient(UUID.randomUUID().toString()));

            registerClientIfMissing(
                    repository,
                    "m2m-client",
                    () -> buildMachineToMachineClient(passwordEncoder.encode(m2mClientSecret)));

            registerClientIfMissing(
                    repository,
                    "canary-app",
                    () ->
                            buildCanaryClient(
                                    passwordEncoder.encode(demoClientSecret),
                                    UUID.randomUUID().toString()));
        };
    }

    private void registerClientIfMissing(
            RegisteredClientRepository repository,
            String clientId,
            Supplier<RegisteredClient> supplier) {
        if (repository.findByClientId(clientId) == null) {
            log.info("Registering OAuth client '{}' in database", clientId);
            repository.save(supplier.get());
        }
    }

    private RegisteredClient buildConfidentialClient(String encodedSecret, String id) {
        return RegisteredClient.withId(id)
                .clientId("demo-client")
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
                .clientSecret(encodedSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("message.read")
                .scope("message.write")
                .tokenSettings(
                        TokenSettings.builder()
                                .accessTokenTimeToLive(Duration.ofMinutes(5))
                                .build())
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).build())
                .build();
    }

    private RegisteredClient buildCanaryClient(String encodedSecret, String id) {
        return RegisteredClient.withId(id)
                .clientId("canary-app")
                .clientSecret(encodedSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("http://localhost:8081/login/oauth2/code/custom-server")
                .redirectUri("http://127.0.0.1:8081/login/oauth2/code/custom-server")
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
}
