# Native Envoy JWT Filter Implementation Analysis

## Executive Summary

This document analyzes three approaches to handle JWT validation and claim extraction in Istio:

1. **Current Approach**: Lua-based claim extraction (operational)
2. **Native Envoy jwt_authn**: Envoy's built-in JWT validation + claim extraction chain
3. **Optimized RequestAuthentication**: Leverage Istio's existing JWT validation for claim extraction

## Current Architecture (Status: ✅ Working)

```
Client → EnvoyProxy 
  ↓
RequestAuthentication (via Istio)
  → Validates JWT against JWKS
  → Extracts claims
  → Outputs x-jwt-payload header (entire JWT payload as JSON)
  ↓
Lua EnvoyFilter (envoyfilter-github-review-jwt-to-headers.yaml)
  → Parses x-jwt-payload JSON string
  → Extracts individual claims (sub, email, preferred_username, etc.)
  → Injects x-jwt-* headers
  → Removes x-jwt-payload
  ↓
Application (receives x-jwt-* headers)
```

**Characteristics**:
- RequestAuthentication handles JWT validation (JWKS verification)
- Lua provides lightweight JSON parsing and header injection
- No external dependencies for JWT parsing
- Pattern: Two-filter chain (validation → claim extraction)

---

## Approach 1: Native Envoy jwt_authn Filter (With claim_to_headers)

### Overview
Use Envoy's native `envoy.filters.http.jwt_authn` filter for JWT validation **and claim extraction**. The jwt_authn filter has built-in `claim_to_headers` configuration that can map JWT claims directly to HTTP headers.

### Architecture (Revised - With claim_to_headers)

```
Client → EnvoyProxy
  ↓
envoy.filters.http.jwt_authn
  → Validates JWT signature against JWKS
  → Verifies audience, issuer
  → Uses claim_to_headers to map claims to HTTP headers ✅
    • sub → x-jwt-sub
    • email → x-jwt-email
    • roles → x-jwt-roles (can handle arrays)
  ↓
Application receives headers directly
```

**Key Feature**: The `claim_to_headers` section in jwt_authn configuration allows direct claim-to-header mapping without requiring Lua:

```yaml
claim_to_headers:
  - header_name: "x-jwt-sub"
    claim_name: "sub"
  - header_name: "x-jwt-email"
    claim_name: "email"
  - header_name: "x-jwt-username"
    claim_name: "preferred_username"
  - header_name: "x-jwt-roles"
    claim_name: "roles"  # Handles arrays
```

This changes the analysis significantly—**native jwt_authn can extract claims to headers without Lua**.

### Detailed Configuration (With claim_to_headers - No Lua Needed)

**Complete jwt_authn EnvoyFilter with claim extraction:**

```yaml
apiVersion: networking.istio.io/v1alpha3
kind: EnvoyFilter
metadata:
  name: github-review-jwt-native-with-claims
  namespace: default
spec:
  workloadSelector:
    labels:
      app: github-review-service
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
        name: envoy.filters.http.jwt_authn
        typed_config:
          "@type": type.googleapis.com/envoy.extensions.filters.http.jwt_authn.v3.JwtAuthentication
          providers:
            github_oauth:
              issuer: "https://oauth2.cat-herding.net"
              audiences: "github-review-service,m2m-client"
              remote_jwks:
                http_uri:
                  uri: "https://oauth2.cat-herding.net/oauth2/jwks"
                  cluster: "outbound|443||oauth2.cat-herding.net"
                  timeout: 5s
                cache_config:
                  cache_size_bytes: 10485760
          # ===== CRITICAL FEATURE: Direct claim-to-header mapping =====
          # This extracts JWT claims directly to HTTP headers
          claim_to_headers:
          - header_name: "x-jwt-sub"
            claim_name: "sub"
          - header_name: "x-jwt-email"
            claim_name: "email"
          - header_name: "x-jwt-username"
            claim_name: "preferred_username"
          - header_name: "x-jwt-name"
            claim_name: "name"
          - header_name: "x-jwt-scope"
            claim_name: "scope"
          - header_name: "x-jwt-roles"
            claim_name: "roles"  # Handles array claims
          # ========================================================
          rules:
          - match:
              prefix: "/"
            requires:
              provider_name: "github_oauth"
          # Public endpoints (no JWT required)
          - match:
              prefix: "/public/"
            allow_missing_or_expired: true
          - match:
              prefix: "/api/status"
            allow_missing_or_expired: true
          - match:
              prefix: "/login/oauth2/code/"
            allow_missing_or_expired: true
          - match:
              prefix: "/actuator"
            allow_missing_or_expired: true
          - match:
              path_separator: EXACT
              path: "/favicon.ico"
            allow_missing_or_expired: true
```

