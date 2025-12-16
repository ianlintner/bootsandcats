# Chat App OAuth2 Setup Troubleshooting & Fix Summary

## Issues Identified

### 1. **OAuth2 Client Secret Mismatch** ❌ → ✅ FIXED

**Problem**: The `chat-backend` OAuth2 client in the database had an incorrect/placeholder client secret.

**Database State**:
- **Before**: `{bcrypt}a2-chat-service-client-secret-placeholder`
- **After**: `{noop}demo-chat-backend-client-secret`

**Root Cause**: The Flyway migration `V14__update_chat_service_client_secret.sql` did not run successfully or was overwritten.

**Fix Applied**:
```sql
UPDATE oauth2_registered_client 
SET client_secret = '{noop}demo-chat-backend-client-secret' 
WHERE client_id = 'chat-backend';
```

### 2. **Incorrect Redirect URIs** ❌ → ✅ FIXED

**Problem**: The redirect URIs included localhost URLs that are not appropriate for production.

**Before**:
- Redirect URIs: `https://chat.cat-herding.net/_oauth2/callback, http://localhost:5001/_oauth2/callback`
- Post-logout: `https://chat.cat-herding.net/_oauth2/logout, http://localhost:5001/_oauth2/logout`

**After**:
- Redirect URIs: `https://chat.cat-herding.net/_oauth2/callback`
- Post-logout: `https://chat.cat-herding.net/`

**Fix Applied**:
```sql
UPDATE oauth2_registered_client 
SET redirect_uris = 'https://chat.cat-herding.net/_oauth2/callback',
    post_logout_redirect_uris = 'https://chat.cat-herding.net/' 
WHERE client_id = 'chat-backend';
```

## Configuration Validation ✅

### Kubernetes Resources (All Properly Configured)

1. **✅ Deployment** ([deployment.yaml](../infrastructure/k8s/apps/chat/deployment.yaml))
   - Pod is running and healthy
   - Init container properly renders OAuth2 SDS configs
   - Secrets mounted correctly from Azure Key Vault

2. **✅ Azure Key Vault Secrets**
   - `chat-client-secret`: `demo-chat-backend-client-secret` ✅
   - `chat-oauth-hmac-secret`: `demo-chat-oauth-hmac-secret` ✅

3. **✅ SecretProviderClass** ([secret-provider-oauth2.yaml](../infrastructure/k8s/apps/chat/secret-provider-oauth2.yaml))
   - Properly configured to read from Key Vault: `inker-kv`
   - Secrets successfully mounted to init container

4. **✅ Istio EnvoyFilter - OAuth2** ([envoyfilter-chat-oauth2-exchange.yaml](../infrastructure/k8s/istio/envoyfilter-chat-oauth2-exchange.yaml))
   - OAuth2 filter configured for authorization code flow
   - Token endpoint: `http://oauth2-server.default.svc.cluster.local:9000/oauth2/token`
   - Authorization endpoint: `https://oauth2.cat-herding.net/oauth2/authorize`
   - Client ID: `chat-backend`
   - Redirect path: `/_oauth2/callback`
   - Signout path: `/_oauth2/logout`

5. **✅ Istio EnvoyFilter - JWT** ([envoyfilter-chat-jwt-to-headers.yaml](../infrastructure/k8s/istio/envoyfilter-chat-jwt-to-headers.yaml))
   - JWT validation configured
   - JWKS URI: `http://oauth2-server.default.svc.cluster.local:9000/oauth2/jwks`
   - Claims mapped to headers (sub, email, name, scope)

6. **✅ Istio RequestAuthentication** ([requestauthentication-chat.yaml](../infrastructure/k8s/istio/requestauthentication-chat.yaml))
   - Issuer: `https://oauth2.cat-herding.net`
   - JWKS URI configured correctly
   - Audiences: `chat-backend`, `m2m-client`

7. **✅ Istio VirtualService** ([istio-virtualservice.yaml](../infrastructure/k8s/apps/chat/istio-virtualservice.yaml))
   - Routes configured for WebSocket, API, health checks
   - CORS policy properly configured

8. **✅ Istio Gateway** ([istio-gateway.yaml](../infrastructure/k8s/apps/chat/istio-gateway.yaml))
   - HTTPS configured for `chat.cat-herding.net`
   - TLS certificate: `cat-herding-wildcard-tls`
   - HTTP redirect to HTTPS enabled

## OAuth2 Flow

