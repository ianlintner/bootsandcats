# Chat client (chat.cat-herding.net) setup

This repo contains the Authorization Server and the Kubernetes/Istio patterns (Envoy OAuth2 + JWT native filters) used by downstream apps.

The chat application itself lives in a different repository, but **it reuses the same OAuth2 callback path** used elsewhere:

- Callback: `https://chat.cat-herding.net/_oauth2/callback`
- Logout (Envoy-managed): `https://chat.cat-herding.net/_oauth2/logout`

## 1) Authorization Server: register the chat client

The OAuth2 Authorization Server must have a confidential client registered.

In this repo, it’s seeded via Flyway migrations in `infrastructure/k8s/apps/configs/flyway-migrations-configmap.yaml`:

- `V13__add_chat_service_client.sql` seeds client `chat-service`
- `V14__update_chat_service_client_secret.sql` sets a dev/test secret (`demo-chat-service-client-secret`)

Redirect URIs:

- `https://chat.cat-herding.net/_oauth2/callback`

Post-logout redirect:

- `https://chat.cat-herding.net/`

## 2) Azure Key Vault: create secrets for the chat app

The chat app’s Istio Envoy OAuth2 filter needs:

- `chat-client-secret` (OAuth2 confidential client secret)
- `chat-oauth-hmac-secret` (cookie-signing HMAC secret)

A helper script is provided:

- `scripts/setup-chat-client-secrets.sh`

By default it uploads the **demo values** that match Flyway V14.

## 3) Kubernetes: SecretProviderClass + Envoy filters

This repo provides the manifest templates you can apply (or copy into the chat repo) to match the existing profile/github-review pattern:

- `infrastructure/k8s/secret-provider-class-chat.yaml`
  - SecretProviderClass name: `azure-keyvault-chat-secrets`
  - Reads Key Vault secrets: `chat-client-secret`, `chat-oauth-hmac-secret`

- `infrastructure/k8s/istio/envoyfilter-chat-oauth2-exchange.yaml`
  - Envoy OAuth2 filter (authorization_code exchange)

- `infrastructure/k8s/istio/requestauthentication-chat.yaml`
  - Istio JWT validation (JWKS from the auth server)

- `infrastructure/k8s/istio/envoyfilter-chat-jwt-to-headers.yaml`
  - Envoy JWT authn filter that forwards and maps claims to headers

> IMPORTANT: In the chat repo, update the `workloadSelector` / `selector.matchLabels` to match the chat deployment labels.

## Secret naming summary

| Purpose | Azure Key Vault secret name | Used by |
|---|---|---|
| OAuth2 client secret | `chat-client-secret` | Envoy OAuth2 filter token exchange |
| Cookie HMAC secret | `chat-oauth-hmac-secret` | Envoy OAuth2 filter cookie signing |

