# OAuth2 Envoy Filter Deployment Summary

**Status**: ✅ **COMPLETE** (Deployed Dec 12, 2025)

## What Was Implemented

### 1. **Envoy OAuth2 Native Filters**
Replaced Lua-based OAuth flows with Envoy's `envoy.filters.http.oauth2`:
- **Profile Service**: `envoyfilter-profile-oauth2-exchange.yaml`
   - Client ID: `profile-service`
   - Callback: `https://profile.cat-herding.net/_oauth2/callback`
    - Cookies: `_profilesvc_session_v3`, `_profilesvc_oauth_hmac_v3`, `_profilesvc_oauth_expires_v3`
       - _Updated to the v3 suffix on Jan 4, 2026 to invalidate stale v2 cookies after rotating the Key Vault client/HMAC secrets._
  
- **GitHub Review Service**: `envoyfilter-github-review-oauth2-exchange.yaml`
  - Client ID: `github-review-service`
  - Callback: `https://gh-review.cat-herding.net/_oauth2/callback`
  - Cookies: `_ghreview_session`, `_ghreview_oauth_hmac`, `_ghreview_oauth_expires`

**Features**:
- Full OAuth2 authorization code flow
- JWT token exchange with backend server
- Automatic cookie-based session management
- HMAC-based token validation (SDS-mounted secrets)
- Public path pass-through (health checks, static assets, API endpoints)
- Logout support via `/_oauth2/logout`

### 2. **Secrets Management**
All secrets moved from inline configuration to **Azure Key Vault**:
- `profile-service-client-secret`: OAuth client credential
- `profile-service-oauth-hmac-secret`: Session HMAC key (base64)
- `github-review-client-secret`: OAuth client credential
- `github-review-oauth-hmac-secret`: Session HMAC key (base64)

**Delivery**: SecretStore CSI Driver → Kubernetes Volumes → Envoy SDS ConfigMap

### 3. **Database Migrations**
Flyway migration `V12__align_oauth2_clients_for_envoy_filter.sql`:
```sql
-- Updated redirect URIs to match Envoy callback path
UPDATE oauth2_registered_client
SET redirect_uris = 'https://profile.cat-herding.net/_oauth2/callback,...'
WHERE client_id = 'profile-service';
```

### 4. **Health Check Exemptions**
Public endpoints that **bypass OAuth**:
- `/health`, `/healthz`, `/ready` (Kubernetes probes)
- `/actuator/*` (Spring Boot actuators)
- `/api/status` (status checks)
- `/favicon.ico` (browser requests)
- `/swagger*`, `/openapi*` (API documentation)
- `/public/`, `/css/`, `/js/`, `/images/`, `/webjars/` (static assets)

### 5. **Deployment & Verification**
All resources applied via Kustomize:
```bash
kubectl apply -k infrastructure/k8s
```

**Verification Results**:
```
✅ Manifests render cleanly (kubectl kustomize passes)
✅ Pods running: profile-service (1/1), github-review-service (1/1)
✅ Secrets mounted: CSI volumes loaded from Key Vault
✅ Health checks passing: /health, /actuator/health accessible
✅ Public paths accessible: /api/status, /swagger, /openapi
✅ OAuth redirect working: Protected paths → OAuth server
✅ RequestAuthentication deployed: JWT validation enabled
```

## Key Files Modified

### Infrastructure
- `infrastructure/k8s/istio/envoyfilter-profile-oauth2-exchange.yaml` ← NEW native filter
- `infrastructure/k8s/istio/envoyfilter-github-review-oauth2-exchange.yaml` ← NEW native filter
- `infrastructure/k8s/istio/envoy-oauth2-sds-configmap.yaml` ← NEW SDS config
- `infrastructure/k8s/secret-provider-class.yaml` ← Updated with new secrets
- `infrastructure/k8s/secret-provider-class-github-review.yaml` ← Updated with HMAC secret
- `infrastructure/k8s/apps/profile-service/profile-service-deployment.yaml` ← SDS volume mounts
- `infrastructure/k8s/apps/github-review-service/github-review-service-deployment.yaml` ← SDS volume mounts
- `infrastructure/k8s/kustomization.yaml` ← Registered new ConfigMap

### Database
- `oauth2-server/server-dao/.../V12__align_oauth2_clients_for_envoy_filter.sql` ← NEW migration

### Testing & Documentation
- `scripts/oauth2-smoke-test.sh` ← NEW smoke test suite
- `docs/JWT_CLAIM_REFACTOR_PLAN.md` ← NEW JWT refactor roadmap

## Deployment Flow

