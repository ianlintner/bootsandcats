# Slop Detector Deployment Configuration

This directory contains the Kubernetes deployment manifests for the `slop-detector` service, configured to use OAuth2 authentication via the central OAuth2 Authorization Server.

## Overview

The `slop-detector` service is deployed with:
- **OAuth2 Client ID**: `slop-detector`
- **Public URL**: `https://slop-detector.cat-herding.net`
- **OAuth2 Callback**: `https://slop-detector.cat-herding.net/_oauth2/callback`
- **Istio Service Mesh**: OAuth2 Envoy filter for automatic token exchange
- **Azure Key Vault**: Client secrets managed via CSI Secret Store driver

## Files Generated

### Kubernetes Manifests
- `slop-detector-deployment.yaml` - Main deployment and service
- `kustomization.yaml` - Kustomize configuration

### Istio Configuration
- `envoyfilter-slop-detector-oauth2-exchange.yaml` - OAuth2 authorization code exchange
- `envoyfilter-slop-detector-jwt-to-headers.yaml` - JWT claims to HTTP headers
- `requestauthentication-slop-detector.yaml` - JWT validation rules
- `envoy-oauth2-sds-configmap-slop-detector.yaml` - Secret Distribution Service config

### Secrets Management
- `secret-provider-class-slop-detector.yaml` - Azure Key Vault integration

### OAuth2 Client Registration
Added to Flyway migrations:
- `V15__add_slop_detector_client.sql` - Initial client registration
- `V16__update_slop_detector_client_secret.sql` - Demo secret update

## Prerequisites

### 1. Azure Key Vault Secrets

Create the following secrets in Azure Key Vault (`inker-kv`):

```bash
# Client secret (used for OAuth2 token exchange)
az keyvault secret set --vault-name inker-kv \
  --name slop-detector-client-secret \
  --value "YOUR_STRONG_CLIENT_SECRET_HERE"

# HMAC secret (used for OAuth2 cookie signing)
az keyvault secret set --vault-name inker-kv \
  --name slop-detector-oauth-hmac-secret \
  --value "YOUR_STRONG_HMAC_SECRET_HERE"
```

**Security Notes**:
- Use cryptographically strong random values (32+ characters)
- Never commit these secrets to source control
- For production, use different secrets than demo values
- Consider using Azure Key Vault rotation policies

### 2. Container Image

Build and push the slop-detector image to Azure Container Registry:

```bash
# In the slop-detector repository
docker build -t gabby.azurecr.io/slop-detector:latest .
docker push gabby.azurecr.io/slop-detector:latest
```

Update the image tag in `slop-detector-deployment.yaml` if using a specific version.

### 3. Application Requirements

Your slop-detector application must:

1. **Health Endpoint**: Expose `/health` endpoint for liveness/readiness probes
   ```
   GET /health -> 200 OK
   ```

2. **Port**: Listen on port 8000 (or update the deployment manifest)

3. **OAuth2 Integration**: 
   - The Envoy filter handles OAuth2 automatically
   - Your app receives the JWT in the `Authorization: Bearer <token>` header
   - User claims are available as HTTP headers (see JWT to Headers filter)

4. **Public Paths** (Optional): If you have public endpoints that don't require auth, update the `pass_through_matcher` in `envoyfilter-slop-detector-oauth2-exchange.yaml`

## Deployment Steps

### Step 1: Copy Deployment Manifests

Copy the generated Kubernetes manifests to your slop-detector repository:

```bash
# From this repository (bootsandcats)
cp infrastructure/k8s/apps/slop-detector/slop-detector-deployment.yaml \
   <slop-detector-repo>/k8s/deployment.yaml
```

Or use the template and customize as needed for your application.

### Step 2: Update Application Configuration

Modify `slop-detector-deployment.yaml` in your repository:

```yaml
containers:
- name: slop-detector
  image: gabby.azurecr.io/slop-detector:YOUR_VERSION
  env:
  - name: YOUR_APP_ENV_VAR
    value: "your_value"
  # Add other app-specific environment variables
```

### Step 3: Configure OAuth2 Paths (Optional)

