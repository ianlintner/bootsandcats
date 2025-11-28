# Complete Setup Instructions

## Quick Start - Automated Setup

Run this single command to set up everything:

```bash
cd /Users/ianlintner/Projects/bootsandcats
chmod +x scripts/setup-now.sh
./scripts/setup-now.sh
```

This will:
1. ✓ Create Azure service principal with federated identity
2. ✓ Configure OIDC for GitHub Actions
3. ✓ Grant all necessary Azure permissions
4. ✓ Set GitHub repository secrets
5. ✓ Create Kubernetes secrets and ConfigMap
6. ✓ Save generated credentials to `~/.oauth2-server-credentials.txt`

## Prerequisites Checklist

Before running the setup, ensure:

- [ ] Azure CLI installed: `brew install azure-cli`
- [ ] GitHub CLI installed: `brew install gh`
- [ ] kubectl installed: `brew install kubectl`
- [ ] jq installed: `brew install jq`
- [ ] Logged into Azure: `az login`
- [ ] Logged into GitHub: `gh auth login`
- [ ] ACR 'gabby' exists in Azure
- [ ] AKS cluster 'bigboy' exists in Azure

## Manual Setup (if automated script fails)

### 1. Get Azure Information

```bash
# Get your Azure details
SUBSCRIPTION_ID=$(az account show --query id -o tsv)
TENANT_ID=$(az account show --query tenantId -o tsv)
echo "Subscription ID: $SUBSCRIPTION_ID"
echo "Tenant ID: $TENANT_ID"

# Find ACR resource group
ACR_RG=$(az acr list --query "[?name=='gabby'].resourceGroup" -o tsv)
echo "ACR Resource Group: $ACR_RG"

# Find AKS resource group
AKS_RG=$(az aks list --query "[?name=='bigboy'].resourceGroup" -o tsv)
echo "AKS Resource Group: $AKS_RG"
```

### 2. Create Service Principal

```bash
APP_NAME="oauth2-server-gh-actions"

# Create app registration
APP_ID=$(az ad app create --display-name "$APP_NAME" --query appId -o tsv)
echo "App ID: $APP_ID"

# Create service principal
az ad sp create --id "$APP_ID"
sleep 10
```

### 3. Configure Federated Credential

```bash
az ad app federated-credential create --id "$APP_ID" --parameters '{
    "name": "oauth2-server-gh-actions-main",
    "issuer": "https://token.actions.githubusercontent.com",
    "subject": "repo:ianlintner/bootsandcats:ref:refs/heads/main",
    "description": "GitHub Actions main branch",
    "audiences": ["api://AzureADTokenExchange"]
}'
```

### 4. Grant Azure Permissions

```bash
# Contributor on resource group
az role assignment create \
    --assignee "$APP_ID" \
    --role Contributor \
    --scope "/subscriptions/$SUBSCRIPTION_ID/resourceGroups/$AKS_RG"

# ACR Push
az role assignment create \
    --assignee "$APP_ID" \
    --role AcrPush \
    --scope "/subscriptions/$SUBSCRIPTION_ID/resourceGroups/$ACR_RG/providers/Microsoft.ContainerRegistry/registries/gabby"

# AKS Cluster User
az role assignment create \
    --assignee "$APP_ID" \
    --role "Azure Kubernetes Service Cluster User Role" \
    --scope "/subscriptions/$SUBSCRIPTION_ID/resourceGroups/$AKS_RG/providers/Microsoft.ContainerService/managedClusters/bigboy"

# AKS RBAC Admin
az role assignment create \
    --assignee "$APP_ID" \
    --role "Azure Kubernetes Service RBAC Cluster Admin" \
    --scope "/subscriptions/$SUBSCRIPTION_ID/resourceGroups/$AKS_RG/providers/Microsoft.ContainerService/managedClusters/bigboy"

# Wait for propagation
sleep 15
```

### 5. Configure GitHub Secrets

```bash
gh secret set AZURE_CLIENT_ID --body "$APP_ID" --repo ianlintner/bootsandcats
gh secret set AZURE_TENANT_ID --body "$TENANT_ID" --repo ianlintner/bootsandcats
gh secret set AZURE_SUBSCRIPTION_ID --body "$SUBSCRIPTION_ID" --repo ianlintner/bootsandcats
gh secret set AZURE_RESOURCE_GROUP --body "$AKS_RG" --repo ianlintner/bootsandcats
```

### 6. Get AKS Credentials

```bash
az aks get-credentials --resource-group "$AKS_RG" --name bigboy --overwrite-existing
kubectl cluster-info
```

### 7. Create Kubernetes Secrets

