package com.bootsandcats.oauth2.service;

import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Service;

import com.bootsandcats.oauth2.model.ClientMetadataEntity;
import com.bootsandcats.oauth2.repository.ClientMetadataRepository;

/**
 * A {@link RegisteredClientRepository} decorator that enforces admin-managed enable/disable state.
 *
 * <p>This keeps Spring Authorization Server runtime behavior unchanged while allowing admin code to
 * bypass the filter by injecting {@link JpaRegisteredClientRepository} directly.
 */
@Service
@Primary
public class FilteringRegisteredClientRepository implements RegisteredClientRepository {

    private final JpaRegisteredClientRepository delegate;
    private final ClientMetadataRepository clientMetadataRepository;

    public FilteringRegisteredClientRepository(
            JpaRegisteredClientRepository delegate, ClientMetadataRepository clientMetadataRepository) {
        this.delegate = delegate;
        this.clientMetadataRepository = clientMetadataRepository;
    }

    @Override
    public void save(RegisteredClient registeredClient) {
        delegate.save(registeredClient);
    }

    @Override
    public RegisteredClient findById(String id) {
        RegisteredClient client = delegate.findById(id);
        if (client == null) {
            return null;
        }
        return isEnabled(client.getClientId()) ? client : null;
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        RegisteredClient client = delegate.findByClientId(clientId);
        if (client == null) {
            return null;
        }
        return isEnabled(client.getClientId()) ? client : null;
    }

    private boolean isEnabled(String clientId) {
        // Default: enabled unless explicitly disabled.
        ClientMetadataEntity meta = clientMetadataRepository.findById(clientId).orElse(null);
        if (meta == null) {
            return true;
        }
        return meta.isEnabled();
    }
}
