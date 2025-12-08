package com.bootsandcats.oauth2.model;

import java.time.Instant;

/**
 * Base registered client model interface for the OAuth2 server.
 * This represents the core client data without JPA dependencies.
 */
public interface RegisteredClientModel {
    
    String getId();
    void setId(String id);
    
    String getClientId();
    void setClientId(String clientId);
    
    Instant getClientIdIssuedAt();
    void setClientIdIssuedAt(Instant clientIdIssuedAt);
    
    String getClientSecret();
    void setClientSecret(String clientSecret);
    
    Instant getClientSecretExpiresAt();
    void setClientSecretExpiresAt(Instant clientSecretExpiresAt);
    
    String getClientName();
    void setClientName(String clientName);
    
    String getClientAuthenticationMethods();
    void setClientAuthenticationMethods(String clientAuthenticationMethods);
    
    String getAuthorizationGrantTypes();
    void setAuthorizationGrantTypes(String authorizationGrantTypes);
    
    String getRedirectUris();
    void setRedirectUris(String redirectUris);
    
    String getPostLogoutRedirectUris();
    void setPostLogoutRedirectUris(String postLogoutRedirectUris);
    
    String getScopes();
    void setScopes(String scopes);
    
    String getClientSettings();
    void setClientSettings(String clientSettings);
    
    String getTokenSettings();
    void setTokenSettings(String tokenSettings);
}
