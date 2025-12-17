# OAuth2 Secrets Sync Issue - Resolution

## Problem
The OAuth2 secrets were not syncing from Azure Key Vault via the Secret Store CSI driver to Kubernetes secrets. The `oauth2-app-secrets` and `oauth2-jwk-secret` Kubernetes secrets were created but contained only empty values (0 bytes), causing the OAuth2 server to fail to start.

## Root Causes

### 1. Missing CSI Volume Mount
The deployment was referencing secrets (`oauth2-app-secrets`) but was **not mounting the SecretProviderClass as a CSI volume**. The Azure Key Vault CSI driver only syncs secrets when a pod actively mounts the volume.

**Symptom:**
```
SecretRotationFailed: failed to patch secret oauth2-app-secrets with new data, err: timed out waiting for the condition
```

### 2. Non-Existent Secrets in Key Vault
The SecretProviderClass referenced social login secrets (github, google, azure) that didn't exist in Azure Key Vault, causing the sync to fail silently.

**Missing secrets:**
- `github-client-id`
- `github-client-secret`
- `google-client-id`
- `google-client-secret`
- `azure-client-id`
- `azure-client-secret`
- `azure-tenant-id`

## Solution

### 1. Added CSI Volume Mount to Deployment

**File:** `infrastructure/k8s/apps/oauth2-server/oauth2-server-deployment.yaml`

Added the CSI volume mount and volume definition:

```yaml
containers:
  - name: oauth2-server
    # ... existing config ...
    volumeMounts:
    - name: secrets-store
      mountPath: "/mnt/secrets-store"
      readOnly: true

volumes:
  - name: flyway-migrations
    configMap:
      name: flyway-migrations
  - name: secrets-store
    csi:
      driver: secrets-store.csi.k8s.io
      readOnly: true
      volumeAttributes:
        secretProviderClass: "oauth2-server-secrets-provider"
```

### 2. Cleaned Up SecretProviderClass

**File:** `infrastructure/k8s/secrets/secret-provider-class-oauth2-server.yaml`

Removed references to non-existent social login secrets:

**Before:**
```yaml
secretObjects:
  - secretName: oauth2-app-secrets
    type: Secret
    data:
      # ... core secrets ...
      - objectName: github-client-id
        key: github-client-id
      - objectName: github-client-secret
        key: github-client-secret
      # ... more social login secrets ...
```

**After:**
```yaml
secretObjects:
  - secretName: oauth2-app-secrets
    type: Secret
    data:
      # ... only existing secrets ...
      - objectName: oauth2-issuer-url
        key: issuer-url
      - objectName: redis-password
        key: redis-password
```

Also removed from the `parameters.objects` array.

### 3. Removed Social Login Environment Variables

**File:** `infrastructure/k8s/apps/oauth2-server/oauth2-server-deployment.yaml`

Removed environment variable references to the non-existent social login secrets:

```yaml
# REMOVED:
# - name: GITHUB_CLIENT_ID
#   valueFrom:
#     secretKeyRef:
#       name: oauth2-app-secrets
#       key: github-client-id
# ... etc
```

## Verification

### Secrets Are Now Populated

```bash
$ kubectl get secret oauth2-app-secrets -o json | jq -r '.data | to_entries[] | "\(.key): \(.value | @base64d | length) bytes"'
admin-user-password:  44    bytes
database-name:        8     bytes
database-password:    44    bytes
database-url:         66    bytes
database-username:    10    bytes
demo-client-secret:   44    bytes
demo-user-password:   44    bytes
issuer-url:           30    bytes
keystore.p12.b64:     5985  bytes
keystore.password:    8     bytes
m2m-client-secret:    44    bytes
redis-password:       32    bytes
```

### OAuth2 Server is Running

```bash
$ kubectl get pods -l app=oauth2-server
NAME                             READY   STATUS    RESTARTS   AGE
oauth2-server-6647645c7c-ggwxm   2/2     Running   0          2m
```

