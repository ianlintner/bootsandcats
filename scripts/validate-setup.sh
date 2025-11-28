#!/usr/bin/env bash
# Quick validation script to check if setup is complete

echo "=== Azure & GitHub Setup Validation ==="
echo ""

echo "1. Checking Azure CLI..."
if command -v az >/dev/null 2>&1; then
    echo "   ✓ Azure CLI installed"
    if az account show >/dev/null 2>&1; then
        echo "   ✓ Logged into Azure"
        az account show --query '{subscription:name,tenant:tenantId}' -o table
    else
        echo "   ✗ Not logged into Azure (run: az login)"
    fi
else
    echo "   ✗ Azure CLI not found"
fi

echo ""
echo "2. Checking GitHub CLI..."
if command -v gh >/dev/null 2>&1; then
    echo "   ✓ GitHub CLI installed"
    if gh auth status >/dev/null 2>&1; then
        echo "   ✓ Logged into GitHub"
    else
        echo "   ✗ Not logged into GitHub (run: gh auth login)"
    fi
else
    echo "   ✗ GitHub CLI not found"
fi

echo ""
echo "3. Checking kubectl..."
if command -v kubectl >/dev/null 2>&1; then
    echo "   ✓ kubectl installed"
    if kubectl cluster-info >/dev/null 2>&1; then
        echo "   ✓ Connected to cluster"
        kubectl config current-context
    else
        echo "   ✗ Not connected to cluster"
    fi
else
    echo "   ✗ kubectl not found"
fi

echo ""
echo "4. Checking GitHub Secrets..."
if gh secret list -R ianlintner/bootsandcats 2>/dev/null | grep -q "AZURE_CLIENT_ID"; then
    echo "   ✓ AZURE_CLIENT_ID configured"
else
    echo "   ✗ AZURE_CLIENT_ID not configured"
fi

if gh secret list -R ianlintner/bootsandcats 2>/dev/null | grep -q "AZURE_TENANT_ID"; then
    echo "   ✓ AZURE_TENANT_ID configured"
else
    echo "   ✗ AZURE_TENANT_ID not configured"
fi

if gh secret list -R ianlintner/bootsandcats 2>/dev/null | grep -q "AZURE_SUBSCRIPTION_ID"; then
    echo "   ✓ AZURE_SUBSCRIPTION_ID configured"
else
    echo "   ✗ AZURE_SUBSCRIPTION_ID not configured"
fi

if gh secret list -R ianlintner/bootsandcats 2>/dev/null | grep -q "AZURE_RESOURCE_GROUP"; then
    echo "   ✓ AZURE_RESOURCE_GROUP configured"
else
    echo "   ✗ AZURE_RESOURCE_GROUP not configured"
fi

echo ""
echo "5. Checking Kubernetes Resources..."
if kubectl get secret oauth2-secrets -n default >/dev/null 2>&1; then
    echo "   ✓ Secret 'oauth2-secrets' exists"
else
    echo "   ✗ Secret 'oauth2-secrets' not found"
fi

if kubectl get configmap oauth2-config -n default >/dev/null 2>&1; then
    echo "   ✓ ConfigMap 'oauth2-config' exists"
    kubectl get configmap oauth2-config -n default -o jsonpath='{.data.issuer-url}' 2>/dev/null && echo ""
else
    echo "   ✗ ConfigMap 'oauth2-config' not found"
fi

if kubectl get deployment oauth2-server -n default >/dev/null 2>&1; then
    echo "   ✓ Deployment 'oauth2-server' exists"
    kubectl get deployment oauth2-server -n default -o jsonpath='{.status.readyReplicas}/{.spec.replicas}' 2>/dev/null && echo " replicas ready"
else
    echo "   ℹ Deployment 'oauth2-server' not found (will be created on first CI run)"
fi

echo ""
echo "=== Validation Complete ==="