If your application has public endpoints that shouldn't require authentication, update the Envoy filter to add them to the pass-through list.

In this repository, edit:
`infrastructure/k8s/istio/envoyfilter-slop-detector-oauth2-exchange.yaml`

Add to `pass_through_matcher`:
```yaml
- name: ":path"
  prefix_match: "/public/"
- name: ":path"
  exact_match: "/your-health-endpoint"
```

### Step 4: DNS Configuration

Configure DNS to point to the Istio ingress gateway:

```bash
# Get the ingress gateway IP
kubectl get svc -n aks-istio-ingress

# Add DNS A record
slop-detector.cat-herding.net -> <INGRESS_IP>
```

### Step 5: Deploy

```bash
# Build and verify manifests
kustomize build infrastructure/k8s | kubectl diff -f -

# Apply the configuration
kustomize build infrastructure/k8s | kubectl apply -f -

# Check deployment status
kubectl get pods -l app=slop-detector
kubectl logs -l app=slop-detector -f
```

## OAuth2 Flow

1. **User visits**: `https://slop-detector.cat-herding.net`
2. **No session**: Envoy redirects to OAuth2 Authorization Server
3. **User authenticates**: At `https://oauth2.cat-herding.net`
4. **Callback**: OAuth2 server redirects to `https://slop-detector.cat-herding.net/_oauth2/callback?code=...`
5. **Token exchange**: Envoy exchanges authorization code for access token
6. **Session established**: Envoy stores token in secure cookies
7. **Subsequent requests**: Envoy adds `Authorization: Bearer <jwt>` header
8. **Application receives**: Authenticated JWT token with user claims

## Monitoring

### Logs
```bash
# Application logs
kubectl logs -l app=slop-detector -c slop-detector

# Istio sidecar logs
kubectl logs -l app=slop-detector -c istio-proxy

# Init container logs (SDS rendering)
kubectl logs -l app=slop-detector -c render-sds-config
```

### Metrics
OAuth2 metrics are exposed via Prometheus:
- Metric prefix: `slopdetector_oauth`
- Check `infrastructure/k8s/apps/monitoring/grafana/dashboards/oauth2-endpoints.json`

### Debugging OAuth2 Issues

```bash
# Check if secrets are mounted
kubectl exec -it <slop-detector-pod> -c slop-detector -- ls -la /mnt/secrets-store

# Check SDS config rendering
kubectl exec -it <slop-detector-pod> -c slop-detector -- ls -la /etc/istio/oauth2

# Test OAuth2 flow
curl -I https://slop-detector.cat-herding.net
# Should redirect to OAuth2 server or show authenticated response
```

## Security Considerations

1. **Secrets Management**
   - Client secrets stored in Azure Key Vault
   - Mounted via CSI Secret Store driver
   - Never logged or exposed

2. **Cookie Security**
   - Cookies use `SameSite=Lax`
   - Session cookies only (no persistent storage)
   - HMAC signed to prevent tampering

3. **JWT Validation**
   - JWTs verified against JWKS endpoint
   - Issuer validation: `https://oauth2.cat-herding.net`
   - Audience validation: `slop-detector`

4. **Network Security**
   - All traffic within cluster uses mTLS (Istio)
   - Token exchange uses internal service DNS
   - External traffic requires HTTPS

## Troubleshooting

### Pod fails to start
- Check if secrets exist in Key Vault
- Verify SecretProviderClass configuration
- Check init container logs

### OAuth2 redirects not working
- Verify redirect_uri matches in both Envoy config and OAuth2 server
- Check VirtualService routes traffic correctly
- Verify DNS resolution

### JWT validation fails
- Check RequestAuthentication configuration
- Verify JWKS endpoint is accessible
- Check JWT audience claim matches `slop-detector`

## References

- [OAuth2 Authorization Server Setup](../../docs/QUICK_START_EC_JWK.md)
- [Istio OAuth2 Filter Documentation](https://www.envoyproxy.io/docs/envoy/latest/api-v3/extensions/filters/http/oauth2/v3/oauth2.proto)
- [Azure Key Vault CSI Driver](https://azure.github.io/secrets-store-csi-driver-provider-azure/)
