package com.bootsandcats.oauth2.config;

import com.bootsandcats.oauth2.crypto.JwkSupport;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

/**
 * Test-specific key management configuration.
 *
 * <p>Provides a static ES256 JWK for the {@code test} profile so that {@link
 * com.bootsandcats.oauth2.service.JwkSetProvider} always has a valid signing key and can
 * initialize without depending on Azure Key Vault or environment variables.
 */
@TestConfiguration
@Profile("test")
public class TestKeyManagementConfig {

    @Bean
    public AzureKeyVaultProperties testAzureKeyVaultProperties() {
        AzureKeyVaultProperties props = new AzureKeyVaultProperties();
        props.setEnabled(false);
        props.setStaticJwk(JwkSupport.generateEcSigningKey().toJSONString());
        return props;
    }
}
