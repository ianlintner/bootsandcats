# Deployment Guide Retired

This page has been retired. The platform now deploys exclusively to Azure Kubernetes Service (AKS) with Istio/Envoy filters and Azure managed services (PostgreSQL Flexible Server, Azure Cache for Redis, Key Vault via CSI, Azure Monitor, and Azure Front Door/Application Gateway).

Please use the canonical guide: [Azure AKS Deployment](azure.md).

## Deployment Script

```bash
#!/bin/bash
# deploy-gcp.sh

set -e

NAMESPACE="oauth2-system"

echo "Creating namespace..."
kubectl create namespace $NAMESPACE --dry-run=client -o yaml | kubectl apply -f -

echo "Creating service account..."
envsubst < serviceaccount.yaml | kubectl apply -f -

echo "Applying ConfigMaps..."
kubectl apply -f configmap.yaml

# Deployment Guide Retired

This page has been retired. The platform now deploys exclusively to Azure Kubernetes Service (AKS) with Istio/Envoy filters and Azure managed services (PostgreSQL Flexible Server, Azure Cache for Redis, Key Vault via CSI, Azure Monitor, and Azure Front Door/Application Gateway).

Please use the canonical guide: [Azure AKS Deployment](azure.md).