```
User Browser
     |
     | 1. Access https://chat.cat-herding.net/
     v
Istio Ingress Gateway
     |
     | 2. Route to chat-backend pod
     v
Envoy Sidecar (OAuth2 Filter)
     |
     | 3. Check for session cookie
     | 4. If not authenticated, redirect to authorize endpoint
     |
     | Redirect: https://oauth2.cat-herding.net/oauth2/authorize?
     |           client_id=chat-backend&
     |           redirect_uri=https://chat.cat-herding.net/_oauth2/callback&
     |           scope=openid+profile+email&
     |           response_type=code
     v
OAuth2 Server (oauth2.cat-herding.net)
     |
     | 5. User authenticates (login form)
     | 6. Authorization granted
     | 7. Redirect with authorization code
     |
     | Redirect: https://chat.cat-herding.net/_oauth2/callback?code=XXXXX&state=YYYYY
     v
Envoy Sidecar (OAuth2 Filter)
     |
     | 8. Exchange authorization code for access token
     |    POST http://oauth2-server.default.svc.cluster.local:9000/oauth2/token
     |    Body: grant_type=authorization_code&code=XXXXX&redirect_uri=...
     |    Auth: Basic base64(chat-backend:demo-chat-backend-client-secret)
     |
     | 9. Receive access token (JWT)
     | 10. Store in cookie: _chat_session
     | 11. Forward request to application with Authorization header
     v
Chat Backend Application
     |
     | 12. Envoy JWT Filter validates token
     | 13. Extract claims and add as headers (x-jwt-sub, x-jwt-email, etc.)
     | 14. Application receives authenticated request
     v
Response to User
```

## Testing OAuth2 Flow

### 1. Test OAuth2 Server Health
```bash
kubectl exec -n default chat-backend-<pod> -c chat-backend -- \
  wget -O- -q http://oauth2-server.default.svc.cluster.local:9000/actuator/health
# Should return: {"status":"UP"}
```

### 2. Test JWKS Endpoint
```bash
kubectl exec -n default chat-backend-<pod> -c chat-backend -- \
  wget -O- -q http://oauth2-server.default.svc.cluster.local:9000/oauth2/jwks
# Should return JWT keys
```

### 3. Test Full OAuth2 Flow
1. Open browser to: `https://chat.cat-herding.net/`
2. Should redirect to: `https://oauth2.cat-herding.net/oauth2/authorize`
3. Login with valid credentials
4. Should redirect back to: `https://chat.cat-herding.net/`
5. Check cookies - should have `_chat_session` cookie

### 4. Verify JWT in Request
```bash
# From inside the chat-backend container, the Authorization header should be present
# and claims should be mapped to headers: x-jwt-sub, x-jwt-email, x-jwt-name, x-jwt-scope
```

## Key Vault Secrets

The following secrets must exist in Azure Key Vault `inker-kv`:

| Secret Name | Purpose | Current Value (Demo) |
|------------|---------|---------------------|
| `chat-client-secret` | OAuth2 client secret for token exchange | `demo-chat-backend-client-secret` |
| `chat-oauth-hmac-secret` | HMAC secret for cookie signing | `demo-chat-oauth-hmac-secret` |

**Setup Script**: `scripts/setup-chat-client-secrets.sh`

## Database Schema

The `oauth2_registered_client` table in PostgreSQL database `oauth2db`:

```sql
SELECT 
  client_id,
  client_secret,
  redirect_uris,
  post_logout_redirect_uris,
  scopes
FROM oauth2_registered_client 
WHERE client_id = 'chat-backend';
```

**Expected Result**:
```
client_id      | chat-backend
client_secret  | {noop}demo-chat-backend-client-secret
redirect_uris  | https://chat.cat-herding.net/_oauth2/callback
post_logout_   | https://chat.cat-herding.net/
scopes         | openid,profile,email
```

## Actions Taken

1. ✅ Fixed OAuth2 client secret in database
2. ✅ Corrected redirect URIs (removed localhost URLs)
3. ✅ Restarted oauth2-server deployment
4. ✅ Restarted chat-backend deployment
5. ✅ Verified all Kubernetes resources are properly configured
6. ✅ Validated Key Vault secrets exist and are accessible

## Next Steps

### For Production Deployment

1. **Update Flyway Migration**
   - Fix `V14__update_chat_service_client_secret.sql` in the ConfigMap
   - Or create a new migration `V15__fix_chat_backend_client_secret.sql`

2. **Rotate Demo Secrets**
   - Generate strong random secrets for:
     - `chat-client-secret`
     - `chat-oauth-hmac-secret`
   - Update Key Vault
   - Update database (encrypt with bcrypt for production)

