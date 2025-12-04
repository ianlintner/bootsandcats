# Azure OAuth2 EC JWK Management Scripts

This directory contains scripts for managing Elliptic Curve (P-256/ES256) JSON Web Keys for the OAuth2 Authorization Server running in Azure Kubernetes Service.

## Scripts Overview

### 1. `complete-azure-setup.sh` ⭐ **START HERE**
**Interactive guided setup wizard for complete deployment**

```bash
# Interactive mode (recommended for first-time setup)
./scripts/complete-azure-setup.sh

# Fully automated mode
./scripts/complete-azure-setup.sh --auto

# Custom configuration
./scripts/complete-azure-setup.sh --vault-name my-vault --auto
```

**What it does:**
1. Checks all prerequisites
2. Generates EC P-256 JWK
3. Uploads to Azure Key Vault
4. Updates Kubernetes manifests
5. Deploys to AKS
6. Verifies the entire setup

**Time needed:** ~5 minutes

---

### 2. `azure-ec-jwk-manager.sh`
**Command-line tool for managing JWK lifecycle (macOS/Linux)**

```bash
./scripts/azure-ec-jwk-manager.sh <command> [options]
```

**Commands:**
- `generate` - Generate new EC P-256 JWK
- `upload <file>` - Upload JWK to Azure Key Vault
- `download` - Download JWK from Key Vault
- `validate <file>` - Validate JWK format
- `update-k8s <file>` - Update Kubernetes secret
- `deploy` - Deploy to AKS
- `verify` - Verify configuration
- `rotate <file>` - Rotate keys (keep old, add new)
- `show-config` - Display current configuration
- `help` - Show help message

**Examples:**
```bash
# Generate and upload
./scripts/azure-ec-jwk-manager.sh generate > my-jwk.json
./scripts/azure-ec-jwk-manager.sh upload my-jwk.json

# Verify everything is working
./scripts/azure-ec-jwk-manager.sh verify

# Rotate keys
./scripts/azure-ec-jwk-manager.sh generate > new.json
./scripts/azure-ec-jwk-manager.sh rotate new.json
./scripts/azure-ec-jwk-manager.sh deploy
```

---

### 3. `azure-ec-jwk-manager.ps1`
**Command-line tool for managing JWK lifecycle (Windows PowerShell)**

```powershell
.\scripts\azure-ec-jwk-manager.ps1 -Command <command> -FilePath <path>
```

**Commands:** Same as bash version

**Examples:**
```powershell
# Generate and upload
.\scripts\azure-ec-jwk-manager.ps1 -Command generate | Out-File my-jwk.json
.\scripts\azure-ec-jwk-manager.ps1 -Command upload -FilePath my-jwk.json

# Verify everything
.\scripts\azure-ec-jwk-manager.ps1 -Command verify

# Rotate keys
.\scripts\azure-ec-jwk-manager.ps1 -Command generate | Out-File new.json
.\scripts\azure-ec-jwk-manager.ps1 -Command rotate -FilePath new.json
.\scripts\azure-ec-jwk-manager.ps1 -Command deploy
```

---

### 4. `generate-ec-jwk.sh`
**Simple EC JWK generation script (macOS/Linux)**

```bash
./scripts/generate-ec-jwk.sh
```

Generates and outputs EC P-256 JWK to stdout. Useful for piping to files or other commands:

```bash
# Save to file
./scripts/generate-ec-jwk.sh > my-jwk.json

# Pretty print
./scripts/generate-ec-jwk.sh | jq .

# Pipe directly to Key Vault
./scripts/generate-ec-jwk.sh | az keyvault secret set --vault-name inker-kv --name oauth2-jwk --file /dev/stdin
```

---

## Prerequisites

### macOS/Linux
```bash
# Install Azure CLI
brew install azure-cli

# Install kubectl
brew install kubectl

# Install jq
brew install jq

# Login to Azure
az login

# Configure kubectl
az aks get-credentials --resource-group <resource-group> --name <aks-cluster>
```

