# Quick Start: Azure EC JWK Setup for OAuth2 Server

This guide walks you through setting up Elliptic Curve (P-256/ES256) JSON Web Keys for your OAuth2 Authorization Server running in Azure Kubernetes Service.

## 🚀 Quick Start (5 minutes)

### 1. Generate EC JWK Locally

```bash
cd bootsandcats

# Generate a new EC P-256 JWK for ES256 signing
./scripts/azure-ec-jwk-manager.sh generate > my-ec-jwk.json

# View the generated key
cat my-ec-jwk.json | jq .
```

**On Windows (PowerShell):**
```powershell
.\scripts\azure-ec-jwk-manager.ps1 -Command generate | Out-File my-ec-jwk.json -Encoding UTF8
```

### 2. Verify the JWK

```bash
./scripts/azure-ec-jwk-manager.sh validate my-ec-jwk.json
```

**On Windows:**
```powershell
.\scripts\azure-ec-jwk-manager.ps1 -Command validate -FilePath my-ec-jwk.json
```

### 3. Upload to Azure Key Vault

```bash
./scripts/azure-ec-jwk-manager.sh upload my-ec-jwk.json
```

**On Windows:**
```powershell
.\scripts\azure-ec-jwk-manager.ps1 -Command upload -FilePath my-ec-jwk.json
```

### 4. Deploy to AKS

```bash
./scripts/azure-ec-jwk-manager.sh deploy
```

**On Windows:**
```powershell
.\scripts\azure-ec-jwk-manager.ps1 -Command deploy
```

### 5. Verify Everything Works

```bash
./scripts/azure-ec-jwk-manager.sh verify
```

**On Windows:**
```powershell
.\scripts\azure-ec-jwk-manager.ps1 -Command verify
```

---

## 📋 Prerequisites

### macOS / Linux

```bash
# Install Azure CLI
brew install azure-cli

# Install kubectl
brew install kubectl

# Install jq (for JSON processing)
brew install jq

# Login to Azure
az login

# Configure kubectl for your AKS cluster
az aks get-credentials --resource-group <resource-group> --name <aks-cluster-name>
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

# Configure kubectl for your AKS cluster
az aks get-credentials --resource-group <resource-group> --name <aks-cluster-name>
```

---

## 🔑 What Gets Generated

The EC JWK includes:

```json
{
  "keys": [
    {
      "kty": "EC",           // Key Type: Elliptic Curve
      "crv": "P-256",        // Curve: P-256 (secp256r1)
      "alg": "ES256",        // Algorithm: ECDSA with SHA-256
      "use": "sig",          // Usage: Signing
      "kid": "uuid-string",  // Key ID (unique identifier)
      "x": "...",            // Public key X coordinate
      "y": "...",            // Public key Y coordinate
      "d": "..."             // Private key (SECRET - never share)
    }
  ]
}
```

---

## 🛠 Script Commands

### Bash (macOS/Linux)

```bash
./scripts/azure-ec-jwk-manager.sh <command>

# Available commands:
generate              # Generate new EC JWK
upload <file>         # Upload to Key Vault
download              # Download from Key Vault
validate <file>       # Validate JWK format
update-k8s <file>     # Update K8s secret
deploy                # Deploy to AKS
verify                # Verify configuration
rotate <file>         # Rotate keys
show-config           # Show current config
help                  # Show help
```

### PowerShell (Windows)

```powershell
.\scripts\azure-ec-jwk-manager.ps1 -Command <command> -FilePath <path>

# Available commands:
generate              # Generate new EC JWK
upload                # Upload to Key Vault
download              # Download from Key Vault
validate              # Validate JWK format
update-k8s            # Update K8s secret
deploy                # Deploy to AKS
verify                # Verify configuration
rotate                # Rotate keys
show-config           # Show current config
help                  # Show help
```

### Using Gradle Directly

```bash
# Generate EC JWK using Gradle
cd bootsandcats
./gradlew -q -p server-logic :server-logic:runGenerator > my-jwk.json
```

---

## 🔄 Key Rotation (Optional)

To rotate keys without downtime:

```bash
# Generate new key
./scripts/azure-ec-jwk-manager.sh generate > new-jwk.json

# Rotate (keeps old key for backward compatibility)
./scripts/azure-ec-jwk-manager.sh rotate new-jwk.json

# Deploy the update
./scripts/azure-ec-jwk-manager.sh deploy
```

