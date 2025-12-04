# 🔐 Azure OAuth2 EC JWK - Complete Implementation Guide

## 📋 Overview

This package provides a complete solution for setting up Elliptic Curve (P-256/ES256) JSON Web Keys for the OAuth2 Authorization Server running in Azure Kubernetes Service.

**Problem Solved:** Algorithm mismatch errors when deploying to Azure due to RSA/EC key mismatch.

**Solution:** Automated tools for generating, managing, and deploying EC JWK keys with Azure Key Vault integration.

---

## 🚀 Quick Start (Choose One)

### Option 1: Fully Automated Setup ⭐ (RECOMMENDED)
```bash
cd bootsandcats
./scripts/complete-azure-setup.sh --auto
```
**Time:** ~5 minutes | **Difficulty:** Easy

### Option 2: Interactive Guided Setup
```bash
cd bootsandcats
./scripts/complete-azure-setup.sh
```
**Time:** ~5 minutes | **Difficulty:** Easy (prompts guide you)

### Option 3: Manual Steps
```bash
cd bootsandcats
./scripts/azure-ec-jwk-manager.sh generate > my-jwk.json
./scripts/azure-ec-jwk-manager.sh validate my-jwk.json
./scripts/azure-ec-jwk-manager.sh upload my-jwk.json
./scripts/azure-ec-jwk-manager.sh deploy
./scripts/azure-ec-jwk-manager.sh verify
```
**Time:** ~10 minutes | **Difficulty:** Medium

---

## 📂 What You Get

### Scripts (Ready to Use)

| File | Purpose | Platform |
|------|---------|----------|
| `scripts/complete-azure-setup.sh` | Interactive setup wizard | macOS/Linux |
| `scripts/azure-ec-jwk-manager.sh` | JWK management CLI | macOS/Linux |
| `scripts/azure-ec-jwk-manager.ps1` | JWK management CLI | Windows |
| `scripts/generate-ec-jwk.sh` | Simple key generator | macOS/Linux |
| `scripts/README.md` | Scripts documentation | All |

### Documentation (Reference)

| File | Content | Read Time |
|------|---------|-----------|
| `docs/QUICK_START_EC_JWK.md` | 5-minute setup guide | 5 min |
| `docs/AZURE_EC_JWK_SETUP.md` | Complete setup details | 20 min |
| `docs/TROUBLESHOOTING_EC_JWK.md` | Problem solving guide | Reference |
| `docs/IMPLEMENTATION_SUMMARY.md` | Technical overview | 15 min |

### Configuration (Updated)

| File | Changes |
|------|---------|
| `k8s/deployment.yaml` | ✅ Added JWK environment variables |
| `k8s/secret-provider-class.yaml` | ✅ Added oauth2-jwk secret |
| `server-ui/src/main/resources/application-prod.properties` | ✅ Added Azure Key Vault config |
| `server-logic/build.gradle.kts` | ✅ Added runGenerator task |

---

## 📖 Documentation Index

### For First-Time Users
1. **START HERE:** `docs/QUICK_START_EC_JWK.md` - 5-minute setup
2. Then: `scripts/README.md` - Understand what scripts do
3. Then: Run `./scripts/complete-azure-setup.sh`

### For Detailed Setup
- Read: `docs/AZURE_EC_JWK_SETUP.md`
- Sections:
  - Problem & Solution (why EC keys?)
  - Step 1-5: Complete setup process
  - Key rotation strategy
  - Security best practices

### For Troubleshooting
- Reference: `docs/TROUBLESHOOTING_EC_JWK.md`
- Common issues:
  - Algorithm mismatch errors
  - "No JWK source configured"
  - Key Vault secret not found
  - Pods not starting

### For Technical Details
- Reference: `docs/IMPLEMENTATION_SUMMARY.md`
- Includes:
  - Architecture diagram
  - How it works
  - File changes made
  - Performance notes
  - Compliance features

---

## 🛠 Script Commands

