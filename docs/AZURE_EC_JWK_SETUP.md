# Azure EC JWK Setup Guide

This guide explains how to create and manage Elliptic Curve (EC) JSON Web Keys for the OAuth2 Authorization Server running in Azure Kubernetes Service (AKS).

## Problem: Algorithm Mismatch Error

When deploying to Azure, you may encounter an "algorithm mismatch" error because:

1. **RSA keys are currently used** - The system was initially configured with RSA keys
2. **ES256 (EC) algorithm is specified** - The AuthorizationServerConfig declares ES256 as the signing algorithm
3. **Mismatch between declared and actual keys** - This causes JWT validation failures

## Solution: Use EC P-256 Keys with ES256

The codebase already supports EC keys through:
- `JwkSupport.generateEcSigningKey()` - Generates P-256 EC keys
- `EcJwkGenerator` - Command-line tool to generate keys
- `JwkSetProvider` - Loads keys from Azure Key Vault or static config
- `AuthorizationServerConfig` - Configured for ES256 signing

## Step 1: Generate EC JWK Locally

### Option A: Using the provided script

```bash
cd /path/to/bootsandcats

# Generate a new EC JWK (P-256/ES256)
./scripts/generate-ec-jwk.sh > my-ec-jwk.json

# View the generated key
cat my-ec-jwk.json | jq .
```

### Option B: Using Gradle directly

```bash
cd /path/to/bootsandcats

./gradlew -q -p server-logic :server-logic:runGenerator > my-ec-jwk.json
```

The output will be a JSON Web Key Set similar to:

```json
{
  "keys": [
    {
      "kty": "EC",
      "crv": "P-256",
      "x": "...",
      "y": "...",
      "d": "...",    // Private key material - KEEP SECRET!
      "use": "sig",
      "alg": "ES256",
      "kid": "uuid-here"
    }
  ]
}
```

### Key Details

| Property | Value |
|----------|-------|
| **kty** | EC (Elliptic Curve) |
| **crv** | P-256 (secp256r1 curve) |
| **use** | sig (signing) |
| **alg** | ES256 (ECDSA with SHA-256) |
| **kid** | Key ID (unique identifier) |
| **x, y** | Public key coordinates |
| **d** | Private key (SECRET - never share) |

## Step 2: Store in Azure Key Vault

### Prerequisites

```bash
# Install Azure CLI if not already installed
brew install azure-cli

# Login to Azure
az login

# Set your subscription
az account set --subscription "your-subscription-id"
```

### Upload to Key Vault

```bash
# Variables
VAULT_NAME="inker-kv"  # Update with your Key Vault name
SECRET_NAME="oauth2-jwk"  # Secret name in Key Vault
JWK_FILE="my-ec-jwk.json"  # Path to generated JWK

# View the content to verify
cat "$JWK_FILE"

# Upload the JWK to Azure Key Vault
az keyvault secret set \
  --vault-name "$VAULT_NAME" \
  --name "$SECRET_NAME" \
  --file "$JWK_FILE"

# Verify it was uploaded
az keyvault secret show \
  --vault-name "$VAULT_NAME" \
  --name "$SECRET_NAME" \
  --query "value" -o json | jq .
```

### Update the SecretProviderClass

Add the JWK secret to your `k8s/secret-provider-class.yaml`:

```yaml
apiVersion: secrets-store.csi.x-k8s.io/v1
kind: SecretProviderClass
metadata:
  name: oauth2-secrets-provider
  namespace: default
spec:
  provider: azure
  secretObjects:
    # ... existing secrets ...
    - secretName: oauth2-app-secrets
      type: Opaque
      data:
        # ... existing data ...
        - objectName: oauth2-jwk
          key: jwk.json
  parameters:
    # ... existing parameters ...
    objects: |
      array:
        # ... existing objects ...
        - |
          objectName: oauth2-jwk
          objectType: secret
```

## Step 3: Configure Application

### Update Kubernetes Deployment

Add the JWK environment variable to `k8s/deployment.yaml`:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: oauth2-server
spec:
  template:
    spec:
      containers:
      - name: oauth2-server
        env:
        # ... existing environment variables ...
        - name: AZURE_KEYVAULT_STATIC_JWK
          valueFrom:
            secretKeyRef:
              name: oauth2-app-secrets
              key: jwk.json
        # Or if using Key Vault directly:
        - name: AZURE_KEYVAULT_ENABLED
          value: "true"
        - name: AZURE_KEYVAULT_VAULT_URI
          value: "https://inker-kv.vault.azure.net/"
        # ... rest of env vars ...
```

### Update application-prod.properties

Add or update in `server-ui/src/main/resources/application-prod.properties`:

```properties
# Azure Key Vault Configuration
azure.keyvault.enabled=${AZURE_KEYVAULT_ENABLED:false}
azure.keyvault.vault-uri=${AZURE_KEYVAULT_VAULT_URI:}
azure.keyvault.jwk-secret-name=${AZURE_KEYVAULT_JWK_SECRET_NAME:oauth2-jwk}
azure.keyvault.cache-ttl=${AZURE_KEYVAULT_CACHE_TTL:PT10M}

