-- Update security-agency confidential client secret to match the Istio EnvoyFilter code-exchange filter.
--
-- NOTE:
-- - V17 initially inserted a placeholder secret.
-- - This migration updates the secret without modifying V17 (avoids Flyway checksum drift).
--
-- SECURITY:
-- - This is a demo secret suitable for dev/test environments.
-- - In production, replace this with a secret sourced from Azure Key Vault and
--   store it encoded (e.g., {bcrypt}...).

UPDATE oauth2_registered_client
SET client_secret = '{noop}demo-security-agency-client-secret'
WHERE client_id = 'security-agency';
