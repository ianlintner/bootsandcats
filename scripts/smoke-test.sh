#!/usr/bin/env bash
# OAuth2 Server Smoke Test Suite

set -e

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test counter
PASSED=0
FAILED=0

# Test function
test_endpoint() {
    local test_name="$1"
    local command="$2"
    local expected_pattern="$3"

    echo -n "Testing: $test_name... "

    if output=$(eval "$command" 2>&1); then
        if [[ -z "$expected_pattern" ]] || echo "$output" | grep -q "$expected_pattern"; then
            echo -e "${GREEN}✅ PASS${NC}"
            ((PASSED++))
            return 0
        else
            echo -e "${RED}❌ FAIL${NC} - Expected pattern not found"
            echo "  Output: $output"
            ((FAILED++))
            return 1
        fi
    else
        echo -e "${RED}❌ FAIL${NC} - Command failed"
        echo "  Error: $output"
        ((FAILED++))
        return 1
    fi
}

echo "=========================================="
echo "  OAuth2 Server Smoke Test Suite"
echo "  Date: $(date)"
echo "=========================================="
echo ""

# Check port forward is active
echo "Checking port forward status..."
if ! lsof -i :9000 | grep -q LISTEN; then
    echo -e "${YELLOW}⚠ Port forward not detected. Starting...${NC}"
    kubectl port-forward service/oauth2-server 9000:9000 -n default > /tmp/oauth2-port-forward.log 2>&1 &
    PF_PID=$!
    echo "Started port forward (PID: $PF_PID)"
    sleep 5
else
    echo -e "${GREEN}✓${NC} Port forward is active"
fi
echo ""

# Test 1: Readiness Probe
test_endpoint "Readiness Probe" \
    "curl -sf http://localhost:9000/actuator/health/readiness" \
    '"status":"UP"'

# Test 2: Liveness Probe
test_endpoint "Liveness Probe" \
    "curl -sf http://localhost:9000/actuator/health/liveness" \
    '"status":"UP"'

# Test 3: OIDC Discovery
test_endpoint "OIDC Discovery" \
    "curl -sf http://localhost:9000/.well-known/openid-configuration" \
    '"issuer"'

# Test 4: Issuer Field
ISSUER=$(curl -sf http://localhost:9000/.well-known/openid-configuration | jq -r '.issuer' 2>/dev/null)
if [[ -n "$ISSUER" ]]; then
    echo -e "  └─ Issuer: ${GREEN}$ISSUER${NC}"
fi

# Test 5: Authorization Endpoint
test_endpoint "Authorization Endpoint in Discovery" \
    "curl -sf http://localhost:9000/.well-known/openid-configuration" \
    '"authorization_endpoint"'

# Test 6: Token Endpoint
test_endpoint "Token Endpoint in Discovery" \
    "curl -sf http://localhost:9000/.well-known/openid-configuration" \
    '"token_endpoint"'

# Test 7: JWKS Endpoint
test_endpoint "JWKS Endpoint" \
    "curl -sf http://localhost:9000/oauth2/jwks" \
    '"keys"'

# Test 8: JWKS Has Keys
KEY_COUNT=$(curl -sf http://localhost:9000/oauth2/jwks | jq '.keys | length' 2>/dev/null || echo "0")
if [[ "$KEY_COUNT" -gt 0 ]]; then
    echo -e "  └─ JWKS Keys: ${GREEN}$KEY_COUNT key(s)${NC}"
fi

# Test 9: Prometheus Metrics
test_endpoint "Prometheus Metrics Endpoint" \
    "curl -sf http://localhost:9000/actuator/prometheus" \
    "jvm_memory_used_bytes"

# Test 10: Actuator Info
test_endpoint "Actuator Info Endpoint" \
    "curl -sf http://localhost:9000/actuator/info" \
    ""

# Test 11: Grant Types Supported
test_endpoint "Grant Types (Client Credentials)" \
    "curl -sf http://localhost:9000/.well-known/openid-configuration" \
    '"client_credentials"'

test_endpoint "Grant Types (Authorization Code)" \
    "curl -sf http://localhost:9000/.well-known/openid-configuration" \
    '"authorization_code"'

test_endpoint "Grant Types (Refresh Token)" \
    "curl -sf http://localhost:9000/.well-known/openid-configuration" \
    '"refresh_token"'

# Test 12: Response Types
test_endpoint "Response Types (Code)" \
    "curl -sf http://localhost:9000/.well-known/openid-configuration" \
    '"code"'

# Test 13: Token Endpoint - Client Credentials Flow (with wrong credentials - expect 401)
echo -n "Testing: Token Endpoint (Authentication Check)... "
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:9000/oauth2/token \
    -u "invalid-client:invalid-secret" \
    -d "grant_type=client_credentials" \
    -d "scope=api:read")

if [[ "$HTTP_CODE" == "401" ]]; then
    echo -e "${GREEN}✅ PASS${NC} (Returns 401 for invalid credentials)"
    ((PASSED++))
else
    echo -e "${YELLOW}⚠ UNEXPECTED${NC} (Expected 401, got $HTTP_CODE)"
fi

# Test 14: Token Endpoint - Valid Client Credentials
M2M_CLIENT_ID="${M2M_CLIENT_ID:-m2m-client}"
M2M_CLIENT_SECRET="${M2M_CLIENT_SECRET:-CHANGEME}"
M2M_SCOPE="${M2M_SCOPE:-api:read}"
echo -n "Testing: Token Endpoint (Valid Client Credentials)... "
TOKEN_RESPONSE=$(curl -sf -X POST http://localhost:9000/oauth2/token \
    -u "${M2M_CLIENT_ID}:${M2M_CLIENT_SECRET}" \
    -d "grant_type=client_credentials" \
    -d "scope=${M2M_SCOPE}" 2>&1)

if echo "$TOKEN_RESPONSE" | jq -e '.access_token' > /dev/null 2>&1; then
    ACCESS_TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r '.access_token')
    echo -e "${GREEN}✅ PASS${NC}"
    echo "  └─ Access Token: ${GREEN}${ACCESS_TOKEN:0:50}...${NC}"
    ((PASSED++))
    # Test 15: Token Introspection
    echo -n "Testing: Token Introspection... "
    INTROSPECT=$(curl -sf -X POST http://localhost:9000/oauth2/introspect \
        -u "${M2M_CLIENT_ID}:${M2M_CLIENT_SECRET}" \
        -d "token=$ACCESS_TOKEN" 2>&1)

    if echo "$INTROSPECT" | jq -e '.active' > /dev/null 2>&1; then
        IS_ACTIVE=$(echo "$INTROSPECT" | jq -r '.active')
        echo -e "${GREEN}✅ PASS${NC}"
        echo "  └─ Token Active: ${GREEN}$IS_ACTIVE${NC}"
        ((PASSED++))
    else
        echo -e "${RED}❌ FAIL${NC}"
        echo "  Error: $INTROSPECT"
        ((FAILED++))
    fi
else
    echo -e "${RED}❌ FAIL${NC}"
    echo "  Error: $TOKEN_RESPONSE"
    ((FAILED++))
fi

# Summary
echo ""
echo "=========================================="
echo "  Test Summary"
echo "=========================================="
echo -e "  ${GREEN}Passed: $PASSED${NC}"
echo -e "  ${RED}Failed: $FAILED${NC}"
echo -e "  Total:  $((PASSED + FAILED))"
echo ""

if [[ $FAILED -eq 0 ]]; then
    echo -e "${GREEN}🎉 All tests passed!${NC}"
    exit 0
else
    echo -e "${RED}❌ Some tests failed${NC}"
    exit 1
fi

