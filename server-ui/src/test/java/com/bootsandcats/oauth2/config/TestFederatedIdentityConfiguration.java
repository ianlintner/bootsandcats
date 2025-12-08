package com.bootsandcats.oauth2.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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

    @Value("${spring.security.oauth2.client.provider.github.authorization-uri:http://localhost:8888/login/oauth/authorize}")
    private String githubAuthorizationUri;

    @Value("${spring.security.oauth2.client.provider.github.token-uri:http://localhost:8888/login/oauth/access_token}")
    private String githubTokenUri;

    @Value("${spring.security.oauth2.client.provider.github.user-info-uri:http://localhost:8888/user}")
    private String githubUserInfoUri;

    @Value("${spring.security.oauth2.client.registration.github.client-id:test-github-client}")
    private String githubClientId;

    @Value("${spring.security.oauth2.client.registration.github.client-secret:test-github-secret}")
    private String githubClientSecret;

    @Value("${spring.security.oauth2.client.provider.google.authorization-uri:http://localhost:8889/oauth2/authorize}")
    private String googleAuthorizationUri;

    @Value("${spring.security.oauth2.client.provider.google.token-uri:http://localhost:8889/oauth2/token}")
    private String googleTokenUri;

    @Value("${spring.security.oauth2.client.provider.google.user-info-uri:http://localhost:8889/userinfo}")
    private String googleUserInfoUri;

    @Value("${spring.security.oauth2.client.provider.google.jwk-set-uri:http://localhost:8889/.well-known/jwks.json}")
    private String googleJwkSetUri;

    @Value("${spring.security.oauth2.client.registration.google.client-id:test-google-client}")
    private String googleClientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret:test-google-secret}")
    private String googleClientSecret;

    @Bean
    @Primary
    public ClientRegistrationRepository federatedClientRegistrationRepository() {
        return new InMemoryClientRegistrationRepository(
                githubClientRegistration(),
                googleClientRegistration()
        );
    }

    private ClientRegistration githubClientRegistration() {
        return ClientRegistration.withRegistrationId("github")
                .clientId(githubClientId)
                .clientSecret(githubClientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("read:user", "user:email")
                .authorizationUri(githubAuthorizationUri)
                .tokenUri(githubTokenUri)
                .userInfoUri(githubUserInfoUri)
                .userNameAttributeName("id")
                .clientName("GitHub")
                .build();
    }

    private ClientRegistration googleClientRegistration() {
        return ClientRegistration.withRegistrationId("google")
                .clientId(googleClientId)
                .clientSecret(googleClientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .authorizationUri(googleAuthorizationUri)
                .tokenUri(googleTokenUri)
                .userInfoUri(googleUserInfoUri)
                .jwkSetUri(googleJwkSetUri)
                .userNameAttributeName(IdTokenClaimNames.SUB)
                .clientName("Google")
                .build();
    }
}
