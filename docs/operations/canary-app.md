# Canary App

The Canary App is a simple Spring Boot web application used to validate end-to-end authentication via the Authorization Server. It shows the authenticated user’s details, claims, and ID token, and includes a logout button.

## Build

```bash
./gradlew :canary-app:bootJar
```

## Container Image

Build a Linux/AMD64 image from your Apple Silicon workstation using BuildKit, then push to ACR:

```bash
docker buildx build \
	--platform linux/amd64 \
	-t gabby.azurecr.io/canary-app:latest \
	-f canary-app/Dockerfile \
	canary-app \
	--push
```

> ℹ️ Use `--load` instead of `--push` if you only need the artifact locally (for example, kind or tilt).

## Deploy

```bash
kubectl apply -f k8s/canary-deployment.yaml
kubectl rollout status deployment/canary-app
kubectl get pods -l app=canary-app -o wide
```

## Configuration

The Canary App uses explicit endpoints to talk to the AS’s internal Service in the cluster:

- `authorization-uri`: `http://oauth2-server:9000/oauth2/authorize`
- `token-uri`: `http://oauth2-server:9000/oauth2/token`
- `jwk-set-uri`: `http://oauth2-server:9000/oauth2/jwks`
- `user-info-uri`: `http://oauth2-server:9000/userinfo`

These are parameterized in `canary-app/src/main/resources/application.properties` with environment variables so they can be adjusted as needed.

## Test

- Access the Canary App Service (via port-forward or ingress) and click “Login via Authorization Server”.
- Authenticate using local credentials or one of the federated providers.
- On success, you’ll see user attributes and the ID token.

## Troubleshooting

- `ImagePullBackOff`: Confirm ACR image `gabby.azurecr.io/canary-app:latest` exists and AKS has permission to pull.
- Issuer mismatch / discovery issues: The app is configured to use explicit endpoints to the internal Service to avoid DNS dependencies.
- Federation errors: Ensure secrets in `oauth2-app-secrets` include provider keys.
