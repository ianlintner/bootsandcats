package com.bootsandcats.oauth2.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;

/** Configuration properties for Azure Key Vault backed JWK management. */
@ConfigurationProperties(prefix = "azure.keyvault")
public class AzureKeyVaultProperties {

    private boolean enabled = false;

    private String vaultUri;

    private String jwkSecretName = "oauth2-jwk";

    private Duration cacheTtl = Duration.ofMinutes(10);

    /**
     * Static JWK JSON string for use when Key Vault is not configured. If set, this will be used
     * instead of generating a random key on startup. The value should be a valid JWK JSON object
     * (single key) or JWKSet JSON object (multiple keys).
     */
    private String staticJwk;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getVaultUri() {
        return vaultUri;
    }

    public void setVaultUri(String vaultUri) {
        this.vaultUri = vaultUri;
    }

    public String getJwkSecretName() {
        return jwkSecretName;
    }

    public void setJwkSecretName(String jwkSecretName) {
        this.jwkSecretName = jwkSecretName;
    }

    public Duration getCacheTtl() {
        return cacheTtl;
    }

    public void setCacheTtl(Duration cacheTtl) {
        this.cacheTtl = cacheTtl;
    }

    public String getStaticJwk() {
        return staticJwk;
    }

    public void setStaticJwk(String staticJwk) {
        this.staticJwk = staticJwk;
    }
}
