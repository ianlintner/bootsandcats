package com.bootsandcats.oauth2.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;

/**
 * Test configuration providing mock OAuth2 client registrations for federated identity testing.
 *
 * <p>This configuration creates GitHub and Google client registrations that point to WireMock
 * servers for complete OAuth2/OIDC flow testing without external dependencies.
 *
 * <p><b>AI Agent Test Context:</b>
 *
 * <ul>
 *   <li>GitHub uses OAuth2 (not OIDC) - requires userInfoUri</li>
 *   <li>Google uses OIDC - issuerUri enables discovery</li>
 *   <li>Provider URIs must match WireMock server URLs</li>
 * </ul>
 */
@TestConfiguration
public class TestFederatedIdentityConfiguration {

    @Bean
    @Primary
    public ClientRegistrationRepository federatedClientRegistrationRepository(Environment env) {
        return new InMemoryClientRegistrationRepository(
                githubClientRegistration(env),
                googleClientRegistration(env)
        );
    }

    private ClientRegistration githubClientRegistration(Environment env) {
        String authUri = env.getProperty("spring.security.oauth2.client.provider.github.authorization-uri",
                "http://localhost:8888/login/oauth/authorize");
        String tokenUri = env.getProperty("spring.security.oauth2.client.provider.github.token-uri",
                "http://localhost:8888/login/oauth/access_token");
        String userInfoUri = env.getProperty("spring.security.oauth2.client.provider.github.user-info-uri",
                "http://localhost:8888/user");
        String clientId = env.getProperty("spring.security.oauth2.client.registration.github.client-id",
                "test-github-client");
        String clientSecret = env.getProperty("spring.security.oauth2.client.registration.github.client-secret",
                "test-github-secret");

        return ClientRegistration.withRegistrationId("github")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("read:user", "user:email")
                .authorizationUri(authUri)
                .tokenUri(tokenUri)
                .userInfoUri(userInfoUri)
                .userNameAttributeName("id")
                .clientName("GitHub")
                .build();
    }

    private ClientRegistration googleClientRegistration(Environment env) {
        String authUri = env.getProperty("spring.security.oauth2.client.provider.google.authorization-uri",
                "http://localhost:8889/o/oauth2/v2/auth");
        String tokenUri = env.getProperty("spring.security.oauth2.client.provider.google.token-uri",
                "http://localhost:8889/token");
        String userInfoUri = env.getProperty("spring.security.oauth2.client.provider.google.user-info-uri",
                "http://localhost:8889/userinfo");
        String jwkSetUri = env.getProperty("spring.security.oauth2.client.provider.google.jwk-set-uri",
                "http://localhost:8889/oauth2/v3/certs");
        String clientId = env.getProperty("spring.security.oauth2.client.registration.google.client-id",
                "test-google-client");
        String clientSecret = env.getProperty("spring.security.oauth2.client.registration.google.client-secret",
                "test-google-secret");

        return ClientRegistration.withRegistrationId("google")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .authorizationUri(authUri)
                .tokenUri(tokenUri)
                .userInfoUri(userInfoUri)
                .jwkSetUri(jwkSetUri)
                .userNameAttributeName(IdTokenClaimNames.SUB)
                .clientName("Google")
                .build();
    }
}
