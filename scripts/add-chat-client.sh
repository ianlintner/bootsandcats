#!/bin/bash

# Script to add chat-backend client to the OAuth2 database
# This creates the necessary client registration for the Chat Backend application

set -e

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-oauth2db}"
DB_USER="${DB_USER:-oauth2user}"

# Prompt for password if not set
if [ -z "$PGPASSWORD" ]; then
    echo "Enter PostgreSQL password for $DB_USER:"
    read -s PGPASSWORD
    export PGPASSWORD
fi

echo "Adding chat-backend client to the OAuth2 database..."

psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" <<EOF
-- Add chat-backend client for the Chat application
-- This client is used by the Chat Backend application via Envoy OAuth2 filter

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
    '{noop}demo-chat-backend-client-secret',
    NULL, 
    'Chat Backend Application', 
    'client_secret_post',
    'authorization_code,refresh_token', 
    'https://chat.cat-herding.net/_oauth2/callback,http://localhost:5001/_oauth2/callback', 
    'https://chat.cat-herding.net/_oauth2/logout,http://localhost:5001/_oauth2/logout', 
    'openid,profile,email', 
    '{"@class":"java.util.Collections\$UnmodifiableMap","settings.client.require-proof-key":false,"settings.client.require-authorization-consent":false}',
    '{"@class":"java.util.Collections\$UnmodifiableMap","settings.token.reuse-refresh-tokens":true,"settings.token.id-token-signature-algorithm":["org.springframework.security.oauth2.jose.jws.SignatureAlgorithm","ES256"],"settings.token.access-token-time-to-live":["java.time.Duration",3600.000000000],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration",86400.000000000],"settings.token.authorization-code-time-to-live":["java.time.Duration",300.000000000]}'
) ON CONFLICT (id) DO UPDATE SET 
    scopes = EXCLUDED.scopes,
    redirect_uris = EXCLUDED.redirect_uris,
    post_logout_redirect_uris = EXCLUDED.post_logout_redirect_uris,
    client_settings = EXCLUDED.client_settings;

SELECT 'Chat backend client registered successfully!' as result;
SELECT id, client_id, client_name, scopes, redirect_uris FROM oauth2_registered_client WHERE client_id = 'chat-backend';
EOF

echo ""
echo "Chat backend client has been registered."
echo ""
echo "Client details:"
echo "  - client_id: chat-backend"
echo "  - Scopes: openid, profile, email"
echo "  - Redirect URIs: https://chat.cat-herding.net/_oauth2/callback"
echo "  - PKCE: Not required (can be enabled server-side)"
echo "  - Authorization consent: Not required"
echo ""
echo "Note: Make sure the client_secret in Key Vault (chat-client-secret) matches the value stored for client_id 'chat-backend'."
