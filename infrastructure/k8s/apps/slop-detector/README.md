# Slop Detector Deployment Configuration

This directory contains the Kubernetes deployment manifests for the `slop-detector` service.

Interactive OAuth2 login is enforced at the **Istio ingress gateway** using a **single unified OAuth2 client** (`secure-subdomain-client`). Individual apps (including `slop-detector`) **do not** manage their own OAuth2 client secrets for browser login.

## Overview

`slop-detector` is deployed with:

- **Public URL**: `https://slop-detector.cat-herding.net`
- **Auth enforcement**: Istio ingress gateway (Envoy OAuth2 filter)
- **Callback path** (handled at gateway): `/_oauth2/callback`
- **Session cookies**: scoped to `.cat-herding.net` to support SSO across subdomains

What this means for the app:

- No per-app OAuth2 client secret, no per-app SDS rendering initContainer, no per-app Key Vault OAuth2 secrets.
- The app receives requests with `Authorization: Bearer <jwt>` when authenticated (plus any optional “JWT to headers” mapping if enabled in Istio).

## What’s in this directory

- `slop-detector-deployment.yaml` – Deployment + Service
- `kustomization.yaml` – Kustomize wiring

Istio OAuth2 configuration lives centrally under `infrastructure/k8s/istio/` (gateway filter). This app directory should not need per-app OAuth2 exchange filters.

## Prerequisites

### 1. Gateway OAuth2 is installed and configured

The ingress gateway must have the OAuth2 filter configured (unified client, cookie domain, callback path, etc.). See:

- `docs/SECURE_SUBDOMAIN_OAUTH2.md`

### 2. `slop-detector.cat-herding.net` is protected

OAuth2 enforcement is **default-on** at the gateway for `*.cat-herding.net` and subdomains.

### 3. DNS points at the ingress gateway

Configure DNS to point to the Istio ingress gateway IP:

```bash
# Get the ingress gateway IP
kubectl get svc -n aks-istio-ingress

# Add DNS A record
slop-detector.cat-herding.net -> <INGRESS_IP>
```

## Application requirements

Your `slop-detector` application should:

1. Expose `/health` for liveness/readiness probes.
2. Listen on port 8000 (or update `slop-detector-deployment.yaml`).
3. Treat authentication as upstream-provided:
   - Expect the JWT in `Authorization: Bearer <token>` when requests are authenticated.
   - Do not perform browser login redirects inside the app.

## Deployment steps

### Step 1: Build and publish the image

Build and push the slop-detector image to Azure Container Registry (or your registry of choice):

```bash
# In the slop-detector repository
docker build -t gabby.azurecr.io/slop-detector:latest .
docker push gabby.azurecr.io/slop-detector:latest
```

### Step 2: Update the deployment manifest

Modify `slop-detector-deployment.yaml` to point at your image tag and add any application-specific env vars.

### Step 3: Apply

```bash
# Build and verify manifests
kustomize build infrastructure/k8s | kubectl diff -f -

# Apply the configuration
kustomize build infrastructure/k8s | kubectl apply -f -

# Check deployment status
kubectl get pods -l app=slop-detector
kubectl logs -l app=slop-detector -f
```

## OAuth2 flow (gateway enforced)

1. **User visits**: `https://slop-detector.cat-herding.net`
2. **No session**: ingress gateway redirects to `https://oauth2.cat-herding.net`
3. **Callback**: user is redirected back to `https://slop-detector.cat-herding.net/_oauth2/callback?code=...`
4. **Token exchange**: the ingress gateway exchanges the authorization code using the unified client (`secure-subdomain-client`)
5. **Session established**: gateway stores tokens in secure cookies (domain: `.cat-herding.net`)
6. **Upstream request**: gateway forwards to `slop-detector` with `Authorization: Bearer <jwt>`

## Monitoring & debugging

### Logs

```bash
# Application logs
kubectl logs -l app=slop-detector -c slop-detector

# Pod sidecar logs
kubectl logs -l app=slop-detector -c istio-proxy
```

If redirects/token exchange look wrong, you’ll usually want the **ingress gateway** logs (that’s where OAuth2 runs now).

### Quick checks

```bash
# Test OAuth2 behavior (expect 302 to the OAuth2 server when unauthenticated)
curl -I https://slop-detector.cat-herding.net
```

### Common issues

- **Not getting redirected / not protected**: verify `slop-detector.cat-herding.net` is included in the OAuth2 allowlist EnvoyFilter.
- **Redirect loops**: verify the allowlist/bypass rules exclude `oauth2.cat-herding.net` itself.
- **JWT validation failures in the mesh**: verify RequestAuthentication / JWT policies for the service match the issuer (`https://oauth2.cat-herding.net`) and the expected audiences for your deployment.

## Security considerations

1. **Secrets live at the gateway**
   - OAuth2 client credentials and cookie HMAC secrets are configured for the ingress gateway (not in app pods).
2. **Cookie security**
   - Cookies are scoped to `.cat-herding.net` to support SSO across subdomains.
   - Cookies are signed (HMAC) to prevent tampering.
3. **Network security**
   - In-cluster traffic can be protected by Istio mTLS.
   - External traffic should be HTTPS-only.

## References

- `docs/SECURE_SUBDOMAIN_OAUTH2.md`
- [Envoy OAuth2 filter API](https://www.envoyproxy.io/docs/envoy/latest/api-v3/extensions/filters/http/oauth2/v3/oauth2.proto)
