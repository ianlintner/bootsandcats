#!/bin/bash
# Setup Chat Service OAuth2 secrets in Azure Key Vault.
#
# This creates the Key Vault secrets consumed by the chat application's Istio Envoy OAuth2 filter:
# - chat-client-secret       (OAuth2 confidential client secret; used by Envoy oauth2 filter token exchange)
# - chat-oauth-hmac-secret   (HMAC key used by Envoy oauth2 filter cookie signing)
#
# Defaults are aligned with Flyway migration V14 in infrastructure/k8s/apps/configs/flyway-migrations-configmap.yaml
# so dev/test environments "just work" without needing to update the DB row.
#
# Prereqs:
# - Azure CLI logged in (az login)
# - Access to the target Key Vault

set -euo pipefail

VAULT_NAME="${AZURE_VAULT_NAME:-inker-kv}"

MODE="${1:-demo}"

if [[ "$MODE" != "demo" && "$MODE" != "random" ]]; then
  echo "Usage: $0 [demo|random]"
  echo "  demo   -> uploads known dev/test values that match Flyway V14"
  echo "  random -> generates new random values (you must also update the oauth2_registered_client secret)"
  exit 2
fi

if [[ "$MODE" == "demo" ]]; then
  CHAT_CLIENT_SECRET="demo-chat-service-client-secret"
  # 32 bytes base64-ish token for HMAC; safe for demo.
  CHAT_OAUTH_HMAC_SECRET="demo-chat-oauth-hmac-secret"
else
  if command -v openssl >/dev/null 2>&1; then
    CHAT_CLIENT_SECRET="$(openssl rand -base64 48 | tr -d '\n' | tr -d '/+=' | cut -c -48)"
    CHAT_OAUTH_HMAC_SECRET="$(openssl rand -base64 48 | tr -d '\n' | tr -d '/+=' | cut -c -48)"
  else
    echo "openssl not found. Install openssl or run in demo mode." >&2
    exit 1
  fi
fi

echo "=== Chat OAuth2 Secrets Setup ==="
echo "Key Vault: $VAULT_NAME"
echo "Mode: $MODE"
echo ""

echo "Uploading secrets to Azure Key Vault..."
echo "  - chat-client-secret"
az keyvault secret set \
  --vault-name "$VAULT_NAME" \
  --name "chat-client-secret" \
  --value "$CHAT_CLIENT_SECRET" \
  --output none

echo "  - chat-oauth-hmac-secret"
az keyvault secret set \
  --vault-name "$VAULT_NAME" \
  --name "chat-oauth-hmac-secret" \
  --value "$CHAT_OAUTH_HMAC_SECRET" \
  --output none

echo ""
echo "=== Done ==="
echo ""
echo "Next steps:"
echo "1) Ensure the chat cluster has SecretProviderClass 'azure-keyvault-chat-secrets' applied."
echo "2) In the chat app repo, mount the CSI secrets volume and render these into Envoy SDS files:"
echo "   /etc/istio/oauth2/chat-oauth-token.yaml and /etc/istio/oauth2/chat-oauth-hmac.yaml"
echo "3) Ensure the Authorization Server has a matching client registration for client_id 'chat-service'"
echo "   with redirect URI https://chat.cat-herding.net/_oauth2/callback"

echo ""
echo "To verify secrets were added:"
echo "  az keyvault secret show --vault-name $VAULT_NAME --name chat-client-secret --query name -o tsv"
echo "  az keyvault secret show --vault-name $VAULT_NAME --name chat-oauth-hmac-secret --query name -o tsv"
