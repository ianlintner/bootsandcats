# Kubernetes Deployment

This directory contains Kubernetes manifests for deploying the OAuth2 Authorization Server to AKS (Azure Kubernetes Service).

## Prerequisites

- AKS cluster "bigboy" configured and accessible
- Azure Container Registry "gabby" with push permissions
- kubectl configured with AKS context

## Manifests

### deployment.yaml

Contains:
- **Deployment**: 2 replicas with liveness/readiness probes
- **Service**: ClusterIP service on port 9000
- **ConfigMap**: oauth2-config with issuer URL
- **Secret**: oauth2-secrets with database credentials and OAuth2 secrets

## Initial Setup

### 1. Create namespace (if not using default)

```bash
kubectl create namespace oauth2
```

### 2. Update secrets

Before deploying, update the secrets in `deployment.yaml`:

```bash
# Create/update secrets from a secure source
kubectl create secret generic oauth2-secrets \
  --from-literal=database-url='jdbc:postgresql://postgres:5432/oauth2db' \
  --from-literal=database-username='oauth2user' \
  --from-literal=database-password='YOUR_SECURE_PASSWORD' \
  --from-literal=demo-client-secret='YOUR_SECURE_SECRET' \
  --from-literal=m2m-client-secret='YOUR_SECURE_SECRET' \
  --from-literal=demo-user-password='YOUR_SECURE_PASSWORD' \
  --from-literal=admin-user-password='YOUR_SECURE_PASSWORD' \
  -n default \
  --dry-run=client -o yaml | kubectl apply -f -
```

### 3. Update ConfigMap

Update the issuer URL in the ConfigMap to match your actual domain:

```bash
kubectl create configmap oauth2-config \
  --from-literal=issuer-url='https://oauth.yourdomain.com' \
  -n default \
  --dry-run=client -o yaml | kubectl apply -f -
```

### 4. Deploy

```bash
# Deploy all resources
kubectl apply -f k8s/deployment.yaml

# Check deployment status
kubectl rollout status deployment/oauth2-server -n default

# Check pods
kubectl get pods -n default -l app=oauth2-server

# Check service
kubectl get service oauth2-server -n default
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

