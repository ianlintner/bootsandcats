# TLS Keystore Setup Guide

This guide explains how the OAuth2 Authorization Server's TLS keystore is managed securely using Azure Key Vault and GitHub Secrets, without storing private keys in the repository.

## Overview

The TLS keystore (`keystore.p12`) contains a self-signed certificate for `oauth2.cat-herding.net` and `cat-herding.net` domains. The keystore is:

- **NOT stored in git** - Protected by `.gitignore` with `*.p12` pattern
- **Stored in Azure Key Vault** - `oauth2-kv` vault with secrets for both keystore and password
- **Available in GitHub Secrets** - For CI/CD workflows and Copilot
- **Mounted in Kubernetes** - Via Azure Key Vault Secret Store CSI Driver

## Keystore Details

| Property | Value |
|----------|-------|
| **Alias** | `oauth2` |
| **Password** | `gabbycat` |
| **Type** | PKCS12 |
| **Algorithm** | RSA 4096-bit |
| **Signature** | SHA256withRSA |
| **Validity** | 825 days |
| **Subject** | `CN=oauth2.cat-herding.net, OU=Boots and Cats, O=Boots and Cats, L=Seattle, ST=WA, C=US` |
| **SANs** | `oauth2.cat-herding.net`, `cat-herding.net` |

## Azure Key Vault Storage

The keystore is stored in Azure Key Vault `oauth2-kv` in the `nekoc` resource group:

### Secrets

| Secret Name | Description |
|-------------|-------------|
| `oauth2-tls-keystore` | Base64-encoded PKCS12 keystore file |
| `oauth2-tls-keystore-password` | Keystore password (`gabbycat`) |

### Access

```bash
# View keystore secret
az keyvault secret show --vault-name oauth2-kv --name oauth2-tls-keystore

# View password secret
az keyvault secret show --vault-name oauth2-kv --name oauth2-tls-keystore-password

# Download keystore locally (for regeneration/rotation)
az keyvault secret show --vault-name oauth2-kv --name oauth2-tls-keystore --query value -o tsv | base64 -d > keystore.p12
```

## GitHub Secrets

The keystore and password are stored as GitHub repository secrets for CI/CD:

### Available Secrets

| Secret Name | Description |
|-------------|-------------|
| `OAUTH2_TLS_KEYSTORE` | Base64-encoded PKCS12 keystore file |
| `SSL_KEYSTORE_PASSWORD` | Keystore password |

### Usage in GitHub Actions

```yaml
- name: Restore keystore for build
  run: |
    echo "${{ secrets.OAUTH2_TLS_KEYSTORE }}" | base64 -d > src/main/resources/keystore.p12
  
- name: Run tests with SSL
  env:
    SSL_KEYSTORE_PASSWORD: ${{ secrets.SSL_KEYSTORE_PASSWORD }}
  run: ./mvnw test
```

## Kubernetes Deployment

The keystore is mounted into Kubernetes pods via the Azure Key Vault Secret Store CSI Driver.

### Prerequisites

1. **Secrets Store CSI Driver** installed in AKS cluster
2. **Azure Key Vault Provider** for CSI driver installed
3. **Managed Identity** with Key Vault access configured for the cluster

### SecretProviderClass

The `k8s/secret-provider-class.yaml` defines how to fetch secrets:

```yaml
apiVersion: secrets-store.csi.x-k8s.io/v1
kind: SecretProviderClass
metadata:
  name: oauth2-keystore-provider
spec:
  provider: azure
  secretObjects:
    - secretName: oauth2-keystore-secret
      type: Opaque
      data:
        - objectName: oauth2-tls-keystore
          key: keystore.p12.b64
        - objectName: oauth2-tls-keystore-password
          key: keystore.password
  parameters:
    keyvaultName: oauth2-kv
    tenantId: "42ddc44e-97dd-4c3b-bf8a-31c785e24c67"
    # ... additional parameters
```

### Deployment Integration

The deployment mounts the keystore volume and reads the password from the synced Kubernetes secret:

```yaml
env:
  - name: SSL_KEYSTORE_PASSWORD
    valueFrom:
      secretKeyRef:
        name: oauth2-keystore-secret
        key: keystore.password

volumeMounts:
  - name: keystore-volume
    mountPath: "/mnt/keystore"
    readOnly: true

volumes:
  - name: keystore-volume
    csi:
      driver: secrets-store.csi.k8s.io
      volumeAttributes:
        secretProviderClass: oauth2-keystore-provider
```

### Application Configuration

The Spring Boot application needs to be configured to load the keystore from the mounted volume:

```properties
server.ssl.enabled=true
server.ssl.key-store=file:/mnt/keystore/keystore.p12.b64
server.ssl.key-store-password=${SSL_KEYSTORE_PASSWORD}
server.ssl.key-store-type=PKCS12
server.ssl.key-alias=oauth2
```

