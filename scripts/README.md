# Complete Azure & GitHub Setup Guide

This directory contains scripts for automated setup of Azure and GitHub resources for CI/CD deployment.

## Prerequisites

Install the required CLI tools:

```bash
# macOS
brew install azure-cli gh kubectl jq

# Login to Azure
az login

# Login to GitHub
gh auth login
```

## Automated Setup

### Option 1: Run the complete setup script

```bash
./scripts/setup-azure-github.sh
```

This script will:
1. Verify Azure and GitHub authentication
2. Locate ACR (gabby) and AKS (bigboy) resources
3. Create Azure service principal with federated identity
4. Configure OIDC federated credentials for GitHub Actions
5. Grant necessary Azure permissions (ACR push, AKS access)
6. Configure GitHub repository secrets
7. Create Kubernetes secrets and ConfigMap
8. Deploy the application manifest

### Option 2: Manual step-by-step setup

See `docs/deployment/azure-setup.md` for detailed manual instructions.

## Validation

After running the setup, validate everything is configured:

```bash
./scripts/validate-setup.sh
```

This will check:
- Azure CLI authentication
- GitHub CLI authentication
- kubectl cluster connection
- GitHub secrets configuration
- Kubernetes resources

## What Gets Created

### Azure Resources

- **Service Principal**: `oauth2-server-gh-actions`
  - Federated identity credential for main branch
  - Contributor role on resource group
  - AcrPush role on ACR
  - AKS Cluster User and RBAC Admin roles

### GitHub Secrets

- `AZURE_CLIENT_ID` - Service principal application ID
- `AZURE_TENANT_ID` - Azure tenant ID
- `AZURE_SUBSCRIPTION_ID` - Azure subscription ID
- `AZURE_RESOURCE_GROUP` - Resource group name

### Kubernetes Resources

- **Secret**: `oauth2-secrets` (namespace: default)
  - database-url
  - database-username
  - database-password
  - demo-client-secret
  - m2m-client-secret
  - demo-user-password
  - admin-user-password

- **ConfigMap**: `oauth2-config` (namespace: default)
  - issuer-url

- **Deployment**: `oauth2-server` (from k8s/deployment.yaml)
  - 2 replicas
  - Health probes
  - Resource limits

## Credentials

Generated credentials are saved to `~/.oauth2-server-credentials.txt`.

⚠️ **IMPORTANT**: Save these credentials to a secure password manager and delete the file:

```bash
# After saving to your password manager
rm ~/.oauth2-server-credentials.txt
```

## Trigger First Deployment

After setup is complete, push to main to trigger the first deployment:

```bash
git add .
git commit -m "chore: configure Azure deployment"
git push origin main

# Watch the deployment
gh run watch
```

## Verify Deployment

```bash
# Check deployment status
kubectl rollout status deployment/oauth2-server -n default

# View pods
kubectl get pods -l app=oauth2-server -n default

# Check logs
kubectl logs -f -l app=oauth2-server -n default

# Port-forward to test locally
kubectl port-forward service/oauth2-server 9000:9000 -n default

# Test health endpoint
curl http://localhost:9000/actuator/health

# Test OIDC discovery
curl http://localhost:9000/.well-known/openid-configuration
```

## Troubleshooting

### Azure authentication fails

```bash
# Re-login to Azure
az login

# Check current account
az account show
```

### GitHub authentication fails

```bash
# Re-login to GitHub
gh auth login

# Check status
gh auth status
```

### AKS connection fails

```bash
# Get AKS credentials
az aks get-credentials --resource-group <RESOURCE_GROUP> --name bigboy --overwrite-existing

# Test connection
kubectl cluster-info
kubectl get nodes
```

### Federated credential not working

Wait 5-10 minutes for Azure AD propagation, then retry the GitHub Actions workflow.

### Permission errors

Verify role assignments:

```bash
# Get your service principal ID
APP_ID="<your-app-id>"

# List role assignments
az role assignment list --assignee $APP_ID --output table
```

## Clean Up

To remove all resources created by the setup:

```bash
# Delete Kubernetes resources
kubectl delete secret oauth2-secrets -n default
kubectl delete configmap oauth2-config -n default
kubectl delete -f k8s/deployment.yaml

# Delete GitHub secrets
gh secret delete AZURE_CLIENT_ID -R ianlintner/bootsandcats
gh secret delete AZURE_TENANT_ID -R ianlintner/bootsandcats
gh secret delete AZURE_SUBSCRIPTION_ID -R ianlintner/bootsandcats
gh secret delete AZURE_RESOURCE_GROUP -R ianlintner/bootsandcats

# Delete service principal (get APP_ID first)
az ad app delete --id <APP_ID>

# Delete credentials file
rm ~/.oauth2-server-credentials.txt
```

## Security Best Practices

1. ✅ **Federated Identity**: Using OIDC instead of long-lived secrets
2. ✅ **Least Privilege**: Only necessary roles granted
3. ✅ **Generated Secrets**: Using strong random passwords
4. ✅ **Secure Storage**: Credentials saved to protected file (0600 permissions)
5. ⚠️ **Manual Cleanup**: Remember to delete credentials file after saving securely

## Support

For issues or questions:
- Check `docs/deployment/azure-setup.md` for detailed setup instructions
- Run `./scripts/validate-setup.sh` to diagnose issues
- Check GitHub Actions logs: `gh run list` and `gh run view`

