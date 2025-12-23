# Kubernetes Deployment

This directory contains Kubernetes manifests for deploying the OAuth2 Authorization Server to AKS (Azure Kubernetes Service).

## Prerequisites

- AKS cluster "bigboy" configured and accessible
- Azure Container Registry "gabby" with push permissions
- kubectl configured with AKS context
- **Secrets Store CSI Driver** and **Azure Key Vault Provider** installed (for keystore mounting)
- **Managed Identity / Workload Identity** with access to Azure Key Vault `inker-kv`

## Manifests

This repo deploys via `kustomization.yaml` (see `infrastructure/k8s/kustomization.yaml`). The key manifests to know about:

- `oauth2-server-deployment.yaml`: the Spring Authorization Server.
- `secrets/secret-provider-class-oauth2-server.yaml`: Key Vault-backed secrets for the OAuth2 server.
- `secrets/secret-provider-class-profile-service.yaml`: Key Vault-backed secrets for profile-service.
- `secrets/secret-provider-class-redis.yaml`: Key Vault-backed secrets for Redis (password).
- `secrets/secret-provider-class-chat.yaml`: Key Vault-backed secrets for the chat OAuth2 exchange filter.
- `secrets/secret-provider-class-github-review.yaml`: Key Vault-backed secrets for the GitHub review service.
- **Istio gateway OAuth2 SSO (secure subdomain)**: `istio/envoyfilter-secure-subdomain-oauth2.yaml` protects `*.secure.cat-herding.net` at the ingress gateway and sets cookies for the apex domain `.cat-herding.net` so sessions can be shared across subdomains.
- **Istio gateway OAuth2 SSO (opt-in allowlist)**: `istio/envoyfilter-oauth2-allowlist.yaml` enables OAuth2 enforcement only for explicitly listed non-secure hosts (see `docs/SECURE_SUBDOMAIN_OAUTH2.md`).

### SecretProviderClass (Azure Key Vault)

We use the Secrets Store CSI driver + Azure Key Vault provider.

- A `SecretProviderClass` defines which Key Vault objects to project.
- Pods reference the class via a CSI volume (`secrets-store.csi.k8s.io`).
- Optionally, a `SecretProviderClass` can also **sync** secrets into a namespace-scoped Kubernetes `Secret` via `secretObjects`, which is convenient for `env.valueFrom.secretKeyRef`.

In this repo, the Key Vault is typically `inker-kv`.

## Central Key Vault for OAuth2 client secrets (recommended)

Yes — the best pattern is:

1. **One central Azure Key Vault** as the source of truth for *all* OAuth2 client secrets.
2. **Per-workload SecretProviderClass** that only references the specific secret(s) that workload needs.

This gives you “central management” *without* handing every pod every client secret.

### Why not one giant shared SecretProviderClass?

You *can* create a single `SecretProviderClass` that syncs all client secrets into one Kubernetes `Secret`, and then have clients read only their key.

Tradeoffs:

- Pros: simplest wiring (one provider, one K8s Secret).
- Cons: it collapses least-privilege boundaries; any pod that mounts the CSI volume would see all projected secret files, and operationally it encourages “everyone depends on the same mega-secret”.

If you want clients to “grab their particular secret”, per-client/per-service SecretProviderClasses are the cleanest way to do that.

### Naming convention

Key Vault secret names should be predictable and unique. For example:

- `oauth2-client-profile-service-secret`
- `oauth2-client-chat-backend-secret`

This repo currently uses names like `profile-service-client-secret`, `chat-client-secret`, etc. That’s fine too—just keep them consistent.

## Initial Setup

### 1. Create namespace (if not using default)

```bash
kubectl create namespace oauth2
```

### 2. Update secrets

In production, secrets should come from Azure Key Vault.

- Upload/update secrets in Key Vault (see scripts under `scripts/` like `setup-chat-client-secrets.sh`).
- Apply/refresh the relevant `SecretProviderClass` manifest(s).

### 3. Update ConfigMap

Update the issuer URL in the ConfigMap to match your actual domain:

```bash
kubectl create configmap oauth2-config \
  --from-literal=issuer-url='https://oauth.yourdomain.com' \
  -n default \
  --dry-run=client -o yaml | kubectl apply -f -
```