### Complete Setup (All-in-One)
```bash
./scripts/complete-azure-setup.sh [--auto]
```
Generates key, uploads to vault, deploys to AKS, verifies.

### Bash Management Tool (macOS/Linux)
```bash
./scripts/azure-ec-jwk-manager.sh <command>

# Commands:
generate              # Generate new EC JWK
upload <file>         # Upload to Key Vault
download              # Download from Key Vault
validate <file>       # Validate JWK format
update-k8s <file>     # Update K8s secret
deploy                # Deploy to AKS
verify                # Verify configuration
rotate <file>         # Rotate keys
show-config           # Show configuration
help                  # Show help
```

### PowerShell Management Tool (Windows)
```powershell
.\scripts\azure-ec-jwk-manager.ps1 -Command <command> [-FilePath <path>]

# All commands same as bash version
```

### Simple Generator (macOS/Linux)
```bash
./scripts/generate-ec-jwk.sh > my-jwk.json
```

---

## ✅ Verification Checklist

After running the setup script:

- [ ] Key Vault secret exists: `az keyvault secret show --vault-name inker-kv --name oauth2-jwk`
- [ ] Pods are running: `kubectl get pods -l app=oauth2-server`
- [ ] Deployment is ready: `kubectl rollout status deployment/oauth2-server`
- [ ] JWKS endpoint works: `curl http://localhost:9000/oauth2/jwks | jq .`
- [ ] Algorithm is ES256: `curl http://localhost:9000/oauth2/jwks | jq '.keys[0].alg'`

---

## 🔑 Key Details

### What Gets Generated

```json
{
  "keys": [
    {
      "kty": "EC",           # Elliptic Curve
      "crv": "P-256",        # P-256 curve (secp256r1)
      "alg": "ES256",        # ES256 algorithm
      "use": "sig",          # Signing use
      "kid": "unique-id",    # Key ID
      "x": "...",            # Public X coordinate
      "y": "...",            # Public Y coordinate
      "d": "..."             # Private key (SECRET!)
    }
  ]
}
```

### Where It's Stored
1. **Private Key:** Azure Key Vault (encrypted)
2. **Pod Access:** SecretProviderClass CSI Driver
3. **Spring Config:** Via `AZURE_KEYVAULT_STATIC_JWK` env var
4. **Public Key:** `/oauth2/jwks` endpoint (safe to share)

### How It Works
1. Pod starts → Managed Identity authenticates to Key Vault
2. SecretProviderClass CSI mounts JWK secret
3. Spring loads JWK via property
4. AuthorizationServerConfig uses JWK for ES256 signing
5. Tokens signed with EC private key
6. Clients verify with EC public key

---

## 🔄 Key Rotation (Optional)

When you need to rotate keys:

```bash
# 1. Generate new key
./scripts/azure-ec-jwk-manager.sh generate > new-key.json

# 2. Rotate (keeps old for backward compatibility)
./scripts/azure-ec-jwk-manager.sh rotate new-key.json

# 3. Deploy
./scripts/azure-ec-jwk-manager.sh deploy

# 4. After tokens expire, use only new key:
./scripts/azure-ec-jwk-manager.sh generate > final-key.json
./scripts/azure-ec-jwk-manager.sh upload final-key.json
./scripts/azure-ec-jwk-manager.sh deploy
```

---

## ⚙️ Configuration

### Environment Variables
```bash
AZURE_VAULT_NAME="inker-kv"           # Key Vault name
AZURE_JWK_SECRET_NAME="oauth2-jwk"    # Secret name
K8S_NAMESPACE="default"               # K8s namespace
```

### Application Properties
File: `server-ui/src/main/resources/application-prod.properties`

```properties
azure.keyvault.enabled=true
azure.keyvault.vault-uri=https://inker-kv.vault.azure.net/
azure.keyvault.jwk-secret-name=oauth2-jwk
azure.keyvault.cache-ttl=PT10M
```

### Kubernetes Deployment
File: `k8s/deployment.yaml`

