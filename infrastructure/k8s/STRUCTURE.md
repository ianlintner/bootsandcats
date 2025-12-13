# Kubernetes manifests: logical structure

This repo’s Kubernetes manifests live under `infrastructure/k8s/`.

The goal of this structure is to keep **blast radius small**, make **ownership obvious**, and keep `kustomize` composition easy.

## Current structure (incremental)

We keep `infrastructure/k8s/kustomization.yaml` as the main entry point.

- `secrets/`
  - All `SecretProviderClass` manifests (Key Vault → CSI / synced Kubernetes Secrets)
  - These are security-sensitive and usually change less often than app rollouts.

Everything else currently remains at the top level for minimal churn.

## Recommended target structure (modules)

As the manifest set grows, consider migrating to this layout:

- `base/`
  - Shared primitives used in all environments
  - `kustomization.yaml`

- `apps/`
  - `oauth2-server/` (Deployment/Service/ConfigMaps specific to oauth2-server)
  - `profile-service/`
  - `github-review-service/`

- `data/`
  - `postgres/` (CNPG operator, clusters)
  - `redis/`

- `istio/`
  - `requestauthentication/`
  - `authorizationpolicy/`
  - `envoyfilter/`
  - `virtualservices/`

- `secrets/`
  - `SecretProviderClass` manifests (Key Vault provider)
  - Optional: any non-KV secrets generation templates

- `overlays/`
  - `dev/`, `staging/`, `prod/`
  - Each overlay imports `../base` and applies environment-specific patches

## Kustomize pattern

Use:

- `base/` for common resources
- `overlays/<env>/` for:
  - image tags
  - replica counts
  - issuer URLs
  - feature flags
  - environment-specific Istio policy

This keeps PRs smaller and avoids per-env drift.

## Naming guidance

- SecretProviderClass names: `<workload>-secrets-provider` (or similar)
- Kubernetes Secret names: `<workload>-secrets` when possible
- Avoid shared “mega” secrets; prefer workload-scoped secrets.
