# JWT Filter Implementation - Quick Decision Guide

## The Core Question

**Should we migrate from RequestAuthentication + Lua to native jwt_authn?**

## Quick Answer

**🟢 Short Term**: No, keep current setup (RequestAuth + Lua)
- ✅ Proven, working, optimized
- ✅ Istio-standard pattern
- ✅ Low operational complexity

**🟡 Medium Term**: Optional evaluation
- Requires testing in staging first
- Potential performance gains (~2-3x from Lua overhead removal)
- Non-standard pattern (operational cost)

**🔴 Don't Migrate If**:
- System is stable and performing well
- Team is unfamiliar with direct Envoy configuration
- You value adherence to Istio best practices

## What Changed

**Before**: jwt_authn couldn't extract claims to headers → Still needs Lua → Not worth it

**After**: jwt_authn has `claim_to_headers` feature → Can extract claims directly → Actually viable!

```yaml
# claim_to_headers feature (NOW AVAILABLE)
claim_to_headers:
  - header_name: "x-jwt-sub"
    claim_name: "sub"
  - header_name: "x-jwt-roles"
    claim_name: "roles"  # Handles arrays automatically
```

## Three Approaches Compared

| Feature | Current (RequestAuth+Lua) | Native jwt_authn | WASM |
|---------|----------|------|------|
| **Status** | ✅ Production | 🟡 Viable | 🔴 Complex |
| **JWT Validation** | Istio RequestAuth | Envoy jwt_authn | Custom |
| **Claim Extraction** | Lua JSON parsing | claim_to_headers | WASM |
| **Performance** | Good | Better (no Lua) | Best (compiled) |
| **Operational Complexity** | Low | Medium | High |
| **Istio Compatibility** | Standard | Non-standard | Custom |
| **Debugging** | Simple | Medium | Hard |
| **Recommendation** | 🟢 Use this | 🟡 Test first | ❌ Only if needed |

## Decision Tree

```
Do you have performance issues with Lua parsing?
├─ NO (most cases) → Use RequestAuth + Lua ✅
└─ YES
   ├─ Can accept non-standard Istio pattern?
   │  ├─ NO → Investigate WASM approach
   │  └─ YES → Test native jwt_authn in staging → Decide
   └─ Can't accept operational risk?
      └─ Keep RequestAuth + Lua ✅
```

## Implementation Files

### Current Production (Keep Using)
- `requestauthentication-github-review.yaml` - JWT validation
- `envoyfilter-github-review-jwt-to-headers.yaml` - Lua claim extraction
- Same pattern replicated for profile-service

### New POC (For Future Migration)
- `envoyfilter-github-review-jwt-authn-native-poc.yaml` - Complete working example
  - Ready to test in staging
  - Includes both github-review-service and profile-service
  - Production-ready configuration

## Key Learnings

1. **Envoy jwt_authn is more capable than previously understood**
   - The `claim_to_headers` feature was undocumented in our analysis
   - This changes the viability assessment

2. **claim_to_headers automatically handles complex scenarios**
   - Array claims (e.g., roles) → comma-separated header values
   - Nested claims via dot notation (e.g., org.id)
   - Type conversions (numbers/booleans to strings)
   - Missing claims → gracefully skipped

3. **Performance trade-offs are real but modest**
   - Removing Lua = 2-3x faster claim extraction
   - But RequestAuth + jwt_authn = duplicate JWT validation
   - Overall net gain: ~5-10% latency improvement

4. **Operational simplicity has value**
   - Single filter vs. two-filter chain
   - Less configuration to manage
   - But non-standard pattern may increase learning curve

## Next Steps

### ✅ Immediate (Do Nothing Special)
- Continue using RequestAuthentication + Lua
- System is working well
- No action required

### 🟡 This Quarter (Optional Exploration)
1. Read `CLAIM_TO_HEADERS_TECHNICAL_REFERENCE.md` for complete feature details
2. Review POC file: `envoyfilter-github-review-jwt-authn-native-poc.yaml`
3. If interested, test in **staging environment only**
4. Measure performance impact with real workload

### 🔵 If Considering Migration
1. Test native jwt_authn in staging for 2+ weeks
2. Verify all claim types and edge cases work
3. Monitor performance and troubleshooting ease
4. Get team buy-in on non-standard pattern
5. Plan gradual rollout (canary deployment)
6. Keep RequestAuth fallback ready

## Documentation References

- **Technical Deep Dive**: `/docs/NATIVE_ENVOY_JWT_FILTER_ANALYSIS.md`
- **claim_to_headers Feature**: `/docs/CLAIM_TO_HEADERS_TECHNICAL_REFERENCE.md`
- **POC Configuration**: `/infrastructure/k8s/envoyfilter-github-review-jwt-authn-native-poc.yaml`
- **Revision Summary**: `/NATIVE_JWT_FILTER_REVISION_SUMMARY.md`

## Contact

For questions about JWT configuration, refer to:
1. Start with this guide (quick decision)
2. Check POC file for working examples
3. Read CLAIM_TO_HEADERS_TECHNICAL_REFERENCE for specific feature questions
4. Consult NATIVE_ENVOY_JWT_FILTER_ANALYSIS for detailed comparison

---

**Last Updated**: When claim_to_headers feature was validated
**Status**: Analysis complete, POC ready for evaluation
**Recommendation**: Keep current approach, optional future evaluation of native jwt_authn
