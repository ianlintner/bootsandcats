package com.bootsandcats.oauth2.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import com.bootsandcats.oauth2.config.AzureKeyVaultProperties;
import com.bootsandcats.oauth2.crypto.JwkSupport;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.util.JSONObjectUtils;

class JwkSetProviderTest {

    private AzureKeyVaultProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AzureKeyVaultProperties();
        properties.setVaultUri("https://example.vault.azure.net/");
        properties.setJwkSecretName("oauth2-jwk");
        properties.setCacheTtl(Duration.ofMinutes(5));
    }

    @Test
    void shouldReturnFallbackWhenKeyVaultDisabled() {
        properties.setEnabled(false);
        JwkSetProvider provider = new JwkSetProvider(properties, null);

        JWKSet jwkSet = provider.getJwkSet();

        assertThat(jwkSet).isNotNull();
        assertThat(jwkSet.getKeys()).hasSize(1);
        assertThat(jwkSet.getKeys().get(0).getAlgorithm().getName()).isEqualTo("ES256");
    }

    @Test
    void shouldLoadJwkFromKeyVaultWhenEnabled() {
        properties.setEnabled(true);
        SecretClient secretClient = mock(SecretClient.class);
        JWKSet expected = new JWKSet(JwkSupport.generateEcSigningKey());
        String jwkJson = JSONObjectUtils.toJSONString(expected.toJSONObject(true));
        KeyVaultSecret secret = new KeyVaultSecret("oauth2-jwk", jwkJson);
        when(secretClient.getSecret("oauth2-jwk")).thenReturn(secret);

        JwkSetProvider provider = new JwkSetProvider(properties, secretClient);

        JWKSet actual = provider.getJwkSet();

        verify(secretClient, times(1)).getSecret("oauth2-jwk");
        assertThat(actual.toJSONObject(true)).isEqualTo(expected.toJSONObject(true));
    }

    @Test
    void shouldFallbackWhenKeyVaultSecretMissing() {
        properties.setEnabled(true);
        SecretClient secretClient = mock(SecretClient.class);
        when(secretClient.getSecret("oauth2-jwk"))
                .thenThrow(new RuntimeException("Secret not found"));

        JwkSetProvider provider = new JwkSetProvider(properties, secretClient);

        JWKSet jwkSet = provider.getJwkSet();

        assertThat(jwkSet).isNotNull();
        assertThat(jwkSet.getKeys()).hasSize(1);
    }
}