**Key Advantages of claim_to_headers**:
- ✅ No Lua filter needed for claim extraction
- ✅ Native Envoy feature (compiled, faster)
- ✅ Direct mapping from JWT claims to HTTP headers
- ✅ Handles both string claims and array claims (e.g., roles)
- ✅ Removes need for JSON parsing logic

### Pros & Cons (Revised with claim_to_headers)

| Aspect | Pro | Con |
|--------|-----|-----|
| **JWT Validation** | Native Envoy filter | Duplicates Istio RequestAuthentication |
| **Claim Extraction** | ✅ Direct via claim_to_headers | Still duplicates work Istio already does |
| **Complexity** | One filter does everything | More configuration than RequestAuth |
| **Performance** | Native code (faster than Lua) | Minimal difference vs RequestAuth + Lua |
| **Troubleshooting** | Single filter to debug | Duplicate validation source |
| **Array Claims** | ✅ claim_to_headers handles arrays | Must configure each header mapping |

### Revised Verdict
**⚠️ VIABLE BUT NOT PREFERRED** - With `claim_to_headers`, native jwt_authn can extract claims without Lua. However, it still duplicates JWT validation already done by Istio RequestAuthentication. Choose based on:
- Use native jwt_authn if you want to remove RequestAuthentication entirely
- Use RequestAuth if you want standard Istio pattern with less duplication

---

## Approach 2: Optimized RequestAuthentication (RECOMMENDED)

### Overview
Keep using Istio's RequestAuthentication (already validating JWTs), but optimize the claim extraction layer.

### Architecture

```
Client → EnvoyProxy
  ↓
RequestAuthentication (Istio)
  → Validates JWT against JWKS (outbound to OAuth2 server)
  → Decodes JWT claims into request.auth.* attributes
  → Outputs x-jwt-payload header (base64+JSON)
  ↓
Lightweight Lua EnvoyFilter
  → Parse x-jwt-payload JSON string
  → Extract claims: sub, email, preferred_username, name, scope
  → Inject x-jwt-* headers
  → Remove raw x-jwt-payload
  ↓
Application (receives x-jwt-* headers)
```

This is **your current architecture** — it's efficient because:
1. RequestAuthentication is the Istio service mesh standard
2. Lua is lightweight and handles only claim parsing
3. JWT validation is not duplicated
4. Claims are already extracted by RequestAuthentication; Lua just reformats them

### Why This is Already Optimal

**RequestAuthentication Benefits**:
- Istio-native JWT validation
- Claims are decoded and available via `request.auth` attributes
- JWKS caching built-in
- Works across all sidecar proxies without additional config per service

**Lua Filter Benefits**:
- Lightweight JSON parsing (no external libraries)
- Fast claim extraction
- Handles custom claim names (e.g., `preferred_username` → `x-jwt-username`)
- Only runs if JWT is present
- Can skip processing for public endpoints

### Current Implementation Review

**RequestAuthentication** ([requestauthentication-github-review.yaml](requestauthentication-github-review.yaml)):
```yaml
jwtRules:
- issuer: "https://oauth2.cat-herding.net"
  jwksUri: "https://oauth2.cat-herding.net/oauth2/jwks"
  audiences:
  - "github-review-service"
  - "m2m-client"
  outputPayloadToHeader: "x-jwt-payload"  # ← Claims available to downstream filters
```

