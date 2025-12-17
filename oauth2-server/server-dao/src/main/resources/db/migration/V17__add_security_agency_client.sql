-- Seed security-agency confidential client for Envoy authorization-code token exchange
--
-- NOTE:
-- - The Istio EnvoyFilter `security-agency-oauth2-exchange` exchanges the authorization code by POSTing
--   client_id/client_secret to /oauth2/token (client_secret_post).
-- - The redirect_uri used by that filter is: https://security-agency.cat-herding.net/_oauth2/callback
--
-- SECURITY:
-- - Replace the placeholder secret with a real secret in BOTH the EnvoyFilter and this DB row.
-- - Prefer storing an encoded secret (e.g., {bcrypt}...) in production.

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
    'security-agency-client-001',
    'security-agency',
    CURRENT_TIMESTAMP,
    '{noop}REPLACE_WITH_SECURITY_AGENCY_CLIENT_SECRET',
    NULL,
    'Security Agency Service',
    'client_secret_post',
    'authorization_code,refresh_token',
    'https://security-agency.cat-herding.net/_oauth2/callback',
    'https://security-agency.cat-herding.net/',
    'openid,profile,email',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":false,"settings.client.require-authorization-consent":false}',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.reuse-refresh-tokens":true,"settings.token.id-token-signature-algorithm":["org.springframework.security.oauth2.jose.jws.SignatureAlgorithm","ES256"],"settings.token.access-token-time-to-live":["java.time.Duration",3600.000000000],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration",86400.000000000],"settings.token.authorization-code-time-to-live":["java.time.Duration",300.000000000]}'
) ON CONFLICT (client_id) DO UPDATE SET
    client_name = EXCLUDED.client_name,
    client_authentication_methods = EXCLUDED.client_authentication_methods,
    authorization_grant_types = EXCLUDED.authorization_grant_types,
    redirect_uris = EXCLUDED.redirect_uris,
    post_logout_redirect_uris = EXCLUDED.post_logout_redirect_uris,
    scopes = EXCLUDED.scopes,
    client_settings = EXCLUDED.client_settings,
    token_settings = EXCLUDED.token_settings;

