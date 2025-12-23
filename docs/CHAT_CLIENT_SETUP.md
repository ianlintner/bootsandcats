# Chat client (chat.cat-herding.net) setup

This repo contains the Authorization Server and the Kubernetes/Istio patterns (Envoy OAuth2 + JWT native filters) used by downstream apps.

The chat application itself lives in a different repository, but **it reuses the same OAuth2 callback path** used elsewhere:

- Callback: `https://chat.cat-herding.net/_oauth2/callback`
- Logout (Envoy-managed): `https://chat.cat-herding.net/_oauth2/logout`

## 1) Authorization Server: register the browser SSO client

Interactive browser login is enforced at the **Istio ingress gateway** using a **single unified OAuth2 client**:

- Client ID: `secure-subdomain-client`
- Callback path (handled at gateway): `/_oauth2/callback`
- Cookie domain: `.cat-herding.net`

Client registration is covered in `docs/SECURE_SUBDOMAIN_OAUTH2.md`.

## 2) Azure Key Vault: create secrets for the ingress gateway

The ingress gateway’s Envoy OAuth2 filter needs:

- `secure-subdomain-client-secret` (OAuth2 confidential client secret)
- `secure-subdomain-oauth-hmac-secret` (cookie-signing HMAC secret)

These are pulled and rendered via the manifests under `infrastructure/k8s/aks-istio-ingress/`.

## 3) Kubernetes: gateway Envoy filters

Chat does not need a per-app OAuth2 exchange EnvoyFilter. Apply the centralized gateway configuration:

- `kubectl apply -k infrastructure/k8s/aks-istio-ingress/`
- `kubectl apply -k infrastructure/k8s/istio/`

For JWT-to-headers mapping or RequestAuthentication, keep using the Istio resources under `infrastructure/k8s/istio/`.

## Secret naming summary

| Purpose | Azure Key Vault secret name | Used by |
|---|---|---|
| OAuth2 client secret | `secure-subdomain-client-secret` | Ingress gateway Envoy OAuth2 filter token exchange |
| Cookie HMAC secret | `secure-subdomain-oauth-hmac-secret` | Ingress gateway Envoy OAuth2 filter cookie signing |

