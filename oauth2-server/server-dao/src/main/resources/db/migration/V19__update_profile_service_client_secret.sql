-- Update profile-service client secret to use bcrypt encoding.
-- The plaintext secret 'demo-profile-service-client-secret' is stored in Azure Key Vault
-- and loaded via the CSI Secret Store driver. This migration stores the bcrypt hash.
--
-- SECURITY:
-- - This is a demo secret suitable for dev/test environments.
-- - In production, replace with a strong secret from Azure Key Vault.
-- - The bcrypt hash was generated with: bcrypt.hashpw(b"demo-profile-service-client-secret", bcrypt.gensalt(rounds=10))

UPDATE oauth2_registered_client
SET client_secret = '{bcrypt}$2b$10$FBo7Qn2F/I4gwI7iJ0JYy.y9HGSY8Jo4r3n1mSgWZkGolZoMeaaUS'
WHERE client_id = 'profile-service';

