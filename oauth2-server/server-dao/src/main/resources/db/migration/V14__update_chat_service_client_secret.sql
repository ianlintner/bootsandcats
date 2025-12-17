-- Update chat-backend confidential client secret to a known demo value.
--
-- NOTE:
-- - V13 initially inserted a placeholder secret.
-- - This migration updates the secret without modifying V13 (avoids Flyway checksum drift).
--
-- SECURITY:
-- - This is a demo secret suitable for dev/test environments.
-- - In production, replace with a strong secret from Azure Key Vault and store it encoded (e.g., {bcrypt}...).

UPDATE oauth2_registered_client
SET client_secret = '{noop}demo-chat-backend-client-secret'
WHERE client_id = 'chat-backend';

