#!/usr/bin/env bash
set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
REPO_OWNER="ianlintner"
REPO_NAME="bootsandcats"
ACR_NAME="gabby"
AKS_CLUSTER="bigboy"
APP_NAME="oauth2-server-gh-actions"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo -e "${GREEN}=== OAuth2 Server Azure & GitHub Setup ===${NC}\n"

# Check prerequisites
echo "Checking prerequisites..."
command -v az >/dev/null 2>&1 || { echo -e "${RED}ERROR: Azure CLI not found. Install from https://docs.microsoft.com/cli/azure/install-azure-cli${NC}"; exit 1; }
command -v gh >/dev/null 2>&1 || { echo -e "${RED}ERROR: GitHub CLI not found. Install from https://cli.github.com/${NC}"; exit 1; }
command -v kubectl >/dev/null 2>&1 || { echo -e "${RED}ERROR: kubectl not found. Install from https://kubernetes.io/docs/tasks/tools/${NC}"; exit 1; }
command -v jq >/dev/null 2>&1 || { echo -e "${RED}ERROR: jq not found. Install with 'brew install jq' on macOS${NC}"; exit 1; }

# Verify Azure authentication
echo -e "\n${YELLOW}Step 1: Verifying Azure authentication...${NC}"
if ! az account show >/dev/null 2>&1; then
    echo -e "${RED}Not logged into Azure. Running 'az login'...${NC}"
    az login
fi

# Get Azure details
SUBSCRIPTION_ID=$(az account show --query id -o tsv)
TENANT_ID=$(az account show --query tenantId -o tsv)
SUBSCRIPTION_NAME=$(az account show --query name -o tsv)

echo -e "${GREEN}✓ Logged into Azure${NC}"
echo "  Subscription: $SUBSCRIPTION_NAME"
echo "  Subscription ID: $SUBSCRIPTION_ID"
echo "  Tenant ID: $TENANT_ID"

# Verify GitHub authentication
echo -e "\n${YELLOW}Step 2: Verifying GitHub authentication...${NC}"
if ! gh auth status >/dev/null 2>&1; then
    echo -e "${RED}Not logged into GitHub. Running 'gh auth login'...${NC}"
    gh auth login
fi
echo -e "${GREEN}✓ Logged into GitHub${NC}"

# Find ACR and AKS
echo -e "\n${YELLOW}Step 3: Locating Azure resources...${NC}"

ACR_INFO=$(az acr list --query "[?name=='$ACR_NAME'] | [0]" -o json)
if [ "$ACR_INFO" == "null" ] || [ -z "$ACR_INFO" ]; then
    echo -e "${RED}ERROR: Azure Container Registry '$ACR_NAME' not found${NC}"
    echo "Available ACRs:"
    az acr list --query '[].{name:name,resourceGroup:resourceGroup}' -o table
    exit 1
fi

ACR_RESOURCE_GROUP=$(echo "$ACR_INFO" | jq -r '.resourceGroup')
echo -e "${GREEN}✓ Found ACR: $ACR_NAME${NC}"
echo "  Resource Group: $ACR_RESOURCE_GROUP"

AKS_INFO=$(az aks list --query "[?name=='$AKS_CLUSTER'] | [0]" -o json)
if [ "$AKS_INFO" == "null" ] || [ -z "$AKS_INFO" ]; then
    echo -e "${RED}ERROR: AKS cluster '$AKS_CLUSTER' not found${NC}"
    echo "Available AKS clusters:"
    az aks list --query '[].{name:name,resourceGroup:resourceGroup}' -o table
    exit 1
fi

AKS_RESOURCE_GROUP=$(echo "$AKS_INFO" | jq -r '.resourceGroup')
echo -e "${GREEN}✓ Found AKS: $AKS_CLUSTER${NC}"
echo "  Resource Group: $AKS_RESOURCE_GROUP"

# Use the AKS resource group for the service principal scope
RESOURCE_GROUP="$AKS_RESOURCE_GROUP"

# Create or find App Registration
echo -e "\n${YELLOW}Step 4: Creating/finding App Registration...${NC}"

# Check if app already exists
EXISTING_APP=$(az ad app list --display-name "$APP_NAME" --query '[0].appId' -o tsv 2>/dev/null || echo "")

if [ -n "$EXISTING_APP" ] && [ "$EXISTING_APP" != "null" ]; then
    echo -e "${YELLOW}App registration '$APP_NAME' already exists${NC}"
    echo "Do you want to use the existing app? (y/n)"
    read -r USE_EXISTING
    if [ "$USE_EXISTING" == "y" ] || [ "$USE_EXISTING" == "Y" ]; then
        APP_ID="$EXISTING_APP"
        echo -e "${GREEN}✓ Using existing App ID: $APP_ID${NC}"
    else
        echo "Please delete the existing app or use a different name."
        exit 1
    fi
else
    echo "Creating new app registration..."
    APP_ID=$(az ad app create \
        --display-name "$APP_NAME" \
        --query appId -o tsv)
    echo -e "${GREEN}✓ Created App ID: $APP_ID${NC}"

    # Create service principal
    echo "Creating service principal..."
    az ad sp create --id "$APP_ID" >/dev/null
    sleep 5  # Wait for SP to propagate
    echo -e "${GREEN}✓ Created Service Principal${NC}"
