-- Update github-review-service confidential client secret to match the Istio EnvoyFilter Lua code-exchange filter.
--
-- NOTE:
-- - V10 initially inserted a placeholder secret.
-- - This migration updates the secret without modifying V10 (avoids Flyway checksum drift).
--
-- SECURITY:
-- - This is a demo secret suitable for dev/test environments.
-- - In production, replace this with a secret sourced from a proper secrets manager and
--   store it encoded (e.g., {bcrypt}...).

UPDATE oauth2_registered_client
SET client_secret = '{noop}demo-github-review-client-secret'
WHERE client_id = 'github-review-service';

