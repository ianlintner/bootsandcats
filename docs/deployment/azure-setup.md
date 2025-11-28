# Azure Setup for CI/CD Deployment

This guide walks through setting up Azure resources and GitHub secrets for automated deployment to AKS.

## Prerequisites

- Azure CLI installed and authenticated (`az login`)
- GitHub CLI installed and authenticated (`gh auth login`)
- Owner or Contributor access to the Azure subscription
- Admin access to the GitHub repository

## 1. Set Variables

```bash
# Azure configuration
SUBSCRIPTION_ID="your-subscription-id"
RESOURCE_GROUP="your-resource-group"
ACR_NAME="gabby"
AKS_CLUSTER="bigboy"
LOCATION="eastus"
APP_NAME="oauth2-server-gh-actions"

# GitHub configuration
REPO_OWNER="ianlintner"
REPO_NAME="bootsandcats"
```

## 2. Create Resource Group (if needed)

```bash
az group create --name $RESOURCE_GROUP --location $LOCATION
```

## 3. Create Azure Container Registry (if needed)

```bash
az acr create \
  --resource-group $RESOURCE_GROUP \
  --name $ACR_NAME \
  --sku Basic \
  --location $LOCATION
```

## 4. Create AKS Cluster (if needed)

```bash
az aks create \
  --resource-group $RESOURCE_GROUP \
  --name $AKS_CLUSTER \
  --node-count 2 \
  --node-vm-size Standard_B2s \
  --generate-ssh-keys \
  --attach-acr $ACR_NAME \
  --location $LOCATION

# Get AKS credentials
az aks get-credentials --resource-group $RESOURCE_GROUP --name $AKS_CLUSTER
```

## 5. Create Service Principal with Federated Identity

### Create App Registration

```bash
# Create app registration
APP_ID=$(az ad app create \
  --display-name $APP_NAME \
  --query appId -o tsv)

echo "App ID: $APP_ID"

# Create service principal
az ad sp create --id $APP_ID

# Get object ID
OBJECT_ID=$(az ad sp show --id $APP_ID --query id -o tsv)
echo "Object ID: $OBJECT_ID"
```

### Configure Federated Credentials

```bash
# For main branch deployments
az ad app federated-credential create \
  --id $APP_ID \
  --parameters "{
    \"name\": \"${APP_NAME}-main\",
    \"issuer\": \"https://token.actions.githubusercontent.com\",
    \"subject\": \"repo:${REPO_OWNER}/${REPO_NAME}:ref:refs/heads/main\",
    \"description\": \"GitHub Actions deployment from main branch\",
    \"audiences\": [\"api://AzureADTokenExchange\"]
  }"

# Optional: For PR deployments to a staging environment
az ad app federated-credential create \
  --id $APP_ID \
  --parameters "{
    \"name\": \"${APP_NAME}-pr\",
    \"issuer\": \"https://token.actions.githubusercontent.com\",
    \"subject\": \"repo:${REPO_OWNER}/${REPO_NAME}:pull_request\",
    \"description\": \"GitHub Actions deployment from pull requests\",
    \"audiences\": [\"api://AzureADTokenExchange\"]
  }"
```

### Grant Permissions

```bash
# Grant Contributor role on resource group
az role assignment create \
  --assignee $APP_ID \
  --role Contributor \
  --scope "/subscriptions/$SUBSCRIPTION_ID/resourceGroups/$RESOURCE_GROUP"

# Grant AcrPush role for pushing images
az role assignment create \
  --assignee $APP_ID \
  --role AcrPush \
  --scope "/subscriptions/$SUBSCRIPTION_ID/resourceGroups/$RESOURCE_GROUP/providers/Microsoft.ContainerRegistry/registries/$ACR_NAME"

# Grant Azure Kubernetes Service Cluster User Role
az role assignment create \
  --assignee $APP_ID \
  --role "Azure Kubernetes Service Cluster User Role" \
  --scope "/subscriptions/$SUBSCRIPTION_ID/resourceGroups/$RESOURCE_GROUP/providers/Microsoft.ContainerService/managedClusters/$AKS_CLUSTER"

# Optional: Grant Kubernetes admin for full cluster access
az role assignment create \
  --assignee $APP_ID \
  --role "Azure Kubernetes Service RBAC Cluster Admin" \
  --scope "/subscriptions/$SUBSCRIPTION_ID/resourceGroups/$RESOURCE_GROUP/providers/Microsoft.ContainerService/managedClusters/$AKS_CLUSTER"
```

## 6. Get Azure Tenant ID

```bash
TENANT_ID=$(az account show --query tenantId -o tsv)
echo "Tenant ID: $TENANT_ID"
```

## 7. Configure GitHub Secrets

### Using GitHub CLI

