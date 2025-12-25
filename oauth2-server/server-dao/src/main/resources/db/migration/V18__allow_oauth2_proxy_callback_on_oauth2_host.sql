-- Allow oauth2-proxy to use a fixed redirect URL on the authorization-server host.
--
-- Without this, authorization requests using:
--   https://oauth2.cat-herding.net/_oauth2/callback
-- will fail with:
--   invalid_request: redirect_uri
--
-- NOTE: redirect_uris are stored as a comma-separated list.

UPDATE oauth2_registered_client
SET redirect_uris = CASE
    WHEN redirect_uris IS NULL OR redirect_uris = '' THEN 'https://oauth2.cat-herding.net/_oauth2/callback'
    WHEN redirect_uris LIKE '%https://oauth2.cat-herding.net/_oauth2/callback%' THEN redirect_uris
    ELSE redirect_uris || ',https://oauth2.cat-herding.net/_oauth2/callback'
END
WHERE client_id = 'secure-subdomain-client';
