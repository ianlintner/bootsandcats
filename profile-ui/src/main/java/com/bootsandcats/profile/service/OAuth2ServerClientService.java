package com.bootsandcats.profile.service;

import com.bootsandcats.oauth2.client.OAuth2ServerHealth;
import com.bootsandcats.oauth2.client.OAuth2ServerHttpClient;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Thin facade around the shared OAuth2 server HTTP client to expose resilient operations for the
 * Profile UI.
 */
@Service
public class OAuth2ServerClientService {
    private static final Logger log = LoggerFactory.getLogger(OAuth2ServerClientService.class);

    private final OAuth2ServerHttpClient client;

    public OAuth2ServerClientService(OAuth2ServerHttpClient client) {
        this.client = client;
    }

    public Optional<OAuth2ServerHealth> fetchHealth() {
        try {
            return Optional.ofNullable(client.fetchHealth());
        } catch (Exception ex) {
            log.warn("Failed to fetch OAuth2 server health", ex);
            return Optional.empty();
        }
    }

    public Optional<Map<String, Object>> fetchUserInfo(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(client.fetchUserInfo(accessToken));
        } catch (Exception ex) {
            log.warn("Failed to fetch userinfo from OAuth2 server", ex);
            return Optional.empty();
        }
    }
}
