-- Seed chat-backend confidential client for Envoy authorization-code token exchange
--
-- The chat application will run at:
--   https://chat.cat-herding.net
-- and uses the same Envoy OAuth2 callback path:
--   https://chat.cat-herding.net/_oauth2/callback
--
-- NOTE:
-- - The Istio EnvoyFilter `chat-oauth2-exchange` exchanges the authorization code by POSTing
--   client_id/client_secret to /oauth2/token (client_secret_post).
--
-- SECURITY:
-- - V14 updates this placeholder secret to a demo value for dev/test.
-- - In production, replace with a strong secret (stored in Azure Key Vault) and store it encoded (e.g., {bcrypt}...).

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
        'chat-backend-client-001',
        'chat-backend',
        CURRENT_TIMESTAMP,
    '{noop}REPLACE_WITH_CHAT_BACKEND_CLIENT_SECRET',
        NULL,
        'Chat Service',
        'client_secret_post',
        'authorization_code,refresh_token',
        'https://chat.cat-herding.net/_oauth2/callback',
        'https://chat.cat-herding.net/',
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