### Environment Variables Are Set

```bash
$ kubectl exec deploy/oauth2-server -- env | grep -E "DATABASE_|OAUTH2_" | head -5
DATABASE_PASSWORD=gqP/kgJ9Jyxd9LOWbcsnwWu9yDKQseEi0nGf8hLoImQ=
DATABASE_URL=jdbc:postgresql://postgres.default.svc.cluster.local:5432/oauth2db?sslmode=disable
DATABASE_USERNAME=oauth2user
OAUTH2_ADMIN_USER_PASSWORD=UySTuB+Tg/uXa0EYk0KRFVBpbzdaADbCpKYyhfrYhQg=
OAUTH2_ISSUER_URL=https://oauth2.cat-herding.net
```

## Key Learnings

### How CSI Secret Store Works

1. **Volume Mount Required**: The SecretProviderClass only syncs secrets when a pod mounts it as a CSI volume
2. **Two-Stage Process**:
   - CSI driver fetches secrets from Azure Key Vault
   - Creates/updates Kubernetes secrets defined in `secretObjects`
3. **Failure Modes**:
   - If any secret in the list doesn't exist, sync may fail silently
   - Empty secrets are created but never populated

### Best Practices

1. **Always mount the CSI volume** in pods that need the secrets
2. **Only reference secrets that exist** in the Key Vault
3. **Use optional: true** for truly optional secrets in the deployment
4. **Monitor CSI driver logs** for sync errors:
   ```bash
   kubectl logs -n kube-system -l app.kubernetes.io/name=csi-secrets-store-provider-azure
   ```
5. **Check secret sizes** to verify they're populated:
   ```bash
   kubectl get secret <name> -o json | jq -r '.data | to_entries[] | "\(.key): \(.value | @base64d | length) bytes"'
   ```

## Troubleshooting

### If secrets are still empty:

1. **Verify secrets exist in Key Vault:**
   ```bash
   az keyvault secret list --vault-name inker-kv --query "[?starts_with(name, 'oauth2')].name" -o table
   ```

2. **Check managed identity permissions:**
   ```bash
   az keyvault show --name inker-kv --query "properties.accessPolicies[?objectId=='<identity-id>']"
   ```

3. **Check CSI driver events:**
   ```bash
   kubectl describe pod <pod-name> | grep -A 20 "Events:"
   ```

4. **Force re-sync:**
   ```bash
   kubectl delete secret <secret-name>
   kubectl delete pod <pod-name>
   ```

## Files Modified

- ✅ `infrastructure/k8s/apps/oauth2-server/oauth2-server-deployment.yaml`
  - Added CSI volume mount
  - Removed social login env vars
  
- ✅ `infrastructure/k8s/secrets/secret-provider-class-oauth2-server.yaml`
  - Removed non-existent secret references
  - Cleaned up parameters.objects array

## Related Issues

- Pod failing with "FATAL: no PostgreSQL user name specified in startup packet"
  - **Cause**: Empty DATABASE_USERNAME environment variable
  - **Fix**: Secrets now properly synced from Azure Key Vault

- SecretRotationFailed timeout errors
  - **Cause**: CSI volume not mounted in pod
  - **Fix**: Added CSI volume mount to deployment

## Future Improvements

### Add Social Login Support (Optional)

If you want to add social login providers:

1. **Create secrets in Azure Key Vault:**
   ```bash
   az keyvault secret set --vault-name inker-kv --name github-client-id --value "your-client-id"
   az keyvault secret set --vault-name inker-kv --name github-client-secret --value "your-secret"
   ```

2. **Update SecretProviderClass** to include them

3. **Update deployment** to add environment variables

### Monitoring

Consider adding monitoring for secret sync status:

```yaml
- name: secret-sync-metrics
  image: prom/pushgateway
  # Push metrics about secret sync status
```

---

**Status:** ✅ **RESOLVED** - Secrets are now syncing correctly and OAuth2 server is running successfully.