# Or use static JWK (useful for testing)
azure.keyvault.static-jwk=${AZURE_KEYVAULT_STATIC_JWK:}
```

## Step 4: Test Locally

### Test with Static JWK

```bash
cd /path/to/bootsandcats

# Generate JWK
./scripts/generate-ec-jwk.sh > test-jwk.json

# Run with static JWK environment variable
export AZURE_KEYVAULT_STATIC_JWK=$(cat test-jwk.json)
export SPRING_PROFILES_ACTIVE=prod

./gradlew :server-ui:bootRun
```

### Verify ES256 Algorithm

```bash
# Get the JWKS endpoint
curl -s http://localhost:9000/oauth2/jwks | jq .

# Should see:
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

## Step 5: Deploy to AKS

```bash
# 1. Build the Docker image
docker build -t gabby.azurecr.io/oauth2-server:latest .

# 2. Push to Azure Container Registry
docker push gabby.azurecr.io/oauth2-server:latest

# 3. Apply the updated SecretProviderClass
kubectl apply -f k8s/secret-provider-class.yaml

# 4. Apply the updated Deployment
kubectl apply -f k8s/deployment.yaml

# 5. Monitor the rollout
kubectl rollout status deployment/oauth2-server

# 6. Check logs for any errors
kubectl logs -l app=oauth2-server -f --tail=50

# 7. Verify the JWKS endpoint
kubectl port-forward svc/oauth2-server 9000:9000 &
curl -s http://localhost:9000/oauth2/jwks | jq .
```

## Key Rotation Strategy

For key rotation without downtime:

1. **Generate new EC JWK**
   ```bash
   ./scripts/generate-ec-jwk.sh > new-jwk.json
   ```

2. **Create JWK Set with both old and new keys**
   ```bash
   # Extract the existing key
   az keyvault secret show --vault-name inker-kv --name oauth2-jwk --query value -o json > old-jwk.json
   
   # Merge keys (both old and new) into a single JWKSet
   # You can use jq or a custom tool for this
   jq -s '{"keys": (.[0].keys + .[1].keys)}' old-jwk.json new-jwk.json > merged-jwk.json
   ```

3. **Upload the merged set**
   ```bash
   az keyvault secret set --vault-name inker-kv --name oauth2-jwk --file merged-jwk.json
   ```

4. **After tokens with old key expire, remove the old key**
   ```bash
   cat new-jwk.json | az keyvault secret set --vault-name inker-kv --name oauth2-jwk --file /dev/stdin
   ```

## Troubleshooting

### Issue: "Algorithm mismatch" errors

**Cause**: JWT decoder expects a different algorithm than what's in the JWK set

**Solution**: 
- Verify JWK has `"alg": "ES256"`
- Check that `AuthorizationServerConfig` specifies ES256
- Ensure both servers (issuer and verifier) use the same algorithm

### Issue: "No JWK source configured"

**Cause**: Neither Azure Key Vault nor static JWK is configured

**Solution**:
- Set `AZURE_KEYVAULT_STATIC_JWK` environment variable with JWK content, OR
- Set `AZURE_KEYVAULT_ENABLED=true` and provide vault URI

### Issue: "Azure Key Vault secret not found"

**Cause**: JWK secret doesn't exist in Key Vault

**Solution**:
```bash
# List all secrets in Key Vault
az keyvault secret list --vault-name inker-kv

# Upload if missing
./scripts/generate-ec-jwk.sh | az keyvault secret set --vault-name inker-kv --name oauth2-jwk --file /dev/stdin
```

### Issue: Managed Identity doesn't have Key Vault access

**Cause**: AKS Managed Identity lacks permissions

**Solution**:
```bash
# Grant the managed identity access to Key Vault
IDENTITY_ID="e502213f-1f15-4f03-9fb4-b546f51aafe9"  # From your deployment
VAULT_ID=$(az keyvault show --name inker-kv --query id -o tsv)

az role assignment create \
  --role "Key Vault Secrets User" \
  --assignee-object-id "$IDENTITY_ID" \
  --scope "$VAULT_ID"
```

## Security Best Practices

1. **Never commit private keys** - Keep `.gitignore` entries for `*.json` JWK files
2. **Rotate keys regularly** - Implement a key rotation schedule (quarterly recommended)
3. **Audit Key Vault access** - Enable Azure Monitor logging for Key Vault operations
4. **Use Managed Identities** - Never use connection strings or passwords in code
5. **Encrypt secrets in transit** - Use HTTPS for all API calls
6. **Monitor key usage** - Set up alerts for suspicious key access patterns

## References

- [RFC 7517 - JSON Web Key (JWK)](https://tools.ietf.org/html/rfc7517)
- [RFC 7518 - JSON Web Algorithms (JWA)](https://tools.ietf.org/html/rfc7518#section-3)
- [Spring Security OAuth2 Documentation](https://spring.io/projects/spring-security)
- [Azure Key Vault Best Practices](https://learn.microsoft.com/en-us/azure/key-vault/general/best-practices)
- [NIST P-256 Specification](https://nvlpubs.nist.gov/nistpubs/FIPS/NIST.FIPS.186-4.pdf)

