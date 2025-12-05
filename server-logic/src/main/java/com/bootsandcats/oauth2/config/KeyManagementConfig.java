package com.bootsandcats.oauth2.config;

import java.util.List;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
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

    private final List<AzureKeyVaultPropertiesCustomizer> customizers;

    public KeyManagementConfig(List<AzureKeyVaultPropertiesCustomizer> customizers) {
        this.customizers = customizers;
    }

    @Bean
    public BeanPostProcessor azureKeyVaultPropertiesPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof AzureKeyVaultProperties properties) {
                    // Apply customizers after the bean is fully initialized and configured
                    if (customizers != null) {
                        for (AzureKeyVaultPropertiesCustomizer customizer : customizers) {
                            customizer.customize(properties);
                        }
                    }
                }
                return bean;
            }
        };
    }

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