---

## 🔍 Verify JWKS Endpoint

After deployment, verify the `/oauth2/jwks` endpoint returns your EC key:

```bash
# Port-forward to the service
kubectl port-forward svc/oauth2-server 9000:9000 &

# Check the JWKS endpoint
curl -s http://localhost:9000/oauth2/jwks | jq .

# Should output:
# {
#   "keys": [
#     {
#       "kty": "EC",
#       "crv": "P-256",
#       "alg": "ES256",
#       "use": "sig",
#       "kid": "...",
#       "x": "...",
#       "y": "..."
#     }
#   ]
# }
```

---

## ⚙️ Configuration

### Environment Variables

```bash
# macOS/Linux
export AZURE_VAULT_NAME="inker-kv"           # Azure Key Vault name
export AZURE_JWK_SECRET_NAME="oauth2-jwk"    # Secret name in Key Vault
export K8S_NAMESPACE="default"               # Kubernetes namespace
```

**Windows (PowerShell):**
```powershell
$env:AZURE_VAULT_NAME = "inker-kv"
$env:AZURE_JWK_SECRET_NAME = "oauth2-jwk"
$env:K8S_NAMESPACE = "default"
```

### Custom Configuration

```bash
# Use different vault/secret
AZURE_VAULT_NAME=my-vault \
AZURE_JWK_SECRET_NAME=my-secret \
./scripts/azure-ec-jwk-manager.sh deploy
```

---

## 🐛 Troubleshooting

### Issue: "Algorithm mismatch" errors in logs

**Solution:**
- Verify JWK has `"alg": "ES256"`
- Check that the JWK was uploaded to Key Vault
- Restart the pods to load the new key:
  ```bash
  kubectl rollout restart deployment/oauth2-server
  ```

### Issue: "No JWK source configured"

**Solution:**
- Verify the JWK secret exists in Key Vault:
  ```bash
  az keyvault secret list --vault-name inker-kv
  ```
- Check the deployment environment variables:
  ```bash
  kubectl describe deployment oauth2-server
  ```

### Issue: "Azure Key Vault secret not found"

**Solution:**
```bash
# Re-upload the JWK
./scripts/azure-ec-jwk-manager.sh upload my-ec-jwk.json

# Or generate and upload in one step
./scripts/azure-ec-jwk-manager.sh generate | \
  jq -s '{keys: [.[0]]}' > new-jwk.json && \
  ./scripts/azure-ec-jwk-manager.sh upload new-jwk.json
```

### Issue: Pods not starting

**Check logs:**
```bash
kubectl logs -l app=oauth2-server -f --tail=50
```

**Check pod events:**
```bash
kubectl describe pod <pod-name>
```

---

## 📚 Additional Resources

- [Azure Key Vault Documentation](https://learn.microsoft.com/en-us/azure/key-vault/)
- [RFC 7517 - JSON Web Key (JWK)](https://tools.ietf.org/html/rfc7517)
- [RFC 7518 - JSON Web Algorithms (JWA)](https://tools.ietf.org/html/rfc7518)
- [Spring Security OAuth2](https://spring.io/projects/spring-security)
- [NIST P-256 Specification](https://nvlpubs.nist.gov/nistpubs/FIPS/NIST.FIPS.186-4.pdf)

---

## ⚠️ Security Best Practices

1. **Never commit private keys** to version control
2. **Keep `*.json` JWK files** in `.gitignore`
3. **Rotate keys quarterly** at minimum
4. **Monitor Key Vault access** with Azure Monitor
5. **Use Managed Identities** (not connection strings)
6. **Audit all secret access** in Key Vault

---

## 🆘 Getting Help

If you encounter issues:

1. **Check script help:**
   ```bash
   ./scripts/azure-ec-jwk-manager.sh help
   ```

2. **Verify configuration:**
   ```bash
   ./scripts/azure-ec-jwk-manager.sh show-config
   ```

3. **Check pod logs:**
   ```bash
   kubectl logs -l app=oauth2-server --tail=100
   ```

4. **Check events:**
   ```bash
   kubectl events --all-namespaces
   ```

5. **Verify Key Vault access:**
   ```bash
   az keyvault secret list --vault-name inker-kv
   ```

