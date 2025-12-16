# Kubernetes Setup for MCP Admin Client

This document describes how to create the admin client and deploy the MCP server in Kubernetes.

## Prerequisites

- Access to the OAuth2 server admin console or API
- `kubectl` configured for your cluster
- Admin credentials for the OAuth2 server

## Step 1: Create the Admin Client

### Option A: Using the Admin Console

1. Navigate to https://oauth2.cat-herding.net/admin
2. Log in with admin credentials (username: `admin`, password: `admin`)
3. Go to the "Clients" tab
4. Click "New Client" and configure:
   - **Client ID**: `mcp-admin-client`
   - **Client Name**: `MCP Admin Client`
   - **Client Secret**: Generate a secure random string (save this!)
   - **Grant Types**: `client_credentials`
   - **Auth Methods**: `client_secret_basic`
   - **Redirect URIs**: (leave empty)
   - **Post-Logout Redirect URIs**: (leave empty)
   - **Scopes**: `admin:read`, `admin:write`
   - **Require PKCE**: No
   - **Require Consent**: No
   - **Enabled**: Yes
   - **Notes**: `MCP server for OAuth2 management`

### Option B: Using the Setup Script

Create a script to register the client:

```bash
#!/bin/bash
# File: scripts/setup-mcp-admin-client.sh

set -e

OAUTH2_URL="${OAUTH2_URL:-https://oauth2.cat-herding.net}"
ADMIN_USER="${ADMIN_USER:-admin}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin}"

# Generate a secure random secret
MCP_CLIENT_SECRET=$(openssl rand -base64 32)

echo "Getting admin access token..."
ADMIN_TOKEN=$(curl -s -X POST "$OAUTH2_URL/oauth2/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -u "$ADMIN_USER:$ADMIN_PASSWORD" \
  -d "grant_type=client_credentials" \
  -d "scope=admin:read admin:write" | jq -r '.access_token')

if [ -z "$ADMIN_TOKEN" ] || [ "$ADMIN_TOKEN" = "null" ]; then
  echo "Error: Failed to get admin token"
  exit 1
fi

echo "Creating MCP admin client..."
curl -X POST "$OAUTH2_URL/api/admin/clients" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"clientId\": \"mcp-admin-client\",
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
  }"

echo ""
echo "MCP Admin Client created successfully!"
echo ""
echo "Client Secret (save this securely):"
echo "$MCP_CLIENT_SECRET"
echo ""
echo "Create Kubernetes secret with:"
echo "kubectl create secret generic mcp-admin-client-secret \\"
echo "  --from-literal=client-secret='$MCP_CLIENT_SECRET' \\"
echo "  -n oauth2-system"
```

Make it executable and run:

```bash
chmod +x scripts/setup-mcp-admin-client.sh
./scripts/setup-mcp-admin-client.sh
```

## Step 2: Create Kubernetes Secret

Store the client secret in Kubernetes:

```bash
# Replace YOUR_CLIENT_SECRET with the secret from Step 1
kubectl create secret generic mcp-admin-client-secret \
  --from-literal=client-secret='YOUR_CLIENT_SECRET' \
  -n oauth2-system
```

Or create a secret YAML file:

```yaml
# File: infrastructure/k8s/secrets/mcp-admin-client-secret.yaml
apiVersion: v1
kind: Secret
metadata:
  name: mcp-admin-client-secret
  namespace: oauth2-system
type: Opaque
stringData:
  client-secret: YOUR_CLIENT_SECRET_HERE
```

Apply it:

```bash
kubectl apply -f infrastructure/k8s/secrets/mcp-admin-client-secret.yaml
```

## Step 3: Verify the Setup

Test the client credentials:

```bash
#!/bin/bash
# File: scripts/test-mcp-client.sh

OAUTH2_URL="${OAUTH2_URL:-https://oauth2.cat-herding.net}"
CLIENT_ID="mcp-admin-client"
CLIENT_SECRET=$(kubectl get secret mcp-admin-client-secret -n oauth2-system -o jsonpath='{.data.client-secret}' | base64 -d)

echo "Testing MCP admin client credentials..."
RESPONSE=$(curl -s -X POST "$OAUTH2_URL/oauth2/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -u "$CLIENT_ID:$CLIENT_SECRET" \
  -d "grant_type=client_credentials" \
  -d "scope=admin:read admin:write")

ACCESS_TOKEN=$(echo "$RESPONSE" | jq -r '.access_token')

if [ -z "$ACCESS_TOKEN" ] || [ "$ACCESS_TOKEN" = "null" ]; then
  echo "Error: Failed to get access token"
  echo "Response: $RESPONSE"
  exit 1
fi

echo "Success! Got access token."
echo "Testing API access..."

curl -s "$OAUTH2_URL/api/admin/clients" \
  -H "Authorization: Bearer $ACCESS_TOKEN" | jq '.[0:3]'

echo ""
echo "MCP admin client is working correctly!"
```

