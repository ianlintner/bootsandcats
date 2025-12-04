#!/bin/bash

# Generate bcrypt hash for test secret
BCRYPT_HASH=$(python3 -c "import bcrypt; secret='test-secret-123'; hashed=bcrypt.hashpw(secret.encode('utf-8'), bcrypt.gensalt(12)); print(hashed.decode('utf-8'))")

echo "Generated bcrypt hash: $BCRYPT_HASH"

# Insert test client into database
PGPASSWORD='nBJxMaVh1pkyGeFfL/C8GudDvYoN6RFGJ+K1W7AwgE8=' psql -h localhost -p 5432 -U oauth2user -d oauth2db <<EOF
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
    'test-client-001', 
    'test-client', 
    CURRENT_TIMESTAMP, 
    '{bcrypt}$BCRYPT_HASH', 
    NULL, 
    'Test Client', 
    'client_secret_basic', 
    'client_credentials', 
    '', 
    '', 
    'read,write', 
    '{"@class":"java.util.Collections\$UnmodifiableMap","settings.client.require-proof-key":false,"settings.client.require-authorization-consent":false}', 
    '{"@class":"java.util.Collections\$UnmodifiableMap","settings.token.reuse-refresh-tokens":true,"settings.token.id-token-signature-algorithm":["org.springframework.security.oauth2.jose.jws.SignatureAlgorithm","RS256"],"settings.token.access-token-time-to-live":["java.time.Duration",300.000000000],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration",3600.000000000],"settings.token.authorization-code-time-to-live":["java.time.Duration",300.000000000]}'
) ON CONFLICT (id) DO UPDATE SET client_secret = EXCLUDED.client_secret;
EOF

echo "Test client inserted/updated: test-client with secret: test-secret-123"
