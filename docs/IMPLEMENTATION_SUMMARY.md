# Azure EC JWK Setup - Implementation Summary

This document summarizes the implementation of EC JWK (P-256/ES256) key management for the OAuth2 Authorization Server running in Azure Kubernetes Service.

## Problem Statement

When deploying to Azure, the OAuth2 server was experiencing **algorithm mismatch errors** because:
- The code declares ES256 (Elliptic Curve) as the signing algorithm
- But RSA keys were being used instead
- This caused JWT validation failures

## Solution Overview

Implement proper EC JWK (P-256/ES256) key generation, storage, and management with:
- Automated key generation with `EcJwkGenerator`
- Secure storage in Azure Key Vault
- Configuration management via Spring properties
- Kubernetes integration with SecretProviderClass
- Comprehensive CLI tools for management

---

## What Was Created

### 1. **Bash Management Script** (macOS/Linux)
   **File:** `scripts/azure-ec-jwk-manager.sh`
   - Generate EC JWK (P-256/ES256)
   - Upload/download from Key Vault
   - Validate JWK format
   - Update Kubernetes secrets
   - Deploy to AKS
   - Key rotation support
   - Full error handling and validation

### 2. **PowerShell Management Script** (Windows)
   **File:** `scripts/azure-ec-jwk-manager.ps1`
   - Same functionality as bash version
   - Native Windows PowerShell implementation
   - Compatible with PowerShell 7.0+

### 3. **Complete Setup Wizard** (macOS/Linux)
   **File:** `scripts/complete-azure-setup.sh`
   - Interactive guided setup
   - Automated setup mode (`--auto`)
   - Step-by-step verification
   - Beautiful formatted output
   - Prerequisites checking
   - Deployment verification

### 4. **Gradle Task**
   **File:** `server-logic/build.gradle.kts`
   - Added `runGenerator` task
   - Executes `EcJwkGenerator` CLI tool
   - Can be called from scripts or directly: `./gradlew :server-logic:runGenerator`

### 5. **Kubernetes Manifests** (Updated)
   **Files:**
   - `k8s/secret-provider-class.yaml` - Added oauth2-jwk secret
   - `k8s/deployment.yaml` - Added AZURE_KEYVAULT environment variables

### 6. **Application Configuration** (Updated)
   **File:** `server-ui/src/main/resources/application-prod.properties`
   - Added Azure Key Vault configuration properties
   - JWK secret name, cache TTL, vault URI

### 7. **Documentation**

   a) **Quick Start Guide**
      **File:** `docs/QUICK_START_EC_JWK.md`
      - 5-minute setup instructions
      - Command examples for both bash and PowerShell
      - Prerequisites installation
      - Configuration overview
      - Basic troubleshooting

   b) **Complete Setup Guide**
      **File:** `docs/AZURE_EC_JWK_SETUP.md`
      - Detailed problem explanation
      - Step-by-step setup process
      - Key rotation strategy
      - Security best practices
      - Comprehensive troubleshooting
      - References and resources

   c) **Troubleshooting Guide**
      **File:** `docs/TROUBLESHOOTING_EC_JWK.md`
      - 8 common issues with solutions
      - Algorithm mismatch errors
      - Configuration problems
      - Diagnostic commands
      - Key rotation issues
      - Escalation procedures

---

## Key Features

### ✅ **Automated Key Generation**
```bash
./scripts/azure-ec-jwk-manager.sh generate > my-jwk.json
```
Generates P-256 Elliptic Curve keys suitable for ES256 JWT signing.

### ✅ **Azure Key Vault Integration**
```bash
./scripts/azure-ec-jwk-manager.sh upload my-jwk.json
```
Secure storage of private key material in Azure Key Vault with:
- Managed Identity authentication (no credentials in code)
- Version tracking
- Audit logging
- Access control

### ✅ **Kubernetes Integration**
- SecretProviderClass pulls JWK from Key Vault
- Pods receive keys via environment variables
- Automatic secret injection into containers
- No key material in container images

### ✅ **Validation & Verification**
```bash
./scripts/azure-ec-jwk-manager.sh validate my-jwk.json
./scripts/azure-ec-jwk-manager.sh verify
```
- Format validation (JSON structure)
- Algorithm verification (ES256)
- Curve verification (P-256)
- Endpoint testing

### ✅ **Key Rotation**
```bash
./scripts/azure-ec-jwk-manager.sh rotate new-jwk.json
```
Zero-downtime key rotation:
- Keeps old key for backward compatibility
- New key used for signing
- Both keys available for validation
- Remove old key after token expiration

