package com.bootsandcats.oauth2.config;

/**
 * Callback interface that can customize {@link AzureKeyVaultProperties} after they have been bound
 * from configuration properties.
 */
@FunctionalInterface
public interface AzureKeyVaultPropertiesCustomizer {

    void customize(AzureKeyVaultProperties properties);
}
