package com.bootsandcats.profileui.security;

import java.net.URI;
import java.util.Optional;

import io.micronaut.security.errors.PriorToLoginPersistence;
import jakarta.inject.Singleton;

/**
 * No-op PriorToLoginPersistence to satisfy Micronaut security beans when OAuth2 login is not used.
 */
@Singleton
@SuppressWarnings({"rawtypes", "unchecked"})
public class NoOpPriorToLoginPersistence implements PriorToLoginPersistence {
    public void persist(Object request, Object response, Object context) {}

    public Optional retrieve(Object request) {
        return Optional.empty();
    }

    public void remove(Object request) {}

    public Optional<URI> getOriginalUri(Object request, Object response) {
        return Optional.empty();
    }

    public void onUnauthorized(Object request, Object response) {}
}