### ✅ **Multi-Platform Support**
- **macOS/Linux:** Bash script with full feature set
- **Windows:** PowerShell script with identical functionality
- **CI/CD:** Scripts can be called from GitHub Actions, Azure Pipelines, etc.

---

## Configuration Files Modified

### `k8s/secret-provider-class.yaml`
**Added:**
```yaml
secretObjects:
  - objectName: oauth2-jwk
    key: jwk.json
```

### `k8s/deployment.yaml`
**Added:**
```yaml
- name: AZURE_KEYVAULT_ENABLED
  value: "true"
- name: AZURE_KEYVAULT_VAULT_URI
  value: "https://inker-kv.vault.azure.net/"
- name: AZURE_KEYVAULT_JWK_SECRET_NAME
  value: "oauth2-jwk"
```

### `server-ui/src/main/resources/application-prod.properties`
**Added:**
```properties
azure.keyvault.enabled=${AZURE_KEYVAULT_ENABLED:false}
azure.keyvault.vault-uri=${AZURE_KEYVAULT_VAULT_URI:}
azure.keyvault.jwk-secret-name=${AZURE_KEYVAULT_JWK_SECRET_NAME:oauth2-jwk}
azure.keyvault.cache-ttl=${AZURE_KEYVAULT_CACHE_TTL:PT10M}
```

---

## How It Works

### 1. **Key Generation**
- Uses `JwkSupport.generateEcSigningKey()` from codebase
- Creates P-256 Elliptic Curve key pair
- Generates unique Key ID (KID)
- Configured for ES256 algorithm

### 2. **Key Storage**
- JWK stored in Azure Key Vault as JSON secret
- Never stored in container images
- Never committed to git
- Versioned by Key Vault

### 3. **Pod Access**
- AKS Managed Identity authenticates to Key Vault
- SecretProviderClass CSI driver mounts secret
- Spring application loads via property: `azure.keyvault.static-jwk`
- `JwkSetProvider` uses cached key set

### 4. **Token Signing**
- `AuthorizationServerConfig` configured for ES256
- `jwtCustomizer()` sets algorithm to ES256
- Tokens signed with EC private key
- Public key published via `/oauth2/jwks` endpoint

### 5. **Verification**
- Clients retrieve public key from `/oauth2/jwks`
- Verify tokens using EC P-256 public key
- Algorithm matches: ES256

---

## Usage Examples

### Complete Setup (Interactive)
```bash
./scripts/complete-azure-setup.sh
```
Guided wizard for complete setup.

### Complete Setup (Automated)
```bash
./scripts/complete-azure-setup.sh --auto
```
Fully automated, no prompts.

### Generate Only
```bash
./scripts/azure-ec-jwk-manager.sh generate > my-jwk.json
```

### Validate JWK
```bash
./scripts/azure-ec-jwk-manager.sh validate my-jwk.json
```

### Upload to Key Vault
```bash
./scripts/azure-ec-jwk-manager.sh upload my-jwk.json
```

### Deploy to AKS
```bash
./scripts/azure-ec-jwk-manager.sh deploy
```

### Verify Everything
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

# After old tokens expire, clean up:
./scripts/azure-ec-jwk-manager.sh generate > final-key.json
./scripts/azure-ec-jwk-manager.sh upload final-key.json
./scripts/azure-ec-jwk-manager.sh deploy
```

---

## Deployment Checklist

- [ ] Prerequisites installed (Azure CLI, kubectl, jq)
- [ ] Logged into Azure: `az login`
- [ ] kubectl context configured: `az aks get-credentials ...`
- [ ] Run setup script: `./scripts/complete-azure-setup.sh --auto`
- [ ] Verify pods are running: `kubectl get pods -l app=oauth2-server`
- [ ] Test JWKS endpoint: `curl http://localhost:9000/oauth2/jwks | jq .`
- [ ] Generate test token and verify algorithm is ES256
- [ ] Document the Key ID for reference

---

## Key ID Reference

After setup, keep track of the Key ID (kid) for reference:

```bash
# Get current Key ID
./scripts/azure-ec-jwk-manager.sh download | jq '.keys[0].kid'

# Use in monitoring and troubleshooting
```

---

## Security Considerations

✅ **What's Secure:**
- Private keys never in container images
- Private keys never in git repository
- Private keys encrypted in Key Vault
- Managed Identity authentication (no passwords)
- Audit logging for Key Vault access
- Version control for key changes

