# JWT claim_to_headers Feature - Technical Reference

## Overview

The **`claim_to_headers`** feature in Envoy's `envoy.filters.http.jwt_authn` filter allows direct mapping of JWT claims to HTTP headers without requiring Lua or additional filters.

## Feature Location

```yaml
# In jwt_authn filter configuration:
apiVersion: networking.istio.io/v1alpha3
kind: EnvoyFilter
metadata:
  name: github-review-jwt-native
spec:
  configPatches:
  - applyTo: HTTP_FILTER
    value:
      name: envoy.filters.http.jwt_authn
      typed_config:
        "@type": type.googleapis.com/envoy.extensions.filters.http.jwt_authn.v3.JwtAuthentication
        
        # JWT validation configuration here
        providers: {...}
        rules: {...}
        
        # ============ claim_to_headers feature ============
        claim_to_headers:
          - header_name: "x-jwt-sub"
            claim_name: "sub"
          - header_name: "x-jwt-roles"
            claim_name: "roles"
```

## Basic Syntax

```yaml
claim_to_headers:
  - header_name: "<HTTP_HEADER_NAME>"
    claim_name: "<JWT_CLAIM_NAME>"
    append: <boolean>  # Optional: default false
```

### Parameters

| Parameter | Type | Required | Description | Default |
|-----------|------|----------|-------------|---------|
| `header_name` | string | ✅ Yes | HTTP header to create/update | - |
| `claim_name` | string | ✅ Yes | JWT claim to extract | - |
| `append` | boolean | ❌ No | Append to existing header value? | `false` |

## Examples

### Example 1: Standard Claims Mapping

```yaml
claim_to_headers:
  # User identity
  - header_name: "x-jwt-sub"
    claim_name: "sub"
  
  - header_name: "x-jwt-email"
    claim_name: "email"
  
  - header_name: "x-jwt-username"
    claim_name: "preferred_username"
  
  - header_name: "x-jwt-name"
    claim_name: "name"
  
  # OAuth2 scope
  - header_name: "x-jwt-scope"
    claim_name: "scope"
```

**JWT Payload Example**:
```json
{
  "sub": "user-123",
  "email": "user@example.com",
  "preferred_username": "john.doe",
  "name": "John Doe",
  "scope": "openid profile email"
}
```

**HTTP Headers Created**:
```
x-jwt-sub: user-123
x-jwt-email: user@example.com
x-jwt-username: john.doe
x-jwt-name: John Doe
x-jwt-scope: openid profile email
```

### Example 2: Array Claims (Roles)

```yaml
claim_to_headers:
  # Extract roles array to comma-separated header value
  - header_name: "x-jwt-roles"
    claim_name: "roles"
```

**JWT Payload**:
```json
{
  "roles": ["admin", "user", "reviewer"]
}
```

**HTTP Header Created**:
```
x-jwt-roles: admin,user,reviewer
```

⚠️ **Note**: jwt_authn automatically converts arrays to comma-separated string values.

### Example 3: Nested Claims (Using Dot Notation)

```yaml
claim_to_headers:
  # Extract nested claim using dot notation
  - header_name: "x-jwt-org-id"
    claim_name: "org.id"
  
  - header_name: "x-jwt-org-name"
    claim_name: "org.name"
```

**JWT Payload**:
```json
{
  "org": {
    "id": "org-456",
    "name": "Acme Corp"
  }
}
```

**HTTP Headers Created**:
```
x-jwt-org-id: org-456
x-jwt-org-name: Acme Corp
```

### Example 4: Custom Claim Names

```yaml
claim_to_headers:
  # Map non-standard claim to standard header
  - header_name: "x-user-id"
    claim_name: "sub"
  
  # Map OAuth provider-specific claim
  - header_name: "x-tenant-id"
    claim_name: "custom_tenant_id"
  
  # Map custom permission claim
  - header_name: "x-permissions"
    claim_name: "permissions"
```

## Behavior Details

### Missing Claims

If a JWT claim is **not present** in the token:
- ✅ No header is added
- ✅ No error is raised
- ✅ Downstream application proceeds normally

Example:
```yaml
claim_to_headers:
  - header_name: "x-jwt-dept"
    claim_name: "department"  # Not in token
```

**Result**: `x-jwt-dept` header is NOT added to the request

### Claim Value Types

| JWT Type | Behavior | Example |
|----------|----------|---------|
| String | Added as-is | `"email": "user@example.com"` → `x-jwt-email: user@example.com` |
| Number | Converted to string | `"user_id": 12345` → `x-user-id: 12345` |
| Boolean | Converted to string | `"is_admin": true` → `x-is-admin: true` |
| Array | Comma-separated | `"roles": ["a","b","c"]` → `x-roles: a,b,c` |
| Object | Not supported | Claims with object values are skipped |
| Null | Not added | Null values don't create headers |

### Header Replacement vs Append

```yaml
# Case 1: append=false (default)
claim_to_headers:
  - header_name: "x-jwt-sub"
    claim_name: "sub"
    append: false

# If request already has x-jwt-sub header, it's REPLACED
# Original: x-jwt-sub: old-value
# Result: x-jwt-sub: new-value-from-jwt
```

```yaml
# Case 2: append=true
claim_to_headers:
  - header_name: "x-jwt-sub"
    claim_name: "sub"
    append: true

# If request already has x-jwt-sub header, JWT value is APPENDED
# Original: x-jwt-sub: old-value
# Result: x-jwt-sub: old-value,new-value-from-jwt
```

⚠️ **Caution**: Use `append: true` carefully in untrusted networks.