```bash
gh secret set AZURE_CLIENT_ID --body "$APP_ID" --repo $REPO_OWNER/$REPO_NAME
gh secret set AZURE_TENANT_ID --body "$TENANT_ID" --repo $REPO_OWNER/$REPO_NAME
gh secret set AZURE_SUBSCRIPTION_ID --body "$SUBSCRIPTION_ID" --repo $REPO_OWNER/$REPO_NAME
gh secret set AZURE_RESOURCE_GROUP --body "$RESOURCE_GROUP" --repo $REPO_OWNER/$REPO_NAME
```

### Using GitHub Web UI

Navigate to: `https://github.com/$REPO_OWNER/$REPO_NAME/settings/secrets/actions`

Add the following secrets:
- `AZURE_CLIENT_ID`: [App ID from above]
- `AZURE_TENANT_ID`: [Tenant ID from above]
- `AZURE_SUBSCRIPTION_ID`: [Your subscription ID]
- `AZURE_RESOURCE_GROUP`: [Your resource group name]

## 8. Create Kubernetes Resources

### Create namespace (optional)

```bash
kubectl create namespace oauth2
```

### Create secrets

```bash
kubectl create secret generic oauth2-secrets \
  --from-literal=database-url='jdbc:postgresql://postgres.default.svc.cluster.local:5432/oauth2db' \
  --from-literal=database-username='oauth2user' \
  --from-literal=database-password='CHANGE_ME_SECURE_PASSWORD' \
  --from-literal=demo-client-secret='CHANGE_ME_SECURE_SECRET' \
  --from-literal=m2m-client-secret='CHANGE_ME_SECURE_SECRET' \
  --from-literal=demo-user-password='CHANGE_ME_SECURE_PASSWORD' \
  --from-literal=admin-user-password='CHANGE_ME_SECURE_PASSWORD' \
  -n default
```

### Create ConfigMap

```bash
kubectl create configmap oauth2-config \
  --from-literal=issuer-url='https://oauth.yourdomain.com' \
  -n default
```

### Deploy application

```bash
kubectl apply -f k8s/deployment.yaml
```

## 9. Verify Setup

### Test Azure authentication

```bash
# Test login with service principal
az login --service-principal \
  --username $APP_ID \
  --tenant $TENANT_ID \
  --federated-token "$(curl -s -H 'Authorization: bearer $ACTIONS_ID_TOKEN_REQUEST_TOKEN' '$ACTIONS_ID_TOKEN_REQUEST_URL&audience=api://AzureADTokenExchange' | jq -r .value)"
```

### Test ACR access

```bash
az acr login --name $ACR_NAME
docker pull hello-world
docker tag hello-world $ACR_NAME.azurecr.io/test:latest
docker push $ACR_NAME.azurecr.io/test:latest
az acr repository delete --name $ACR_NAME --repository test --yes
```

### Test AKS access

```bash
kubectl get nodes
kubectl get pods --all-namespaces
```

### Trigger a deployment

```bash
# Make a change and push to main
git checkout main
git pull
echo "# trigger deployment" >> README.md
git add README.md
git commit -m "test: trigger deployment"
git push origin main

# Watch the GitHub Actions run
gh run watch
```

## Troubleshooting

### Federated credential not working

Wait 5-10 minutes after creating federated credentials for Azure AD to propagate changes.

### Permission denied errors

Verify role assignments:
```bash
az role assignment list --assignee $APP_ID --output table
```

### ACR authentication fails

Check ACR permissions:
```bash
az acr show --name $ACR_NAME --resource-group $RESOURCE_GROUP
az role assignment list --scope "/subscriptions/$SUBSCRIPTION_ID/resourceGroups/$RESOURCE_GROUP/providers/Microsoft.ContainerRegistry/registries/$ACR_NAME"
```

### AKS context not found

Re-authenticate:
```bash
az aks get-credentials --resource-group $RESOURCE_GROUP --name $AKS_CLUSTER --overwrite-existing
```

## Security Best Practices

1. **Use federated identity** instead of service principal secrets (this setup uses OIDC)
2. **Principle of least privilege** - grant only necessary roles
3. **Rotate credentials** regularly if using secrets
4. **Use Azure Key Vault** for application secrets in production
5. **Enable Azure Policy** for compliance and governance
6. **Configure network policies** in AKS
7. **Enable Azure Defender** for Kubernetes

## Cost Optimization

- Use **Basic SKU** for ACR in dev/test environments
- Use **B-series VMs** for AKS node pools in dev/test
- Enable **cluster autoscaler** for production
- Set **resource quotas** to prevent runaway costs
- Use **spot instances** for batch workloads

## Next Steps

1. Configure custom domain and TLS certificate
2. Set up Azure Monitor and Application Insights
3. Configure backup and disaster recovery
4. Implement GitOps with Flux or ArgoCD
5. Add staging environment for pre-production testing

