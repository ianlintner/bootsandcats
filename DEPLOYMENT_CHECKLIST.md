# ✅ Deployment Checklist

## Pre-Deployment Verification

- [ ] All scripts created and executable:
  ```bash
  ls -lah scripts/*.sh scripts/*.ps1
  ```

- [ ] All documentation created:
  ```bash
  ls -lah docs/AZURE_EC_JWK_SETUP.md docs/QUICK_START_EC_JWK.md \
           docs/TROUBLESHOOTING_EC_JWK.md docs/IMPLEMENTATION_SUMMARY.md
  ```

- [ ] Master index files created:
  ```bash
  ls -lah INDEX.md scripts/README.md
  ```

- [ ] Configuration files updated:
  ```bash
  grep -l "oauth2-jwk" k8s/*.yaml server-ui/src/main/resources/application-prod.properties
  ```

- [ ] Gradle task added:
  ```bash
  ./gradlew :server-logic:runGenerator --help
  ```

## Prerequisites Installed

- [ ] Azure CLI: `az --version`
- [ ] kubectl: `kubectl version --client`
- [ ] jq: `jq --version`

## Azure Setup

- [ ] Logged into Azure: `az account show`
- [ ] kubectl configured: `kubectl config current-context`
- [ ] Can access Key Vault: `az keyvault show --name inker-kv`
- [ ] Managed Identity has Key Vault access

## Deployment Steps

### Option A: Automated (Recommended)

1. [ ] Navigate to project:
   ```bash
   cd /Users/ianlintner/Projects/bootsandcats
   ```

2. [ ] Run automated setup:
   ```bash
   ./scripts/complete-azure-setup.sh --auto
   ```

3. [ ] Wait for completion (should take ~5 minutes)

### Option B: Interactive

1. [ ] Navigate to project:
   ```bash
   cd /Users/ianlintner/Projects/bootsandcats
   ```

2. [ ] Run interactive setup:
   ```bash
   ./scripts/complete-azure-setup.sh
   ```

3. [ ] Follow the prompts through each step

### Option C: Manual Steps

1. [ ] Generate EC JWK:
   ```bash
   ./scripts/azure-ec-jwk-manager.sh generate > my-jwk.json
   ```

2. [ ] Validate JWK:
   ```bash
   ./scripts/azure-ec-jwk-manager.sh validate my-jwk.json
   ```

3. [ ] Upload to Key Vault:
   ```bash
   ./scripts/azure-ec-jwk-manager.sh upload my-jwk.json
   ```

4. [ ] Deploy to AKS:
   ```bash
   ./scripts/azure-ec-jwk-manager.sh deploy
   ```

5. [ ] Verify configuration:
   ```bash
   ./scripts/azure-ec-jwk-manager.sh verify
   ```

## Post-Deployment Verification

### Immediate Checks

- [ ] Key Vault secret created:
  ```bash
  az keyvault secret show --vault-name inker-kv --name oauth2-jwk
  ```

- [ ] Pods are running:
  ```bash
  kubectl get pods -l app=oauth2-server
  # Expected: At least 2 pods in Running state
  ```

- [ ] Deployment is ready:
  ```bash
  kubectl rollout status deployment/oauth2-server
  # Expected: deployment "oauth2-server" successfully rolled out
  ```

### Endpoint Tests

- [ ] Port-forward to service:
  ```bash
  kubectl port-forward svc/oauth2-server 9000:9000 &
  sleep 2
  ```

- [ ] JWKS endpoint responds:
  ```bash
  curl -s http://localhost:9000/oauth2/jwks | jq .
  # Expected: JSON with keys array
  ```

- [ ] Algorithm is ES256:
  ```bash
  curl -s http://localhost:9000/oauth2/jwks | jq '.keys[0].alg'
  # Expected: "ES256"
  ```

- [ ] Curve is P-256:
  ```bash
  curl -s http://localhost:9000/oauth2/jwks | jq '.keys[0].crv'
  # Expected: "P-256"
  ```

### Pod Logs

- [ ] Check for startup messages:
  ```bash
  kubectl logs -l app=oauth2-server --tail=50 | grep -i jwk
  # Expected: "Loaded X key(s) from Azure Key Vault"
  ```

