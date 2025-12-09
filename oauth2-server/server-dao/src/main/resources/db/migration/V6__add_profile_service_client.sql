-- Seed profile-service confidential client for profile service callbacks
-- Uses Flyway placeholder profile_service_client_secret to avoid hard-coding secrets
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
    'profile-service-client-001',
    'profile-service',
    CURRENT_TIMESTAMP,
    '{noop}profile-service-placeholder-secret',
    NULL,
    'Profile Service',
    'client_secret_basic,client_secret_post',
    'authorization_code,refresh_token',
    'https://profile.cat-herding.net/',
    'https://profile.cat-herding.net/',
    'openid,profile,email,profile:read,profile:write',
    '{}',
    '{}'
) ON CONFLICT (client_id) DO UPDATE SET 
    client_authentication_methods = EXCLUDED.client_authentication_methods,
    authorization_grant_types = EXCLUDED.authorization_grant_types,
    redirect_uris = EXCLUDED.redirect_uris,
    post_logout_redirect_uris = EXCLUDED.post_logout_redirect_uris,
    scopes = EXCLUDED.scopes;
