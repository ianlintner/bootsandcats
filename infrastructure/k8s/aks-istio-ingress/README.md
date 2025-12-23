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

You must mount `secure-subdomain-oauth-sds` into the **AKS-managed** ingress gateway Pod at
`/etc/istio/oauth2`.

Because the gateway Deployment is managed by the AKS Istio addon/Helm release, it is **not** rendered
by this repo’s `kustomize build infrastructure/k8s/aks-istio-ingress` output. That means you typically
apply the mount as a **runtime patch** (manual operation) rather than expecting kustomize to emit it.

A template patch is provided in:

- `patch-ingressgateway-mount-secure-subdomain-oauth-sds.yaml`

Update `metadata.name` in that file to match your gateway Deployment (example: `aks-istio-ingressgateway-external-asm-1-27`).

To mount `secure-subdomain-oauth-sds` into the ingress gateway at `/etc/istio/oauth2`, use the
template patch in:

- `patch-ingressgateway-mount-secure-subdomain-oauth-sds.yaml`

or run an imperative patch (recommended) that auto-detects the gateway Deployment by label.

Example approach:

1. Find the external gateway Deployment (label `istio=aks-istio-ingressgateway-external`).
2. Add the Secret-backed volume + mount to the `istio-proxy` container.

This repo includes only the template patch; the actual deployment name may include an ASM suffix
(for example `aks-istio-ingressgateway-external-asm-1-27`).
