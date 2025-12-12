-- Align downstream OAuth2 clients with the Envoy OAuth2 filter callback paths
-- and ensure the tokens can be exchanged with the expected client secrets.

UPDATE oauth2_registered_client
SET
    redirect_uris = 'https://profile.cat-herding.net/_oauth2/callback,https://profile.cat-herding.net/',
    post_logout_redirect_uris = 'https://profile.cat-herding.net/',
    client_secret = '{noop}demo-profile-service-client-secret'
WHERE client_id = 'profile-service';

UPDATE oauth2_registered_client
SET
    redirect_uris = 'https://gh-review.cat-herding.net/_oauth2/callback',
    post_logout_redirect_uris = 'https://gh-review.cat-herding.net/'
WHERE client_id = 'github-review-service';
