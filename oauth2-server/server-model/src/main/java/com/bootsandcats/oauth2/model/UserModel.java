package com.bootsandcats.oauth2.model;

import java.time.Instant;

/**
 * Base user model interface for the OAuth2 server. This represents the core user data without JPA
 * dependencies.
 */
public interface UserModel {

    Long getId();

    void setId(Long id);

    String getUsername();

    void setUsername(String username);

    String getEmail();

    void setEmail(String email);

    String getProvider();

    void setProvider(String provider);

    String getProviderId();

    void setProviderId(String providerId);

    String getName();

    void setName(String name);

    String getPictureUrl();

    void setPictureUrl(String pictureUrl);

    Instant getLastLogin();

    void setLastLogin(Instant lastLogin);
}
