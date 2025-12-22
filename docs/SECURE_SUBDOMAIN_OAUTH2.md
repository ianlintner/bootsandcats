# Secure Subdomain OAuth2 Configuration

This configuration automatically protects any application deployed under `*.secure.cat-herding.net` with OAuth2 authentication and JWT validation, without requiring per-app configuration.

## Overview

All traffic to `*.secure.cat-herding.net` is automatically protected by:
- **OAuth2 Authorization Code Flow** with PKCE
- **JWT validation** and claim extraction
- **Shared session cookies** across all secure subdomains

## How It Works

1. **Gateway-Level Protection**: EnvoyFilters are applied at the Istio ingress gateway level, matching on the domain `*.secure.cat-herding.net`
2. **Single OAuth2 Client**: One client (`secure-subdomain-client`) handles authentication for all secure subdomains
3. **Wildcard Redirect URIs**: The client supports `https://*.secure.cat-herding.net/_oauth2/callback`
4. **Shared Cookies**: Session cookies are set with `domain: .secure.cat-herding.net` for seamless SSO

## Setup Instructions

### 1. Register the OAuth2 Client

```bash
# Set your database connection details
export DB_HOST="your-db-host"
export DB_PORT="5432"
export DB_NAME="oauth2db"
export DB_USER="oauth2user"
export PGPASSWORD="your-password"

# Run the registration script
./scripts/add-secure-subdomain-client.sh
```

This script will:
- Create the `secure-subdomain-client` in the OAuth2 database
- Generate secure random secrets for client authentication and HMAC
- Display the secrets you need to save

### 2. Create Kubernetes Secrets

The script output will show you the exact command to run. It will look like:

```bash
kubectl create secret generic secure-subdomain-oauth-secrets \
  --from-literal=client-secret='<CLIENT_SECRET>' \
  --from-literal=hmac-secret='<HMAC_SECRET>' \
  -n aks-istio-ingress \
  --dry-run=client -o yaml | kubectl apply -f -
```

### 3. Update the SDS ConfigMap

Edit the ConfigMap to use the secrets:

```bash
kubectl edit configmap envoy-oauth2-sds-secure-subdomain -n aks-istio-ingress
```

Replace `REPLACE_WITH_CLIENT_SECRET` and `REPLACE_WITH_HMAC_SECRET` with the values from step 1.

### 4. Apply the Configuration

```bash
kubectl apply -k infrastructure/k8s/istio/
```

### 5. Verify the Setup

Deploy any app under `*.secure.cat-herding.net`:

```yaml
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: my-app
  namespace: default
spec:
  hosts:
  - myapp.secure.cat-herding.net
  gateways:
  - aks-istio-ingress/cat-herding-gateway
  http:
  - route:
    - destination:
        host: my-app-service
        port:
          number: 80
```

Access `https://myapp.secure.cat-herding.net` - you'll be automatically redirected to OAuth2 login!

## Architecture

### EnvoyFilters

1. **envoyfilter-secure-subdomain-oauth2.yaml**
   - Applied to: Istio ingress gateway
   - Matches: `*.secure.cat-herding.net`
   - Provides: OAuth2 authorization code flow, session management

2. **envoyfilter-secure-subdomain-jwt.yaml**
   - Applied to: Istio ingress gateway
   - Matches: `*.secure.cat-herding.net`
   - Provides: JWT validation, claim extraction to headers

### OAuth2 Flow

1. User accesses `https://myapp.secure.cat-herding.net/some-path`
2. Envoy OAuth2 filter checks for valid session cookie
3. If no valid session, redirects to `https://oauth2.cat-herding.net/oauth2/authorize`
4. User authenticates and is redirected back to `https://myapp.secure.cat-herding.net/_oauth2/callback`
5. Envoy exchanges authorization code for access token
6. Envoy sets session cookies (shared across all `*.secure.cat-herding.net`)
7. User is redirected to original path with Bearer token in header
8. JWT filter validates token and extracts claims to headers:
   - `x-jwt-sub`: User ID
   - `x-jwt-username`: Username
   - `x-jwt-email`: Email
   - `x-jwt-name`: Full name
   - `x-jwt-scope`: Scopes
   - `x-jwt-client-id`: Client ID

### Session Management

- **Cookie Domain**: `.secure.cat-herding.net` (note the leading dot for subdomain sharing)
- **Cookie Names**:
  - `_secure_session`: Bearer token
  - `_secure_oauth_hmac`: HMAC for token validation
  - `_secure_oauth_expires`: Expiration timestamp
- **Session Duration**: 15 minutes (900s)
- **Same-Site Policy**: LAX

## Health Check Exemptions

The following paths bypass OAuth2 authentication:
- `/actuator/*`
- `/health`
- `/healthz`
- `/ready`

## Logout

Users can logout from any secure subdomain by visiting:
```
https://<any-subdomain>.secure.cat-herding.net/_oauth2/logout
```

This will clear all session cookies and redirect to the OAuth2 server logout endpoint.

## Troubleshooting

### Check EnvoyFilter Status

```bash
kubectl get envoyfilter -n aks-istio-ingress
```

### View Envoy Configuration

```bash
kubectl exec -n aks-istio-ingress <ingress-pod> -c istio-proxy -- curl localhost:15000/config_dump | jq '.configs[] | select(.["@type"] | contains("filters"))'
```

### Check OAuth2 Client Registration

```bash
psql -h $DB_HOST -U oauth2user -d oauth2db -c \
  "SELECT client_id, client_name, redirect_uris, scopes FROM oauth2_registered_client WHERE client_id = 'secure-subdomain-client';"
```

### Debug JWT Validation

Use the diagnostic script:
```bash
./scripts/diagnose-jwt-validation.sh
```

## Security Considerations

- **PKCE Required**: The client enforces PKCE to prevent authorization code interception
- **Secure Cookies**: All cookies use `SameSite: LAX` to prevent CSRF
- **Token Forwarding**: Bearer tokens are forwarded to backend services for additional validation
- **JWT Validation**: All requests are validated against the OAuth2 server's JWKS endpoint
- **Short-Lived Tokens**: Access tokens expire after 15 minutes

## Adding New Apps

To add a new app under the secure subdomain:

1. **Deploy your application** to Kubernetes
2. **Create a VirtualService** pointing to `<yourapp>.secure.cat-herding.net`
3. **That's it!** No additional OAuth2 configuration needed

The gateway-level filters will automatically protect your app.
