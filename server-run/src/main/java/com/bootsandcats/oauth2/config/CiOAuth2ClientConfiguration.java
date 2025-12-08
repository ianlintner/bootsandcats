package com.bootsandcats.oauth2.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

/**
 * Stub OAuth2 client configuration for CI/smoke tests.
 *
 * <p>This provides a mock ClientRegistrationRepository to satisfy the oauth2Login() dependency
 * without requiring network access to external OAuth2 providers (GitHub, Google, Azure AD).
 *
 * <p>Active only when the "ci" profile is enabled.
 */
@Configuration
@Profile("ci")
public class CiOAuth2ClientConfiguration {

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository() {
        ClientRegistration stubRegistration =
                ClientRegistration.withRegistrationId("stub-client")
                        .clientId("stub-client-id")
                        .clientSecret("stub-client-secret")
                        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                        .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                        .authorizationUri("https://stub.example.com/oauth2/authorize")
                        .tokenUri("https://stub.example.com/oauth2/token")
                        .userInfoUri("https://stub.example.com/oauth2/userinfo")
                        .userNameAttributeName("sub")
                        .scope("openid", "profile", "email")
                        .build();
        return new InMemoryClientRegistrationRepository(stubRegistration);
    }
}