**Note:** The keystore is stored base64-encoded in Key Vault. You'll need to decode it before use or update the application to handle base64-encoded keystores.

## Deployment Steps

### 1. Install CSI Driver (if not already installed)

```bash
# Install Secrets Store CSI Driver
helm repo add secrets-store-csi-driver https://kubernetes-sigs.github.io/secrets-store-csi-driver/charts
helm install csi-secrets-store secrets-store-csi-driver/secrets-store-csi-driver --namespace kube-system

# Install Azure Key Vault Provider
kubectl apply -f https://raw.githubusercontent.com/Azure/secrets-store-csi-driver-provider-azure/master/deployment/provider-azure-installer.yaml
```

### 2. Configure Managed Identity

```bash
# Grant AKS managed identity access to Key Vault
az keyvault set-policy \
  --name oauth2-kv \
  --object-id <MANAGED_IDENTITY_OBJECT_ID> \
  --secret-permissions get list
```

### 3. Deploy to Kubernetes

```bash
# Apply SecretProviderClass
kubectl apply -f k8s/secret-provider-class.yaml

# Deploy application
kubectl apply -f k8s/deployment.yaml

# Verify secrets are mounted
kubectl exec -it <pod-name> -- ls -la /mnt/keystore
```

## Keystore Regeneration

If you need to regenerate or rotate the keystore:

### 1. Generate New Keystore

```bash
keytool -genkeypair \
  -alias oauth2 \
  -keyalg RSA \
  -keysize 4096 \
  -sigalg SHA256withRSA \
  -storetype PKCS12 \
  -keystore keystore.p12 \
  -storepass <NEW_PASSWORD> \
  -validity 825 \
  -dname "CN=oauth2.cat-herding.net, OU=Boots and Cats, O=Boots and Cats, L=Seattle, ST=WA, C=US" \
  -ext "SAN=dns:oauth2.cat-herding.net,dns:cat-herding.net"
```

### 2. Update Azure Key Vault

```bash
# Base64 encode
base64 -i keystore.p12 -o keystore.p12.b64

# Upload to Key Vault
az keyvault secret set \
  --vault-name oauth2-kv \
  --name oauth2-tls-keystore \
  --file keystore.p12.b64

# Update password if changed
az keyvault secret set \
  --vault-name oauth2-kv \
  --name oauth2-tls-keystore-password \
  --value "<NEW_PASSWORD>"
```

### 3. Update GitHub Secrets

```bash
# Update keystore
gh secret set OAUTH2_TLS_KEYSTORE < keystore.p12.b64

# Update password if changed
gh secret set SSL_KEYSTORE_PASSWORD --body "<NEW_PASSWORD>"
```

### 4. Restart Pods

```bash
# Restart to pick up new secrets
kubectl rollout restart deployment/oauth2-server
```

## Security Best Practices

1. **Never commit keystores to git** - Always keep `*.p12` in `.gitignore`
2. **Rotate credentials regularly** - Plan to regenerate the keystore before expiry
3. **Use strong passwords** - The current password `gabbycat` should be rotated for production
4. **Limit Key Vault access** - Use managed identities and least-privilege policies
5. **Monitor access** - Enable Key Vault audit logging
6. **Use production certificates** - Replace self-signed certs with CA-issued certs for production

## Troubleshooting

### Keystore not mounted in pod

```bash
# Check SecretProviderClass status
kubectl describe secretproviderclass oauth2-keystore-provider

# Check pod events
kubectl describe pod <pod-name>

# Check CSI driver logs
kubectl logs -n kube-system -l app=secrets-store-csi-driver
```

### Key Vault access denied

```bash
# Verify managed identity has access
az keyvault show --name oauth2-kv --query properties.accessPolicies

# Check managed identity assignment
az aks show --name bigboy --resource-group <rg> --query identityProfile
```

### Application can't load keystore

```bash
# Verify keystore is decoded from base64
kubectl exec -it <pod-name> -- cat /mnt/keystore/keystore.p12.b64 | base64 -d > /tmp/test.p12
kubectl exec -it <pod-name> -- keytool -list -keystore /tmp/test.p12 -storepass gabbycat

# Check environment variable
kubectl exec -it <pod-name> -- env | grep SSL_KEYSTORE_PASSWORD
```

## References

- [Azure Key Vault Provider for Secrets Store CSI Driver](https://azure.github.io/secrets-store-csi-driver-provider-azure/)
- [Spring Boot SSL Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/howto.html#howto.webserver.configure-ssl)
- [GitHub Encrypted Secrets](https://docs.github.com/en/actions/security-guides/encrypted-secrets)
