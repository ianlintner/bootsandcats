# OAuth2 Sidecar Secret Mount Issue - Root Cause Analysis

## Problem Summary
Profile-service OAuth2 flow fails with 404 at `/oauth2/authorize` due to volume name mismatch between init container and Istio sidecar mount.

## Root Cause
**Volume name mismatch**: Init container writes secrets to `rendered-oauth-sds` but Istio annotation mounts `rendered-sds`, causing Envoy to fail loading OAuth2 credentials.

## Resolution (Dec 16, 2025)
**✅ STANDARDIZED ON `rendered-sds`** for all services to match github-review's working pattern.

### Changes Applied

#### Chat (bootsandcats repo) ✅ FIXED
- Updated `infrastructure/k8s/apps/chat/deployment.yaml`
- Changed volume name from `rendered-oauth-sds` → `rendered-sds`
- Added explicit annotation: `sidecar.istio.io/userVolumeMount: '[{"name":"rendered-sds","mountPath":"/etc/istio/oauth2","readonly":true}]'`

#### Profile-Service (bootsandcats repo) ✅ FIXED
- Updated `infrastructure/k8s/apps/profile-service/profile-service-deployment.yaml`
- Changed volume name from `rendered-oauth-sds` → `rendered-sds`
- Updated annotation to array format: `sidecar.istio.io/userVolumeMount: '[{"name":"rendered-sds","mountPath":"/etc/istio/oauth2","readonly":true}]'`

#### Github-Review (external repo) ✅ ALREADY CORRECT
No changes needed - already uses `rendered-sds` consistently.

### Current State (Dec 16, 2025) - After Fix

| Service | Init Container Output | Istio Mount | Status |
|---------|----------------------|-------------|--------|
| **github-review** ✅ | `rendered-sds` | `rendered-sds` | Working |
| **chat** ✅ | `rendered-sds` | `rendered-sds` | **FIXED & TESTED** |
| **profile-service** ✅ | `rendered-sds` | `rendered-sds` | **FIXED & TESTED** |

All services now use consistent `rendered-sds` volume naming.

### Detailed Findings

#### Github-Review (Working)
```yaml
# Annotation
sidecar.istio.io/userVolumeMount: '[{"name":"rendered-sds","mountPath":"/etc/istio/oauth2","readonly":true}]'

# Init container writes to:
volumeMounts:
  - mountPath: /etc/istio/oauth2
    name: rendered-sds  # ✅ MATCHES

# Istio-proxy mounts:
volumeMounts:
  - mountPath: /etc/istio/oauth2
    name: rendered-sds
    readOnly: true  # ✅ ACCESSIBLE
```

#### Profile-Service (Broken)
```yaml
# Annotation
sidecar.istio.io/userVolumeMount: '[{"name":"rendered-sds","mountPath":"/etc/istio/oauth2","readonly":true}]'

# Init container writes to:
volumeMounts:
  - mountPath: /etc/istio/oauth2
    name: rendered-oauth-sds  # ❌ MISMATCH!

# Istio-proxy tries to mount:
# (fails silently - no volume named "rendered-sds" with OAuth data)
```

#### Chat (Working)
```yaml
# Annotation  
sidecar.istio.io/userVolumeMount: '{"rendered-oauth-sds":{"mountPath":"/etc/istio/oauth2","readOnly":true}}'

# Init container writes to:
volumeMounts:
  - mountPath: /etc/istio/oauth2
    name: rendered-oauth-sds  # ✅ MATCHES

# Istio-proxy mounts:
volumeMounts:
  - mountPath: /etc/istio/oauth2
    name: rendered-oauth-sds
    readOnly: true  # ✅ ACCESSIBLE
```

## Symptoms
- Authorization redirect returns 404 from `istio-envoy`
- OAuth2 EnvoyFilter references SDS paths that don't exist in sidecar
- Init container logs show success, but Envoy can't load secrets
- No errors in Istio logs (silent mount failure)

## Solution Applied: Standardize on `rendered-sds`

All services now use `rendered-sds` as the volume name for OAuth2 secrets. This is the simplest, shortest name and matches the working github-review service.