3. **Remove Hardcoded Credentials**
   - Update [secrets.yaml](../infrastructure/k8s/apps/chat/secrets.yaml)
   - Remove placeholder values
   - Document that all secrets come from Key Vault

4. **Test End-to-End**
   - Navigate to `https://chat.cat-herding.net/`
   - Complete OAuth2 login flow
   - Verify application functionality
   - Test WebSocket connections
   - Test logout flow

## Monitoring & Debugging

### Check Pod Logs
```bash
# Chat backend logs
kubectl logs -n default -l app=chat-backend -c chat-backend --tail=100

# Istio proxy logs (OAuth2 filter)
kubectl logs -n default -l app=chat-backend -c istio-proxy --tail=100

# OAuth2 server logs
kubectl logs -n default -l app=oauth2-server -c oauth2-server --tail=100
```

### Check Events
```bash
kubectl get events -n default --sort-by='.lastTimestamp' | grep chat
```

### Verify Secrets Mounted
```bash
POD=$(kubectl get pod -n default -l app=chat-backend -o jsonpath='{.items[0].metadata.name}')
kubectl exec -n default $POD -c chat-backend -- ls -la /etc/istio/oauth2/
kubectl exec -n default $POD -c chat-backend -- cat /etc/istio/oauth2/chat-oauth-token.yaml
```

## Common Issues

### Issue: 404 at https://oauth2.cat-herding.net/oauth2/authorize from istio-envoy
**Symptoms**: Browser is redirected from `https://chat.cat-herding.net/` to the authorization endpoint, but the response is `404` with `server: istio-envoy` in headers. OAuth2 server logs show no request reaching `/oauth2/authorize`.

**Likely Causes**:
- Ingress gateway does not have a route for `oauth2.cat-herding.net` due to export scoping or config not propagated to all gateway pods.
- A conflicting Istio object for the same host (e.g., `ServiceEntry` for `oauth2.cat-herding.net`) causes an unintended virtual host collision for the ingress proxy.

**Fix Applied**:
- Ensured the inbound `VirtualService oauth2-server` is exported mesh-wide:
  - `metadata.annotations["networking.istio.io/exportTo"]: "*"`
- Removed `serviceentry-oauth2.yaml` from the Istio kustomization so inbound host routing for `oauth2.cat-herding.net` is unambiguous at the ingress gateway.

**How to Verify**:
1. Re-apply manifests and wait ~1–2 minutes for config push.
2. Verify the gateway has the route for the host:
   - `istioctl proxy-config routes <ingress-pod> -n aks-istio-ingress --name https.443.https --filter oauth2.cat-herding.net`
   - `istioctl proxy-status | grep -E "ACK|SYNCED"` (all ingress pods should be in sync)
3. Hit the authorize URL directly and confirm the login UI renders:
   - `https://oauth2.cat-herding.net/oauth2/authorize?...`

**Notes**:
- If internal workloads must egress to `https://oauth2.cat-herding.net`, prefer configuring an egress `ServiceEntry` and related policies in a separate overlay, avoiding reuse of the same host for ingress routing.

### Issue: "Invalid client credentials"
**Cause**: Client secret mismatch between Key Vault and database
**Solution**: Verify secrets match:
```bash
az keyvault secret show --vault-name inker-kv --name chat-client-secret --query value -o tsv
kubectl exec -n default postgres-ha-2 -- psql -U postgres -d oauth2db -c \
  "SELECT client_secret FROM oauth2_registered_client WHERE client_id='chat-backend';"
```

### Issue: "Invalid redirect URI"
**Cause**: Redirect URI mismatch
**Solution**: Check database redirect_uris match the EnvoyFilter configuration

### Issue: "JWT validation failed"
**Cause**: JWKS endpoint not accessible or issuer mismatch
**Solution**: 
1. Verify JWKS endpoint: `http://oauth2-server.default.svc.cluster.local:9000/oauth2/jwks`
2. Check issuer matches: `https://oauth2.cat-herding.net`

## References

- [Chat Client Setup Guide](CHAT_CLIENT_SETUP.md)
- [OAuth2 Authorization Server Docs](architecture/oauth2-server.md)
- [Istio OAuth2 Configuration](https://istio.io/latest/docs/tasks/security/authorization/authz-http/)
- [Envoy OAuth2 Filter](https://www.envoyproxy.io/docs/envoy/latest/configuration/http/http_filters/oauth2_filter)