```yaml
env:
- name: AZURE_KEYVAULT_ENABLED
  value: "true"
- name: AZURE_KEYVAULT_VAULT_URI
  value: "https://inker-kv.vault.azure.net/"
```

---

## 🔍 Monitoring & Debugging

### Check Configuration
```bash
./scripts/azure-ec-jwk-manager.sh show-config
```

### Verify Setup
```bash
./scripts/azure-ec-jwk-manager.sh verify
```

### View Pod Logs
```bash
kubectl logs -l app=oauth2-server -f --tail=50
```

### Test JWKS Endpoint
```bash
kubectl port-forward svc/oauth2-server 9000:9000 &
curl http://localhost:9000/oauth2/jwks | jq .
```

### Download Current Key
```bash
./scripts/azure-ec-jwk-manager.sh download | jq .keys[0].alg
# Should output: "ES256"
```

---

## 🐛 Common Issues & Solutions

### "Algorithm mismatch" Error
**Solution:** Verify JWK is loaded and restart pods
```bash
./scripts/azure-ec-jwk-manager.sh verify
kubectl rollout restart deployment/oauth2-server
```

### "No JWK source configured"
**Solution:** Check environment variables and upload JWK
```bash
./scripts/azure-ec-jwk-manager.sh generate > new.json
./scripts/azure-ec-jwk-manager.sh upload new.json
./scripts/azure-ec-jwk-manager.sh deploy
```

### "Azure Key Vault secret not found"
**Solution:** Create the secret
```bash
./scripts/azure-ec-jwk-manager.sh generate > new.json
./scripts/azure-ec-jwk-manager.sh upload new.json
```

### Pods in CrashLoopBackOff
**Solution:** Check logs and database connection
```bash
kubectl logs <pod-name> --tail=100
kubectl describe pod <pod-name>
```

For more issues, see: `docs/TROUBLESHOOTING_EC_JWK.md`

---

## 📋 System Requirements

### macOS/Linux
```bash
# Install prerequisites
brew install azure-cli kubectl jq

# Or use package manager of choice
apt-get install azure-cli kubectl jq  # Ubuntu/Debian
yum install azure-cli kubectl jq      # RedHat/CentOS

# Verify
az --version
kubectl version --client
jq --version
```

### Windows (PowerShell)
```powershell
# Install prerequisites
choco install azure-cli kubernetes-cli jq

# Verify
az --version
kubectl version --client
jq --version
```

### Azure Resources Needed
- Azure AKS cluster (1.24+)
- Azure Key Vault
- AKS Managed Identity with Key Vault access
- kubectl configured

---

## 🔐 Security Best Practices

✅ **DO:**
- Store JWK in Azure Key Vault
- Use Managed Identities for pod auth
- Rotate keys quarterly
- Audit Key Vault access logs
- Keep Key IDs in secure documentation
- Use RBAC to limit access

❌ **DON'T:**
- Commit JWK to git
- Store keys in container images
- Share private key material
- Use passwords for pod authentication
- Log sensitive key data
- Disable Key Vault auditing

---

## 📊 Architecture

```
Client App
    │
    ├─ Requests /oauth2/authorize
    ├─ Gets authorization code
    ├─ Requests /oauth2/token
    │
    └─ Receives JWT with ES256 signature
           │
           ▼
    OAuth2 Server (AKS Pod)
           │
           ├─ Reads EC P-256 key from:
           │  ├─ Azure Key Vault (via Managed Identity)
           │  └─ Cached in memory
           │
           ├─ Signs JWT with ES256 algorithm
           ├─ Publishes public key at /oauth2/jwks
           │
           └─ Key material never exposed
```

---

## 📞 Getting Help

1. **Setup issues?** → `docs/QUICK_START_EC_JWK.md`
2. **How it works?** → `docs/AZURE_EC_JWK_SETUP.md`
3. **Troubleshooting?** → `docs/TROUBLESHOOTING_EC_JWK.md`
4. **Technical details?** → `docs/IMPLEMENTATION_SUMMARY.md`
5. **Script help?** → `./scripts/azure-ec-jwk-manager.sh help`

---

