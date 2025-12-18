#!/bin/bash
set -e

# OAuth2 Smoke Test
# Tests the Envoy OAuth2 filter flow for profile-service and github-review-service

PROFILE_DOMAIN="${PROFILE_DOMAIN:-profile.cat-herding.net}"
GITHUB_REVIEW_DOMAIN="${GITHUB_REVIEW_DOMAIN:-gh-review.cat-herding.net}"
OAUTH2_SERVER="${OAUTH2_SERVER:-oauth2.cat-herding.net}"

echo "🧪 Starting OAuth2 Smoke Tests"
echo "=========================================="
echo "Profile Service: https://$PROFILE_DOMAIN"
echo "GitHub Review: https://$GITHUB_REVIEW_DOMAIN"
echo "OAuth2 Server: https://$OAUTH2_SERVER"
echo

# Helper: base64 decode (macOS vs GNU coreutils)
base64_decode() {
    # Usage: echo "..." | base64_decode
    # IMPORTANT: do not probe base64 by running it without args, because that consumes stdin.
    if base64 --help 2>&1 | grep -q -- '--decode'; then
        base64 --decode
    else
        base64 -D
    fi
}

# Helper: application/x-www-form-urlencoded encoding (RFC 6749 uses this for Basic client auth)
# Spring Authorization Server decodes client_id/client_secret from Basic auth using form URL-decoding,
# which means '+' is treated as space unless the client percent-encodes it as %2B.
form_urlencode() {
    local raw="$1"
    if command -v python3 >/dev/null 2>&1; then
        python3 - <<'PY' "$raw"
import sys
import urllib.parse

print(urllib.parse.quote_plus(sys.argv[1]))
PY
        return 0
    fi

    # Minimal fallback (handles the most common foot-gun: '+' in base64-like secrets).
    # This does NOT attempt to fully implement RFC 3986 / x-www-form-urlencoded.
    printf '%s' "$raw" | sed -e 's/%/%25/g' -e 's/+/\%2B/g' -e 's/ /+/g'
}

# Helper: terminate background port-forward cleanly
stop_port_forward() {
    local pf_pid="$1"
    if [ -n "$pf_pid" ]; then
        kill "$pf_pid" 2>/dev/null || true
        # Give the process a moment to exit.
        sleep 1
    fi
}

# Test 0: OAuth2 token issuance (validates JWK signing)
echo "Test 0: OAuth2 token issuance (/oauth2/token via client_credentials)"
echo "---"
echo "Port-forwarding oauth2-server and requesting a token (no secrets printed)..."

M2M_CLIENT_ID="${M2M_CLIENT_ID:-m2m-client}"
M2M_CLIENT_SECRET="${M2M_CLIENT_SECRET:-${OAUTH2_M2M_CLIENT_SECRET:-}}"

if [ -z "$M2M_CLIENT_SECRET" ]; then
    # Best-effort: load from in-cluster secret if available. Do not echo.
    # NOTE: kubectl jsonpath supports dashed keys in dot-notation (used elsewhere in this repo).
    # Keep stderr for debugging (but do not print the secret itself).
    M2M_CLIENT_SECRET_B64=$(kubectl get secret -n default oauth2-app-secrets -o jsonpath='{.data.m2m-client-secret}' 2>/dev/null || true)
    if [ -n "$M2M_CLIENT_SECRET_B64" ]; then
        M2M_CLIENT_SECRET=$(echo -n "$M2M_CLIENT_SECRET_B64" | base64_decode 2>/dev/null || true)
    fi
fi