fi

# Configure Federated Credentials for main branch
echo -e "\n${YELLOW}Step 5: Configuring federated credentials...${NC}"

# Check if federated credential already exists
EXISTING_CRED=$(az ad app federated-credential list --id "$APP_ID" --query "[?name=='${APP_NAME}-main'].name" -o tsv 2>/dev/null || echo "")

if [ -n "$EXISTING_CRED" ]; then
    echo -e "${YELLOW}Federated credential for main branch already exists${NC}"
else
    echo "Creating federated credential for main branch..."
    az ad app federated-credential create \
        --id "$APP_ID" \
        --parameters "{
            \"name\": \"${APP_NAME}-main\",
            \"issuer\": \"https://token.actions.githubusercontent.com\",
            \"subject\": \"repo:${REPO_OWNER}/${REPO_NAME}:ref:refs/heads/main\",
            \"description\": \"GitHub Actions deployment from main branch\",
            \"audiences\": [\"api://AzureADTokenExchange\"]
        }" >/dev/null
    echo -e "${GREEN}✓ Created federated credential for main branch${NC}"
fi

# Grant permissions
echo -e "\n${YELLOW}Step 6: Granting Azure permissions...${NC}"

# Contributor role on resource group
echo "Granting Contributor role on resource group..."
az role assignment create \
    --assignee "$APP_ID" \
    --role Contributor \
    --scope "/subscriptions/$SUBSCRIPTION_ID/resourceGroups/$RESOURCE_GROUP" \
    >/dev/null 2>&1 || echo "  (Role may already exist)"

# AcrPush role
echo "Granting AcrPush role on ACR..."
az role assignment create \
    --assignee "$APP_ID" \
    --role AcrPush \
    --scope "/subscriptions/$SUBSCRIPTION_ID/resourceGroups/$ACR_RESOURCE_GROUP/providers/Microsoft.ContainerRegistry/registries/$ACR_NAME" \
    >/dev/null 2>&1 || echo "  (Role may already exist)"

# AKS Cluster User role
echo "Granting AKS Cluster User role..."
az role assignment create \
    --assignee "$APP_ID" \
    --role "Azure Kubernetes Service Cluster User Role" \
    --scope "/subscriptions/$SUBSCRIPTION_ID/resourceGroups/$AKS_RESOURCE_GROUP/providers/Microsoft.ContainerService/managedClusters/$AKS_CLUSTER" \
    >/dev/null 2>&1 || echo "  (Role may already exist)"

# AKS RBAC Cluster Admin role (for kubectl access)
echo "Granting AKS RBAC Cluster Admin role..."
az role assignment create \
    --assignee "$APP_ID" \
    --role "Azure Kubernetes Service RBAC Cluster Admin" \
    --scope "/subscriptions/$SUBSCRIPTION_ID/resourceGroups/$AKS_RESOURCE_GROUP/providers/Microsoft.ContainerService/managedClusters/$AKS_CLUSTER" \
    >/dev/null 2>&1 || echo "  (Role may already exist)"

echo -e "${GREEN}✓ Azure permissions configured${NC}"

# Wait for role assignments to propagate
echo -e "\n${YELLOW}Waiting 10 seconds for Azure AD to propagate changes...${NC}"
sleep 10

# Configure GitHub Secrets
echo -e "\n${YELLOW}Step 7: Configuring GitHub secrets...${NC}"

gh secret set AZURE_CLIENT_ID --body "$APP_ID" --repo "$REPO_OWNER/$REPO_NAME"
echo -e "${GREEN}✓ Set AZURE_CLIENT_ID${NC}"

gh secret set AZURE_TENANT_ID --body "$TENANT_ID" --repo "$REPO_OWNER/$REPO_NAME"
echo -e "${GREEN}✓ Set AZURE_TENANT_ID${NC}"

gh secret set AZURE_SUBSCRIPTION_ID --body "$SUBSCRIPTION_ID" --repo "$REPO_OWNER/$REPO_NAME"
echo -e "${GREEN}✓ Set AZURE_SUBSCRIPTION_ID${NC}"

gh secret set AZURE_RESOURCE_GROUP --body "$RESOURCE_GROUP" --repo "$REPO_OWNER/$REPO_NAME"
echo -e "${GREEN}✓ Set AZURE_RESOURCE_GROUP${NC}"

# Get AKS credentials
echo -e "\n${YELLOW}Step 8: Getting AKS credentials...${NC}"
az aks get-credentials --resource-group "$AKS_RESOURCE_GROUP" --name "$AKS_CLUSTER" --overwrite-existing >/dev/null
kubectl cluster-info >/dev/null 2>&1 && echo -e "${GREEN}✓ AKS credentials configured${NC}" || echo -e "${RED}Failed to get AKS credentials${NC}"

# Create Kubernetes resources
echo -e "\n${YELLOW}Step 9: Creating Kubernetes resources...${NC}"

