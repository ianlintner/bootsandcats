# Slop Detector OAuth2 Client Setup - Summary

## Overview
Successfully created a new OAuth2 client configuration for **slop-detector** service.

- **Client ID**: `slop-detector`
- **URL**: `https://slop-detector.cat-herding.net`
- **OAuth2 Callback**: `https://slop-detector.cat-herding.net/_oauth2/callback`

## Files Created

### Kubernetes Manifests
```
infrastructure/k8s/apps/slop-detector/
├── README.md                          # Comprehensive deployment guide
├── kustomization.yaml                  # Updated with deployment resource
├── slop-detector-deployment.yaml      # Main deployment and service
└── slop-detector-oauth2-patch.yaml    # OAuth2 EnvoyFilter patch (auto-generated)
```

### Istio Service Mesh Configuration
```
infrastructure/k8s/istio/
├── envoyfilter-slop-detector-oauth2-exchange.yaml      # OAuth2 code exchange
├── envoyfilter-slop-detector-jwt-to-headers.yaml       # JWT claims to headers
├── requestauthentication-slop-detector.yaml            # JWT validation
├── envoy-oauth2-sds-configmap-slop-detector.yaml      # Secret Distribution Service
└── virtualservices.yaml                                # Updated with slop-detector route
```

### Secrets Management
```
infrastructure/k8s/secrets/
└── secret-provider-class-slop-detector.yaml    # Azure Key Vault CSI integration
```

### Database Migrations
```
infrastructure/k8s/apps/configs/flyway-migrations-configmap.yaml
```
Added:
- `V15__add_slop_detector_client.sql` - Client registration
- `V16__update_slop_detector_client_secret.sql` - Demo secret

### Scripts
```
scripts/
└── setup-slop-detector-secrets.sh      # Azure Key Vault setup script
```

## Kustomization Updates

Updated the following kustomization files to include slop-detector:

1. **apps/slop-detector/kustomization.yaml** - Added deployment resource
2. **istio/kustomization.yaml** - Added EnvoyFilters, RequestAuthentication, and SDS ConfigMap
3. **secrets/kustomization.yaml** - Added SecretProviderClass
4. **apps/kustomization.yaml** - Added slop-detector to apps list

## Next Steps

### 1. Set up Azure Key Vault Secrets
```bash
# Run the automated setup script
./scripts/setup-slop-detector-secrets.sh

# Or manually:
az keyvault secret set --vault-name inker-kv \
  --name slop-detector-client-secret \
  --value "$(openssl rand -base64 32)"

az keyvault secret set --vault-name inker-kv \
  --name slop-detector-oauth-hmac-secret \
  --value "$(openssl rand -base64 32)"
```

### 2. Update OAuth2 Server Database

The database migrations are already configured. When the oauth2-server pod restarts, Flyway will automatically apply:
- V15: Create slop-detector client with placeholder secret
- V16: Update to demo secret

For production, you should update the client secret to match what's in Key Vault:
```sql
UPDATE oauth2_registered_client
SET client_secret = '{bcrypt}$2b$10$...' -- Use bcrypt hash of the Key Vault secret
WHERE client_id = 'slop-detector';
```

### 3. Build and Deploy the Slop Detector Application

In the **slop-detector repository**:

```bash
# Build the Docker image
docker build -t gabby.azurecr.io/slop-detector:latest .

# Push to Azure Container Registry
docker push gabby.azurecr.io/slop-detector:latest

# Copy the deployment manifest
cp /path/to/bootsandcats/infrastructure/k8s/apps/slop-detector/slop-detector-deployment.yaml \
   ./k8s/deployment.yaml

# Customize as needed for your application
```

### 4. Deploy to Kubernetes

From the **bootsandcats repository**:

```bash
# Build and preview the manifests
kustomize build infrastructure/k8s > preview.yaml
less preview.yaml

# Apply to cluster
kubectl apply -k infrastructure/k8s

# Watch the deployment
kubectl get pods -l app=slop-detector -w
```

### 5. Configure DNS

Point the domain to the Istio ingress gateway:

```bash
# Get the ingress IP
kubectl get svc -n aks-istio-ingress istio-ingressgateway

# Add DNS A record
# slop-detector.cat-herding.net -> <INGRESS_IP>
```

### 6. Test the OAuth2 Flow

```bash
# Should redirect to OAuth2 login
curl -I https://slop-detector.cat-herding.net

# Test after authentication
# Visit in browser: https://slop-detector.cat-herding.net
```

## OAuth2 Configuration Details

### Authorization Endpoints
- **Authorization**: `https://oauth2.cat-herding.net/oauth2/authorize`
- **Token**: `http://oauth2-server.default.svc.cluster.local:9000/oauth2/token`
- **JWKS**: `http://oauth2-server.default.svc.cluster.local:9000/oauth2/jwks`

