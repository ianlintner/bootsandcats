#!/bin/bash
# Chat App OAuth2 Setup Validation Script
# This script validates that all OAuth2 components are properly configured

set -e

echo "=========================================="
echo "Chat App OAuth2 Setup Validation"
echo "=========================================="
echo ""

# Color codes
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check functions
check_pass() {
    echo -e "${GREEN}✓${NC} $1"
}

check_fail() {
    echo -e "${RED}✗${NC} $1"
    exit 1
}

check_warn() {
    echo -e "${YELLOW}⚠${NC} $1"
}

echo "1. Checking Kubernetes resources..."
echo "-----------------------------------"

# Check chat-backend pod
if kubectl get pod -n default -l app=chat-backend -o jsonpath='{.items[0].status.phase}' 2>/dev/null | grep -q "Running"; then
    POD_NAME=$(kubectl get pod -n default -l app=chat-backend -o jsonpath='{.items[0].metadata.name}')
    check_pass "chat-backend pod is running: $POD_NAME"
else
    check_fail "chat-backend pod is not running"
fi

# Check oauth2-server pod
if kubectl get pod -n default -l app=oauth2-server -o jsonpath='{.items[0].status.phase}' 2>/dev/null | grep -q "Running"; then
    check_pass "oauth2-server pod is running"
else
    check_fail "oauth2-server pod is not running"
fi

echo ""
echo "2. Checking Istio configuration..."
echo "-----------------------------------"

# Check EnvoyFilters
if kubectl get envoyfilter chat-oauth2-exchange -n default &>/dev/null; then
    check_pass "EnvoyFilter chat-oauth2-exchange exists"
else
    check_fail "EnvoyFilter chat-oauth2-exchange not found"
fi

if kubectl get envoyfilter chat-jwt-to-headers -n default &>/dev/null; then
    check_pass "EnvoyFilter chat-jwt-to-headers exists"
else
    check_fail "EnvoyFilter chat-jwt-to-headers not found"
fi

# Check RequestAuthentication
if kubectl get requestauthentication chat-jwt-auth -n default &>/dev/null; then
    check_pass "RequestAuthentication chat-jwt-auth exists"
else
    check_fail "RequestAuthentication chat-jwt-auth not found"
fi

# Check VirtualService
if kubectl get virtualservice chat-virtualservice -n default &>/dev/null; then
    check_pass "VirtualService chat-virtualservice exists"
else
    check_fail "VirtualService chat-virtualservice not found"
fi

# Check Gateway
if kubectl get gateway chat-gateway -n default &>/dev/null; then
    check_pass "Gateway chat-gateway exists"
else
    check_fail "Gateway chat-gateway not found"
fi

echo ""
echo "3. Checking secrets and SecretProviderClass..."
echo "-----------------------------------------------"

# Check SecretProviderClass for OAuth2
if kubectl get secretproviderclass azure-keyvault-oauth2-secrets -n default &>/dev/null; then
    check_pass "SecretProviderClass azure-keyvault-oauth2-secrets exists"
else
    check_fail "SecretProviderClass azure-keyvault-oauth2-secrets not found"
fi

# Check SecretProviderClass for app secrets
if kubectl get secretproviderclass azure-keyvault-chat-secrets -n default &>/dev/null; then
    check_pass "SecretProviderClass azure-keyvault-chat-secrets exists"
else
    check_fail "SecretProviderClass azure-keyvault-chat-secrets not found"
fi

# Check secret mount
if kubectl get secret chat-oauth2-secrets -n default &>/dev/null; then
    check_pass "Secret chat-oauth2-secrets exists (synced from Key Vault)"
else
    check_warn "Secret chat-oauth2-secrets not found (will be created when pod mounts it)"
fi

if kubectl get secret chat-secrets-from-keyvault -n default &>/dev/null; then
    check_pass "Secret chat-secrets-from-keyvault exists (synced from Key Vault)"
else
    check_warn "Secret chat-secrets-from-keyvault not found"
fi

echo ""
echo "4. Checking OAuth2 secrets in pod..."
echo "-------------------------------------"

# Check if secrets are mounted in the pod
if kubectl exec -n default $POD_NAME -c chat-backend -- test -f /etc/istio/oauth2/chat-oauth-token.yaml 2>/dev/null; then
    check_pass "OAuth2 token secret mounted in pod"
    
    # Verify secret content
    SECRET_VALUE=$(kubectl exec -n default $POD_NAME -c chat-backend -- cat /etc/istio/oauth2/chat-oauth-token.yaml 2>/dev/null | grep "inline_string" | sed 's/.*inline_string: "\(.*\)".*/\1/')
    if [ "$SECRET_VALUE" == "demo-chat-backend-client-secret" ]; then
        check_pass "OAuth2 token secret has correct value"
    else
        check_warn "OAuth2 token secret value: $SECRET_VALUE (expected: demo-chat-backend-client-secret)"
    fi
else
    check_fail "OAuth2 token secret not mounted in pod"
fi

if kubectl exec -n default $POD_NAME -c chat-backend -- test -f /etc/istio/oauth2/chat-oauth-hmac.yaml 2>/dev/null; then
    check_pass "OAuth2 HMAC secret mounted in pod"