### Windows (PowerShell)
```powershell
# Install Azure CLI
choco install azure-cli

# Install kubectl
choco install kubernetes-cli

# Install jq
choco install jq

# Login to Azure
az login

# Configure kubectl
az aks get-credentials --resource-group <resource-group> --name <aks-cluster>
```

---

## Quick Start

### Option 1: Guided Setup (Recommended)
```bash
cd bootsandcats
./scripts/complete-azure-setup.sh
```

### Option 2: Manual Steps
```bash
cd bootsandcats

# 1. Generate JWK
./scripts/azure-ec-jwk-manager.sh generate > my-jwk.json

# 2. Validate
./scripts/azure-ec-jwk-manager.sh validate my-jwk.json

# 3. Upload to Key Vault
./scripts/azure-ec-jwk-manager.sh upload my-jwk.json

# 4. Deploy to AKS
./scripts/azure-ec-jwk-manager.sh deploy

# 5. Verify
./scripts/azure-ec-jwk-manager.sh verify
```

---

## Configuration

### Environment Variables
```bash
export AZURE_VAULT_NAME="inker-kv"           # Key Vault name
export AZURE_JWK_SECRET_NAME="oauth2-jwk"    # Secret name in Key Vault
export K8S_NAMESPACE="default"               # Kubernetes namespace
```

### Custom Vault/Secret
```bash
AZURE_VAULT_NAME=my-vault \
./scripts/azure-ec-jwk-manager.sh verify
```

---

## Common Tasks

### Generate New Key
```bash
./scripts/azure-ec-jwk-manager.sh generate > new-key.json
./scripts/azure-ec-jwk-manager.sh validate new-key.json
```

### Upload to Key Vault
```bash
./scripts/azure-ec-jwk-manager.sh upload my-key.json
```

### Verify Setup
```bash
./scripts/azure-ec-jwk-manager.sh verify
```

### Key Rotation
```bash
# Generate new key
./scripts/azure-ec-jwk-manager.sh generate > new-key.json

# Rotate (merges old and new)
./scripts/azure-ec-jwk-manager.sh rotate new-key.json

# Deploy
./scripts/azure-ec-jwk-manager.sh deploy

# After tokens expire, use only new key:
./scripts/azure-ec-jwk-manager.sh generate > final-key.json
./scripts/azure-ec-jwk-manager.sh upload final-key.json
./scripts/azure-ec-jwk-manager.sh deploy
```

### View Current Key
```bash
./scripts/azure-ec-jwk-manager.sh download | jq .
```

### Check JWKS Endpoint
```bash
# Port-forward
kubectl port-forward svc/oauth2-server 9000:9000 &

# Check endpoint
curl -s http://localhost:9000/oauth2/jwks | jq .
```

---

## Troubleshooting

### Script won't run
```bash
# Make scripts executable
chmod +x scripts/*.sh

# Check permissions
ls -la scripts/
```

### Missing prerequisites
```bash
# Check what's missing
command -v az kubectl jq

# Install using Homebrew (macOS)
brew install azure-cli kubectl jq

# Install using Chocolatey (Windows)
choco install azure-cli kubernetes-cli jq
```

### Not logged into Azure
```bash
# Login
az login

# Verify
az account show
```

### kubectl not configured
```bash
# Get credentials from AKS cluster
az aks get-credentials --resource-group nekoc --name <cluster-name>

# Verify
kubectl config current-context
```

### Key Vault not found
```bash
# List available vaults
az keyvault list

# Use the correct vault name
AZURE_VAULT_NAME=correct-name ./scripts/azure-ec-jwk-manager.sh verify
```

### Pod still running old version
```bash
# Force restart pods
kubectl rollout restart deployment/oauth2-server

# Watch the rollout
kubectl rollout status deployment/oauth2-server -w

# Check logs
kubectl logs -l app=oauth2-server -f
```

---

## Exit Codes

- `0` - Success
- `1` - General error
- `2` - Prerequisites not met
- `3` - Key Vault error
- `4` - Kubernetes error
- `5` - JWK validation error

