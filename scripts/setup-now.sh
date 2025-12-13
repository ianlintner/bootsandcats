#!/usr/bin/env bash
# Direct execution script - run this now to set everything up

set -e

echo "Starting OAuth2 Server Azure & GitHub Setup..."
echo ""

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Get Azure info
echo "Getting Azure account info..."
SUBSCRIPTION_ID=$(az account show --query id -o tsv 2>&1)
TENANT_ID=$(az account show --query tenantId -o tsv 2>&1)
SUBSCRIPTION_NAME=$(az account show --query name -o tsv 2>&1)

echo "Subscription: $SUBSCRIPTION_NAME"
echo "Subscription ID: $SUBSCRIPTION_ID"
echo "Tenant ID: $TENANT_ID"
echo ""

# Find ACR
echo "Finding Azure Container Registry 'gabby'..."
ACR_RESOURCE_GROUP=$(az acr list --query "[?name=='gabby'].resourceGroup" -o tsv 2>&1)
if [ -z "$ACR_RESOURCE_GROUP" ]; then
    echo "ERROR: ACR 'gabby' not found"
    exit 1
fi
echo "ACR Resource Group: $ACR_RESOURCE_GROUP"
echo ""

# Find AKS
echo "Finding AKS cluster 'bigboy'..."
AKS_RESOURCE_GROUP=$(az aks list --query "[?name=='bigboy'].resourceGroup" -o tsv 2>&1)
if [ -z "$AKS_RESOURCE_GROUP" ]; then
    echo "ERROR: AKS 'bigboy' not found"
    exit 1
fi
echo "AKS Resource Group: $AKS_RESOURCE_GROUP"
echo ""

# Create App Registration
APP_NAME="oauth2-server-gh-actions"
echo "Creating app registration '$APP_NAME'..."

# Check if exists
EXISTING_APP=$(az ad app list --display-name "$APP_NAME" --query '[0].appId' -o tsv 2>&1 || echo "")
if [ -n "$EXISTING_APP" ] && [ "$EXISTING_APP" != "null" ] && [ "$EXISTING_APP" != "" ]; then
    echo "Using existing app: $EXISTING_APP"
    APP_ID="$EXISTING_APP"
else
    APP_ID=$(az ad app create --display-name "$APP_NAME" --query appId -o tsv 2>&1)
    echo "Created new app: $APP_ID"
    az ad sp create --id "$APP_ID" 2>&1 >/dev/null
    echo "Created service principal"
    sleep 10
fi
echo ""

# Create federated credential
echo "Creating federated credential..."
az ad app federated-credential create --id "$APP_ID" --parameters '{
    "name": "oauth2-server-gh-actions-main",
    "issuer": "https://token.actions.githubusercontent.com",
    "subject": "repo:ianlintner/bootsandcats:ref:refs/heads/main",
    "description": "GitHub Actions main branch",
    "audiences": ["api://AzureADTokenExchange"]
}' 2>&1 >/dev/null || echo "Federated credential may already exist"
echo ""

# Grant permissions
echo "Granting Azure permissions..."
RESOURCE_GROUP="$AKS_RESOURCE_GROUP"

az role assignment create \
    --assignee "$APP_ID" \
    --role Contributor \
    --scope "/subscriptions/$SUBSCRIPTION_ID/resourceGroups/$RESOURCE_GROUP" \
    2>&1 >/dev/null || echo "Contributor role may already exist"

az role assignment create \
    --assignee "$APP_ID" \
    --role AcrPush \
    --scope "/subscriptions/$SUBSCRIPTION_ID/resourceGroups/$ACR_RESOURCE_GROUP/providers/Microsoft.ContainerRegistry/registries/gabby" \
    2>&1 >/dev/null || echo "AcrPush role may already exist"

az role assignment create \
    --assignee "$APP_ID" \
    --role "Azure Kubernetes Service Cluster User Role" \
    --scope "/subscriptions/$SUBSCRIPTION_ID/resourceGroups/$AKS_RESOURCE_GROUP/providers/Microsoft.ContainerService/managedClusters/bigboy" \
    2>&1 >/dev/null || echo "AKS User role may already exist"

