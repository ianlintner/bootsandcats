#!/bin/bash
# Regenerate EC JWK, upload to Key Vault, restart OAuth2 pods, verify JWKS/token.
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

info "Generating new EC P-256 JWK"
cd "$PROJECT_ROOT"
./gradlew -q -p server-logic :server-logic:runGenerator > /tmp/new-jwk.json
ok "JWK generated: $(jq -r '.keys[0].kid' /tmp/new-jwk.json)"

info "Uploading to Azure Key Vault (inker-kv/oauth2-jwk)"
az keyvault secret set --vault-name inker-kv --name oauth2-jwk --file /tmp/new-jwk.json >/dev/null
ok "Secret updated"

info "Restarting oauth2-server deployment"
kubectl rollout restart deployment/oauth2-server >/dev/null
kubectl rollout status deployment/oauth2-server --timeout=5m
ok "Deployment restarted"

info "Verifying JWKS"
kubectl port-forward svc/oauth2-server 9000:9000 >/dev/null 2>&1 &
PORT_FORWARD_PID=$!
trap 'kill $PORT_FORWARD_PID' EXIT
sleep 5
JWKS=$(curl -s http://localhost:9000/oauth2/jwks)
echo "$JWKS" | jq '.keys[] | {kid, alg, kty, crv}'

info "Requesting token"
M2M_SECRET=$(kubectl get secret oauth2-app-secrets -o jsonpath='{.data.m2m-client-secret}' | base64 -d 2>/dev/null || true)
if [ -z "$M2M_SECRET" ]; then
  warn "Unable to load m2m-client secret from Kubernetes; falling back to default"
  M2M_SECRET="m2m-secret"
fi
TOKEN=$(curl -s -X POST http://localhost:9000/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=m2m-client&client_secret=$M2M_SECRET" | jq -r '.access_token')
if [ "$TOKEN" = "null" ] || [ -z "$TOKEN" ]; then
  err "Token request failed"
  exit 1
fi
ok "Token issued"
HEADER=$(echo "$TOKEN" | cut -d'.' -f1 | base64 -d 2>/dev/null || true)
[ -n "$HEADER" ] && echo "$HEADER" | jq '.' || warn "Unable to decode JWT header"

ok "Fix complete"