else
    check_fail "OAuth2 HMAC secret not mounted in pod"
fi

echo ""
echo "5. Checking OAuth2 server connectivity..."
echo "------------------------------------------"

# Check if OAuth2 server is reachable
if kubectl exec -n default $POD_NAME -c chat-backend -- wget -O- -q --timeout=5 http://oauth2-server.default.svc.cluster.local:9000/actuator/health 2>/dev/null | grep -q "UP"; then
    check_pass "OAuth2 server health endpoint is accessible"
else
    check_fail "Cannot reach OAuth2 server health endpoint"
fi

# Check JWKS endpoint
if kubectl exec -n default $POD_NAME -c chat-backend -- wget -O- -q --timeout=5 http://oauth2-server.default.svc.cluster.local:9000/oauth2/jwks 2>/dev/null | grep -q "keys"; then
    check_pass "OAuth2 JWKS endpoint is accessible"
else
    check_fail "Cannot reach OAuth2 JWKS endpoint"
fi

echo ""
echo "6. Checking database OAuth2 client configuration..."
echo "----------------------------------------------------"

# Get PostgreSQL pod
POSTGRES_POD=$(kubectl get pod -n default -l cnpg.io/cluster=postgres-ha,role=primary -o jsonpath='{.items[0].metadata.name}')

if [ -z "$POSTGRES_POD" ]; then
    check_warn "PostgreSQL primary pod not found (skipping database checks)"
else
    # Check client registration
    CLIENT_EXISTS=$(kubectl exec -n default $POSTGRES_POD -- psql -U postgres -d oauth2db -t -c "SELECT COUNT(*) FROM oauth2_registered_client WHERE client_id='chat-backend';" 2>/dev/null | tr -d ' ')
    
    if [ "$CLIENT_EXISTS" == "1" ]; then
        check_pass "OAuth2 client 'chat-backend' is registered"
        
        # Check client secret
        CLIENT_SECRET=$(kubectl exec -n default $POSTGRES_POD -- psql -U postgres -d oauth2db -t -c "SELECT client_secret FROM oauth2_registered_client WHERE client_id='chat-backend';" 2>/dev/null | tr -d ' ')
        
        if [ "$CLIENT_SECRET" == "{noop}demo-chat-backend-client-secret" ]; then
            check_pass "Client secret is correct"
        else
            check_warn "Client secret in database: $CLIENT_SECRET"
        fi
        
        # Check redirect URIs
        REDIRECT_URIS=$(kubectl exec -n default $POSTGRES_POD -- psql -U postgres -d oauth2db -t -c "SELECT redirect_uris FROM oauth2_registered_client WHERE client_id='chat-backend';" 2>/dev/null | tr -d ' ')
        
        if echo "$REDIRECT_URIS" | grep -q "https://chat.cat-herding.net/_oauth2/callback"; then
            check_pass "Redirect URI is correctly configured"
        else
            check_warn "Redirect URIs: $REDIRECT_URIS"
        fi
        
        # Check grant types
        GRANT_TYPES=$(kubectl exec -n default $POSTGRES_POD -- psql -U postgres -d oauth2db -t -c "SELECT authorization_grant_types FROM oauth2_registered_client WHERE client_id='chat-backend';" 2>/dev/null | tr -d ' ')
        
        if echo "$GRANT_TYPES" | grep -q "authorization_code"; then
            check_pass "Authorization code grant type is configured"
        else
            check_fail "Authorization code grant type not found: $GRANT_TYPES"
        fi
        
    else
        check_fail "OAuth2 client 'chat-backend' is not registered"
    fi
fi

echo ""
echo "7. Checking Azure Key Vault secrets..."
echo "---------------------------------------"

# Check if secrets exist in Key Vault
if az keyvault secret show --vault-name inker-kv --name chat-client-secret &>/dev/null; then
    check_pass "Key Vault secret 'chat-client-secret' exists"
    
    KV_SECRET=$(az keyvault secret show --vault-name inker-kv --name chat-client-secret --query value -o tsv)
    if [ "$KV_SECRET" == "demo-chat-backend-client-secret" ]; then
        check_pass "Key Vault secret matches expected value"
    else
        check_warn "Key Vault secret value: $KV_SECRET"
    fi
else
    check_fail "Key Vault secret 'chat-client-secret' not found"
fi

if az keyvault secret show --vault-name inker-kv --name chat-oauth-hmac-secret &>/dev/null; then
    check_pass "Key Vault secret 'chat-oauth-hmac-secret' exists"
else
    check_fail "Key Vault secret 'chat-oauth-hmac-secret' not found"
fi

echo ""
echo "=========================================="
echo "Validation Complete!"
echo "=========================================="
echo ""
echo "Next steps:"
echo "1. Test OAuth2 flow by navigating to: https://chat.cat-herding.net/"
echo "2. You should be redirected to: https://oauth2.cat-herding.net/oauth2/authorize"
echo "3. After login, you should be redirected back to the chat app"
echo "4. Check browser cookies - you should see '_chat_session' cookie"
echo ""
echo "For troubleshooting, see: docs/CHAT_APP_OAUTH2_TROUBLESHOOTING.md"