## Complete Working Example

```yaml
apiVersion: networking.istio.io/v1alpha3
kind: EnvoyFilter
metadata:
  name: jwt-claim-to-headers
  namespace: default
spec:
  workloadSelector:
    labels:
      app: my-service
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
          
          # OAuth2/OIDC provider configuration
          providers:
            my_provider:
              issuer: "https://oauth2.example.com"
              audiences: "my-service"
              remote_jwks:
                http_uri:
                  uri: "https://oauth2.example.com/.well-known/jwks.json"
                  cluster: "outbound|443||oauth2.example.com"
                  timeout: 5s
                cache_config:
                  cache_size_bytes: 10485760
                  cache_time_to_live: 3600s
              verify_exp_time: true
          
          # ========================================
          # CLAIM-TO-HEADER MAPPING
          # ========================================
          # These are the headers your application will receive
          claim_to_headers:
            # Standard OIDC claims
            - header_name: "x-jwt-sub"
              claim_name: "sub"
            
            - header_name: "x-jwt-email"
              claim_name: "email"
            
            - header_name: "x-jwt-username"
              claim_name: "preferred_username"
            
            # Authorization data
            - header_name: "x-jwt-roles"
              claim_name: "roles"
            
            - header_name: "x-jwt-scope"
              claim_name: "scope"
          
          # JWT requirement rules
          rules:
            # All paths require JWT
            - match:
                prefix: "/"
              requires:
                provider_name: "my_provider"
            
            # Except these public endpoints
            - match:
                prefix: "/public/"
              allow_missing_or_expired: true
            
            - match:
                prefix: "/health"
              allow_missing_or_expired: true
```

## Use Case: Spring Boot Application

When using this with a Spring Boot application:

```java
@RestController
public class MyController {
  
  @GetMapping("/api/profile")
  public ResponseEntity<Profile> getProfile(
      @RequestHeader("x-jwt-sub") String userId,
      @RequestHeader("x-jwt-email") String email,
      @RequestHeader("x-jwt-roles") String roles  // comma-separated
  ) {
    // userId = "user-123"
    // email = "user@example.com"
    // roles = "admin,user,reviewer"
    
    String[] roleArray = roles.split(",");
    // Process with role-based authorization
    
    return ResponseEntity.ok(new Profile(userId, email, roleArray));
  }
}
```

## Performance Implications

| Aspect | Impact |
|--------|--------|
| **CPU** | Minimal - native Envoy code |
| **Memory** | Negligible - just header injection |
| **Latency** | ~0.1-0.5ms per claim (native operation) |
| **vs Lua** | 2-3x faster than Lua JSON parsing |
| **vs RequestAuth+Lua** | Similar performance to RequestAuth, eliminates Lua overhead |

## Troubleshooting

### Headers Not Appearing

**Check 1**: Is the JWT valid?
```bash
# jwt_authn rejects invalid JWTs before claim_to_headers runs
# No headers will be injected
```

**Check 2**: Is the claim present in the JWT?
```bash
# Decode JWT and verify claim exists
# Missing claims don't create headers (by design)
```

**Check 3**: Is the filter ordered correctly?
```yaml
patch:
  operation: INSERT_BEFORE
  value:
    name: envoy.filters.http.jwt_authn  # Must be before router
```

**Check 4**: Verify claim name matches JWT payload
```bash
# JWT may have different claim names
# Common variations:
# - sub, user_id, uid
# - email, mail, email_address
# - preferred_username, username, name
```

### Headers Have Wrong Values

**Check 1**: Verify claim extraction with curl
```bash
curl -H "Authorization: Bearer $JWT" https://your-service/api/test \
  -v  # Will show x-jwt-* headers from claim_to_headers
```

**Check 2**: Decode JWT to verify claim values
```bash
echo $JWT | cut -d. -f2 | base64 -d | jq .
```

**Check 3**: Check for header name conflicts
```yaml
# Verify no other filters are setting same header names
# RequestAuthentication might also set headers
```

## Comparison with Lua Alternative

| Aspect | claim_to_headers | Lua Filter |
|--------|------------------|-----------|
| **Code** | Configuration | Lua script |
| **Performance** | Native Envoy | VM-based |
| **Array Handling** | ✅ Automatic | ⚠️ Manual parsing |
| **Type Conversion** | ✅ Automatic | ⚠️ Manual logic |
| **Debugging** | 🟢 Simple | 🟡 More complex |
| **Error Handling** | ✅ Graceful defaults | ⚠️ Requires implementation |
| **Dependencies** | None | Lua runtime |

## Recommendations

✅ **Use claim_to_headers when**:
- You need direct JWT claim to HTTP header mapping
- You want to eliminate Lua filter overhead
- Your claims are in standard OIDC format
- Performance is a consideration

⚠️ **Consider Lua when**:
- You need complex transformation logic
- Claims require conditional processing
- You need computed/derived headers
- You need format conversions beyond string

## References

- [Envoy JWT Authentication Filter Documentation](https://www.envoyproxy.io/docs/envoy/latest/api-v3/extensions/filters/http/jwt_authn/v3/config.proto)
- [JwtAuthentication v3 API Proto](https://www.envoyproxy.io/docs/envoy/latest/api-v3/extensions/filters/http/jwt_authn/v3/config.proto#envoy-v3-api-field-extensions-filters-http-jwt-authn-v3-jwtauthentication-claim-to-headers)
- [Istio JWT Authentication](https://istio.io/latest/docs/tasks/security/authentication/authn-policy/)
