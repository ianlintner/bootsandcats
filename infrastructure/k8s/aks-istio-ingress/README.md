# AKS Istio Ingress: secure-subdomain OAuth2 secrets

This folder wires the **unified ingress OAuth2 client** (`secure-subdomain-client`) secrets from **Azure Key Vault** (`inker-kv`) into Kubernetes, and renders the **Envoy SDS files** expected by `envoyfilter-secure-subdomain-oauth2.yaml`.

## What it creates

- `SecretProviderClass` `secure-subdomain-oauth-secrets-provider`
  - Pulls:
    - `secure-subdomain-client-secret`
    - `secure-subdomain-oauth-hmac-secret`
  - Syncs them into Kubernetes Secret `secure-subdomain-oauth-secrets` (keys: `client-secret`, `hmac-secret`).

- `CronJob` `secure-subdomain-oauth-sds-renderer`
  - Mounts the SecretProviderClass (to trigger KV fetch/sync).
  - Creates/updates Kubernetes Secret `secure-subdomain-oauth-sds` with two keys:
    - `secure-subdomain-oauth-token.yaml`
    - `secure-subdomain-oauth-hmac.yaml`

Those two keys are the files Envoy loads from:

- `/etc/istio/oauth2/secure-subdomain-oauth-token.yaml`
- `/etc/istio/oauth2/secure-subdomain-oauth-hmac.yaml`

## Mounting into the ingress gateway

`secure-subdomain-oauth-sds` is mounted into the ingress gateway at `/etc/istio/oauth2` via the
`patch-ingressgateway-mount-secure-subdomain-oauth-sds.yaml` strategic-merge patch. This patch is
referenced directly from `kustomization.yaml`, so applying the `aks-istio-ingress` overlay will
automatically wire the volume/volumeMount into the `aks-istio-ingressgateway-external` Deployment.

If your AKS-managed gateway Deployment name differs, update `metadata.name` inside the patch before
running `kustomize build`.
