#!/bin/bash
# Script to add MongoDB secrets to Azure Key Vault for profile-ui service
#
# Prerequisites:
# - Azure CLI logged in
# - Access to the Key Vault 'inker-kv'
#
# The MongoDB cluster connection string template:
# mongodb+srv://<user>:<password>@titan.mongocluster.cosmos.azure.com/?tls=true&authMechanism=SCRAM-SHA-256&retrywrites=false&maxIdleTimeMS=120000

set -e

KEYVAULT_NAME="inker-kv"
RESOURCE_GROUP="ai-chat-rg"

# MongoDB connection details
# Using the admin user for now - you may want to create a separate user for the service
MONGODB_USERNAME="${1:-ianlintner}"
MONGODB_DATABASE="profile-db"

echo "=== MongoDB Secrets Setup for Profile-UI ==="
echo ""
echo "Key Vault: $KEYVAULT_NAME"
echo "MongoDB Username: $MONGODB_USERNAME"
echo "MongoDB Database: $MONGODB_DATABASE"
echo ""

# Prompt for password if not provided
if [ -z "$2" ]; then
    echo -n "Enter MongoDB password for user '$MONGODB_USERNAME': "
    read -s MONGODB_PASSWORD
    echo ""
else
    MONGODB_PASSWORD="$2"
fi

# Construct the full connection URI
# Note: The connection string includes the database name
MONGODB_URI="mongodb+srv://${MONGODB_USERNAME}:${MONGODB_PASSWORD}@titan.mongocluster.cosmos.azure.com/${MONGODB_DATABASE}?tls=true&authMechanism=SCRAM-SHA-256&retrywrites=false&maxIdleTimeMS=120000"

echo ""
echo "Adding secrets to Key Vault..."

# Add MongoDB URI (full connection string with credentials)
echo "  - Adding profile-ui-mongodb-uri..."
az keyvault secret set \
    --vault-name "$KEYVAULT_NAME" \
    --name "profile-ui-mongodb-uri" \
    --value "$MONGODB_URI" \
    --output none

# Add MongoDB database name (for reference)
echo "  - Adding profile-ui-mongodb-database..."
az keyvault secret set \
    --vault-name "$KEYVAULT_NAME" \
    --name "profile-ui-mongodb-database" \
    --value "$MONGODB_DATABASE" \
    --output none

# Add MongoDB username (for reference/debugging)
echo "  - Adding profile-ui-mongodb-username..."
az keyvault secret set \
    --vault-name "$KEYVAULT_NAME" \
    --name "profile-ui-mongodb-username" \
    --value "$MONGODB_USERNAME" \
    --output none

# Add MongoDB password (separate secret for rotation)
echo "  - Adding profile-ui-mongodb-password..."
az keyvault secret set \
    --vault-name "$KEYVAULT_NAME" \
    --name "profile-ui-mongodb-password" \
    --value "$MONGODB_PASSWORD" \
    --output none

echo ""
echo "=== Secrets added successfully! ==="
echo ""
echo "Next steps:"
echo "1. Update infrastructure/k8s/secrets/secret-provider-class-profile-service.yaml to include the new secrets"
echo "2. Ensure infrastructure/k8s/profile-service-deployment.yaml uses the expected secret keys"
echo "3. Apply the Kubernetes manifests:"
echo "   kubectl apply -k infrastructure/k8s"
echo ""
echo "To verify secrets were added:"
echo "  az keyvault secret list --vault-name $KEYVAULT_NAME --query \"[?starts_with(name, 'profile-ui')].name\" -o table"