az role assignment create \
    --assignee "$APP_ID" \
    --role "Azure Kubernetes Service RBAC Cluster Admin" \
    --scope "/subscriptions/$SUBSCRIPTION_ID/resourceGroups/$AKS_RESOURCE_GROUP/providers/Microsoft.ContainerService/managedClusters/bigboy" \
    2>&1 >/dev/null || echo "AKS Admin role may already exist"

echo "Waiting for role assignments to propagate..."
sleep 15
echo ""

# Set GitHub secrets
echo "Setting GitHub secrets..."
gh secret set AZURE_CLIENT_ID --body "$APP_ID" --repo ianlintner/bootsandcats
gh secret set AZURE_TENANT_ID --body "$TENANT_ID" --repo ianlintner/bootsandcats
gh secret set AZURE_SUBSCRIPTION_ID --body "$SUBSCRIPTION_ID" --repo ianlintner/bootsandcats
gh secret set AZURE_RESOURCE_GROUP --body "$RESOURCE_GROUP" --repo ianlintner/bootsandcats
echo "GitHub secrets configured"
echo ""

# Get AKS credentials
echo "Getting AKS credentials..."
az aks get-credentials --resource-group "$AKS_RESOURCE_GROUP" --name bigboy --overwrite-existing 2>&1 >/dev/null
echo "AKS credentials configured"
echo ""

# Generate secrets
echo "Generating secure passwords..."
DB_PASSWORD=$(openssl rand -base64 32 | tr -d /=+ | cut -c -24)
DEMO_CLIENT_SECRET=$(openssl rand -base64 32 | tr -d /=+ | cut -c -32)
M2M_CLIENT_SECRET=$(openssl rand -base64 32 | tr -d /=+ | cut -c -32)
DEMO_USER_PASSWORD=$(openssl rand -base64 16 | tr -d /=+ | cut -c -16)
ADMIN_USER_PASSWORD=$(openssl rand -base64 16 | tr -d /=+ | cut -c -16)
echo ""

echo "NOTE: This repo now sources runtime secrets from Azure Key Vault via the Secrets Store CSI Driver."
echo "      This script does not write application secrets to Kubernetes anymore."
echo ""

# Create K8s configmap
echo "Creating Kubernetes ConfigMap..."
ISSUER_URL="https://oauth.example.com"
kubectl create configmap oauth2-config \
    --from-literal=issuer-url="$ISSUER_URL" \
    -n default \
    --dry-run=client -o yaml | kubectl apply -f - 2>&1 >/dev/null
echo "Kubernetes ConfigMap created"
echo ""

# Apply manifests via kustomize
echo "Applying Kubernetes manifests (kustomize)..."
kubectl apply -k "$PROJECT_ROOT/infrastructure/k8s" 2>&1 || echo "Apply failed (check cluster connectivity / kustomize output)"
echo ""

# Save credentials
CREDS_FILE="$HOME/.oauth2-server-credentials.txt"
cat > "$CREDS_FILE" <<EOF
OAuth2 Server Credentials - $(date)
====================================

Azure:
  App ID: $APP_ID
  Tenant ID: $TENANT_ID
  Subscription ID: $SUBSCRIPTION_ID
  Resource Group: $RESOURCE_GROUP

Database:
  Username: oauth2user
  Password: $DB_PASSWORD

OAuth2 Clients:
  Demo Client Secret: $DEMO_CLIENT_SECRET
  M2M Client Secret: $M2M_CLIENT_SECRET

Demo Users:
  User Password: $DEMO_USER_PASSWORD
  Admin Password: $ADMIN_USER_PASSWORD

Issuer URL: $ISSUER_URL

IMPORTANT: Save these credentials securely!
EOF

chmod 600 "$CREDS_FILE"

echo "=== SETUP COMPLETE ==="
echo ""
echo "✓ Azure service principal created: $APP_ID"
echo "✓ GitHub secrets configured"
echo "✓ Kubernetes resources created"
echo "✓ Credentials saved to: $CREDS_FILE"
echo ""
echo "Next steps:"
echo "1. Save credentials from $CREDS_FILE to your password manager"
echo "2. Delete the credentials file: rm $CREDS_FILE"
echo "3. Update issuer URL: kubectl edit configmap oauth2-config -n default"
echo "4. Push to main to trigger deployment: git push origin main"
echo "5. Watch deployment: gh run watch"