### Standard Pattern for All Services

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {service-name}
spec:
  template:
    metadata:
      annotations:
        sidecar.istio.io/inject: 'true'
        # CRITICAL: Must match volume name below
        sidecar.istio.io/userVolumeMount: '[{"name":"rendered-sds","mountPath":"/etc/istio/oauth2","readonly":true}]'
    spec:
      initContainers:
      - name: render-sds-config
        image: python:3.11-alpine  # or busybox
        command: ["/bin/sh", "-c"]
        args:
        - |
          # Read secrets from Key Vault CSI mount
          CLIENT_SECRET=$(cat /mnt/secrets-store/{service}-client-secret)
          HMAC_SECRET=$(cat /mnt/secrets-store/{service}-oauth-hmac-secret)
          
          # Write SDS YAML files
          cat > /etc/istio/oauth2/{service}-oauth-token.yaml <<EOF
          resources:
          - "@type": type.googleapis.com/envoy.extensions.transport_sockets.tls.v3.Secret
            name: {service}-oauth-token
            generic_secret:
              secret:
                inline_string: "${CLIENT_SECRET}"
          EOF
          
          cat > /etc/istio/oauth2/{service}-oauth-hmac.yaml <<EOF
          resources:
          - "@type": type.googleapis.com/envoy.extensions.transport_sockets.tls.v3.Secret
            name: {service}-oauth-hmac
            generic_secret:
              secret:
                inline_string: "${HMAC_SECRET}"
          EOF
        volumeMounts:
        - name: secrets-store
          mountPath: /mnt/secrets-store
          readOnly: true
        - name: rendered-sds  # MUST MATCH annotation above
          mountPath: /etc/istio/oauth2
      
      containers:
      - name: {service-name}
        # ... app container config
      
      volumes:
      - name: secrets-store
        csi:
          driver: secrets-store.csi.k8s.io
          readOnly: true
          volumeAttributes:
            secretProviderClass: azure-keyvault-oauth2-secrets
      - name: rendered-sds  # MUST MATCH annotation above
        emptyDir: {}
```

### EnvoyFilter Configuration (Consistent)

All EnvoyFilters reference the SDS paths inside `/etc/istio/oauth2/`:

```yaml
credentials:
  client_id: {service-name}
  token_secret:
    name: {service}-oauth-token
    sds_config:
      path: /etc/istio/oauth2/{service}-oauth-token.yaml
  hmac_secret:
    name: {service}-oauth-hmac
    sds_config:
      path: /etc/istio/oauth2/{service}-oauth-hmac.yaml
```

## Before/After Comparison

## Immediate Actions

### ✅ All Services Fixed (Dec 16, 2025)

All deployments updated and tested successfully:
- **Chat**: OAuth flow working
- **Profile-Service**: OAuth flow working  
- **Github-Review**: OAuth flow working (unchanged)

## Testing After Fix

```bash
# 1. Chat - verified
$ curl -sI https://chat.cat-herding.net/ | head -3
HTTP/2 302
location: https://oauth2.cat-herding.net/oauth2/authorize?client_id=chat-backend...

# 2. Profile-Service - verified
$ curl -sI https://profile.cat-herding.net/ | head -3
HTTP/2 302
location: https://oauth2.cat-herding.net/oauth2/authorize?client_id=profile-service...

# 3. Github-Review - verified
$ curl -sI https://gh-review.cat-herding.net/ | head -3
HTTP/2 302
location: https://oauth2.cat-herding.net/oauth2/authorize?client_id=github-review-service...

# 4. Volume mounts - all show rendered-sds
$ for app in chat-backend profile-service github-review-service; do
  POD=$(kubectl get pod -l app=$app -o jsonpath='{.items[0].metadata.name}')
  echo -n "$app: "
  kubectl get pod $POD -o jsonpath='{.spec.containers[?(@.name=="istio-proxy")].volumeMounts[?(@.mountPath=="/etc/istio/oauth2")].name}'
  echo ""
done
chat-backend: rendered-sds
profile-service: rendered-sds
github-review-service: rendered-sds
```

## Client Registration Validation

All three clients are correctly registered in Postgres:

```sql
SELECT client_id, client_secret, redirect_uris 
FROM oauth2_registered_client 
WHERE client_id IN ('chat-backend', 'profile-service', 'github-review-service');
```

Results:
- ✅ `chat-backend`: bcrypt secret matches `demo-chat-backend-client-secret`
- ✅ `profile-service`: bcrypt secret correctly stored
- ✅ `github-review-service`: bcrypt secret correctly stored

All redirect URIs correctly include `https://{host}/_oauth2/callback`.

## Related Files
- `infrastructure/k8s/istio/envoyfilter-chat-oauth2-exchange.yaml`
- `infrastructure/k8s/istio/envoyfilter-profile-service-oauth2-exchange.yaml`
- `infrastructure/k8s/istio/envoyfilter-github-review-oauth2-exchange.yaml`
- `infrastructure/k8s/apps/chat/deployment.yaml`

## Next Steps
1. Update profile-service deployment in external repo to fix volume name
2. Consider migrating all services to Option 3 (K8s secrets) to eliminate init container complexity
3. Add validation script to detect volume mount mismatches
4. Document standard OAuth2 sidecar setup pattern
