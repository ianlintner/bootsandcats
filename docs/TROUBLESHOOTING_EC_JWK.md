# Troubleshooting Guide: Algorithm Mismatch and EC JWK Issues

This guide helps resolve common issues when setting up EC JWK (P-256/ES256) keys for the OAuth2 Authorization Server in Azure.

## Common Issues and Solutions

### 1. Algorithm Mismatch Error

**Symptom:**
```
Error: Algorithm mismatch
Invalid token signature
JWT algorithm ES256 expected but RS256 found
```

**Root Causes:**
- RSA keys still being used instead of EC keys
- JWK not loaded from Key Vault
- Stale keys cached in pods
- Mismatch between token issuer and verifier algorithms

**Solutions:**

a) **Verify current JWK in Key Vault:**
```bash
# Check what's currently in Key Vault
az keyvault secret show \
  --vault-name inker-kv \
  --name oauth2-jwk \
  --query value -o json | jq '.keys[] | {alg, kty, crv}'

# Should show: alg: "ES256", kty: "EC", crv: "P-256"
```

b) **If wrong algorithm, upload correct JWK:**
```bash
# Generate new EC JWK
./scripts/azure-ec-jwk-manager.sh generate > correct-jwk.json

# Verify it's correct
./scripts/azure-ec-jwk-manager.sh validate correct-jwk.json

# Upload to Key Vault
./scripts/azure-ec-jwk-manager.sh upload correct-jwk.json
```

c) **Force pod restart to reload keys:**
```bash
# Restart all oauth2-server pods
kubectl rollout restart deployment/oauth2-server

# Watch the rollout
kubectl rollout status deployment/oauth2-server -w

# Check logs
kubectl logs -l app=oauth2-server -f --tail=50
```

d) **Verify JWKS endpoint after restart:**
```bash
# Port-forward to service
kubectl port-forward svc/oauth2-server 9000:9000 &
sleep 2

# Check JWKS endpoint
curl -s http://localhost:9000/oauth2/jwks | jq '.keys[] | {alg, kty, crv}'
```

---

### 2. "No JWK Source Configured" Error

**Symptom:**
```
FATAL: No JWK source configured. The OAuth2 Authorization Server requires signing keys...
```

**Root Causes:**
- Neither Azure Key Vault nor static JWK is configured
- Environment variables not set
- Key Vault secret doesn't exist

**Solutions:**

a) **Check current configuration:**
```bash
./scripts/azure-ec-jwk-manager.sh show-config

# Check pod environment variables
kubectl exec -it <pod-name> -- env | grep -i azure
```

b) **Ensure environment variables are set in deployment:**
```bash
# Check deployment env vars
kubectl get deployment oauth2-server -o yaml | grep -A 20 "env:"

# Should include:
# - AZURE_KEYVAULT_ENABLED: "true"
# - AZURE_KEYVAULT_VAULT_URI: "https://inker-kv.vault.azure.net/"
# - AZURE_KEYVAULT_JWK_SECRET_NAME: "oauth2-jwk"
```

c) **If not set, update deployment:**
```bash
# Verify the deployment file has the env vars
cat k8s/deployment.yaml | grep -A 5 "AZURE_KEYVAULT"

# Apply updated deployment
kubectl apply -f k8s/deployment.yaml

# Restart pods
kubectl rollout restart deployment/oauth2-server
```

d) **Generate and upload JWK:**
```bash
# If Key Vault secret doesn't exist
./scripts/azure-ec-jwk-manager.sh generate > new-jwk.json
./scripts/azure-ec-jwk-manager.sh upload new-jwk.json

# Restart pods to pick up new key
kubectl rollout restart deployment/oauth2-server
```

---

### 3. "Azure Key Vault Secret Not Found"

**Symptom:**
```
FATAL: Azure Key Vault secret 'oauth2-jwk' not found
```

**Root Causes:**
- Secret doesn't exist in Key Vault
- Wrong vault name configured
- Wrong secret name configured
- Access permissions missing

**Solutions:**

a) **Check if secret exists:**
```bash
# List all secrets in Key Vault
az keyvault secret list --vault-name inker-kv

# Look for "oauth2-jwk" in the list
# If not present, it needs to be created
```

b) **Create the secret:**
```bash
# Generate JWK
./scripts/azure-ec-jwk-manager.sh generate > my-jwk.json

# Upload to Key Vault
./scripts/azure-ec-jwk-manager.sh upload my-jwk.json

# Verify it was created
az keyvault secret show --vault-name inker-kv --name oauth2-jwk
```