- [ ] Check for errors:
  ```bash
  kubectl logs -l app=oauth2-server | grep -i error
  # Expected: No errors related to JWK or algorithm
  ```

## Token Generation Test

- [ ] Generate test token:
  ```bash
  curl -X POST http://localhost:9000/oauth2/token \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=client_credentials&client_id=m2m-client&client_secret=m2m-secret" \
    | jq -r '.access_token'
  ```

- [ ] Decode token header:
  ```bash
  TOKEN="<token-from-above>"
  echo $TOKEN | cut -d'.' -f1 | base64 -d | jq .
  # Expected: alg: "ES256"
  ```

## Security Checklist

- [ ] Private keys NOT in container images
- [ ] Private keys NOT in git repository
- [ ] Private keys stored in Azure Key Vault
- [ ] Managed Identity used for authentication
- [ ] No hardcoded passwords in code
- [ ] Key Vault audit logging enabled
- [ ] RBAC policies configured

## Documentation Handoff

- [ ] Team has access to INDEX.md
- [ ] Team knows how to run setup scripts
- [ ] Team understands troubleshooting guide
- [ ] Key IDs documented for reference
- [ ] Key rotation procedures documented

## Rollback Plan (if needed)

- [ ] Previous keys backed up: `./scripts/azure-ec-jwk-manager.sh download > backup-jwk.json`
- [ ] Docker image tagged properly
- [ ] Deployment history available: `kubectl rollout history deployment/oauth2-server`
- [ ] Key Vault version history available: `az keyvault secret list-versions --vault-name inker-kv --name oauth2-jwk`

## Monitoring Setup (Optional but Recommended)

- [ ] Key Vault audit logging enabled
- [ ] Pod logs aggregated to Log Analytics
- [ ] Metrics exported to Prometheus
- [ ] Alerts configured for:
  - Key Vault access failures
  - Pod restart loops
  - Token signing failures
  - JWK loading errors

## Documentation Links

| Document | Purpose | Location |
|----------|---------|----------|
| Quick Start | 5-minute setup | `docs/QUICK_START_EC_JWK.md` |
| Complete Setup | Full reference | `docs/AZURE_EC_JWK_SETUP.md` |
| Troubleshooting | Problem solving | `docs/TROUBLESHOOTING_EC_JWK.md` |
| Implementation | Technical details | `docs/IMPLEMENTATION_SUMMARY.md` |
| Master Index | All resources | `INDEX.md` |
| Scripts Help | Script commands | `scripts/README.md` |

## Support Resources

- **Quick Questions:** `scripts/README.md`
- **Script Help:** `./scripts/azure-ec-jwk-manager.sh help`
- **Verify Setup:** `./scripts/azure-ec-jwk-manager.sh verify`
- **Check Config:** `./scripts/azure-ec-jwk-manager.sh show-config`
- **View Logs:** `kubectl logs -l app=oauth2-server -f`

## Next Steps After Deployment

1. [ ] Monitor logs for 24 hours
2. [ ] Generate and verify test tokens
3. [ ] Document any issues
4. [ ] Train team on maintenance procedures
5. [ ] Schedule key rotation (quarterly)
6. [ ] Set up monitoring and alerts
7. [ ] Review security settings
8. [ ] Plan disaster recovery

## Sign-Off

- [ ] All checks completed
- [ ] No errors encountered
- [ ] System working as expected
- [ ] Team trained and ready
- [ ] Documentation updated

**Deployment completed on:** _______________

**Deployed by:** _______________

**Verified by:** _______________

---

## Notes

```
[Space for additional notes or issues encountered]




```

---

## Quick Reference Commands

```bash
# Setup
./scripts/complete-azure-setup.sh --auto

# Verify
./scripts/azure-ec-jwk-manager.sh verify

# Generate new key
./scripts/azure-ec-jwk-manager.sh generate > new-key.json

# Rotate keys
./scripts/azure-ec-jwk-manager.sh rotate new-key.json
./scripts/azure-ec-jwk-manager.sh deploy

# View logs
kubectl logs -l app=oauth2-server -f

# Test JWKS endpoint
kubectl port-forward svc/oauth2-server 9000:9000 &
curl -s http://localhost:9000/oauth2/jwks | jq .

# Check configuration
./scripts/azure-ec-jwk-manager.sh show-config
```

---

**Status: Ready for Deployment ✅**

