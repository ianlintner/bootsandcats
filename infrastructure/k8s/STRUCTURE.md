# Kubernetes manifests: logical structure

This repo’s Kubernetes manifests live under `infrastructure/k8s/` with a single entry point (`kustomization.yaml`) that composes the `base/` module.

We follow a modular layout so that each team understands ownership, kustomize can enforce file containment, and upgrades stay localized.

## Module layout

- `base/`
  - Shared helpers (namespace, labels, generator options) used by every environment
  - Points at `apps/`, `data/`, `istio/`, and `secrets/`
- `apps/`
  - One per workload (`oauth2-server`, `profile-service`, `github-review-service`)
  - Shared resources (`configs/`) such as `flyway-migrations-configmap.yaml` and `pdb.yaml`
- `data/`
  - CNPG/PostgreSQL + Redis manifests are grouped here (`cnpg-operator.yaml`, `postgres.yaml`, `redis.yaml`)
- `istio/`
  - Istio policy, RequestAuthentication, EnvoyFilter, ServiceEntry, and SDS ConfigMap artifacts live here
- `secrets/`
  - Least-privileged `SecretProviderClass` manifests (one per workload, plus templates)
- `overlays/`
  - Environment-specific patches (dev, staging, prod) compose `base/`

## Kustomize best practices

- Keep each module’s `resources` array pointing to files within the module directory (no `../` escapes). This lets `kubectl kustomize` render anywhere under the module tree.
- Use `apps/configs/` for shared operational resources so workloads can consume them via a single `apps` kustomization.
- Prefer per-workload `SecretProviderClass` manifests in `secrets/` rather than a monolithic bundle.

## Naming reminders

- SecretProviderClass names: `<workload>-secrets-provider`
- Kubernetes Secret names: `<workload>-secrets`
- Avoid sharing secrets across unrelated workloads; keep blast radius small.
