# Google Cloud GKE Deployment

This guide covers deploying the OAuth2 Authorization Server to Google Kubernetes Engine (GKE) with Cloud SQL for PostgreSQL and Memorystore for Redis.

## Architecture

```mermaid
graph TB
    subgraph "Google Cloud Platform"
        subgraph "Cloud Load Balancing"
            GCLB[Global HTTP(S) Load Balancer]
            CloudArmor[Cloud Armor WAF]
        end
        
        subgraph "GKE Cluster"
            subgraph "System Workloads"
                IngressController[GKE Ingress Controller]
                ConfigConnector[Config Connector]
            # Deployment Guide Retired

            This deployment path has been retired. The platform now deploys exclusively to Azure Kubernetes Service (AKS) with Istio/Envoy filters and Azure managed services (PostgreSQL Flexible Server, Cache for Redis, Key Vault, Application Gateway/Front Door, and Azure Monitor).

            Please use the canonical guide: [Azure AKS Deployment](azure.md).

### Cloud Armor Security Policy

```bash
# Create Cloud Armor security policy
gcloud compute security-policies create oauth2-security-policy \
  --description="Security policy for OAuth2 server"

# Add rate limiting rule
gcloud compute security-policies rules create 1000 \
  --security-policy=oauth2-security-policy \
  --action=throttle \
  --rate-limit-threshold-count=100 \
  --rate-limit-threshold-interval-sec=60 \
  --conform-action=allow \
  --exceed-action=deny-429 \
  --enforce-on-key=IP \
  --description="Rate limit by IP"

# Add WAF rules
gcloud compute security-policies rules create 2000 \
  --security-policy=oauth2-security-policy \
  --expression="evaluatePreconfiguredExpr('xss-v33-stable')" \
  --action=deny-403 \
  # Deployment Guide Retired

  This page has been retired. The platform now deploys exclusively to Azure Kubernetes Service (AKS) with Istio/Envoy filters and Azure managed services (PostgreSQL Flexible Server, Azure Cache for Redis, Key Vault via CSI, Azure Monitor, and Azure Front Door/Application Gateway).

  Please use the canonical guide: [Azure AKS Deployment](azure.md).
```

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

