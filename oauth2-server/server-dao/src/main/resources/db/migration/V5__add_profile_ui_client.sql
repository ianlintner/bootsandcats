-- Add profile-ui client with profile scopes
-- This client is used by the Profile UI application for managing user profiles

-- Use INSERT ... ON CONFLICT to handle both id and client_id conflicts
INSERT INTO oauth2_registered_client (
    id, 
    client_id, 
    client_id_issued_at, 
    client_secret, 
    client_secret_expires_at, 
    client_name, 
    client_authentication_methods, 
    authorization_grant_types, 
    redirect_uris, 
    post_logout_redirect_uris, 
    scopes, 
    client_settings, 
    token_settings
) VALUES (
    'profile-ui-client-001', 
    'profile-ui', 
    CURRENT_TIMESTAMP, 
    NULL,  -- Public client, no secret (uses PKCE)
    NULL, 
    'Profile UI Application', 
    'none',  -- Public client
    'authorization_code,refresh_token', 
    'http://localhost:8082/oauth/callback,http://localhost:8082/,https://profile-ui.example.com/oauth/callback', 
    'http://localhost:8082/,https://profile-ui.example.com/', 
    'openid,profile,email,profile:read,profile:write', 
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":true,"settings.client.require-authorization-consent":true}', 
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.reuse-refresh-tokens":true,"settings.token.id-token-signature-algorithm":["org.springframework.security.oauth2.jose.jws.SignatureAlgorithm","ES256"],"settings.token.access-token-time-to-live":["java.time.Duration",3600.000000000],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration",86400.000000000],"settings.token.authorization-code-time-to-live":["java.time.Duration",300.000000000]}'
) ON CONFLICT (client_id) DO UPDATE SET 
    scopes = EXCLUDED.scopes,
    redirect_uris = EXCLUDED.redirect_uris,
    post_logout_redirect_uris = EXCLUDED.post_logout_redirect_uris;

-- Also add an admin client for admin users
INSERT INTO oauth2_registered_client (
    id, 
    client_id, 
    client_id_issued_at, 
    client_secret, 
    client_secret_expires_at, 
    client_name, 
    client_authentication_methods, 
    authorization_grant_types, 
    redirect_uris, 
    post_logout_redirect_uris, 
    scopes, 
    client_settings, 
    token_settings
) VALUES (
    'profile-admin-client-001', 
    'profile-admin', 
    CURRENT_TIMESTAMP, 
    NULL,  -- Public client, no secret (uses PKCE)
    NULL, 
    'Profile Admin Application', 
    'none',  -- Public client
    'authorization_code,refresh_token', 
    'http://localhost:8082/oauth/callback,http://localhost:8082/,https://profile-admin.example.com/oauth/callback', 
    'http://localhost:8082/,https://profile-admin.example.com/', 
    'openid,profile,email,profile:read,profile:write,profile:admin', 
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":true,"settings.client.require-authorization-consent":true}', 
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.reuse-refresh-tokens":true,"settings.token.id-token-signature-algorithm":["org.springframework.security.oauth2.jose.jws.SignatureAlgorithm","ES256"],"settings.token.access-token-time-to-live":["java.time.Duration",3600.000000000],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration",86400.000000000],"settings.token.authorization-code-time-to-live":["java.time.Duration",300.000000000]}'
) ON CONFLICT (client_id) DO UPDATE SET 
    scopes = EXCLUDED.scopes,
    redirect_uris = EXCLUDED.redirect_uris,
    post_logout_redirect_uris = EXCLUDED.post_logout_redirect_uris;