⚠️ **What to Do:**
- Rotate keys quarterly
- Monitor Key Vault access logs
- Keep Key IDs for audit trail
- Use strong RBAC policies
- Enable Key Vault firewall rules
- Archive old keys for compliance

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                     Azure AKS Cluster                       │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  oauth2-server Pod                                   │  │
│  │  ┌─────────────────────────────────────────────────┐ │  │
│  │  │ Spring Boot Application                         │ │  │
│  │  │ ┌─────────────────────────────────────────────┐ │ │  │
│  │  │ │ AuthorizationServerConfig                   │ │ │  │
│  │  │ │ - ES256 Algorithm                           │ │ │  │
│  │  │ │ - JwkSetProvider                            │ │ │  │
│  │  │ │ - /oauth2/jwks endpoint                     │ │ │  │
│  │  │ └─────────────────────────────────────────────┘ │ │  │
│  │  └─────────────────────────────────────────────────┘ │  │
│  │  ┌─────────────────────────────────────────────────┐ │  │
│  │  │ SecretProviderClass Volume Mount               │ │  │
│  │  │ - oauth2-jwk.json (EC P-256 Key)               │ │  │
│  │  └─────────────────────────────────────────────────┘ │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
          │
          │ (Azure Managed Identity)
          │
          ▼
┌─────────────────────────────────────────────────────────────┐
│                  Azure Key Vault                            │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ oauth2-jwk (Secret)                                 │  │
│  │ ┌────────────────────────────────────────────────┐  │  │
│  │ │ {                                              │  │  │
│  │ │   "keys": [                                    │  │  │
│  │ │     {                                          │  │  │
│  │ │       "kty": "EC",                             │  │  │
│  │ │       "crv": "P-256",                          │  │  │
│  │ │       "alg": "ES256",                          │  │  │
│  │ │       "use": "sig",                            │  │  │
│  │ │       "d": "... private key ..."               │  │  │
│  │ │       "x": "... public x ...",                 │  │  │
│  │ │       "y": "... public y ..."                  │  │  │
│  │ │     }                                          │  │  │
│  │ │   ]                                            │  │  │
│  │ │ }                                              │  │  │
│  │ └────────────────────────────────────────────────┘  │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

---

## Performance Considerations

- **JWK Caching:** 10 minutes (configurable via `AZURE_KEYVAULT_CACHE_TTL`)
- **Key Validation:** Once per cache period per pod
- **Token Signing:** In-memory, no external calls
- **JWKS Endpoint:** Public key only, safe to cache client-side

---

## Compliance Notes

✅ **Compliance Features:**
- Audit logging of all Key Vault accesses
- Encryption at rest and in transit
- No secrets in logs
- No secrets in configuration files
- Version control for all changes
- Managed Identity for pod authentication

---

## Next Steps

1. **Immediate:** Run `./scripts/complete-azure-setup.sh --auto`
2. **Verification:** Monitor logs and test `/oauth2/jwks` endpoint
3. **Documentation:** Share guide with team
4. **Automation:** Integrate key rotation into maintenance schedule
5. **Monitoring:** Set up alerts for Key Vault access patterns

---

## Support & Troubleshooting

- **Quick questions?** See `docs/QUICK_START_EC_JWK.md`
- **Setup details?** See `docs/AZURE_EC_JWK_SETUP.md`
- **Problems?** See `docs/TROUBLESHOOTING_EC_JWK.md`
- **Script help:** `./scripts/azure-ec-jwk-manager.sh help`

---

## Files Created

```
scripts/
├── azure-ec-jwk-manager.sh         # Bash management tool
├── azure-ec-jwk-manager.ps1        # PowerShell management tool
├── complete-azure-setup.sh         # Interactive setup wizard
└── generate-ec-jwk.sh              # Simple generator script

docs/
├── AZURE_EC_JWK_SETUP.md           # Complete setup guide
├── QUICK_START_EC_JWK.md           # Quick start guide
└── TROUBLESHOOTING_EC_JWK.md       # Troubleshooting guide

k8s/
├── deployment.yaml                 # Updated with JWK env vars
└── secret-provider-class.yaml      # Updated with JWK secret

server-ui/src/main/resources/
└── application-prod.properties     # Updated with JWK config

server-logic/
└── build.gradle.kts                # Added runGenerator task
```

---

## Version Information

- **Created:** December 4, 2025
- **Java:** 21 (LTS)
- **Spring Boot:** 3.4.x
- **JWK Algorithm:** ES256 (ECDSA with P-256 and SHA-256)
- **Key Type:** EC (Elliptic Curve)
- **Curve:** P-256 (secp256r1)

---

## Contact & Support

For issues or questions:
1. Check the troubleshooting guide
2. Review pod logs: `kubectl logs -l app=oauth2-server`
3. Verify configuration: `./scripts/azure-ec-jwk-manager.sh show-config`
4. Check Key Vault access: `az keyvault secret list --vault-name inker-kv`

