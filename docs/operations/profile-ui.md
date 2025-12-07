# Profile UI

Profile UI is a Spring Boot micro-app used to validate end-to-end authentication via the Authorization Server. It shows the authenticated user’s details, claims, and ID token, and includes a logout button. Styling is provided via TailwindCSS with the shared portfolio theme `@ianlintner/theme` and is visible at the root path (`/`).

- If the user is not authenticated, the UI automatically redirects to the Authorization Server login (`/oauth/login/oauth2server`).
- After login, `/api/me` returns the authenticated principal attributes displayed on the page.
- A lightweight JSON status check remains at `/api/status` (anonymous access).

## Build

```bash
./gradlew :profile-ui:bootJar
```

## Container Image

Build a Linux/AMD64 image from your Apple Silicon workstation using BuildKit, then push to ACR:

```bash
docker buildx build \
	--platform linux/amd64 \
	-t gabby.azurecr.io/profile-ui:latest \
	-f profile-ui/Dockerfile \
	profile-ui \
	--push
```

> ℹ️ Use `--load` instead of `--push` if you only need the artifact locally (for example, kind or tilt).

## Deploy

```bash
kubectl apply -f k8s/profile-ui-deployment.yaml
kubectl rollout status deployment/profile-ui
kubectl get pods -l app=profile-ui -o wide
```

## Configuration

Profile UI relies on issuer discovery for the Authorization Server. Override via environment variables when deploying:

- `OAUTH2_ISSUER_URI` (default `https://oauth2.cat-herding.net`)
- `OAUTH2_CLIENT_ID` (default `profile-ui`)
- `OAUTH2_CLIENT_SECRET` (defaults to the demo secret in `oauth2-app-secrets`)

These are parameterized in `profile-ui/src/main/resources/application.properties` so they can be adjusted as needed.

The Tailwind-based styling is built during `processResources`, but you can run it manually while iterating on UI changes:

```bash
cd profile-ui
npm install
npm run build:css
```

## Test

- Access the Profile UI Service (via port-forward or ingress) and click “Login via Authorization Server”.
- Authenticate using local credentials or one of the federated providers.
- On success, you’ll see your profile summary, claims, and ID token.

## Troubleshooting

- `ImagePullBackOff`: Confirm ACR image `gabby.azurecr.io/profile-ui:latest` exists and AKS has permission to pull.
- Issuer mismatch / discovery issues: ensure `OAUTH2_ISSUER_URI` matches the Authorization Server ingress.
- Federation errors: ensure secrets in `oauth2-app-secrets` include provider keys.