---

## Logging & Debugging

### Enable verbose output
```bash
# Bash scripts support -v for verbose
bash -v ./scripts/azure-ec-jwk-manager.sh verify

# Or use set -x for debugging
set -x
./scripts/azure-ec-jwk-manager.sh verify
set +x
```

### Check pod logs
```bash
# Current logs
kubectl logs -l app=oauth2-server

# Follow logs
kubectl logs -l app=oauth2-server -f

# Last 100 lines
kubectl logs -l app=oauth2-server --tail=100

# Previous pod logs (if crashed)
kubectl logs -l app=oauth2-server --previous
```

### View Key Vault operations
```bash
# List all secrets
az keyvault secret list --vault-name inker-kv

# Show specific secret
az keyvault secret show --vault-name inker-kv --name oauth2-jwk

# View secret version history
az keyvault secret list-versions --vault-name inker-kv --name oauth2-jwk
```

---

## Examples

### Complete Setup from Scratch
```bash
#!/bin/bash
cd bootsandcats

# Run automated setup
./scripts/complete-azure-setup.sh --auto

# Verify
./scripts/azure-ec-jwk-manager.sh verify

# Test endpoint
kubectl port-forward svc/oauth2-server 9000:9000 &
curl -s http://localhost:9000/oauth2/jwks | jq .
```

### CI/CD Integration

**GitHub Actions:**
```yaml
- name: Deploy OAuth2 with EC JWK
  run: |
    cd bootsandcats
    az login --service-principal \
      -u ${{ secrets.AZURE_CLIENT_ID }} \
      -p ${{ secrets.AZURE_CLIENT_SECRET }} \
      --tenant ${{ secrets.AZURE_TENANT_ID }}
    
    az aks get-credentials \
      --resource-group nekoc \
      --name production
    
    ./scripts/complete-azure-setup.sh --auto
```

**Azure Pipelines:**
```yaml
- script: |
    cd bootsandcats
    ./scripts/complete-azure-setup.sh --auto
  env:
    AZURE_VAULT_NAME: $(vaultName)
    K8S_NAMESPACE: $(kubeNamespace)
  displayName: Deploy OAuth2 JWK Setup
```

---

## Security Notes

⚠️ **DO NOT:**
- Commit JWK files to git (add `*.json` to `.gitignore`)
- Share JWK content in logs or chat
- Store private key material outside Key Vault
- Use container images with embedded keys

✅ **DO:**
- Store keys in Azure Key Vault
- Use Managed Identities for pod authentication
- Rotate keys quarterly
- Audit Key Vault access logs
- Keep Key IDs for reference

---

## Getting Help

1. **Quick questions:** See `docs/QUICK_START_EC_JWK.md`
2. **Setup details:** See `docs/AZURE_EC_JWK_SETUP.md`
3. **Issues:** See `docs/TROUBLESHOOTING_EC_JWK.md`
4. **Script help:**
   ```bash
   ./scripts/azure-ec-jwk-manager.sh help
   ./scripts/complete-azure-setup.sh --help
   ```
5. **Check config:**
   ```bash
   ./scripts/azure-ec-jwk-manager.sh show-config
   ```

---

## References

- [Azure Key Vault Documentation](https://learn.microsoft.com/en-us/azure/key-vault/)
- [RFC 7517 - JSON Web Key (JWK)](https://tools.ietf.org/html/rfc7517)
- [RFC 7518 - JSON Web Algorithms (JWA)](https://tools.ietf.org/html/rfc7518)
- [Spring Security OAuth2](https://spring.io/projects/spring-security)
- [Kubernetes Secrets Store CSI Driver](https://kubernetes-csi.github.io/docs/secrets-store-csi-driver.html)

---

## Version Information

- **Created:** December 4, 2025
- **Bash Version:** 4.0+
- **PowerShell Version:** 7.0+
- **Kubernetes:** 1.24+
- **Azure CLI:** 2.50+

---

## License

These scripts are part of the OAuth2 Authorization Server project and follow the same license terms.