## Step 4: Update the Admin Client (if needed)

To rotate the secret or update configuration:

```bash
# Generate new secret
NEW_SECRET=$(openssl rand -base64 32)

# Get admin token
ADMIN_TOKEN=$(curl -s -X POST https://oauth2.cat-herding.net/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -u "admin:admin" \
  -d "grant_type=client_credentials" \
  -d "scope=admin:read admin:write" | jq -r '.access_token')

# Update client
curl -X PUT https://oauth2.cat-herding.net/api/admin/clients/mcp-admin-client \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"clientId\": \"mcp-admin-client\",
    \"clientName\": \"MCP Admin Client\",
    \"clientSecret\": \"$NEW_SECRET\",
    \"authorizationGrantTypes\": [\"client_credentials\"],
    \"clientAuthenticationMethods\": [\"client_secret_basic\"],
    \"redirectUris\": [],
    \"postLogoutRedirectUris\": [],
    \"scopes\": [\"admin:read\", \"admin:write\"],
    \"requireProofKey\": false,
    \"requireAuthorizationConsent\": false,
    \"enabled\": true,
    \"notes\": \"MCP server for OAuth2 management\"
  }"

# Update Kubernetes secret
kubectl create secret generic mcp-admin-client-secret \
  --from-literal=client-secret="$NEW_SECRET" \
  -n oauth2-system \
  --dry-run=client -o yaml | kubectl apply -f -

echo "Client secret rotated successfully!"
```

## Using in Local Development

For local MCP server usage (Claude Desktop, Cline, etc.):

```bash
# Export the secret to your environment
export OAUTH2_CLIENT_SECRET=$(kubectl get secret mcp-admin-client-secret -n oauth2-system -o jsonpath='{.data.client-secret}' | base64 -d)

# Run the MCP server
cd mcp-oauth2-admin
npm start
```

Or add to your shell profile:

```bash
# ~/.zshrc or ~/.bashrc
export OAUTH2_CLIENT_SECRET=$(kubectl get secret mcp-admin-client-secret -n oauth2-system -o jsonpath='{.data.client-secret}' | base64 -d 2>/dev/null)
```

## Security Best Practices

1. **Never commit secrets**: Use `.gitignore` to exclude secret files
2. **Use Sealed Secrets**: For GitOps workflows, use sealed-secrets or external-secrets
3. **Rotate regularly**: Set up a schedule to rotate the client secret
4. **Audit access**: Monitor the audit logs for MCP client activity
5. **Limit permissions**: Only grant `admin:read` and `admin:write`, nothing more
6. **Use namespaces**: Keep the secret in the `oauth2-system` namespace

## Troubleshooting

### Secret Not Found

```bash
# List secrets in the namespace
kubectl get secrets -n oauth2-system

# Describe the secret
kubectl describe secret mcp-admin-client-secret -n oauth2-system
```

### Cannot Get Access Token

```bash
# Check client exists
curl -s https://oauth2.cat-herding.net/api/admin/clients/mcp-admin-client \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq

# Check client is enabled
# enabled should be true
```

### Permission Denied

```bash
# Verify scopes
curl -s https://oauth2.cat-herding.net/oauth2/introspect \
  -u "mcp-admin-client:$CLIENT_SECRET" \
  -d "token=$ACCESS_TOKEN" | jq

# Should show admin:read and admin:write in scope
```

## Monitoring

Set up alerts for:

1. Failed authentication attempts for `mcp-admin-client`
2. Unusual activity patterns
3. Secret rotation schedule

Query audit logs:

```bash
# Get audit events for MCP client
ACCESS_TOKEN=$(# ... get token ...)
curl "https://oauth2.cat-herding.net/api/audit/client/mcp-admin-client?page=0&size=20" \
  -H "Authorization: Bearer $ACCESS_TOKEN" | jq
```
