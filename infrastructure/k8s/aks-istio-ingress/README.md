# AKS Istio Ingress: secure-subdomain OAuth2 (oauth2-proxy)

This folder wires the **unified ingress OAuth2 client** (`secure-subdomain-client`) secrets from **Azure Key Vault** (`inker-kv`) into Kubernetes and deploys **oauth2-proxy** in the ingress namespace.

The ingress gateway enforces auth by calling oauth2-proxy (see `infrastructure/k8s/istio/envoyfilter-secure-subdomain-oauth2.yaml`). Envoy no longer consumes client secrets via SDS.

## What it creates

- `SecretProviderClass` `secure-subdomain-oauth-secrets-provider`
  - Pulls:
    - `secure-subdomain-client-secret`
    - `secure-subdomain-oauth-hmac-secret`
  - Syncs them into Kubernetes Secret `secure-subdomain-oauth-secrets` (keys: `client-secret`, `hmac-secret`).

- `Deployment`/`Service` `oauth2-proxy`
  - Mounts the SecretProviderClass as a CSI volume at `/mnt/secrets-store`
  - Reads:
    - client secret from `/mnt/secrets-store/secure-subdomain-client-secret`
    - cookie secret from `/mnt/secrets-store/secure-subdomain-oauth-hmac-secret`

## Applying

This namespace wiring is deployed via:

- `kubectl apply -k infrastructure/k8s/aks-istio-ingress/`

Then apply the gateway enforcement and routing (EnvoyFilters + VirtualServices) via:

- `kubectl apply -k infrastructure/k8s/istio/`

## Notes

- The previous SDS rendering/mount approach is now deprecated in favor of oauth2-proxy.
- Ensure the `secure-subdomain-oauth-hmac-secret` value is compatible with oauth2-proxy cookie secret requirements.