c) **Verify pod can access Key Vault:**
```bash
# Check managed identity permissions
az role assignment list \
  --assignee "e502213f-1f15-4f03-9fb4-b546f51aafe9" \
  --scope /subscriptions/<subscription-id>/resourceGroups/nekoc/providers/Microsoft.KeyVault/vaults/inker-kv

# Should have "Key Vault Secrets User" role
```

d) **If permissions missing, grant access:**
```bash
# Get the Key Vault ID
VAULT_ID=$(az keyvault show --name inker-kv --query id -o tsv)

# Grant the managed identity access
az role assignment create \
  --role "Key Vault Secrets User" \
  --assignee-object-id "e502213f-1f15-4f03-9fb4-b546f51aafe9" \
  --scope "$VAULT_ID"

# Restart pods
kubectl rollout restart deployment/oauth2-server
```

---

### 4. Invalid JWK Format Error

**Symptom:**
```
FATAL: Failed to parse JWK Set from Azure Key Vault
Invalid JSON or missing required fields
```

**Root Causes:**
- Corrupted JWK file uploaded
- Wrong file format uploaded
- Incomplete JWK data

**Solutions:**

a) **Validate current JWK in Key Vault:**
```bash
# Download and check
az keyvault secret show \
  --vault-name inker-kv \
  --name oauth2-jwk \
  --query value -o json | jq . > current-jwk.json

# Verify it's valid
./scripts/azure-ec-jwk-manager.sh validate current-jwk.json
```

b) **If invalid, regenerate:**
```bash
# Generate new JWK
./scripts/azure-ec-jwk-manager.sh generate > new-jwk.json

# Validate before uploading
./scripts/azure-ec-jwk-manager.sh validate new-jwk.json

# Upload (will replace invalid one)
./scripts/azure-ec-jwk-manager.sh upload new-jwk.json

# Restart pods
kubectl rollout restart deployment/oauth2-server
```

---

### 5. Pods Not Starting / CrashLoopBackOff

**Symptom:**
```
Pod is stuck in CrashLoopBackOff state
```

**Solutions:**

a) **Check pod logs:**
```bash
# Get pod names
kubectl get pods -l app=oauth2-server

# Check logs
kubectl logs <pod-name> --tail=100

# Check previous logs (if pod crashed)
kubectl logs <pod-name> --previous
```

b) **Check pod events:**
```bash
# Get detailed pod info
kubectl describe pod <pod-name>

# Look at Events section
```

c) **Common startup issues:**
- **Database connection failing**: Check DATABASE_URL env var
- **JWK not found**: Check AZURE_KEYVAULT_* env vars
- **Permission denied**: Check managed identity permissions
- **Resource limits**: Check if pod has enough memory/CPU

d) **Debug with exec:**
```bash
# If pod starts temporarily, exec into it
kubectl exec -it <pod-name> -- bash

# Check environment
env | grep -i azure
env | grep -i spring

# Check if JWK can be read
cat /run/secrets/kubernetes.io/serviceaccount/token
```

---

### 6. Token Validation Failures

**Symptom:**
```
Invalid token signature
JWT validation failed
Token signature verification failed
```

**Causes:**
- Client verifying with old key
- Server restarted and generated new key (in non-persistent mode)
- Token issued before key rotation
- Public key not available to verifier

**Solutions:**

a) **Verify server is using persisted keys:**
```bash
# Check Key Vault secret is being read
kubectl logs -l app=oauth2-server | grep -i "jwk\|key" | tail -20

# Should show: "Loaded X key(s) from Azure Key Vault"
```

b) **Verify token matches key:**
```bash
# Decode token header
TOKEN="your-jwt-token"
echo $TOKEN | cut -d'.' -f1 | base64 -d | jq . | grep kid

# Get the key ID from JWKS
curl -s http://localhost:9000/oauth2/jwks | jq '.keys[] | {kid, alg, use}'

# Match should exist with same alg and use
```

c) **If keys don't match, check for partial rotation:**
```bash
# Download current keys from Key Vault
./scripts/azure-ec-jwk-manager.sh download > current-keys.json

# Check how many keys are in the set
jq '.keys | length' current-keys.json

# If you're in middle of rotation, both old and new keys should be present
jq '.keys[] | {kid, alg}' current-keys.json
```

---

### 7. Manual Testing and Verification

