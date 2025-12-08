#!/bin/bash

# Script to add profile-ui client to the OAuth2 database
# This creates the necessary client registration for the Profile UI application

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

echo "Adding profile-ui clients to the OAuth2 database..."

psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" <<EOF
-- Add profile-ui client with profile scopes
-- This client is used by the Profile UI application for managing user profiles

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
    NULL,
    NULL, 
    'Profile UI Application', 
    'none',
    'authorization_code,refresh_token', 
    'http://localhost:8082/oauth/callback,http://localhost:8082/', 
    'http://localhost:8082/', 
    'openid,profile,email,profile:read,profile:write', 
    '{"@class":"java.util.Collections\$UnmodifiableMap","settings.client.require-proof-key":true,"settings.client.require-authorization-consent":true}', 
    '{"@class":"java.util.Collections\$UnmodifiableMap","settings.token.reuse-refresh-tokens":true,"settings.token.id-token-signature-algorithm":["org.springframework.security.oauth2.jose.jws.SignatureAlgorithm","ES256"],"settings.token.access-token-time-to-live":["java.time.Duration",3600.000000000],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration",86400.000000000],"settings.token.authorization-code-time-to-live":["java.time.Duration",300.000000000]}'
) ON CONFLICT (id) DO UPDATE SET 
    scopes = EXCLUDED.scopes,
    redirect_uris = EXCLUDED.redirect_uris,
    post_logout_redirect_uris = EXCLUDED.post_logout_redirect_uris;

-- Also add an admin client for admin users with profile:admin scope
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
    NULL,
    NULL, 
    'Profile Admin Application', 
    'none',
    'authorization_code,refresh_token', 
    'http://localhost:8082/oauth/callback,http://localhost:8082/', 
    'http://localhost:8082/', 
    'openid,profile,email,profile:read,profile:write,profile:admin', 
    '{"@class":"java.util.Collections\$UnmodifiableMap","settings.client.require-proof-key":true,"settings.client.require-authorization-consent":true}', 
    '{"@class":"java.util.Collections\$UnmodifiableMap","settings.token.reuse-refresh-tokens":true,"settings.token.id-token-signature-algorithm":["org.springframework.security.oauth2.jose.jws.SignatureAlgorithm","ES256"],"settings.token.access-token-time-to-live":["java.time.Duration",3600.000000000],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration",86400.000000000],"settings.token.authorization-code-time-to-live":["java.time.Duration",300.000000000]}'
) ON CONFLICT (id) DO UPDATE SET 
    scopes = EXCLUDED.scopes,
    redirect_uris = EXCLUDED.redirect_uris,
    post_logout_redirect_uris = EXCLUDED.post_logout_redirect_uris;

SELECT 'Profile UI clients registered successfully!' as result;
SELECT id, client_id, client_name, scopes FROM oauth2_registered_client WHERE client_id LIKE 'profile%';
EOF

echo ""
echo "Profile UI clients have been registered."
echo ""
echo "Available clients:"
echo "  - profile-ui: For regular users (profile:read, profile:write)"
echo "  - profile-admin: For admin users (includes profile:admin)"
echo ""
echo "Note: The JWT customizer automatically adds profile scopes based on user role."
echo "      All users get profile:read and profile:write."
echo "      Users with ADMIN role also get profile:admin."
