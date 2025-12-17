-- Update slop-detector confidential client secret to match the Istio EnvoyFilter code-exchange filter.
--
-- NOTE:
-- - V15 initially inserted a placeholder secret.
-- - This migration updates the secret without modifying V15 (avoids Flyway checksum drift).
--
-- SECURITY:
-- - This is a demo secret suitable for dev/test environments.
-- - In production, replace this with a secret sourced from Azure Key Vault and
--   store it encoded (e.g., {bcrypt}...).

UPDATE oauth2_registered_client
SET client_secret = '{noop}demo-slop-detector-client-secret'
WHERE client_id = 'slop-detector';

