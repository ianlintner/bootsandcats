# Gateway OAuth2 Deployment Summary (oauth2-proxy + Lua)

**Status**: ✅ **CURRENT** (Unified gateway OAuth2; single client)

## What Was Implemented

### 1. **Unified gateway OAuth2 enforcement**
OAuth2 login is enforced at the **Istio ingress gateway** (not per-service) using a Lua `auth_request` pattern to `oauth2-proxy`.

- **Single client**: `secure-subdomain-client`
- **Callback** (computed from the incoming request host): `https://<public-host>.cat-herding.net/_oauth2/callback`
- **Shared cookies (apex domain)**: `_secure_session`

> Note: Spring Authorization Server requires **exact** redirect URI matches. That means the
> `secure-subdomain-client` must have each protected host registered explicitly (no wildcards like
> `https://*.cat-herding.net/...` or `http://localhost:*/...`). See `docs/SECURE_SUBDOMAIN_OAUTH2.md`.

**Features**:
- Full OAuth2 authorization code flow (PKCE handled by `oauth2-proxy`)
- Automatic cookie-based session management via `oauth2-proxy`
- Public path pass-through (health checks, etc.)
- Centralized auth enforcement for all protected `*.cat-herding.net` hosts

### 2. **Secrets Management**
OAuth2 secrets are handled by `oauth2-proxy` (not Envoy SDS):

- `secure-subdomain-client-secret` is mounted from Azure Key Vault via SecretStore CSI.
- `oauth2-proxy-cookie-secret` is a Kubernetes Secret (used to encrypt/sign the session cookie).

### 3. **Redirect URI Management**
Spring Authorization Server requires **exact** redirect URI matches.

For the unified client (`secure-subdomain-client`), you must register each protected host's callback explicitly:

- `https://profile.cat-herding.net/_oauth2/callback`
- `https://chat.cat-herding.net/_oauth2/callback`
- etc.

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
- `infrastructure/k8s/istio/envoyfilter-secure-subdomain-oauth2.yaml` ← gateway Lua auth_request enforcement
- `infrastructure/k8s/aks-istio-ingress/oauth2-proxy.yaml` ← oauth2-proxy Deployment/Service
- `infrastructure/k8s/aks-istio-ingress/secret-provider-class-secure-subdomain-oauth.yaml` ← Key Vault integration
- `infrastructure/k8s/istio/virtualservices.yaml` ← routes `/_oauth2/*` to oauth2-proxy per host

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
3. CSI Driver mounts /mnt/secrets-store into oauth2-proxy
   ↓
4. Gateway Lua filter calls oauth2-proxy /_oauth2/auth (auth_request)
   ↓
5. oauth2-proxy redirects to the Authorization Server when unauthenticated
   ↓
6. oauth2-proxy sets the session cookie on success
   ↓
7. Gateway forwards Authorization and x-auth-request-* headers upstream
   ↓
8. (Optional) jwt_authn validates Bearer token and maps claims to x-jwt-* headers
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
# Revert gateway OAuth2 enforcement (removes protection)
kubectl delete envoyfilter secure-subdomain-oauth2-exchange -n aks-istio-ingress

# Restore previous deployment
kubectl rollout undo deployment/profile-service -n default
kubectl rollout undo deployment/github-review-service -n default
```

## Security Considerations

✅ **Secrets**: All OAuth2 client secrets stored in Azure Key Vault (never in manifests)  
✅ **Transport**: HTTPS enforced for all OAuth endpoints  
✅ **Cookies**: HttpOnly, Secure, SameSite=Lax  
✅ **HMAC**: Single key for shared gateway session validation  
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
