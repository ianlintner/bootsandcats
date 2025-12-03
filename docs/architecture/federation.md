# Federated Login Architecture

This project adds federated login support to the OAuth2 Authorization Server, enabling users to sign in via GitHub, Google, and Azure (Microsoft Entra ID).

## Overview

- The Authorization Server (AS) is Spring Authorization Server with OIDC and JWT.
- Federated providers are configured via Spring Security OAuth2 Client.
- On successful federated login, a `FederatedIdentityAuthenticationSuccessHandler` persists user info to `app_users`.
- The Canary App is a simple OIDC client that displays the authenticated user’s info and token.

## Flow

1. User visits Canary App and clicks “Login via Authorization Server”.
2. Canary App redirects to AS `/oauth2/authorize` (OIDC).
3. AS login page offers federated buttons (GitHub/Google/Azure) or local login.
4. After successful auth, AS issues ID/Access token, and redirects back to Canary App.
5. Canary App shows claims, ID token, and a logout button.

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

Application properties (`src/main/resources/application.properties`) map these to Spring’s OAuth2 client registrations.

### Canary App

- Configured to call the Authorization Server directly via internal Service endpoints:
  - `authorization-uri`, `token-uri`, `jwk-set-uri`, `user-info-uri`
- Registration uses `demo-client` configured in the AS.

## Kubernetes

- `k8s/deployment.yaml` — AS deployment; federated env vars marked optional to avoid startup failure if secrets are missing.
- `k8s/canary-deployment.yaml` — Canary App deployment; uses `OAUTH2_ISSUER_URI` or explicit endpoints.

## Observability

- Metrics and traces via OpenTelemetry.

## Security Notes

- Never commit provider secrets.
- Rotate client secrets regularly.
- Use TLS/Ingress for external access to the AS; internal Service for cluster clients.
