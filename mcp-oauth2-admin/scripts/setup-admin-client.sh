#!/bin/bash
# Setup script for MCP Admin Client
# Creates the admin client on the OAuth2 server and generates Kubernetes secrets

set -e

# Configuration
OAUTH2_URL="${OAUTH2_URL:-https://oauth2.cat-herding.net}"
ADMIN_USER="${ADMIN_USER:-admin}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin}"
NAMESPACE="${NAMESPACE:-oauth2-system}"
CLIENT_ID="mcp-admin-client"

echo "========================================="
echo "MCP Admin Client Setup"
echo "========================================="
echo ""
echo "OAuth2 Server: $OAUTH2_URL"
echo "Namespace: $NAMESPACE"
echo ""

# Check prerequisites
command -v curl >/dev/null 2>&1 || { echo "Error: curl is required but not installed."; exit 1; }
command -v jq >/dev/null 2>&1 || { echo "Error: jq is required but not installed."; exit 1; }
command -v openssl >/dev/null 2>&1 || { echo "Error: openssl is required but not installed."; exit 1; }
command -v kubectl >/dev/null 2>&1 || { echo "Error: kubectl is required but not installed."; exit 1; }

# Generate a secure random secret
echo "Generating client secret..."
MCP_CLIENT_SECRET=$(openssl rand -base64 32)

# Get admin access token
echo "Authenticating as admin..."
TOKEN_RESPONSE=$(curl -s -X POST "$OAUTH2_URL/oauth2/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -u "$ADMIN_USER:$ADMIN_PASSWORD" \
  -d "grant_type=client_credentials" \
  -d "scope=admin:read admin:write")

ADMIN_TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r '.access_token')

if [ -z "$ADMIN_TOKEN" ] || [ "$ADMIN_TOKEN" = "null" ]; then
  echo "Error: Failed to get admin token"
  echo "Response: $TOKEN_RESPONSE"
  exit 1
fi

echo "✓ Admin authentication successful"

# Check if client already exists
echo "Checking if client already exists..."
EXISTING_CLIENT=$(curl -s "$OAUTH2_URL/api/admin/clients/$CLIENT_ID" \
  -H "Authorization: Bearer $ADMIN_TOKEN" || echo "{}")

if echo "$EXISTING_CLIENT" | jq -e '.clientId' > /dev/null 2>&1; then
  echo "⚠ Client '$CLIENT_ID' already exists"
  read -p "Do you want to update it with a new secret? (y/N) " -n 1 -r
  echo
  if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Aborted."
    exit 0
  fi
  
  # Update existing client
  echo "Updating client..."
  CREATE_RESPONSE=$(curl -s -X PUT "$OAUTH2_URL/api/admin/clients/$CLIENT_ID" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{
      \"clientId\": \"$CLIENT_ID\",
      \"clientName\": \"MCP Admin Client\",
      \"clientSecret\": \"$MCP_CLIENT_SECRET\",
      \"authorizationGrantTypes\": [\"client_credentials\"],
      \"clientAuthenticationMethods\": [\"client_secret_basic\"],
      \"redirectUris\": [],
      \"postLogoutRedirectUris\": [],
      \"scopes\": [\"admin:read\", \"admin:write\"],
      \"requireProofKey\": false,
      \"requireAuthorizationConsent\": false,
      \"enabled\": true,
      \"notes\": \"MCP server for OAuth2 management\"
    }")
  
  echo "✓ Client updated successfully"
else
  # Create new client
  echo "Creating MCP admin client..."
  CREATE_RESPONSE=$(curl -s -X POST "$OAUTH2_URL/api/admin/clients" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{
      \"clientId\": \"$CLIENT_ID\",
      \"clientName\": \"MCP Admin Client\",
      \"clientSecret\": \"$MCP_CLIENT_SECRET\",
      \"authorizationGrantTypes\": [\"client_credentials\"],
      \"clientAuthenticationMethods\": [\"client_secret_basic\"],
      \"redirectUris\": [],
      \"postLogoutRedirectUris\": [],
      \"scopes\": [\"admin:read\", \"admin:write\"],
      \"requireProofKey\": false,
      \"requireAuthorizationConsent\": false,
      \"enabled\": true,
      \"notes\": \"MCP server for OAuth2 management\"
    }")
  
  echo "✓ Client created successfully"
fi

# Verify client was created/updated
CREATED_CLIENT_ID=$(echo "$CREATE_RESPONSE" | jq -r '.clientId')
if [ "$CREATED_CLIENT_ID" != "$CLIENT_ID" ]; then
  echo "Error: Client creation failed"
  echo "Response: $CREATE_RESPONSE"
  exit 1
fi

# Test the client credentials
echo "Testing client credentials..."
TEST_RESPONSE=$(curl -s -X POST "$OAUTH2_URL/oauth2/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -u "$CLIENT_ID:$MCP_CLIENT_SECRET" \
  -d "grant_type=client_credentials" \
  -d "scope=admin:read admin:write")

TEST_TOKEN=$(echo "$TEST_RESPONSE" | jq -r '.access_token')

if [ -z "$TEST_TOKEN" ] || [ "$TEST_TOKEN" = "null" ]; then
  echo "Error: Client credentials test failed"
  echo "Response: $TEST_RESPONSE"
  exit 1
fi

echo "✓ Client credentials verified"

# Create Kubernetes secret
echo "Creating Kubernetes secret..."
kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -

kubectl create secret generic mcp-admin-client-secret \
  --from-literal=client-secret="$MCP_CLIENT_SECRET" \
  --from-literal=client-id="$CLIENT_ID" \
  --from-literal=server-url="$OAUTH2_URL" \
  -n "$NAMESPACE" \
  --dry-run=client -o yaml | kubectl apply -f -

echo "✓ Kubernetes secret created/updated"

# Save secret to file for local development
SECRET_FILE=".env.local"
cat > "$SECRET_FILE" << EOF
# MCP Admin Client Configuration
# Generated on $(date)
# DO NOT COMMIT THIS FILE

OAUTH2_SERVER_URL=$OAUTH2_URL
OAUTH2_CLIENT_ID=$CLIENT_ID
OAUTH2_CLIENT_SECRET=$MCP_CLIENT_SECRET
OAUTH2_SCOPES=admin:read admin:write
EOF

chmod 600 "$SECRET_FILE"
echo "✓ Local configuration saved to $SECRET_FILE"

# Test API access
echo "Testing API access..."
TEST_API=$(curl -s "$OAUTH2_URL/api/admin/clients" \
  -H "Authorization: Bearer $TEST_TOKEN")

CLIENT_COUNT=$(echo "$TEST_API" | jq 'length')
echo "✓ API access confirmed (found $CLIENT_COUNT clients)"

echo ""
echo "========================================="
echo "Setup Complete!"
echo "========================================="
echo ""
echo "Client ID: $CLIENT_ID"
echo "Kubernetes Secret: mcp-admin-client-secret (namespace: $NAMESPACE)"
echo "Local Config: $SECRET_FILE"
echo ""
echo "Next steps:"
echo "1. Build the MCP server: npm run build"
echo "2. Test locally: source $SECRET_FILE && npm start"
echo "3. Configure Claude Desktop or Cline with the client secret"
echo ""
echo "View client details at: $OAUTH2_URL/admin"
echo ""
