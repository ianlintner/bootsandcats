#!/usr/bin/env bash
set -euo pipefail

NAMESPACE=${NAMESPACE:-default}

say() { printf "\n[%s] %s\n" "$(date +%H:%M:%S)" "$*"; }

say "Deleting profile-service resources (namespace: $NAMESPACE)"
kubectl delete -n "$NAMESPACE" \
  deployment/profile-service \
  service/profile-service \
  envoyfilter/profile-jwt-to-headers \
  requestauthentication/profile-jwt-auth \
  secretproviderclass/profile-service-secrets-provider \
  secret/profile-service-secrets \
  --ignore-not-found

say "Re-applying profile-service resources"
kubectl apply -n "$NAMESPACE" -f infrastructure/k8s/secrets/secret-provider-class-profile-service.yaml
kubectl apply -n "$NAMESPACE" -f infrastructure/k8s/apps/profile-service/profile-service-deployment.yaml
kubectl apply -n "$NAMESPACE" -f infrastructure/k8s/istio/requestauthentication-profile.yaml
kubectl apply -n "$NAMESPACE" -f infrastructure/k8s/istio/envoyfilter-profile-service-jwt-to-headers.yaml

say "Waiting for rollout"
kubectl rollout status -n "$NAMESPACE" deployment/profile-service --timeout=180s

say "Quick checks"
# Public pass-through
curl -fsS "https://profile.cat-herding.net/api/status" >/dev/null || true

say "Done"