**Complete verification script:**

```bash
#!/bin/bash

echo "=== OAuth2 Server EC JWK Verification ==="
echo

# 1. Check Key Vault
echo "1. Checking Azure Key Vault..."
if az keyvault secret show --vault-name inker-kv --name oauth2-jwk &>/dev/null; then
    echo "  ✓ Secret exists in Key Vault"
    echo "  Key info:"
    az keyvault secret show --vault-name inker-kv --name oauth2-jwk --query value -o json | jq '.keys[] | {alg, kty, crv, kid}'
else
    echo "  ✗ Secret NOT found in Key Vault"
fi
echo

# 2. Check Deployment
echo "2. Checking Deployment..."
kubectl get deployment oauth2-server -o wide
echo

# 3. Check Pod Status
echo "3. Checking Pod Status..."
kubectl get pods -l app=oauth2-server
echo

# 4. Check Environment Variables
echo "4. Checking Environment Variables in Pod..."
POD=$(kubectl get pods -l app=oauth2-server -o jsonpath='{.items[0].metadata.name}')
echo "  Pod: $POD"
kubectl exec $POD -- env | grep -i azure
echo

# 5. Check Logs
echo "5. Recent Pod Logs..."
kubectl logs $POD --tail=20
echo

# 6. Check JWKS Endpoint
echo "6. Checking JWKS Endpoint..."
kubectl port-forward svc/oauth2-server 9000:9000 >/dev/null 2>&1 &
PORT_PID=$!
sleep 2
curl -s http://localhost:9000/oauth2/jwks | jq '.keys[] | {alg, kty, crv, kid, use}'
kill $PORT_PID
echo

echo "=== Verification Complete ==="
```

---

### 8. Key Rotation Troubleshooting

**Issue: "Algorithm mismatch" after key rotation**

```bash
# Check both old and new keys are in Key Vault
./scripts/azure-ec-jwk-manager.sh download | jq '.keys | length'

# Should see both keys if in middle of rotation:
./scripts/azure-ec-jwk-manager.sh download | jq '.keys[] | {kid, alg, use}'

# Wait for old tokens to expire (check token TTL)
# Then remove old key:
./scripts/azure-ec-jwk-manager.sh generate > final-key.json
./scripts/azure-ec-jwk-manager.sh upload final-key.json

# Restart pods
kubectl rollout restart deployment/oauth2-server
```

---

## Quick Diagnostic Commands

```bash
# Show current configuration
./scripts/azure-ec-jwk-manager.sh show-config

# Verify everything
./scripts/azure-ec-jwk-manager.sh verify

# Download current JWK from Key Vault
./scripts/azure-ec-jwk-manager.sh download > current.json

# Check JWK format
jq '.keys[] | {kty, crv, alg, use}' current.json

# Port-forward and test JWKS endpoint
kubectl port-forward svc/oauth2-server 9000:9000 &
curl -s http://localhost:9000/oauth2/jwks | jq .

# Check pod logs
kubectl logs -l app=oauth2-server -f --tail=50

# Get deployment status
kubectl rollout status deployment/oauth2-server

# Describe deployment
kubectl describe deployment oauth2-server

# Get all events
kubectl get events --sort-by='.lastTimestamp'
```

---

## When to Escalate

If the above solutions don't work:

1. **Collect diagnostic information:**
   ```bash
   kubectl describe deployment oauth2-server > deployment.txt
   kubectl logs -l app=oauth2-server > pod-logs.txt
   ./scripts/azure-ec-jwk-manager.sh download > keyvault-jwk.json
   az keyvault secret show --vault-name inker-kv --name oauth2-jwk > keyvault-secret.txt
   ```

2. **Check Azure Support:**
   - Managed Identity permissions
   - Key Vault firewall rules
   - AKS cluster health

3. **Review application logs:**
   - Spring Boot startup logs
   - JWK loading logs
   - JWT validation logs

---

## References

- [RFC 7517 - JSON Web Key (JWK)](https://tools.ietf.org/html/rfc7517)
- [RFC 7518 - JSON Web Algorithms](https://tools.ietf.org/html/rfc7518)
- [NIST Elliptic Curves](https://nvlpubs.nist.gov/nistpubs/FIPS/NIST.FIPS.186-4.pdf)
- [Azure Key Vault Documentation](https://learn.microsoft.com/en-us/azure/key-vault/)
- [Spring Security OAuth2](https://spring.io/projects/spring-security)

