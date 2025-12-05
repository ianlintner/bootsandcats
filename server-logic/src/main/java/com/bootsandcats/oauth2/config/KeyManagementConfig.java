package com.bootsandcats.oauth2.config;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;

@Configuration
@EnableConfigurationProperties(AzureKeyVaultProperties.class)
public class KeyManagementConfig implements EnvironmentAware {

    private ConfigurableEnvironment environment;

    private final List<AzureKeyVaultPropertiesCustomizer> customizers;

    public KeyManagementConfig(List<AzureKeyVaultPropertiesCustomizer> customizers) {
        this.customizers = customizers;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = (ConfigurableEnvironment) environment;
    }

    @Bean
    @Primary
    public AzureKeyVaultProperties azureKeyVaultProperties() {
        // Create a new instance and let Spring's configuration properties mechanism bind it
        AzureKeyVaultProperties properties = new AzureKeyVaultProperties();
        
        // Use Binder to bind properties from the environment (handles placeholder resolution)
        Binder binder = new Binder(ConfigurationPropertySources.get(environment));
        binder.bind("azure.keyvault", org.springframework.boot.context.properties.bind.Bindable.of(AzureKeyVaultProperties.class))
              .ifBound(bound -> {
                  // Copy bound values to our instance
                  properties.setEnabled(bound.isEnabled());
                  properties.setVaultUri(bound.getVaultUri());
                  properties.setJwkSecretName(bound.getJwkSecretName());
                  properties.setCacheTtl(bound.getCacheTtl());
                  properties.setStaticJwk(bound.getStaticJwk());
              });
        
        // Apply customizers after binding (this allows tests to override)
        if (customizers != null) {
            for (AzureKeyVaultPropertiesCustomizer customizer : customizers) {
                customizer.customize(properties);
            }
        }
        
        return properties;
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