## 🎯 Next Steps

### Immediate (Today)
1. ✅ Prerequisites installed (`az`, `kubectl`, `jq`)
2. ✅ Logged into Azure (`az login`)
3. ✅ kubectl configured (`az aks get-credentials ...`)
4. ✅ Run setup: `./scripts/complete-azure-setup.sh --auto`

### Short Term (This Week)
1. ✅ Verify pods running: `kubectl get pods -l app=oauth2-server`
2. ✅ Test JWKS endpoint: `curl http://localhost:9000/oauth2/jwks | jq .`
3. ✅ Generate test token and validate signature
4. ✅ Document Key ID for reference

### Medium Term (This Month)
1. ✅ Review Azure Key Vault audit logs
2. ✅ Set up monitoring and alerts
3. ✅ Plan quarterly key rotation schedule
4. ✅ Train team on scripts and procedures

---

## 📝 File Summary

```
scripts/
├── README.md                           # Scripts documentation
├── complete-azure-setup.sh             # Interactive wizard ⭐
├── azure-ec-jwk-manager.sh             # Bash CLI tool
├── azure-ec-jwk-manager.ps1            # PowerShell CLI tool
└── generate-ec-jwk.sh                  # Simple generator

docs/
├── QUICK_START_EC_JWK.md               # 5-min setup
├── AZURE_EC_JWK_SETUP.md               # Complete guide
├── TROUBLESHOOTING_EC_JWK.md           # Problem solving
└── IMPLEMENTATION_SUMMARY.md           # Technical overview

k8s/
├── deployment.yaml                     # ✅ Updated with JWK env vars
└── secret-provider-class.yaml          # ✅ Updated with JWK secret

server-ui/src/main/resources/
└── application-prod.properties         # ✅ Updated with JWK config

server-logic/
└── build.gradle.kts                    # ✅ Added runGenerator task
```

---

## 📊 Implementation Status

| Component | Status | Details |
|-----------|--------|---------|
| EC JWK Generator | ✅ Done | `EcJwkGenerator.java` generates P-256 keys |
| Bash CLI Tool | ✅ Done | `azure-ec-jwk-manager.sh` - full featured |
| PowerShell Tool | ✅ Done | `azure-ec-jwk-manager.ps1` - Windows support |
| Setup Wizard | ✅ Done | `complete-azure-setup.sh` - interactive guide |
| Kubernetes Config | ✅ Done | Updated deployment and SecretProviderClass |
| Spring Config | ✅ Done | Added Azure Key Vault properties |
| Gradle Task | ✅ Done | `./gradlew :server-logic:runGenerator` |
| Documentation | ✅ Done | 4 comprehensive guides |

---

## 🎓 Learning Resources

- [RFC 7517 - JSON Web Key](https://tools.ietf.org/html/rfc7517)
- [RFC 7518 - JSON Web Algorithms](https://tools.ietf.org/html/rfc7518)
- [NIST P-256 Specification](https://nvlpubs.nist.gov/nistpubs/FIPS/NIST.FIPS.186-4.pdf)
- [Azure Key Vault Documentation](https://learn.microsoft.com/en-us/azure/key-vault/)
- [Spring Security OAuth2](https://spring.io/projects/spring-security)
- [Kubernetes Secrets Store CSI Driver](https://kubernetes-csi.github.io/docs/secrets-store-csi-driver.html)

---

## ✨ Summary

You now have a complete, production-ready solution for:
- ✅ Generating EC JWK keys (P-256/ES256)
- ✅ Securely storing keys in Azure Key Vault
- ✅ Deploying to AKS with proper integration
- ✅ Rotating keys without downtime
- ✅ Monitoring and troubleshooting
- ✅ Full documentation and guides

**Ready to start?** Run:
```bash
./scripts/complete-azure-setup.sh --auto
```

**Questions?** Check the documentation or run:
```bash
./scripts/azure-ec-jwk-manager.sh help
```

---

**Created:** December 4, 2025 | **Version:** 1.0 | **Status:** Production Ready ✅

