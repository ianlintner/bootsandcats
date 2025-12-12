# JWT Claim Mapping Refactor Plan

## Overview
Replace Lua-based JWT claim extraction with Envoy-native filters using `RequestAuthentication` + `EnvoyFilter` header mutations.

## Current State
- **RequestAuthentication**: Validates JWT from OAuth2 server and extracts claims
- **Lua filters** (profile-oauth2-lua-configmap.yaml): Parse JWT payload and inject `x-jwt-*` headers
- **Issue**: Lua processing adds latency; should use native Envoy capabilities

## Goal State
- Remove Lua claim parsing
- Use Envoy's `envoy.filters.http.header_to_metadata` + JWT payload to inject standard headers
- Maintain backward-compatible header names (`x-jwt-sub`, `x-jwt-email`, etc.)

## Implementation Steps

### Phase 1: Validate RequestAuthentication Output
**Status**: Ready to verify

RequestAuthentication already exists:
- `requestauthentication-profile.yaml`
- `requestauthentication-github-review.yaml`

Each validates the JWT issuer and makes claims available to downstream filters.

**Action Items**:
1. Confirm RequestAuthentication outputs `request.auth.claims.*` in Envoy
2. Check sidecar logs for JWT validation success: `kubectl logs <pod> -c istio-proxy -n default | grep jwt_authn`
3. Verify no JWT validation failures are occurring

### Phase 2: Replace Lua with EnvoyFilter Header Mutations
**Status**: Design ready

Current Lua extracts and injects:
```
x-jwt-sub (from "sub" claim)
x-jwt-email (from "email" claim)
x-jwt-username (from "preferred_username" claim)
x-jwt-name (from "name" claim)
x-jwt-scope (from "scope" claim)
x-jwt-payload (entire JWT payload)
```

**New approach**: Use `envoy.filters.http.header_to_metadata` EnvoyFilter to:
1. Extract JWT claims from `request.auth.claims` (populated by RequestAuthentication)
2. Inject as HTTP headers before forwarding to application

**Example EnvoyFilter structure**:
```yaml
apiVersion: networking.istio.io/v1alpha3
kind: EnvoyFilter
metadata:
  name: jwt-claims-to-headers-profile
  namespace: default
spec:
  workloadSelector:
    labels:
      app: profile-service
  configPatches:
  - applyTo: HTTP_FILTER
    match:
      context: SIDECAR_INBOUND
      listener:
        filterChain:
          filter:
            name: envoy.filters.network.http_connection_manager
            subFilter:
              name: envoy.filters.http.router
    patch:
      operation: INSERT_BEFORE
      value:
        name: envoy.filters.http.lua
        typed_config:
          "@type": type.googleapis.com/envoy.extensions.filters.http.lua.v3.Lua
          inlineCode: |
            function envoy_on_request(request_handle)
              local auth_header = request_handle:headers():get("x-jwt-payload")
              if auth_header then
                -- Decode and extract claims from request.auth.* headers
                -- (RequestAuthentication already decoded the JWT)
                local claims = request_handle:headers():get("request.auth.claims.sub")
                if claims then
                  request_handle:headers():add("x-jwt-sub", claims)
                end
                -- Repeat for other claims...
              end
            end
```

**Alternative (Recommended)**: Switch to WASM filter if available in Istio for better performance.

### Phase 3: Testing & Validation
**Status**: Smoke tests passed; ready for extended testing

**Test Plan**:
1. Deploy new EnvoyFilter for header injection
2. Verify headers appear in application logs/requests
3. Compare header values with previous Lua output (should be identical)
4. Monitor sidecar logs for performance improvements
5. Gradually roll out across services

**Health Checks**:
```bash
# Check RequestAuthentication is validating JWTs
kubectl logs <pod> -c istio-proxy -n default | grep jwt_authn | head -5

# Check headers are being injected
kubectl logs <pod> -c app -n default | grep "x-jwt-" | head -5

# Verify no Lua overhead
kubectl top pod <pod> -n default  # Compare CPU before/after
```

### Phase 4: Cleanup
**Status**: Deferred

Once testing confirms headers are correct:
1. Remove Lua EnvoyFilters completely
2. Remove ConfigMap: `profile-oauth2-lua-configmap.yaml`
3. Update Istio sidecar injection to skip Lua runtime
4. Update documentation

## Configuration Files to Modify

### Create new files:
- `envoyfilter-profile-jwt-claims-headers.yaml`
- `envoyfilter-github-review-jwt-claims-headers.yaml`

### Update:
- `kustomization.yaml` - add new EnvoyFilter resources

### Remove (later):
- `profile-oauth2-lua-configmap.yaml`
- Lua patches from existing EnvoyFilters

## Rollback Plan
If new header injection fails:
1. Keep existing Lua filters in place
2. Disable new EnvoyFilter by removing from kustomization
3. Revert pods: `kubectl rollout undo deployment/profile-service -n default`

## Success Criteria
- [ ] All JWT claim headers (`x-jwt-*`) are present in downstream requests
- [ ] Header values match previous Lua output exactly
- [ ] No performance regression in sidecar metrics
- [ ] No JWT validation errors in sidecar logs
- [ ] Health check endpoints still bypass OAuth
- [ ] Logout flow still works (cookies cleared)

## Timeline
- **Phase 1**: Immediate (validation only)
- **Phase 2**: Next session (EnvoyFilter design + deployment)
- **Phase 3**: Following session (extended testing)
- **Phase 4**: Final session (cleanup)

## References
- [Istio RequestAuthentication](https://istio.io/latest/docs/reference/config/security/request-authentication/)
- [Envoy JWT Authn Filter](https://www.envoyproxy.io/docs/envoy/latest/api-v3/extensions/filters/http/jwt_authn/v3/jwt_authn.proto)
- [Envoy Header-to-Metadata Filter](https://www.envoyproxy.io/docs/envoy/latest/api-v3/extensions/filters/http/header_to_metadata/v3/header_to_metadata.proto)
