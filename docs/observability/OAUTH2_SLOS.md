# OAuth2 Server SLO Plan (Endpoints, SLIs, and Dashboards)

This document defines pragmatic, endpoint-level Service Level Objectives (SLOs) for the OAuth2 Authorization Server, along with the metrics and PromQL queries used to measure them in Grafana.

## Goals

1. **Detect reliability regressions quickly** (burn-rate alerting)
2. **Separate user/client errors from server/platform errors**
3. **Keep metrics low-cardinality** (no per-user, no per-client-id labels)
4. **Make Azure-compatible**: all SLIs are Prometheus-compatible (works with Azure Managed Prometheus)

## Key endpoints

| Endpoint | Purpose |
|---|---|
| `/oauth2/token` | Token issuance (client_credentials, authorization_code, refresh_token, etc.) |
| `/oauth2/authorize` | Authorization code flow initiation (browser redirects) |
| `/oauth2/introspect` | Token introspection (resource server → auth server) |
| `/oauth2/revoke` | Token revocation |
| `/userinfo` | OIDC UserInfo |
| `/.well-known/openid-configuration` | OIDC discovery |
| `/oauth2/jwks` and `/.well-known/jwks.json` | JWK Set (key discovery) |

## Proposed SLOs (initial)

These are **starting SLOs**. After 1–2 weeks of baseline data, we should re-tune thresholds.

### 1) Availability SLO (server-side)

**What it measures:** the fraction of requests that are **not** failing due to server/platform issues.

**Bad events:** HTTP `5xx` (and optionally `429` if we want throttling to count against availability).

| Endpoint | SLO target | Window |
|---|---:|---:|
| `/oauth2/token` | 99.9% | 30d |
| `/oauth2/authorize` | 99.9% | 30d |
| `/oauth2/introspect` | 99.9% | 30d |
| `/oauth2/revoke` | 99.9% | 30d |
| `/userinfo` | 99.9% | 30d |
| Discovery + JWKS | 99.99% | 30d |

### 2) Latency SLO (p95)

**What it measures:** request latency as seen by clients.

| Endpoint | p95 target | Window |
|---|---:|---:|
| `/oauth2/token` | 250ms | 30d |
| `/oauth2/authorize` | 500ms | 30d |
| `/oauth2/introspect` | 200ms | 30d |
| `/oauth2/revoke` | 200ms | 30d |
| `/userinfo` | 250ms | 30d |
| Discovery + JWKS | 100ms | 30d |

### 3) OAuth2 protocol error-rate (informational, not an availability SLO)

Client-driven OAuth2 errors (e.g. `invalid_grant`, `invalid_request`) are often **expected** and shouldn’t page the on-call. We still want visibility because spikes can indicate:

- bad client deployments
- an outage in an upstream IdP
- time drift / PKCE issues
- brute-force attempts

We track these via `oauth2_endpoint_requests_total{outcome="failure",error=...}`.

## SLIs and PromQL

### HTTP availability (server-side) SLI

For endpoints where HTTP status is meaningful, use `http_server_requests_seconds_count`.

**Token endpoint (exclude 5xx):**

- Total:
  - `sum(rate(http_server_requests_seconds_count{uri="/oauth2/token"}[5m]))`
- Bad:
  - `sum(rate(http_server_requests_seconds_count{uri="/oauth2/token",status=~"5.."}[5m]))`
- Availability:
  - `1 - (bad / total)`

Repeat for other `uri` values.

### Latency p95 SLI

**Token endpoint p95:**

`histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{uri="/oauth2/token"}[5m])))`

Repeat for other `uri` values.

### OAuth2 endpoint outcome metrics (protocol-level)

These come from the custom metrics added in this repo (see `oauth2-server/server-logic`).

**Token success rate:**

`sum(rate(oauth2_endpoint_requests_total{endpoint="token",outcome="success"}[5m])) / sum(rate(oauth2_endpoint_requests_total{endpoint="token"}[5m]))`

**Token failures by error code:**

`sum by (error) (rate(oauth2_endpoint_requests_total{endpoint="token",outcome="failure"}[5m]))`

**Token failures by grant type + error:**

`sum by (grant_type, error) (rate(oauth2_endpoint_requests_total{endpoint="token",outcome="failure"}[5m]))`

## Burn-rate alerting (recommended)

Once we have Prometheus alerting wired in, use multi-window burn rates:

- Fast burn: 5m / 1h
- Slow burn: 30m / 6h

Example error budget burn for 99.9% SLO:

- Error budget = 0.1% = 0.001
- Burn rate = `error_rate / 0.001`

Where:

`error_rate = bad / total`

We typically alert when burn rate exceeds:

- 14.4x (page) over 5m and 1h
- 6x (ticket) over 30m and 6h

## Tracing expectations

Tracing is enabled via Micrometer Tracing + OTLP export.

We expect:

- A trace/span per request (HTTP server span)
- Correlation: trace IDs present in logs (where supported)
- Tempo as the default trace backend for Grafana in-cluster

## Notes on label cardinality

- Do **not** label by `client_id`, `username`, `sub`, or IP.
- Grant type is OK (small bounded set).
- OAuth2 error codes are OK (bounded set).

