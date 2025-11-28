# Deployment Implementation - Complete Checklist

## ✅ Files Created

### CI/CD Workflows
- [x] `.github/workflows/ci.yml` - Main CI pipeline with deploy job
- [x] `.github/workflows/security.yml` - Weekly OWASP scanning
- [x] `.github/workflows/load-test.yml` - Manual Gatling load testing

### Kubernetes Manifests
- [x] `k8s/deployment.yaml` - Deployment, Service, ConfigMap, Secret templates
- [x] `k8s/README.md` - Kubernetes deployment guide

### Setup Scripts
- [x] `scripts/setup-azure-github.sh` - Interactive complete setup (with prompts)
- [x] `scripts/setup-now.sh` - Direct automated setup (no prompts)
- [x] `scripts/validate-setup.sh` - Validation checker
- [x] `scripts/gh-actions-summary.sh` - GitHub Actions analyzer
- [x] `scripts/README.md` - Scripts documentation

### Documentation
- [x] `SETUP.md` - Quick start deployment guide
- [x] `docs/ci.md` - CI/CD pipeline overview (updated)
- [x] `docs/deployment/azure-setup.md` - Detailed Azure setup guide
- [x] `README.md` - Updated with deployment section

## 🎯 What You Need to Do Now

### 1. Run the Automated Setup Script

Open a terminal and run:

```bash
cd /Users/ianlintner/Projects/bootsandcats
chmod +x scripts/setup-now.sh
./scripts/setup-now.sh
```

This single command will:
1. Create Azure service principal with OIDC federated identity
2. Grant all necessary Azure permissions
3. Configure GitHub repository secrets
4. Create Kubernetes secrets with generated passwords
5. Create Kubernetes ConfigMap
6. Save credentials to `~/.oauth2-server-credentials.txt`

### 2. Validate Everything

```bash
./scripts/validate-setup.sh
```

Look for all ✓ checkmarks.

### 3. Commit and Push

```bash
git add .
git commit -m "feat: add CI/CD pipeline with Azure deployment to AKS"
git push origin main
```

### 4. Watch the First Deployment

```bash
gh run watch
```

## 📋 Prerequisites

Ensure these are installed and authenticated:

```bash
# Check installations
which az gh kubectl jq

# If missing, install on macOS:
brew install azure-cli gh kubectl jq

# Login to Azure
az login

# Login to GitHub  
gh auth login
```

## 🔍 Expected Setup Output

When you run `./scripts/setup-now.sh`, you should see:

```
Starting OAuth2 Server Azure & GitHub Setup...

Getting Azure account info...
Subscription: Your Subscription Name
Subscription ID: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
Tenant ID: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx

Finding Azure Container Registry 'gabby'...
ACR Resource Group: your-acr-rg

Finding AKS cluster 'bigboy'...
AKS Resource Group: your-aks-rg

Creating app registration 'oauth2-server-gh-actions'...
Created new app: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx

Creating federated credential...

Granting Azure permissions...
Waiting for role assignments to propagate...

Setting GitHub secrets...
GitHub secrets configured

Getting AKS credentials...
AKS credentials configured

Generating secure passwords...

Creating Kubernetes secret...
Kubernetes secret created

Creating Kubernetes ConfigMap...
Kubernetes ConfigMap created

=== SETUP COMPLETE ===

✓ Azure service principal created: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
✓ GitHub secrets configured
✓ Kubernetes resources created
✓ Credentials saved to: /Users/ianlintner/.oauth2-server-credentials.txt

Next steps:
1. Save credentials from /Users/ianlintner/.oauth2-server-credentials.txt to your password manager
2. Delete the credentials file: rm /Users/ianlintner/.oauth2-server-credentials.txt
3. Update issuer URL: kubectl edit configmap oauth2-config -n default
4. Push to main to trigger deployment: git push origin main
5. Watch deployment: gh run watch
```

## 🚨 Important Security Notes

1. **Save Credentials**: The setup script generates secure random passwords and saves them to `~/.oauth2-server-credentials.txt`
   - **ACTION REQUIRED**: Copy these to your password manager immediately
   - **ACTION REQUIRED**: Delete the file after saving: `rm ~/.oauth2-server-credentials.txt`

2. **Update Issuer URL**: The default issuer URL is `https://oauth.example.com`
   - Update it to your real domain:
   ```bash
   kubectl edit configmap oauth2-config -n default
   ```

## 🎯 Verification Checklist

After running the setup, verify:

- [ ] GitHub secrets are set (4 secrets):
  ```bash
  gh secret list -R ianlintner/bootsandcats
  ```
  Should show: AZURE_CLIENT_ID, AZURE_TENANT_ID, AZURE_SUBSCRIPTION_ID, AZURE_RESOURCE_GROUP

- [ ] Kubernetes secret exists:
  ```bash
  kubectl get secret oauth2-secrets -n default
  ```

- [ ] Kubernetes ConfigMap exists:
  ```bash
  kubectl get configmap oauth2-config -n default
  kubectl get configmap oauth2-config -n default -o yaml
  ```

- [ ] AKS credentials configured:
  ```bash
  kubectl cluster-info
  kubectl get nodes
  ```

- [ ] Azure permissions granted:
  ```bash
  APP_ID=$(gh secret get AZURE_CLIENT_ID -R ianlintner/bootsandcats)
  az role assignment list --assignee "$APP_ID" --output table
  ```

## 🚀 First Deployment Flow

After pushing to main:

1. **GitHub Actions starts** (~1 min)
   - Checks out code
   - Sets up Java 17
   - Validates formatting

2. **Build & Unit Tests** (~3-5 min)
   - Compiles code
   - Runs unit tests
   - Generates JaCoCo coverage report
   - Uploads JAR artifact

3. **Integration Tests** (~2-4 min, parallel)
   - Runs Failsafe integration tests
   - Uses Testcontainers if needed

4. **Static Analysis** (~2-3 min, parallel)
   - SpotBugs with FindSecBugs
   - Security vulnerability scan

5. **Smoke Test** (~2-3 min)
   - Downloads JAR
   - Starts app locally
   - Validates health endpoint
   - Validates OIDC discovery endpoint

6. **Deploy to AKS** (~5-7 min, main only)
   - Authenticates to Azure via OIDC
   - Builds Docker image
   - Pushes to ACR: gabby.azurecr.io/oauth2-server
   - Updates AKS deployment
   - Waits for rollout
   - Verifies pods

**Total Time**: ~15-20 minutes for first deployment

## 📊 Post-Deployment Verification

```bash
# Check deployment status
kubectl rollout status deployment/oauth2-server -n default

# View pods
kubectl get pods -l app=oauth2-server -n default

# View deployment
kubectl get deployment oauth2-server -n default

# View service
kubectl get svc oauth2-server -n default

# Check logs
kubectl logs -l app=oauth2-server -n default --tail=50

# Port-forward to test
kubectl port-forward service/oauth2-server 9000:9000 -n default

# Test endpoints (in another terminal)
curl http://localhost:9000/actuator/health
curl http://localhost:9000/.well-known/openid-configuration
```

## 🔧 Troubleshooting Common Issues

### Issue: "ACR 'gabby' not found"
**Solution**: 
```bash
az acr list -o table
# Note the actual name and update scripts if different
```

### Issue: "AKS 'bigboy' not found"
**Solution**:
```bash
az aks list -o table
# Note the actual name and update scripts if different
```

### Issue: "Permission denied" in GitHub Actions
**Solution**: Wait 5-10 minutes for Azure AD propagation, then re-run workflow

### Issue: kubectl commands fail
**Solution**:
```bash
az aks get-credentials --resource-group <RG> --name bigboy --overwrite-existing
kubectl config current-context
```

### Issue: Pods won't start
**Solution**:
```bash
kubectl describe pod -l app=oauth2-server -n default
kubectl logs -l app=oauth2-server -n default --previous
kubectl get events -n default --sort-by='.lastTimestamp'
```

## 📚 Reference Documentation

- **Quick Start**: `SETUP.md`
- **CI/CD Overview**: `docs/ci.md`
- **Azure Setup Details**: `docs/deployment/azure-setup.md`
- **Kubernetes Guide**: `k8s/README.md`
- **Scripts Documentation**: `scripts/README.md`

## ✅ Success Criteria

You'll know everything is working when:

1. ✅ `./scripts/validate-setup.sh` shows all checkmarks
2. ✅ GitHub Actions workflow completes successfully
3. ✅ Pods are running: `kubectl get pods -l app=oauth2-server`
4. ✅ Health endpoint returns UP: `curl localhost:9000/actuator/health`
5. ✅ OIDC discovery returns JSON: `curl localhost:9000/.well-known/openid-configuration`

## 🎉 You're Done When...

- [x] Setup script completed successfully
- [x] Credentials saved and file deleted
- [x] All validations pass
- [x] Changes committed and pushed to main
- [x] GitHub Actions workflow succeeds
- [x] Pods are running in AKS
- [x] Application endpoints respond

---

**Ready to proceed?** Run `./scripts/setup-now.sh` now! 🚀