**Lua EnvoyFilter** ([envoyfilter-github-review-jwt-to-headers.yaml](envoyfilter-github-review-jwt-to-headers.yaml)):
```lua
local function parse_claim(payload, key)
  -- Extract claim from x-jwt-payload JSON
end

function envoy_on_request(handle)
  local payload_json = handle:headers():get("x-jwt-payload")
  if payload_json == nil then return end
  
  -- Skip public endpoints
  if path_only:find("^/login/oauth2/code/") == 1 then return end
  
  -- Extract and inject headers
  add_claim("x-jwt-sub", "sub")
  add_claim("x-jwt-username", "preferred_username")
  -- ...
end
```

### Optimization Opportunities

While the current approach is sound, there are **two optimization paths**:

#### Option A: Reduce Lua Parsing Overhead
Instead of string regex parsing, use metadata attributes directly:

```yaml
# NOT WORKING in current Istio without custom filters
# RequestAuthentication stores claims in request.auth.claims
# but there's no native way to expose them as headers without Lua
```

**Status**: Blocked by Istio limitation (no native metadata → header conversion filter)

#### Option B: Use WASM Filter for Performance
Replace Lua with a WebAssembly module compiled to native code:

```yaml
- name: envoy.filters.http.wasm
  typed_config:
    "@type": type.googleapis.com/google.protobuf.Empty
    config:
      vm_config:
        runtime: "envoy.wasm.runtime.v8"  # or wastime
        code:
          local:
            filename: "jwt-claim-extractor.wasm"
      configuration:
        "@type": type.googleapis.com/google.protobuf.StringValue
        value: |
          {
            "claims_to_extract": ["sub", "email", "preferred_username"]
          }
```

**Pros**: ~2x faster than Lua
**Cons**: Requires compiling WASM module, more operational complexity

---

## Decision Matrix

| Criteria | Current (Lua) | Native jwt_authn (with claim_to_headers) | RequestAuth+WASM |
|----------|---------------|--------------------------|-----------------|
| **JWT Validation** | ✅ Istio (standard) | ✅ Envoy (native) | ✅ Istio (standard) |
| **Claim Extraction** | ✅ Lua parsing | ✅ claim_to_headers mapping | ✅ WASM (faster) |
| **Operational Complexity** | ⭐⭐ Low | ⭐⭐⭐ Medium | ⭐⭐⭐ Medium |
| **Performance** | ⭐⭐⭐ Good | ⭐⭐⭐⭐ Better (native) | ⭐⭐⭐⭐ Excellent |
| **Istio Compatibility** | ✅ Standard | ⚠️ Duplicates validation | ✅ Standard |
| **Debugging** | ✅ Simple | 🟡 More config | 🟡 Complex |
| **Maintenance Burden** | Low | Medium | Medium |
| **Array Claim Support** | ⚠️ Manual in Lua | ✅ Native via claim_to_headers | ✅ Custom logic |

---

## Recommendations

### 🟢 Short Term (Current Best Practice)
**Keep current approach** (Lua + RequestAuthentication):
- It's working and efficient
- Istio-native JWT validation (standard mesh pattern)
- Simple Lua parsing with no external dependencies
- Low operational overhead
- Well-documented and debugged

**Rationale**: The current two-filter approach is optimized for your use case. RequestAuthentication handles validation, Lua handles lightweight claim parsing.

### 🟡 Medium Term (If Consolidation Desired)
**Migrate to native jwt_authn with claim_to_headers**:
- Eliminates RequestAuthentication/AuthorizationPolicy duplication
- Single filter handles validation + claim extraction
- claim_to_headers feature makes it viable without Lua
- Better performance (native Envoy code)

**Considerations**:
- Requires removing RequestAuthentication from your setup
- Need to configure claim_to_headers for each claim you extract
- Loss of Istio's standard JWT pattern (fewer operators will recognize it)

**Decision Point**: Is the slight performance gain worth the non-standard approach?
Evaluate **WASM-based claim extraction**:
1. Profile current Lua CPU usage
2. If CPU > threshold, compile claim extractor to WASM
3. Test performance improvement
4. Roll out gradually

