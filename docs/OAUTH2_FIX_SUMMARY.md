# OAuth2 404 Fix Summary - Dec 16, 2025

## Problem
Chat and profile-service OAuth2 flows failed with HTTP 404 at `/oauth2/authorize` when redirected from application URLs. Github-review OAuth2 worked correctly.

## Root Cause
**Volume mount mismatch** between init containers and Istio sidecar annotations prevented Envoy OAuth2 filter from loading client credentials.

### Specific Issues:
1. **Profile-service**: Init container wrote secrets to `rendered-oauth-sds` but Istio annotation mounted `rendered-sds` → sidecar couldn't access secrets
2. **Chat**: Used inconsistent volume name `rendered-oauth-sds` while github-review (working) used `rendered-sds`
3. Silent failure: No error logs because Envoy OAuth2 filter simply couldn't find SDS files

## Solution
Standardized all services on `rendered-sds` volume name to match the working github-review pattern.

### Changes Applied

#### ✅ Chat (bootsandcats repo) - FIXED
File: `infrastructure/k8s/apps/chat/deployment.yaml`

1. Added explicit Istio annotation:
   ```yaml
   sidecar.istio.io/userVolumeMount: '[{"name":"rendered-sds","mountPath":"/etc/istio/oauth2","readonly":true}]'
   ```

2. Renamed volume throughout deployment:
   - Init container `render-oauth-sds` volumeMount: `rendered-oauth-sds` → `rendered-sds`
   - App container volumeMount: `rendered-oauth-sds` → `rendered-sds`
   - Volume definition: `rendered-oauth-sds` → `rendered-sds`

**Result**: OAuth flow now works correctly ✅
- Redirect: `302 → https://oauth2.cat-herding.net/oauth2/authorize`
- PKCE cookies: `OauthNonce`, `CodeVerifier` set correctly
- Init container: "OAuth2 SDS configs rendered successfully"
- Sidecar mount: `/etc/istio/oauth2` mounted from `rendered-sds`

#### ⚠️ Profile-Service (external repo) - REQUIRES UPDATE
Action needed in profile-service deployment:

```yaml
# 1. Update init container volumeMount
initContainers:
- name: render-oauth-sds
  volumeMounts:
  - mountPath: /etc/istio/oauth2
    name: rendered-sds  # Change from rendered-oauth-sds

# 2. Rename volume definition
volumes:
- name: rendered-sds
  emptyDir: {}
# Remove: rendered-oauth-sds volume

# 3. Annotation should remain (already correct):
# sidecar.istio.io/userVolumeMount: '[{"name":"rendered-sds",...}]'
```

## Testing Results

### Chat OAuth Flow ✅
```bash
$ curl -sI https://chat.cat-herding.net/ | head -3
HTTP/2 302 
set-cookie: OauthNonce=...;secure;HttpOnly
location: https://oauth2.cat-herding.net/oauth2/authorize?client_id=chat-backend&code_challenge=...
```

### Volume Mount Verification ✅
```bash
$ kubectl get pod chat-backend-85c57569f8-qltm4 -o jsonpath='{.spec.containers[?(@.name=="istio-proxy")].volumeMounts[?(@.mountPath=="/etc/istio/oauth2")].name}'
rendered-sds
```

### Init Container Logs ✅
```bash
$ kubectl logs chat-backend-85c57569f8-qltm4 -c render-oauth-sds
OAuth2 SDS configs rendered successfully
```

## Standard Pattern (All Services)

```yaml
apiVersion: apps/v1
kind: Deployment
spec:
  template:
    metadata:
      annotations:
        sidecar.istio.io/inject: 'true'
        sidecar.istio.io/userVolumeMount: '[{"name":"rendered-sds","mountPath":"/etc/istio/oauth2","readonly":true}]'
    spec:
      initContainers:
      - name: render-oauth-sds
        volumeMounts:
        - mountPath: /etc/istio/oauth2
          name: rendered-sds  # MUST MATCH annotation
      
      containers:
      - name: app
        # ... app config
      
      volumes:
      - name: rendered-sds  # MUST MATCH annotation
        emptyDir: {}
```

## Related Changes

### Earlier Fixes (Also Applied)
1. Added `networking.istio.io/exportTo: "*"` to oauth2 VirtualService (mesh-wide visibility)
2. Removed ServiceEntry from kustomization (eliminated host conflict)
3. Updated all EnvoyFilters to use prefix matching:
   - `redirect_path_matcher.path.prefix: /_oauth2/callback`
   - `signout_path.path.prefix: /_oauth2/logout`

## Key Learnings

1. **Volume naming must be consistent**: Init container output volume name MUST match Istio `userVolumeMount` annotation
2. **Silent failures are dangerous**: Envoy OAuth2 filter doesn't error if SDS files are missing, it just fails to initialize
3. **Istio sidecar injection is selective**: Only volumes explicitly listed in `userVolumeMount` annotation are mounted to istio-proxy
4. **Array format is preferred**: Use `[{"name":"vol","mountPath":"/path"}]` for consistency across services
5. **Init container success != sidecar accessibility**: Init logs can show success while sidecar can't access the volume

## Documentation
- [OAUTH2_SIDECAR_SECRET_ISSUE.md](./OAUTH2_SIDECAR_SECRET_ISSUE.md) - Detailed root cause analysis with comparison tables
- [CHAT_APP_OAUTH2_TROUBLESHOOTING.md](./CHAT_APP_OAUTH2_TROUBLESHOOTING.md) - Historical troubleshooting steps

## Next Steps

### For Profile-Service Owners
1. Update deployment manifest with volume name changes
2. Apply: `kubectl apply -f deployment.yaml`
3. Restart: `kubectl rollout restart deployment/profile-service`
4. Test: `curl -sI https://profile.cat-herding.net/` (should get 302 to authorize endpoint)

### Future Improvements
Consider migrating to native Kubernetes secrets with Istio SDS to eliminate init container complexity:
- No emptyDir volumes needed
- No init containers to maintain
- Simpler YAML manifests
- Use `sds_config.ads: {}` in EnvoyFilters
