package com.bootsandcats.oauth2.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

/**
 * Test configuration providing a stub ClientRegistrationRepository.
 *
 * <p>This avoids the need to resolve external OAuth2 issuer URIs during test context bootstrap.
 * Must be explicitly imported via @Import or made available via component scanning.
 */
@TestConfiguration
public class TestOAuth2ClientConfiguration {

    @Bean
    @Primary
    public ClientRegistrationRepository clientRegistrationRepository() {
        ClientRegistration stubRegistration =
                ClientRegistration.withRegistrationId("test-client")
                        .clientId("test-client-id")
                        .clientSecret("test-client-secret")
                        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                        .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                        .authorizationUri("https://example.com/oauth2/authorize")
                        .tokenUri("https://example.com/oauth2/token")
                        .userInfoUri("https://example.com/oauth2/userinfo")
                        .userNameAttributeName("sub")
                        .scope("openid", "profile", "email")
                        .build();
        return new InMemoryClientRegistrationRepository(stubRegistration);
    }
}
