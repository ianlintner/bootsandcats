# E2E Tests

End-to-end OAuth2/OIDC flow tests that run against a live deployment (local or Kubernetes).

## Environment

Override defaults via environment variables:

- `E2E_BASE_URL` (default `http://localhost:9000`)
- `E2E_CLIENT_ID` (default `demo-client`)
- `E2E_CLIENT_SECRET` (default `demo-secret`)
- `E2E_REDIRECT_URI` (default `http://localhost:8080/callback`)
- `E2E_USERNAME` (default `user`)
- `E2E_PASSWORD` (default `password`)

## Run

```bash
./gradlew :e2e-tests:test
```

Ensure the authorization server is reachable at `E2E_BASE_URL` and the client/user credentials are valid in that environment.