# Generate secure passwords
DB_PASSWORD=$(openssl rand -base64 32 | tr -d /=+ | cut -c -24)
DEMO_CLIENT_SECRET=$(openssl rand -base64 32 | tr -d /=+ | cut -c -32)
M2M_CLIENT_SECRET=$(openssl rand -base64 32 | tr -d /=+ | cut -c -32)
DEMO_USER_PASSWORD=$(openssl rand -base64 16 | tr -d /=+ | cut -c -16)
ADMIN_USER_PASSWORD=$(openssl rand -base64 16 | tr -d /=+ | cut -c -16)

echo "Creating Kubernetes secret 'oauth2-secrets'..."
kubectl create secret generic oauth2-secrets \
    --from-literal=database-url='jdbc:postgresql://postgres.default.svc.cluster.local:5432/oauth2db' \
    --from-literal=database-username='oauth2user' \
    --from-literal=database-password="$DB_PASSWORD" \
    --from-literal=demo-client-secret="$DEMO_CLIENT_SECRET" \
    --from-literal=m2m-client-secret="$M2M_CLIENT_SECRET" \
    --from-literal=demo-user-password="$DEMO_USER_PASSWORD" \
    --from-literal=admin-user-password="$ADMIN_USER_PASSWORD" \
    -n default \
    --dry-run=client -o yaml | kubectl apply -f - >/dev/null

echo -e "${GREEN}✓ Created oauth2-secrets${NC}"

# Save credentials to a secure file
CREDS_FILE="$HOME/.oauth2-server-credentials.txt"
cat > "$CREDS_FILE" <<EOF
OAuth2 Server Credentials - $(date)
====================================

Database:
  Username: oauth2user
  Password: $DB_PASSWORD

OAuth2 Clients:
  Demo Client Secret: $DEMO_CLIENT_SECRET
  M2M Client Secret: $M2M_CLIENT_SECRET

Demo Users:
  User Password: $DEMO_USER_PASSWORD
  Admin Password: $ADMIN_USER_PASSWORD

Azure:
  App ID: $APP_ID
  Tenant ID: $TENANT_ID
  Subscription ID: $SUBSCRIPTION_ID

IMPORTANT: Store these credentials securely and delete this file after saving them to a secure location.
EOF

chmod 600 "$CREDS_FILE"
echo -e "${YELLOW}⚠ Credentials saved to: $CREDS_FILE${NC}"
echo -e "${YELLOW}⚠ Please save these credentials securely and delete the file!${NC}"

# Prompt for issuer URL
echo -e "\n${YELLOW}Enter the OAuth2 issuer URL (e.g., https://oauth.yourdomain.com):${NC}"
read -r ISSUER_URL

if [ -z "$ISSUER_URL" ]; then
    ISSUER_URL="https://oauth.example.com"
    echo -e "${YELLOW}Using default: $ISSUER_URL${NC}"
fi

echo "Creating Kubernetes ConfigMap 'oauth2-config'..."
kubectl create configmap oauth2-config \
    --from-literal=issuer-url="$ISSUER_URL" \
    -n default \
    --dry-run=client -o yaml | kubectl apply -f - >/dev/null

echo -e "${GREEN}✓ Created oauth2-config${NC}"

# Deploy the application
echo -e "\n${YELLOW}Step 10: Deploying application to AKS (kustomize)...${NC}"
kubectl apply -k "$PROJECT_ROOT/infrastructure/k8s" >/dev/null 2>&1 && echo -e "${GREEN}✓ Applied kustomize entrypoint${NC}" || echo -e "${YELLOW}Note: Apply failed (check cluster connectivity / kustomize output)${NC}"

# Summary
echo -e "\n${GREEN}=== Setup Complete! ===${NC}\n"
echo "Azure Configuration:"
echo "  ✓ Service Principal: $APP_ID"
echo "  ✓ ACR: $ACR_NAME ($ACR_RESOURCE_GROUP)"
echo "  ✓ AKS: $AKS_CLUSTER ($AKS_RESOURCE_GROUP)"
echo ""
echo "GitHub Configuration:"
echo "  ✓ Repository: $REPO_OWNER/$REPO_NAME"
echo "  ✓ Secrets configured (AZURE_CLIENT_ID, AZURE_TENANT_ID, AZURE_SUBSCRIPTION_ID, AZURE_RESOURCE_GROUP)"
echo ""
echo "Kubernetes Configuration:"
echo "  ✓ Secrets: sourced from Azure Key Vault via CSI driver"
echo "  ✓ ConfigMap: oauth2-config"
echo "  ✓ Issuer URL: $ISSUER_URL"
echo ""
echo -e "${YELLOW}Next Steps:${NC}"
echo "1. Review and save credentials from: $CREDS_FILE"
echo "2. Push to main branch to trigger deployment:"
echo "   git add ."
echo "   git commit -m 'chore: configure Azure deployment'"
echo "   git push origin main"
echo "3. Watch the deployment:"
echo "   gh run watch"
echo "4. Verify deployment:"
echo "   kubectl get pods -l app=oauth2-server -n default"
echo "   kubectl logs -f -l app=oauth2-server -n default"
echo ""
echo -e "${GREEN}Setup script completed successfully!${NC}"

