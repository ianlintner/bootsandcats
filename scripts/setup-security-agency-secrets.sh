#!/usr/bin/env bash
# Script to set up Azure Key Vault secrets for security-agency OAuth2 client
set -euo pipefail

VAULT_NAME="${VAULT_NAME:-inker-kv}"
CLIENT_NAME="security-agency"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}Setting up Azure Key Vault secrets for ${CLIENT_NAME}${NC}"
echo "Vault: ${VAULT_NAME}"
echo ""

# Function to generate a secure random secret
generate_secret() {
  openssl rand -base64 32 | tr -d "=+/" | cut -c1-32
}

# Check if az CLI is logged in
if ! az account show &>/dev/null; then
  echo -e "${RED}Error: Not logged in to Azure CLI${NC}"
  echo "Please run: az login"
  exit 1
fi

# Check if vault exists
if ! az keyvault show --name "${VAULT_NAME}" &>/dev/null; then
  echo -e "${RED}Error: Key Vault '${VAULT_NAME}' not found${NC}"
  exit 1
fi

echo -e "${YELLOW}Step 1: Client Secret${NC}"
CLIENT_SECRET_NAME="${CLIENT_NAME}-client-secret"

# Check if secret already exists
if az keyvault secret show --vault-name "${VAULT_NAME}" --name "${CLIENT_SECRET_NAME}" &>/dev/null; then
  echo -e "${YELLOW}Secret '${CLIENT_SECRET_NAME}' already exists${NC}"
  read -p "Do you want to update it? (y/N) " -n 1 -r
  echo
  if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Skipping client secret update"
  else
    CLIENT_SECRET=$(generate_secret)
    az keyvault secret set \
      --vault-name "${VAULT_NAME}" \
      --name "${CLIENT_SECRET_NAME}" \
      --value "${CLIENT_SECRET}" \
      --output none
    echo -e "${GREEN}✓ Updated client secret${NC}"
  fi
else
  CLIENT_SECRET=$(generate_secret)
  az keyvault secret set \
    --vault-name "${VAULT_NAME}" \
    --name "${CLIENT_SECRET_NAME}" \
    --value "${CLIENT_SECRET}" \
    --output none
  echo -e "${GREEN}✓ Created client secret${NC}"
fi

echo ""
echo -e "${YELLOW}Step 2: OAuth HMAC Secret${NC}"
HMAC_SECRET_NAME="${CLIENT_NAME}-oauth-hmac-secret"

if az keyvault secret show --vault-name "${VAULT_NAME}" --name "${HMAC_SECRET_NAME}" &>/dev/null; then
  echo -e "${YELLOW}Secret '${HMAC_SECRET_NAME}' already exists${NC}"
  read -p "Do you want to update it? (y/N) " -n 1 -r
  echo
  if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Skipping HMAC secret update"
  else
    HMAC_SECRET=$(generate_secret)
    az keyvault secret set \
      --vault-name "${VAULT_NAME}" \
      --name "${HMAC_SECRET_NAME}" \
      --value "${HMAC_SECRET}" \
      --output none
    echo -e "${GREEN}✓ Updated HMAC secret${NC}"
  fi
else
  HMAC_SECRET=$(generate_secret)
  az keyvault secret set \
    --vault-name "${VAULT_NAME}" \
    --name "${HMAC_SECRET_NAME}" \
    --value "${HMAC_SECRET}" \
    --output none
  echo -e "${GREEN}✓ Created HMAC secret${NC}"
fi

echo ""
echo -e "${GREEN}✓ Azure Key Vault setup complete!${NC}"
echo ""
echo "Secrets created in vault '${VAULT_NAME}':"
echo "  - ${CLIENT_SECRET_NAME}"
echo "  - ${HMAC_SECRET_NAME}"
echo ""
echo -e "${YELLOW}Next steps:${NC}"
echo "1. Update the OAuth2 server database with the client secret"
echo "2. Build and push the security-agency Docker image"
echo "3. Deploy the manifests: kubectl apply -k infrastructure/k8s"
echo "4. Configure DNS: security-agency.cat-herding.net -> Istio ingress IP"
echo ""
echo "For more details, see: infrastructure/k8s/apps/security-agency/README.md"