### 🔴 Not Recommended
**Never migrate to native jwt_authn alone** because:
1. Still requires Lua for claim extraction
2. Duplicates JWT validation already done by RequestAuthentication
3. Adds troubleshooting complexity
4. No performance benefit

---

## Testing & Validation

### Verify Current Approach Works

```bash
# Check RequestAuthentication is active
kubectl get requestauthentication -n default
# Output: github-review-jwt-auth, profile-jwt-auth

# Check Lua filter is injected
kubectl get envoyfilter -n default | grep jwt-to-headers

# Verify JWT validation in logs
kubectl logs <github-review-service-pod> -c istio-proxy -n default \
  | grep "jwt_authn" | head -10

# Check headers in application
kubectl logs <github-review-service-pod> -n default \
  | grep "x-jwt-" | head -10
```

### Monitor Performance

```bash
# Sidecar CPU/memory usage
kubectl top pod <pod> -n default --containers

# Lua filter execution time
kubectl exec -it <pod> -c istio-proxy -n default -- \
  curl -s localhost:15000/stats | grep lua

# RequestAuthentication timing
kubectl logs <pod> -c istio-proxy -n default \
  | grep -E "(jwt_authn|request_auth)" | jq '.duration_ms'
```

### Compare Header Output

```bash
# Get headers as seen by application
curl -H "Authorization: Bearer <your-jwt>" \
  http://github-review-service/api/me \
  -v 2>&1 | grep "x-jwt-"

# Expected headers:
# x-jwt-sub: user-id
# x-jwt-username: github-username
# x-jwt-email: user@example.com
# x-jwt-name: Full Name
# x-jwt-scope: openid email profile
```

---

## Appendix: Technical Details

### How RequestAuthentication Works

```
1. Client sends request with Authorization: Bearer <JWT>
2. RequestAuthentication sidecar filter:
   - Fetches JWKS from jwksUri
   - Validates JWT signature
   - Validates issuer, audience
   - Extracts claims into request.auth.claims.* attributes
   - Outputs x-jwt-payload header (entire payload as JSON)
3. Lua filter receives request with:
   - x-jwt-payload: '{"sub":"user-id","email":"user@example.com",...}'
   - request.auth attributes (internal to Envoy, not directly accessible from Lua)
4. Lua parses x-jwt-payload and injects x-jwt-* headers
5. Application receives request with x-jwt-* headers
```

### Why jwt_authn Alone Isn't Sufficient

The native `envoy.filters.http.jwt_authn` filter validates JWTs but doesn't expose individual claims as headers. The `payload_in_metadata` option stores the payload in Envoy metadata (used for routing decisions), not HTTP headers visible to applications.

To make claims available as headers, you must:
1. Extract from metadata (requires custom filter)
2. Extract from x-jwt-payload header (current approach with Lua)
3. Use another filter (not available in Envoy's stock filters)

The Lua approach is the simplest for this reason.

### Istio's RequestAuthentication Advantages

- **Standard**: Built into Istio, consistent across all workloads
- **Caching**: JWKS cached per issuers; no repeated downloads
- **Observability**: Integrates with Istio metrics
- **Policies**: Works with AuthorizationPolicies for RBAC
- **Multi-tenant**: Handles multiple issuers/audiences seamlessly

---

## Related Files

- [envoyfilter-github-review-jwt-to-headers.yaml](../infrastructure/k8s/envoyfilter-github-review-jwt-to-headers.yaml) - Current Lua claim extraction
- [requestauthentication-github-review.yaml](../infrastructure/k8s/requestauthentication-github-review.yaml) - JWT validation
- [JWT_CLAIM_REFACTOR_PLAN.md](JWT_CLAIM_REFACTOR_PLAN.md) - Original refactor plan (deprecated in favor of this analysis)

---

## Conclusion

The current architecture combining **Istio RequestAuthentication + Lua claim extraction** is well-optimized for the claim extraction use case. Moving to native `jwt_authn` doesn't solve the fundamental problem (claim extraction) and would add complexity without benefit.

**Recommendation**: Keep the current approach. Optimize only if profiling shows Lua is a CPU bottleneck (unlikely for typical JWT payload sizes < 2KB).
