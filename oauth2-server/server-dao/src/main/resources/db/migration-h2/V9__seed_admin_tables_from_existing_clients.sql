-- H2-friendly seeding for admin-managed scope catalog and metadata.
--
-- H2 lacks PostgreSQL's LATERAL/unnest helpers, so we seed only known system
-- scopes and client metadata.

MERGE INTO oauth2_scope (scope, description, enabled, is_system, created_by, created_at, updated_at)
KEY(scope)
VALUES ('openid', 'OpenID Connect scope', true, true, 'system', CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP());

MERGE INTO oauth2_scope (scope, description, enabled, is_system, created_by, created_at, updated_at)
KEY(scope)
VALUES ('profile', 'OpenID Connect profile scope', true, true, 'system', CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP());

MERGE INTO oauth2_scope (scope, description, enabled, is_system, created_by, created_at, updated_at)
KEY(scope)
VALUES ('email', 'OpenID Connect email scope', true, true, 'system', CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP());

MERGE INTO oauth2_scope (scope, description, enabled, is_system, created_by, created_at, updated_at)
KEY(scope)
VALUES ('read', 'Demo read scope', true, true, 'system', CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP());

MERGE INTO oauth2_scope (scope, description, enabled, is_system, created_by, created_at, updated_at)
KEY(scope)
VALUES ('write', 'Demo write scope', true, true, 'system', CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP());

MERGE INTO oauth2_scope (scope, description, enabled, is_system, created_by, created_at, updated_at)
KEY(scope)
VALUES ('api:read', 'Machine-to-machine API read scope', true, true, 'system', CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP());

MERGE INTO oauth2_scope (scope, description, enabled, is_system, created_by, created_at, updated_at)
KEY(scope)
VALUES ('api:write', 'Machine-to-machine API write scope', true, true, 'system', CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP());

MERGE INTO oauth2_scope (scope, description, enabled, is_system, created_by, created_at, updated_at)
KEY(scope)
VALUES ('profile:read', 'Profile service read scope', true, true, 'system', CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP());

MERGE INTO oauth2_scope (scope, description, enabled, is_system, created_by, created_at, updated_at)
KEY(scope)
VALUES ('profile:write', 'Profile service write scope', true, true, 'system', CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP());

-- Seed metadata for any clients already present
MERGE INTO oauth2_client_metadata (client_id, is_system, enabled, notes, created_by, created_at, updated_at)
KEY(client_id)
SELECT client_id, true, true, 'Seeded for existing client', 'system', CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()
FROM oauth2_registered_client;
