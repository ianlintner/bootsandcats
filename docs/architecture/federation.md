# Federated Login Architecture

This project adds federated login support to the OAuth2 Authorization Server, enabling users to sign in via GitHub, Google, and Azure (Microsoft Entra ID).

## Overview

- The Authorization Server (AS) is Spring Authorization Server with OIDC and JWT.
- Federated providers are configured via Spring Security OAuth2 Client.
- On successful federated login, a `FederatedIdentityAuthenticationSuccessHandler` persists user info to `app_users`.
- The Profile UI is a simple OIDC client that displays the authenticated user’s profile and claims.

## Flow

1. User visits Profile UI and clicks “Login via Authorization Server”.
2. Profile UI redirects to AS `/oauth2/authorize` (OIDC).
3. AS login page offers federated buttons (GitHub/Google/Azure) or local login.
4. After successful auth, AS issues ID/Access token, and redirects back to Profile UI.
5. Profile UI shows claims, ID token, and a logout button.

## Data Model

- Table: `app_users`
  - `id` BIGSERIAL (PK)
  - `username`, `email`
  - `provider` (e.g., `github`, `google`, `azure`)
  - `provider_id` (provider’s subject/user ID)
  - `name`, `picture_url`, `last_login`
  - Unique constraint on `(provider, provider_id)`

## Configuration

### Authorization Server

Environment variables (in Kubernetes via `oauth2-app-secrets`):
- `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`
- `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`
- `AZURE_CLIENT_ID`, `AZURE_CLIENT_SECRET`, `AZURE_TENANT_ID`

Application properties (`server-ui/src/main/resources/application.properties`) map these to Spring’s OAuth2 client registrations.

### Profile UI

- Uses issuer discovery (`OAUTH2_ISSUER_URI`) to locate the Authorization Server.
- Registration uses the `profile-ui` client configured in the AS.

## Kubernetes

- `k8s/deployment.yaml` — AS deployment; federated env vars marked optional to avoid startup failure if secrets are missing.
- `k8s/profile-ui-deployment.yaml` — Profile UI deployment; uses `OAUTH2_ISSUER_URI` or explicit endpoints.

## Observability

- Metrics and traces via OpenTelemetry.

## Security Notes

- Never commit provider secrets.
- Rotate client secrets regularly.
- Use TLS/Ingress for external access to the AS; internal Service for cluster clients.
