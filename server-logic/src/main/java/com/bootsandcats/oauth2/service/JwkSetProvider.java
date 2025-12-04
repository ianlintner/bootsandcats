package com.bootsandcats.oauth2.service;

import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.azure.core.exception.ResourceNotFoundException;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import com.bootsandcats.oauth2.config.AzureKeyVaultProperties;
import com.nimbusds.jose.jwk.JWKSet;

/**
 * Loads JSON Web Keys from Azure Key Vault or a static JWK configuration.
 *
 * <p>This provider requires explicit JWK configuration and will fail fast during startup if no JWK
 * source is available. This ensures production deployments have stable, persistent signing keys.
 *
 * <p>Configuration options (in order of precedence):
 *
 * <ol>
 *   <li>Azure Key Vault (when {@code azure.keyvault.enabled=true})
 *   <li>Static JWK (when {@code azure.keyvault.static-jwk} is set)
 * </ol>
 *
 * <p>If neither is configured, the application will fail to start.
 */
@Component
public class JwkSetProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwkSetProvider.class);

    private final KeyVaultSettings keyVaultSettings;
    private final ObjectProvider<SecretClient> secretClientProvider;
    private final Duration cacheTtl;
    private final JWKSet staticJwkSet;

    private volatile JWKSet cachedKeyVaultSet;
    private volatile Instant cacheExpiresAt = Instant.EPOCH;

    public JwkSetProvider(
            AzureKeyVaultProperties properties, ObjectProvider<SecretClient> secretClientProvider) {
        this.keyVaultSettings = KeyVaultSettings.from(properties);
        this.secretClientProvider = secretClientProvider;
        this.cacheTtl = keyVaultSettings.cacheTtl();
        this.staticJwkSet = parseStaticJwk(properties.getStaticJwk());

        validateConfiguration();
    }

    /**
     * Validates that at least one JWK source is configured. Fails fast if no JWK source is
     * available.
     *
     * @throws IllegalStateException if no JWK source is configured
     */
    private void validateConfiguration() {
        if (keyVaultSettings.enabled()) {
            LOGGER.info(
                    "JWK source: Azure Key Vault (secret: '{}')", keyVaultSettings.jwkSecretName());
            return;
        }

        if (staticJwkSet != null) {
            LOGGER.info(
                    "JWK source: Static JWK with {} key(s) (kids: {})",
                    staticJwkSet.getKeys().size(),
                    staticJwkSet.getKeys().stream()
                            .map(k -> k.getKeyID())
                            .collect(Collectors.joining(", ")));
            return;
        }

        String errorMessage =
                """
                FATAL: No JWK source configured. The OAuth2 Authorization Server requires \
                signing keys to issue tokens. Configure one of the following:
                  1. Azure Key Vault: Set azure.keyvault.enabled=true and configure vault URI
                  2. Static JWK: Set azure.keyvault.static-jwk with a valid JWK JSON string
                     (can be set via AZURE_KEYVAULT_STATIC_JWK environment variable)
                """;
        LOGGER.error(errorMessage);
        throw new IllegalStateException(errorMessage);
    }

    private JWKSet parseStaticJwk(String staticJwk) {
        if (staticJwk == null || staticJwk.isBlank()) {
            return null;
        }

        try {
            return JWKSet.parse(staticJwk);
        } catch (ParseException ex) {
            String errorMessage =
                    String.format(
                            "FATAL: Failed to parse static JWK configuration: %s", ex.getMessage());
            LOGGER.error(errorMessage, ex);
            throw new IllegalStateException(errorMessage, ex);
        }
    }

    /**
     * Returns the active JWK set.
     *
     * <p>When Azure Key Vault is enabled, keys are fetched from Key Vault with caching. Otherwise,
     * returns the static JWK set.
     *
     * @return JWKSet containing signing keys
     * @throws IllegalStateException if Key Vault is enabled but keys cannot be loaded and no static
     *     fallback exists
     */
    public JWKSet getJwkSet() {
        if (!keyVaultSettings.enabled()) {
            if (staticJwkSet == null) {
                throw new IllegalStateException("No JWK source configured");
            }
            return staticJwkSet;
        }

        SecretClient client = secretClientProvider.getIfAvailable();
        if (client == null) {
            throw new IllegalStateException(
                    "Azure Key Vault is enabled but SecretClient is not available");
        }

        refreshCacheIfNeeded(client);

        if (cachedKeyVaultSet != null) {
            return cachedKeyVaultSet;
        }

        throw new IllegalStateException(
                "Failed to load JWK from Azure Key Vault and no static JWK configured");
    }

    private void refreshCacheIfNeeded(SecretClient client) {
        if (Instant.now().isBefore(cacheExpiresAt)) {
            return;
        }

        synchronized (this) {
            if (Instant.now().isBefore(cacheExpiresAt)) {
                return;
            }

            try {
                KeyVaultSecret secret = client.getSecret(keyVaultSettings.jwkSecretName());
                cachedKeyVaultSet = JWKSet.parse(secret.getValue());
                cacheExpiresAt = Instant.now().plus(cacheTtl);
                LOGGER.info(
                        "Loaded {} key(s) from Azure Key Vault secret '{}' (kids: {}).",
                        cachedKeyCount(),
                        keyVaultSettings.jwkSecretName(),
                        describeKeyIds());
            } catch (ResourceNotFoundException ex) {
                LOGGER.error(
                        "FATAL: Azure Key Vault secret '{}' not found.",
                        keyVaultSettings.jwkSecretName(),
                        ex);
                throw new IllegalStateException(
                        "Azure Key Vault secret '"
                                + keyVaultSettings.jwkSecretName()
                                + "' not found",
                        ex);
            } catch (ParseException ex) {
                LOGGER.error(
                        "FATAL: Failed to parse JWK Set from Azure Key Vault secret '{}'.",
                        keyVaultSettings.jwkSecretName(),
                        ex);
                throw new IllegalStateException("Failed to parse JWK Set from Azure Key Vault", ex);
            } catch (RuntimeException ex) {
                LOGGER.error(
                        "FATAL: Unexpected error while loading JWK Set from Azure Key Vault.", ex);
                throw new IllegalStateException(
                        "Unexpected error loading JWK from Azure Key Vault", ex);
            }
        }
    }

    private long cachedKeyCount() {
        return cachedKeyVaultSet == null ? 0 : cachedKeyVaultSet.getKeys().size();
    }

    private String describeKeyIds() {
        if (cachedKeyVaultSet == null) {
            return "n/a";
        }
        return cachedKeyVaultSet.getKeys().stream()
                .map(jwk -> jwk.getKeyID() == null ? "(no kid)" : jwk.getKeyID())
                .collect(Collectors.joining(", "));
    }

    private static final class KeyVaultSettings {
        private final boolean enabled;
        private final String jwkSecretName;
        private final Duration cacheTtl;

        private KeyVaultSettings(boolean enabled, String jwkSecretName, Duration cacheTtl) {
            this.enabled = enabled;
            this.jwkSecretName = jwkSecretName;
            this.cacheTtl = cacheTtl;
        }

        static KeyVaultSettings from(AzureKeyVaultProperties properties) {
            return new KeyVaultSettings(
                    properties.isEnabled(),
                    properties.getJwkSecretName(),
                    properties.getCacheTtl());
        }

        boolean enabled() {
            return enabled;
        }

        String jwkSecretName() {
            return jwkSecretName;
        }

        Duration cacheTtl() {
            return cacheTtl;
        }
    }
}
