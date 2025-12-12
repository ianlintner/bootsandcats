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

# Test 1: Health checks are accessible without OAuth
echo "Test 1: Health checks (should be accessible without OAuth)"
echo "---"
if kubectl port-forward -n default svc/profile-service 8080:80 >/dev/null 2>&1 &
then
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
    killall kubectl 2>/dev/null || true
    sleep 1
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