### 4. Deploy

Deploy with kustomize:

```bash
kubectl apply -k infrastructure/k8s
```

## CI/CD Deployment

The GitHub Actions CI workflow automatically deploys to AKS on successful main branch builds:

1. **Smoke tests pass** → triggers deploy job
2. **Build Docker image** → tagged with git SHA and latest
3. **Push to ACR** → gabby.azurecr.io/oauth2-server
4. **Deploy to AKS** → kubectl set image on bigboy cluster
5. **Verify** → rollout status check

### Required GitHub Secrets

Configure these secrets in your GitHub repository:

- `AZURE_CLIENT_ID` - Azure service principal client ID
- `AZURE_TENANT_ID` - Azure tenant ID
- `AZURE_SUBSCRIPTION_ID` - Azure subscription ID
- `AZURE_RESOURCE_GROUP` - Resource group containing the AKS cluster

### Federated Identity Setup

For OIDC authentication (recommended):

```bash
# Create service principal
az ad sp create-for-rbac --name "oauth2-server-gh-actions" \
  --role contributor \
  --scopes /subscriptions/<SUBSCRIPTION_ID>/resourceGroups/<RESOURCE_GROUP>

# Configure federated credential for GitHub Actions
az ad app federated-credential create \
  --id <APP_ID> \
  --parameters '{
    "name": "oauth2-server-main",
    "issuer": "https://token.actions.githubusercontent.com",
    "subject": "repo:ianlintner/bootsandcats:ref:refs/heads/main",
    "audiences": ["api://AzureADTokenExchange"]
  }'

# Grant ACR push permission
az role assignment create \
  --assignee <APP_ID> \
  --role AcrPush \
  --scope /subscriptions/<SUBSCRIPTION_ID>/resourceGroups/<RESOURCE_GROUP>/providers/Microsoft.ContainerRegistry/registries/gabby

# Grant AKS access
az role assignment create \
  --assignee <APP_ID> \
  --role "Azure Kubernetes Service Cluster User Role" \
  --scope /subscriptions/<SUBSCRIPTION_ID>/resourceGroups/<RESOURCE_GROUP>/providers/Microsoft.ContainerService/managedClusters/bigboy
```

## Monitoring

### View logs

```bash
# Tail logs
kubectl logs -f -l app=oauth2-server -n default

# Logs from all pods
kubectl logs -l app=oauth2-server -n default --all-containers=true
```

### Check health

```bash
# Port-forward to access actuator locally
kubectl port-forward service/oauth2-server 9000:9000 -n default

# Check health
curl http://localhost:9000/actuator/health

# Check OIDC discovery
curl http://localhost:9000/.well-known/openid-configuration
```

### Scale

```bash
# Scale up
kubectl scale deployment/oauth2-server --replicas=3 -n default

# Scale down
kubectl scale deployment/oauth2-server --replicas=1 -n default
```

## Troubleshooting

### Image pull errors

```bash
# Check ACR credentials
kubectl get secret -n default | grep acr

# Create image pull secret if needed
kubectl create secret docker-registry acr-secret \
  --docker-server=gabby.azurecr.io \
  --docker-username=<USERNAME> \
  --docker-password=<PASSWORD> \
  -n default
```

### Pod not starting

```bash
# Describe pod
kubectl describe pod -l app=oauth2-server -n default

# Check events
kubectl get events -n default --sort-by='.lastTimestamp'

# Check logs
kubectl logs -l app=oauth2-server -n default --previous
```

### Rollback deployment

```bash
# View rollout history
kubectl rollout history deployment/oauth2-server -n default

# Rollback to previous version
kubectl rollout undo deployment/oauth2-server -n default

# Rollback to specific revision
kubectl rollout undo deployment/oauth2-server --to-revision=2 -n default
```

## Production Considerations

1. **Use a dedicated namespace** instead of default
2. **Enable TLS** via Ingress or service mesh
3. **Configure HPA** (Horizontal Pod Autoscaler) for auto-scaling
4. **Set up PodDisruptionBudget** for high availability
5. **Use external secrets** (Azure Key Vault, Sealed Secrets, etc.)
6. **Configure network policies** for security
7. **Set up monitoring** (Prometheus, Grafana)
8. **Configure backup** for persistent data