```
1. SecretProviderClass (via Managed Identity)
   ↓
2. Azure Key Vault (secrets stored)
   ↓
3. CSI Driver mounts /mnt/secrets-store in pods
   ↓
4. SDS ConfigMap references mounted secret files
   ↓
5. Envoy OAuth2 filter reads token/hmac via SDS
   ↓
6. User login → Envoy exchanges code for JWT
   ↓
7. JWT stored in cookie, used for downstream requests
   ↓
8. RequestAuthentication validates JWT claims
```

## Testing Results

### Smoke Test Summary
```
Test 1: Health checks (should be accessible without OAuth)
✅ /health endpoint accessible
✅ /actuator/health endpoint accessible

Test 2: Public paths bypass OAuth
✅ /api/status accessible without auth
✅ /favicon.ico accessible without auth
✅ /swagger-ui.html accessible without auth
✅ /openapi.json accessible without auth

Test 3: Protected paths trigger OAuth
✅ Protected path returns 302 redirect to OAuth server

Test 4: JWT claim extraction ready
✅ RequestAuthentication deployed for profile and github-review
```

## Known Issues & Workarounds

### Issue 1: Lua Filter Still Active
The old Lua-based JWT claim extraction is still running in the background but not blocking functionality. This is being phased out per JWT_CLAIM_REFACTOR_PLAN.md.

**Workaround**: None needed; Lua filters will be removed in Phase 4.

### Issue 2: Cookie Domain Not Set
Removed `cookie_domain` field from Envoy OAuth2 config (not part of v3 proto spec). Cookies will use the default domain from request authority header.

**Impact**: Minimal; cookies are still SameSite=Lax and work correctly.

## Next Steps (JWT Refactor)

See `docs/JWT_CLAIM_REFACTOR_PLAN.md` for the 4-phase migration:

1. **Phase 1** (Immediate): Validate RequestAuthentication is extracting JWT claims
2. **Phase 2** (Next): Replace Lua claim parsing with Envoy header-to-metadata filter
3. **Phase 3** (Following): Extended testing and performance validation
4. **Phase 4** (Final): Remove Lua filters and cleanup

## Operations & Monitoring

### Check Sidecar Filter Status
```bash
# Verify OAuth2 filter is loaded
kubectl get envoyfilter -n default | grep oauth2

# Check sidecar logs for JWT validation
kubectl logs <pod> -c istio-proxy -n default | grep -E "(oauth2|jwt_authn)"

# Port-forward to test health checks
kubectl port-forward svc/profile-service 8080:80 -n default
curl http://localhost:8080/health
```

### Debug OAuth2 Issues
```bash
# Check SDS ConfigMap mounted correctly
kubectl describe pod <pod> -n default | grep -A 5 "Mounts:"

# Verify secrets are in Key Vault
az keyvault secret list --vault-name inker-kv | grep -E "(profile|github-review)"

# Check RequestAuthentication status
kubectl describe requestauthentication -n default
```

### Rollback Procedure
```bash
# Revert EnvoyFilter deployment
kubectl delete envoyfilter profile-oauth2-exchange github-review-oauth2-exchange -n default

# Restore previous deployment
kubectl rollout undo deployment/profile-service -n default
kubectl rollout undo deployment/github-review-service -n default
```

## Security Considerations

✅ **Secrets**: All OAuth2 client secrets stored in Azure Key Vault (never in manifests)  
✅ **Transport**: HTTPS enforced for all OAuth endpoints  
✅ **Cookies**: HttpOnly, Secure, SameSite=Lax  
✅ **HMAC**: Independent key per service for session validation  
✅ **Token Expiry**: 900s (15 min) default, configurable  
✅ **CSRF**: Enabled by default in Envoy OAuth2 filter

## Metrics & Observability

### Key Metrics to Track
- `envoy.http.oauth2.authorized` - OAuth requests successfully processed
- `envoy.http.oauth2.denied` - OAuth requests denied
- `envoy.http.oauth2.oauth_failure` - Token exchange failures
- Pod CPU/memory during heavy auth load

### Log Patterns
```
Good: "oauth2_exchange.lua: authorization succeeded"
Good: "Envoy.filters.http.oauth2: authorized request"
Bad: "oauth2_exchange.lua: Token exchange failed"
Bad: "jwt_authn: invalid bearer token"
```

## Compliance & Standards

- ✅ OAuth 2.1 compliant (PKCE support in backend)
- ✅ OpenID Connect 1.0 (JWT tokens)
- ✅ Kubernetes RBAC (pod identity & CSI)
- ✅ Azure Key Vault integration
- ✅ Istio service mesh security

---

**Deployment completed**: Dec 12, 2025  
**Status**: Production-ready  
**Contact**: OAuth2 team / Platform team
