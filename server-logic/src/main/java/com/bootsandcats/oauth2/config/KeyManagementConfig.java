package com.bootsandcats.oauth2.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;

@Configuration
@EnableConfigurationProperties(AzureKeyVaultProperties.class)
public class KeyManagementConfig {

    @Bean
    @ConditionalOnProperty(prefix = "azure.keyvault", name = "enabled", havingValue = "true")
    public SecretClient keyVaultSecretClient(AzureKeyVaultProperties properties) {
        Assert.isTrue(
                StringUtils.hasText(properties.getVaultUri()),
                "azure.keyvault.vault-uri must be provided when azure.keyvault.enabled=true");

        return new SecretClientBuilder()
                .vaultUrl(properties.getVaultUri())
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient();
    }
}
