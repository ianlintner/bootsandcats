-- Seed admin-managed scope catalog and metadata from existing registered clients
--
-- This migration makes the admin UI usable immediately in environments where
-- oauth2_registered_client is already populated (either by previous migrations
-- or by provisioning scripts).

-- 1) Ensure common system scopes exist
INSERT INTO oauth2_scope (scope, description, enabled, is_system, created_by, created_at, updated_at)
VALUES
    ('openid', 'OpenID Connect scope', true, true, 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('profile', 'OpenID Connect profile scope', true, true, 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('email', 'OpenID Connect email scope', true, true, 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('read', 'Demo read scope', true, true, 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('write', 'Demo write scope', true, true, 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('api:read', 'Machine-to-machine API read scope', true, true, 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('api:write', 'Machine-to-machine API write scope', true, true, 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('profile:read', 'Profile service read scope', true, true, 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('profile:write', 'Profile service write scope', true, true, 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (scope) DO NOTHING;

-- 2) Seed any scopes already present on registered clients (as system scopes)
WITH existing_scopes AS (
    SELECT DISTINCT trim(s) AS scope
    FROM oauth2_registered_client rc
    CROSS JOIN LATERAL unnest(string_to_array(rc.scopes, ',')) s
    WHERE trim(s) <> ''
)
INSERT INTO oauth2_scope (scope, description, enabled, is_system, created_by, created_at, updated_at)
SELECT es.scope, null, true, true, 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM existing_scopes es
ON CONFLICT (scope) DO NOTHING;

-- 3) Seed metadata rows for existing clients (defaults to system+enabled)
INSERT INTO oauth2_client_metadata (client_id, is_system, enabled, notes, created_by, created_at, updated_at)
SELECT rc.client_id, true, true, 'Seeded for existing client', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM oauth2_registered_client rc
ON CONFLICT (client_id) DO NOTHING;

-- 4) Seed normalized client-to-scope mapping for existing clients
INSERT INTO oauth2_client_scope (client_id, scope, created_by, created_at)
SELECT rc.client_id, trim(s) AS scope, 'system', CURRENT_TIMESTAMP
FROM oauth2_registered_client rc
CROSS JOIN LATERAL unnest(string_to_array(rc.scopes, ',')) s
WHERE trim(s) <> ''
ON CONFLICT (client_id, scope) DO NOTHING;
