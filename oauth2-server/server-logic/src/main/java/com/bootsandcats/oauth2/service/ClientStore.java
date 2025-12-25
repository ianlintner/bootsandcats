package com.bootsandcats.oauth2.service;

import java.util.List;

import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/** Storage abstraction for OAuth2 registered clients regardless of backing store. */
public interface ClientStore extends RegisteredClientRepository {

    List<RegisteredClient> findAllClients();

    void deleteByClientId(String clientId);
}