### Token Settings
- **Access Token TTL**: 3600s (1 hour)
- **Refresh Token TTL**: 86400s (24 hours)
- **Authorization Code TTL**: 300s (5 minutes)
- **JWT Algorithm**: ES256 (Elliptic Curve)
- **Token Format**: Self-contained JWT

### Scopes
- `openid` - OpenID Connect
- `profile` - User profile information
- `email` - User email address

### Cookies
- `_slopdetector_session` - Bearer token
- `_slopdetector_oauth_hmac` - HMAC signature
- `_slopdetector_oauth_expires` - Expiration time
- All cookies use `SameSite=Lax`

## Application Integration Guide

### Required Endpoints
Your slop-detector application must expose:
- `GET /health` - Health check endpoint (returns 200 OK)

### OAuth2 Integration
The Istio Envoy sidecar handles OAuth2 automatically:
1. User visits the app without a session
2. Envoy redirects to OAuth2 authorization server
3. User authenticates
4. Envoy exchanges authorization code for JWT
5. Subsequent requests include `Authorization: Bearer <jwt>` header

### JWT Claims Available
The JWT contains standard claims:
- `sub` - Subject (user ID)
- `iss` - Issuer (oauth2.cat-herding.net)
- `aud` - Audience (slop-detector)
- `exp` - Expiration time
- `iat` - Issued at time
- Custom claims as configured

### Public Endpoints (Optional)
If you need public endpoints that don't require authentication, update the EnvoyFilter:

Edit: `infrastructure/k8s/istio/envoyfilter-slop-detector-oauth2-exchange.yaml`

Add to `pass_through_matcher`:
```yaml
- name: ":path"
  prefix_match: "/public/"
```

## Deployment Template

The deployment is based on `github-review-service` with the following adaptations:

### Similarities
- Istio sidecar with OAuth2 Envoy filter
- Azure Key Vault CSI Secret Store driver
- Init container to render SDS configs
- Health probes on `/health` endpoint
- Spot instance toleration

### Differences
- **Service name**: slop-detector
- **Public URL**: slop-detector.cat-herding.net
- **Client ID**: slop-detector
- **Cookie prefix**: slopdetector
- **Environment variables**: Customizable per app needs

## Monitoring

### Prometheus Metrics
OAuth2 metrics are available with prefix: `slopdetector_oauth`

### Grafana Dashboard
Update the OAuth2 endpoints dashboard at:
`infrastructure/k8s/apps/monitoring/grafana/dashboards/oauth2-endpoints.json`

Add panels for slop-detector metrics.

### Logs
```bash
# Application logs
kubectl logs -l app=slop-detector -c slop-detector

# Istio proxy logs (OAuth2 filter)
kubectl logs -l app=slop-detector -c istio-proxy

# Init container (secret rendering)
kubectl logs -l app=slop-detector -c render-sds-config
```

## Troubleshooting

### Common Issues

**Pod won't start - secrets not found**
```bash
# Verify secrets exist in Key Vault
az keyvault secret show --vault-name inker-kv --name slop-detector-client-secret

# Check SecretProviderClass
kubectl describe secretproviderclass slop-detector-secrets-provider

# Check pod events
kubectl describe pod <slop-detector-pod>
```

**OAuth2 redirect loop**
- Verify redirect_uri in EnvoyFilter matches OAuth2 server registration
- Check VirtualService routes traffic correctly
- Verify DNS resolves to ingress gateway

**JWT validation fails**
- Check RequestAuthentication issuer matches OAuth2 server
- Verify JWKS endpoint is accessible from the pod
- Check JWT audience claim includes "slop-detector"

**Secrets not updating**
- Delete and recreate the pod to remount secrets
- Check CSI driver logs: `kubectl logs -n kube-system -l app=secrets-store-csi-driver`

## Security Checklist

- [ ] Strong client secret generated and stored in Key Vault
- [ ] Strong HMAC secret generated and stored in Key Vault
- [ ] Secrets never committed to source control
- [ ] Database client secret uses bcrypt encoding
- [ ] DNS configured with HTTPS
- [ ] Health endpoints don't expose sensitive information
- [ ] Istio mTLS enabled for internal traffic
- [ ] Container image scanned for vulnerabilities
- [ ] Resource limits configured appropriately

## References

- [Deployment README](infrastructure/k8s/apps/slop-detector/README.md) - Detailed deployment guide
- [OAuth2 Setup Guide](docs/QUICK_START_EC_JWK.md) - OAuth2 server configuration
- [Istio OAuth2 Filter](https://www.envoyproxy.io/docs/envoy/latest/api-v3/extensions/filters/http/oauth2/v3/oauth2.proto) - Envoy documentation
- [Azure CSI Driver](https://azure.github.io/secrets-store-csi-driver-provider-azure/) - Secrets management

## Script Generated By
```bash
./scripts/add-oauth2-wrapper.sh slop-detector slop-detector slopdetector
```

Run this command to regenerate the OAuth2 configuration if needed.