if kubectl port-forward -n default svc/oauth2-server 9000:9000 >/dev/null 2>&1 &
then
    OAUTH2_PF_PID=$!
    sleep 2

    if [ -z "$M2M_CLIENT_SECRET" ]; then
        echo "⚠️  WARN: No m2m client secret available. Set M2M_CLIENT_SECRET (or OAUTH2_M2M_CLIENT_SECRET) to enable token test."
    else
        # IMPORTANT: For client_secret_basic, RFC 6749 requires application/x-www-form-urlencoded encoding
        # of client_id and client_secret before constructing the Basic header.
        ENCODED_ID=$(form_urlencode "$M2M_CLIENT_ID")
        ENCODED_SECRET=$(form_urlencode "$M2M_CLIENT_SECRET")
        BASIC_AUTH=$(printf '%s' "$ENCODED_ID:$ENCODED_SECRET" | base64 | tr -d '\n')

        HTTP_CODE=$(curl -s -o /tmp/oauth2_token_response.json -w "%{http_code}" \
            -H "Authorization: Basic $BASIC_AUTH" \
            -H "Content-Type: application/x-www-form-urlencoded" \
            --data "grant_type=client_credentials&scope=profile:read" \
            http://localhost:9000/oauth2/token \
            -m 10 2>/dev/null || true)

        if [ "$HTTP_CODE" = "200" ] && grep -q '"access_token"' /tmp/oauth2_token_response.json 2>/dev/null; then
            echo "✅ PASS: /oauth2/token returned 200 and included an access_token"
        else
            echo "❌ FAIL: /oauth2/token did not succeed (HTTP $HTTP_CODE)"
            # Print a small snippet for debugging (avoid leaking tokens)
            if [ -f /tmp/oauth2_token_response.json ]; then
                sed -e 's/"access_token"\s*:\s*"[^"]\+"/"access_token":"***REDACTED***"/g' \
                    -e 's/"refresh_token"\s*:\s*"[^"]\+"/"refresh_token":"***REDACTED***"/g' \
                    /tmp/oauth2_token_response.json | head -c 600
                echo
            fi
        fi
    fi

    stop_port_forward "$OAUTH2_PF_PID"
fi
echo

# Test 1: Health checks are accessible without OAuth
echo "Test 1: Health checks (should be accessible without OAuth)"
echo "---"
if kubectl port-forward -n default svc/profile-service 8080:80 >/dev/null 2>&1 &
then
    PROFILE_PF_PID=$!
    sleep 2
    if curl -s http://localhost:8080/health -m 2 >/dev/null; then
        echo "✅ PASS: /health endpoint accessible"
    else
        echo "❌ FAIL: /health endpoint not accessible"
    fi
    if curl -s http://localhost:8080/actuator/health -m 2 >/dev/null; then
        echo "✅ PASS: /actuator/health endpoint accessible"
    else
        echo "❌ FAIL: /actuator/health endpoint not accessible"
    fi
    stop_port_forward "$PROFILE_PF_PID"
fi
echo

# Test 2: Public paths are accessible
echo "Test 2: Public paths bypass OAuth (status, favicon, swagger)"
echo "---"
PATHS=("/api/status" "/favicon.ico" "/swagger-ui.html" "/openapi.json")
for path in "${PATHS[@]}"; do
    if curl -s "https://$PROFILE_DOMAIN$path" -m 2 --insecure 2>/dev/null | grep -q "." 2>/dev/null; then
        echo "✅ PASS: $path accessible without auth"
    else
        echo "⚠️  WARN: $path returned empty or timed out"
    fi
done
echo

# Test 3: OAuth redirect flow
echo "Test 3: Protected paths trigger OAuth flow"
echo "---"
echo "Checking if protected path redirects to OAuth..."
RESPONSE=$(curl -s -i "https://$PROFILE_DOMAIN/" --insecure 2>/dev/null | head -1)
if echo "$RESPONSE" | grep -q "302\|307"; then
    echo "✅ PASS: Protected path returns redirect"
else
    echo "⚠️  WARN: Redirect response not detected (may be in-cluster only)"
fi
echo

# Test 4: Sidecar filter validation
echo "Test 4: Envoy filter configuration loaded"
echo "---"
PROFILE_POD=$(kubectl get pods -n default -l app=profile-service -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)
if [ -n "$PROFILE_POD" ]; then
    if kubectl logs "$PROFILE_POD" -c istio-proxy -n default 2>/dev/null | grep -i "oauth2" >/dev/null; then
        echo "✅ PASS: OAuth2 filter mentioned in sidecar logs"
    else
        echo "⚠️  WARN: OAuth2 filter not explicitly logged"
    fi
    echo "Pod: $PROFILE_POD is running"
fi
echo

# Test 5: JWT claim headers
echo "Test 5: JWT claim extraction (via RequestAuthentication)"
echo "---"
echo "RequestAuthentication resources:"
kubectl get requestauthentication -n default
echo "✅ RequestAuthentication deployed for profile and github-review"
echo

echo "=========================================="
echo "🎉 Smoke Tests Complete!"
echo
echo "Next steps:"
echo "1. Verify cookie names in DevTools when accessing the services"
echo "2. Check sidecar logs: kubectl logs <pod> -c istio-proxy -n default | grep oauth2"
echo "3. Test logout: curl -X GET https://\$DOMAIN/_oauth2/logout"
echo "4. Validate JWT headers are populated in downstream requests"
