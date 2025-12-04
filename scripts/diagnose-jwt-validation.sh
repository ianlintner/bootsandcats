#!/bin/bash
# OAuth2 JWT Validation Diagnostic Script
# Checks Azure Key Vault, AKS pods, JWKS endpoint, and token generation.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

BLUE='\033[0;34m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

info(){ echo -e "${BLUE}ℹ${NC} $*"; }
ok(){ echo -e "${GREEN}✓${NC} $*"; }
warn(){ echo -e "${YELLOW}⚠${NC} $*"; }
err(){ echo -e "${RED}✗${NC} $*" >&2; }

info "Checking Azure Key Vault secret (inker-kv/oauth2-jwk)"
if SECRET_VALUE=$(az keyvault secret show --vault-name inker-kv --name oauth2-jwk --query value -o tsv 2>/dev/null); then
  if echo "$SECRET_VALUE" | jq . >/dev/null 2>&1; then
    SECRET_JSON=$(echo "$SECRET_VALUE" | jq .)
    COUNT=$(echo "$SECRET_JSON" | jq '.keys | length')
    ok "Key Vault secret present with $COUNT key(s)"
    echo "$SECRET_JSON" | jq '.keys[] | {kid, kty, alg, crv}'
  else
    warn "Secret value is not valid JSON"
    echo "$SECRET_VALUE" | head -c 200 && echo
  fi
else
  err "Azure Key Vault secret missing or inaccessible"
fi

echo
info "Inspecting AKS pod"
POD=$(kubectl get pods -l app=oauth2-server -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || true)
if [ -z "${POD}" ]; then
  err "No oauth2-server pods running"
  exit 1
fi
ok "Using pod $POD"
info "Relevant environment variables:"
kubectl exec "$POD" -- env | grep -E 'AZURE_KEYVAULT|OAUTH2' || warn "Env vars not found"

echo
info "Checking JWKS endpoint via port-forward"
PORT_FORWARD_PID=""
cleanup(){ [ -n "$PORT_FORWARD_PID" ] && kill "$PORT_FORWARD_PID" >/dev/null 2>&1 || true; }
trap cleanup EXIT
kubectl port-forward svc/oauth2-server 9000:9000 >/dev/null 2>&1 &
PORT_FORWARD_PID=$!
sleep 3
if JWKS=$(curl -s http://localhost:9000/oauth2/jwks 2>/dev/null); then
  ok "JWKS reachable"
  echo "$JWKS" | jq '.keys[] | {kid, alg, kty, crv}'
else
  err "JWKS endpoint not reachable"
fi

echo
info "Generating test token"
TOKEN_RESPONSE=$(curl -s -X POST http://localhost:9000/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=m2m-client&client_secret=m2m-secret" 2>/dev/null)
if echo "$TOKEN_RESPONSE" | jq -e '.access_token' >/dev/null 2>&1; then
  ok "Token issued"
  ACCESS_TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r '.access_token')
  HEADER=$(echo "$ACCESS_TOKEN" | cut -d'.' -f1 | base64 -d 2>/dev/null || true)
  if [ -n "$HEADER" ]; then
    echo "JWT header:"; echo "$HEADER" | jq.
  else
    warn "Unable to decode JWT header"
  fi
else
  err "Token request failed"; echo "$TOKEN_RESPONSE"
fi

echo
info "Recent log snippets"
kubectl logs "$POD" --tail=50 | grep -i 'jwk\|key\|error' || warn "No relevant log lines"

ok "Diagnostics complete"
