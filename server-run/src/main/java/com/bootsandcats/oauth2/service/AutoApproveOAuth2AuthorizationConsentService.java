package com.bootsandcats.oauth2.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/**
 * OAuth2AuthorizationConsentService that auto-approves all scopes for every client.
 *
 * <p>This ensures that the framework never triggers the consent interstitial HTML page by eagerly
 * persisting an {@link OAuth2AuthorizationConsent} record the first time a client is encountered.
 */
public class AutoApproveOAuth2AuthorizationConsentService
        implements OAuth2AuthorizationConsentService {

    private static final Logger log =
            LoggerFactory.getLogger(AutoApproveOAuth2AuthorizationConsentService.class);

    private final OAuth2AuthorizationConsentService delegate;
    private final RegisteredClientRepository registeredClientRepository;

    public AutoApproveOAuth2AuthorizationConsentService(
            OAuth2AuthorizationConsentService delegate,
            RegisteredClientRepository registeredClientRepository) {
        this.delegate = delegate;
        this.registeredClientRepository = registeredClientRepository;
    }

    @Override
    public void save(OAuth2AuthorizationConsent authorizationConsent) {
        delegate.save(authorizationConsent);
    }

    @Override
    public void remove(OAuth2AuthorizationConsent authorizationConsent) {
        delegate.remove(authorizationConsent);
    }

    @Override
    public OAuth2AuthorizationConsent findById(String registeredClientId, String principalName) {
        OAuth2AuthorizationConsent authorizationConsent =
                delegate.findById(registeredClientId, principalName);
        if (authorizationConsent != null) {
            return authorizationConsent;
        }

        RegisteredClient registeredClient = registeredClientRepository.findById(registeredClientId);
        if (registeredClient == null) {
            log.warn(
                    "Requested auto-consent for unknown registered client id='{}' (principal='{}')",
                    registeredClientId,
                    principalName);
            return null;
        }

        OAuth2AuthorizationConsent.Builder builder =
                OAuth2AuthorizationConsent.withId(registeredClientId, principalName);
        registeredClient.getScopes().forEach(builder::scope);
        OAuth2AuthorizationConsent autoApprovedConsent = builder.build();

        log.debug(
                "Auto-approving scopes {} for client='{}' principal='{}'",
                autoApprovedConsent.getScopes(),
                registeredClient.getClientId(),
                principalName);
        delegate.save(autoApprovedConsent);
        return autoApprovedConsent;
    }
}
