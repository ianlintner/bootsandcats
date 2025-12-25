-- H2 parity migration: allow oauth2-proxy callback on the authorization-server host.
-- Safe if the row doesn't exist yet (no-op).

UPDATE oauth2_registered_client
SET redirect_uris = CASE
    WHEN redirect_uris IS NULL OR redirect_uris = '' THEN 'https://oauth2.cat-herding.net/_oauth2/callback'
    WHEN redirect_uris LIKE '%https://oauth2.cat-herding.net/_oauth2/callback%' THEN redirect_uris
    ELSE redirect_uris || ',https://oauth2.cat-herding.net/_oauth2/callback'
END
WHERE client_id = 'secure-subdomain-client';
