package com.bootsandcats.oauth2.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import({TestOAuth2ClientConfiguration.class, TestKeyManagementConfig.class})
class AuthorizationServerConfigTest {

    @Autowired private RegisteredClientRepository registeredClientRepository;

    @Autowired private AuthorizationServerSettings authorizationServerSettings;

    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void registeredClientRepository_shouldContainDemoClient() {
        RegisteredClient client = registeredClientRepository.findByClientId("demo-client");

        assertThat(client).isNotNull();
        assertThat(client.getClientId()).isEqualTo("demo-client");
        assertThat(client.getScopes()).contains("openid", "profile", "email", "read", "write");
    }

    @Test
    void registeredClientRepository_shouldContainPublicClient() {
        RegisteredClient client = registeredClientRepository.findByClientId("public-client");

        assertThat(client).isNotNull();
        assertThat(client.getClientId()).isEqualTo("public-client");
        assertThat(client.getClientSettings().isRequireProofKey()).isTrue();
    }

    @Test
    void registeredClientRepository_shouldContainM2MClient() {
        RegisteredClient client = registeredClientRepository.findByClientId("m2m-client");

        assertThat(client).isNotNull();
        assertThat(client.getClientId()).isEqualTo("m2m-client");
        assertThat(client.getScopes()).contains("api:read", "api:write");
    }

    @Test
    void authorizationServerSettings_shouldHaveCorrectEndpoints() {
        assertThat(authorizationServerSettings.getAuthorizationEndpoint())
                .isEqualTo("/oauth2/authorize");
        assertThat(authorizationServerSettings.getTokenEndpoint()).isEqualTo("/oauth2/token");
        assertThat(authorizationServerSettings.getJwkSetEndpoint()).isEqualTo("/oauth2/jwks");
        assertThat(authorizationServerSettings.getTokenRevocationEndpoint())
                .isEqualTo("/oauth2/revoke");
        assertThat(authorizationServerSettings.getTokenIntrospectionEndpoint())
                .isEqualTo("/oauth2/introspect");
        assertThat(authorizationServerSettings.getOidcUserInfoEndpoint()).isEqualTo("/userinfo");
    }

    @Test
    void passwordEncoder_shouldBeConfigured() {
        assertThat(passwordEncoder).isNotNull();
        String encoded = passwordEncoder.encode("password");
        assertThat(passwordEncoder.matches("password", encoded)).isTrue();
    }
}
