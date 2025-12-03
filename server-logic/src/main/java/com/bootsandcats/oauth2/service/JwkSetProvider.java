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
import com.bootsandcats.oauth2.crypto.JwkSupport;
import com.nimbusds.jose.jwk.JWKSet;

/** Loads JSON Web Keys from Azure Key Vault or falls back to an in-memory EC key. */
@Component
public class JwkSetProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwkSetProvider.class);

    private final KeyVaultSettings keyVaultSettings;
    private final ObjectProvider<SecretClient> secretClientProvider;
    private final Duration cacheTtl;
    private final JWKSet fallbackJwkSet;

    private volatile JWKSet cachedKeyVaultSet;
    private volatile Instant cacheExpiresAt = Instant.EPOCH;

    public JwkSetProvider(
            AzureKeyVaultProperties properties,
            ObjectProvider<SecretClient> secretClientProvider) {
        this.keyVaultSettings = KeyVaultSettings.from(properties);
        this.secretClientProvider = secretClientProvider;
        this.cacheTtl = keyVaultSettings.cacheTtl();
        this.fallbackJwkSet = new JWKSet(JwkSupport.generateEcSigningKey());
    }

    /**
     * Returns the active JWK set, preferring Azure Key Vault when configured.
     *
     * @return JWKSet containing signing keys
     */
    public JWKSet getJwkSet() {
        if (!keyVaultSettings.enabled()) {
            return fallbackJwkSet;
        }

        SecretClient client = secretClientProvider.getIfAvailable();
        if (client == null) {
            return fallbackJwkSet;
        }

        refreshCacheIfNeeded(client);
        return cachedKeyVaultSet != null ? cachedKeyVaultSet : fallbackJwkSet;
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
                cacheExpiresAt = Instant.now().plus(Duration.ofMinutes(1));
                LOGGER.error(
                        "Azure Key Vault secret '{}' not found. Falling back to in-memory key.",
                        keyVaultSettings.jwkSecretName(),
                        ex);
            } catch (ParseException ex) {
                cacheExpiresAt = Instant.now().plus(Duration.ofMinutes(1));
                LOGGER.error(
                        "Failed to parse JWK Set from Azure Key Vault secret '{}'.",
                        keyVaultSettings.jwkSecretName(),
                        ex);
            } catch (RuntimeException ex) {
                cacheExpiresAt = Instant.now().plus(Duration.ofMinutes(1));
                LOGGER.error("Unexpected error while loading JWK Set from Azure Key Vault.", ex);
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
