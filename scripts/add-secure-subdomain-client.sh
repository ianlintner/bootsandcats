#!/bin/bash

# Script to add secure-subdomain client to the OAuth2 database
# This creates a single client for all apps under *.secure.cat-herding.net

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

# Generate secure random secrets
CLIENT_SECRET=$(openssl rand -base64 32)
HMAC_SECRET=$(openssl rand -base64 32)

echo "Adding secure-subdomain client to the OAuth2 database..."
echo "Client ID: secure-subdomain-client"
echo ""

psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" <<EOF
-- Add secure-subdomain client for all apps under *.secure.cat-herding.net
-- This client uses wildcard redirect URIs to support any subdomain

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
    'secure-subdomain-client-001', 
    'secure-subdomain-client', 
    CURRENT_TIMESTAMP, 
    '{bcrypt}$(echo -n "$CLIENT_SECRET" | openssl passwd -6 -stdin)',
    NULL, 
    'Secure Subdomain - All Apps', 
    'client_secret_post',
    'authorization_code,refresh_token', 
    'https://*.secure.cat-herding.net/_oauth2/callback,http://localhost:*/_oauth2/callback', 
    'https://*.secure.cat-herding.net/_oauth2/logout,http://localhost:*/_oauth2/logout', 
    'openid,profile,email', 
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":true,"settings.client.require-authorization-consent":false}', 
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.reuse-refresh-tokens":false,"settings.token.id-token-signature-algorithm":["org.springframework.security.oauth2.jose.jws.SignatureAlgorithm","ES256"],"settings.token.access-token-time-to-live":["java.time.Duration",900.000000000],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration",3600.000000000],"settings.token.authorization-code-time-to-live":["java.time.Duration",300.000000000]}'
) ON CONFLICT (client_id) DO UPDATE SET
    client_secret = EXCLUDED.client_secret,
    redirect_uris = EXCLUDED.redirect_uris,
    post_logout_redirect_uris = EXCLUDED.post_logout_redirect_uris,
    scopes = EXCLUDED.scopes,
    client_settings = EXCLUDED.client_settings,
    token_settings = EXCLUDED.token_settings;

SELECT id, client_id, client_name FROM oauth2_registered_client WHERE client_id = 'secure-subdomain-client';
EOF

echo ""
echo "✓ Client registered successfully!"
echo ""
echo "Save these secrets (you'll need them for Kubernetes secrets):"
echo "CLIENT_SECRET=$CLIENT_SECRET"
echo "HMAC_SECRET=$HMAC_SECRET"
echo ""
echo "To create Kubernetes secrets, run:"
echo ""
echo "kubectl create secret generic secure-subdomain-oauth-secrets \\"
echo "  --from-literal=client-secret='$CLIENT_SECRET' \\"
echo "  --from-literal=hmac-secret='$HMAC_SECRET' \\"
echo "  -n aks-istio-ingress \\"
echo "  --dry-run=client -o yaml | kubectl apply -f -"
echo ""
echo "Then update the SDS ConfigMap with these values."