```bash
# Generate secure passwords
DB_PASSWORD=$(openssl rand -base64 32 | tr -d /=+ | cut -c -24)
DEMO_CLIENT_SECRET=$(openssl rand -base64 32 | tr -d /=+ | cut -c -32)
M2M_CLIENT_SECRET=$(openssl rand -base64 32 | tr -d /=+ | cut -c -32)
DEMO_USER_PASSWORD=$(openssl rand -base64 16 | tr -d /=+ | cut -c -16)
ADMIN_USER_PASSWORD=$(openssl rand -base64 16 | tr -d /=+ | cut -c -16)

# Create secret
kubectl create secret generic oauth2-secrets \
    --from-literal=database-url='jdbc:postgresql://postgres.default.svc.cluster.local:5432/oauth2db' \
    --from-literal=database-username='oauth2user' \
    --from-literal=database-password="$DB_PASSWORD" \
    --from-literal=demo-client-secret="$DEMO_CLIENT_SECRET" \
    --from-literal=m2m-client-secret="$M2M_CLIENT_SECRET" \
    --from-literal=demo-user-password="$DEMO_USER_PASSWORD" \
    --from-literal=admin-user-password="$ADMIN_USER_PASSWORD" \
    -n default

# Save credentials securely!
echo "Database Password: $DB_PASSWORD"
echo "Demo Client Secret: $DEMO_CLIENT_SECRET"
echo "M2M Client Secret: $M2M_CLIENT_SECRET"
echo "Demo User Password: $DEMO_USER_PASSWORD"
echo "Admin User Password: $ADMIN_USER_PASSWORD"
```

### 8. Create ConfigMap

```bash
ISSUER_URL="https://oauth.yourdomain.com"  # Update this!

kubectl create configmap oauth2-config \
    --from-literal=issuer-url="$ISSUER_URL" \
    -n default
```

### 9. Deploy Application

```bash
kubectl apply -f k8s/deployment.yaml
```

## Validation

After setup, validate everything:

```bash
./scripts/validate-setup.sh
```

Expected output:
```
1. Checking Azure CLI...
   ✓ Azure CLI installed
   ✓ Logged into Azure

2. Checking GitHub CLI...
   ✓ GitHub CLI installed
   ✓ Logged into GitHub

3. Checking kubectl...
   ✓ kubectl installed
   ✓ Connected to cluster

4. Checking GitHub Secrets...
   ✓ AZURE_CLIENT_ID configured
   ✓ AZURE_TENANT_ID configured
   ✓ AZURE_SUBSCRIPTION_ID configured
   ✓ AZURE_RESOURCE_GROUP configured

5. Checking Kubernetes Resources...
   ✓ Secret 'oauth2-secrets' exists
   ✓ ConfigMap 'oauth2-config' exists
   ℹ Deployment 'oauth2-server' not found (will be created on first CI run)
```

## Trigger First Deployment

After everything is validated:

```bash
# Commit the setup
git add .
git commit -m "chore: configure Azure deployment"
git push origin main

# Watch the deployment
gh run watch

# Once deployed, check status
kubectl get pods -l app=oauth2-server -n default
kubectl logs -f -l app=oauth2-server -n default
```

## Troubleshooting

### Command not found errors

Install missing tools:
```bash
brew install azure-cli gh kubectl jq
```

### Not logged into Azure

```bash
az login
az account show
```

### Not logged into GitHub

```bash
gh auth login
gh auth status
```

### Can't find ACR or AKS

List available resources:
```bash
az acr list --query '[].{name:name,resourceGroup:resourceGroup}' -o table
az aks list --query '[].{name:name,resourceGroup:resourceGroup}' -o table
```

### Federated credential fails

Check if it already exists:
```bash
az ad app federated-credential list --id "$APP_ID"
```

### Permission denied in GitHub Actions

1. Wait 5-10 minutes for Azure AD propagation
2. Verify role assignments:
```bash
az role assignment list --assignee "$APP_ID" --output table
```

### Kubernetes connection fails

```bash
# Re-get credentials
az aks get-credentials --resource-group "$AKS_RG" --name bigboy --overwrite-existing

# Test connection
kubectl get nodes
kubectl get pods --all-namespaces
```

## Security Notes

1. ✅ Using OIDC federated identity (no long-lived secrets)
2. ✅ Generated strong random passwords
3. ✅ Credentials saved to protected file (600 permissions)
4. ⚠️ **CRITICAL**: Save credentials to password manager and delete file:
   ```bash
   rm ~/.oauth2-server-credentials.txt
   ```

## What Happens on First Push

When you push to main:

1. GitHub Actions workflow starts
2. Authenticates to Azure using OIDC
3. Builds Docker image from packaged JAR
4. Pushes to ACR: `gabby.azurecr.io/oauth2-server:latest`
5. Deploys to AKS cluster 'bigboy'
6. Waits for rollout to complete
7. Verifies pods are running

You can watch this happen:
```bash
gh run watch
```

## Next Steps After First Deployment

1. Update issuer URL in ConfigMap:
   ```bash
   kubectl edit configmap oauth2-config -n default
   ```

2. Set up Ingress for external access

3. Configure TLS certificates

4. Set up monitoring and alerts

5. Configure backup for secrets

## Support

- Full manual setup: `docs/deployment/azure-setup.md`
- Kubernetes guide: `k8s/README.md`
- CI/CD overview: `docs/ci.md`

