# Secure Subdomain OAuth2 Configuration

This configuration provides gateway-level OAuth2 authentication (via `oauth2-proxy`) and JWT validation.

OAuth2 enforcement is **default-on** at the Istio ingress gateway for `*.cat-herding.net` and subdomains.

## Overview

Traffic to hosts you explicitly opt in is protected by:

- **OAuth2 Authorization Code Flow** with PKCE (handled by `oauth2-proxy`)
- **JWT validation** and claim extraction
- **Shared session cookies** across all secure subdomains

## How It Works

1. **Gateway-Level Protection**: EnvoyFilters are applied at the Istio ingress gateway level.
1. **Single OAuth2 Client**: One client (`secure-subdomain-client`) handles authentication for all subdomains.
1. **Redirect URIs must be registered**: Spring Authorization Server requires redirect URIs to match exactly.

    - With `oauth2-proxy` (and no explicit `--redirect-url` set), the callback is computed from the incoming request host.
    - This means `secure-subdomain-client` must allowlist every protected host's callback:
      - `https://profile.cat-herding.net/_oauth2/callback`
      - `https://chat.cat-herding.net/_oauth2/callback`
      - etc.

1. **Shared Cookies (apex)**: Session cookies are set with `domain: .cat-herding.net` so a successful login can be reused across other subdomains.

### Exclusions

If you need to allow unauthenticated access for specific endpoints (e.g., Kubernetes probes), exclusions are handled in the gateway Lua filter (see `should_skip(...)` in `infrastructure/k8s/istio/envoyfilter-secure-subdomain-oauth2.yaml`).

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

> Important: the **client secret used by the OAuth2 server** must match the secret used by the **ingress gateway**.
>
> In this repo, the ingress gateway is wired from **Azure Key Vault** (see next step). If you already generated
> `secure-subdomain-client-secret` in Key Vault, update the DB client secret to match that value.

### 2. Sync secrets from Azure Key Vault (recommended)

Apply the manifests that:

- Pull `secure-subdomain-client-secret` and `secure-subdomain-oauth-hmac-secret` from Key Vault `inker-kv`
- Deploy `oauth2-proxy` in the ingress namespace and mount the secrets via CSI

```bash
kubectl apply -k infrastructure/k8s/aks-istio-ingress/
```

### 3. Apply the configuration

```bash
kubectl apply -k infrastructure/k8s/istio/
```

### 4. Verify the setup

Deploy an app and opt it in (example for a secure host):

```yaml
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: my-app
  namespace: default
spec:
  hosts:
  - myapp.cat-herding.net
  gateways:
  - aks-istio-ingress/cat-herding-gateway
  http:
  - route:
    - destination:
        host: my-app-service
        port:
          number: 80
```

Access `https://myapp.cat-herding.net` - you'll be automatically redirected to OAuth2 login!

## Architecture

### EnvoyFilters

- `envoyfilter-secure-subdomain-oauth2.yaml`: Gateway-wide auth enforcement (Lua `auth_request` to `oauth2-proxy` via `/_oauth2/auth`, redirects to `/_oauth2/start` on 401).

- `envoyfilter-secure-subdomain-jwt.yaml`: Gateway-wide JWT validation + claim extraction to headers.

The previous opt-in allowlist EnvoyFilter has been removed in favor of default-on gateway enforcement.

### OAuth2 Flow

1. User accesses `https://myapp.cat-herding.net/some-path`
1. Gateway Lua filter calls `https://<host>/_oauth2/auth` (routed to `oauth2-proxy`)
1. If unauthenticated, gateway redirects to `https://<host>/_oauth2/start?rd=<original_url>`
1. `oauth2-proxy` initiates login at `https://oauth2.cat-herding.net/oauth2/authorize`
1. After login, `oauth2-proxy` handles the callback at `https://<host>/_oauth2/callback`
1. `oauth2-proxy` sets session cookies (shared across all `*.cat-herding.net`) and redirects to the original URL
1. On subsequent requests, `oauth2-proxy` returns `202` for `/_oauth2/auth` and supplies `Authorization: Bearer <access_token>` to the gateway
1. JWT filter validates token and extracts claims to headers:
   - `x-jwt-sub`: User ID
   - `x-jwt-username`: Username
   - `x-jwt-email`: Email
   - `x-jwt-name`: Full name
   - `x-jwt-scope`: Scopes
   - `x-jwt-client-id`: Client ID

### Session Management

- **Cookie Domain**: `.cat-herding.net` (note the leading dot for subdomain sharing)
- **Cookie Names**:
  - `_secure_session`: oauth2-proxy session cookie (and an additional CSRF cookie may be set during login)
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

`https://<any-subdomain>.cat-herding.net/_oauth2/sign_out`

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

To add a new app under `*.cat-herding.net`:

1. **Deploy your application** to Kubernetes
1. **Create a VirtualService** pointing to `<yourapp>.cat-herding.net`
1. Ensure the host routes `/_oauth2/*` to `oauth2-proxy` (this repo’s `infrastructure/k8s/istio/virtualservices.yaml` already does this for the built-in apps).

If you're running the `oauth2-server` from this repo, the application will reconcile the
`secure-subdomain-client` configuration on startup (including redirect and post-logout URIs)
even when `oauth2.preserve-client-secrets=true`. After updating redirect/callback settings,
restart the oauth2-server deployment and confirm you see a log like:

- `Reconciling OAuth client 'secure-subdomain-client' configuration in database ...`

1. **That's it!** No per-service EnvoyFilter is required.

The gateway-level filters will automatically protect your app.
