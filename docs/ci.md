# CI/CD Pipeline Overview

This project uses GitHub Actions. The pipeline is optimized for fast feedback and clear separation of concerns.

## Workflows

- CI (`.github/workflows/ci.yml`)
  - Triggers: push to `main` and `feature/**`, and pull requests to `main` (docs-only changes are ignored)
  - Jobs (in order):
    1. Build & Unit Tests
       - Java 17 via Temurin
       - Caching: Maven dependencies (actions/setup-java cache=maven)
       - Formatting check (spotless:check)
       - Unit tests with JaCoCo coverage and rule (>=70%)
       - Uploads: surefire and JaCoCo reports; packaged JAR artifact
    2. Integration Tests
       - Runs Failsafe tests only (`*IT.java`, `*IntegrationTest.java`)
       - Uploads: failsafe reports
    3. Static Analysis
       - SpotBugs (with FindSecBugs), strict fail on findings
       - Uploads: XML/HTML reports
    4. Smoke Test
       - Downloads packaged JAR from build job
       - Launches the app on port 9000 with `OTEL_SDK_DISABLED=true`
       - Verifies `/actuator/health` is `UP`
       - Fetches and validates OIDC discovery document
       - Uploads: app logs and discovery JSON
    5. Deploy to AKS (main branch only)
       - Downloads packaged JAR from build job
       - Builds Docker image (reuses JAR, no recompile)
       - Pushes to Azure Container Registry (gabby.azurecr.io)
       - Tags: `latest` and git SHA
       - Deploys to AKS cluster "bigboy" via kubectl
       - Verifies rollout status and pod health

- Manual Load Test (`.github/workflows/load-test.yml`)
  - Trigger: manual (workflow_dispatch)
  - Parameters: `users`, `durationSeconds`
  - Runs Gatling via `-Pload-test` and uploads results

- Security Scan (`.github/workflows/security.yml`)
  - Triggers: weekly schedule + manual
  - Caching: OWASP NVD data for faster runs
  - Runs OWASP Dependency-Check and uploads reports

## Performance Optimizations
- Maven dependency caching via setup-java
- Multi-threaded Maven (`-T 1C`) where safe
- Artifact reuse (JAR built once; smoke and deploy jobs reuse it)
- Docker build reuses packaged JAR (no rebuild in container)
- Concurrency control cancels superseded runs for same ref
- Path filters skip docs-only edits

## Deployment Flow (main branch)

```
Push to main
    ↓
Build & Unit Tests → package JAR → upload artifact
    ↓
Integration Tests (parallel with Static Analysis)
    ↓
Smoke Test (validates app boots and serves OIDC)
    ↓
Deploy (main only):
  - Download JAR artifact
  - Build Docker image
  - Push to ACR (gabby.azurecr.io/oauth2-server)
  - Deploy to AKS (bigboy cluster)
  - Verify rollout
```

## Required GitHub Secrets

For Azure deployment:
- `AZURE_CLIENT_ID` - Service principal client ID (federated identity)
- `AZURE_TENANT_ID` - Azure tenant ID
- `AZURE_SUBSCRIPTION_ID` - Azure subscription ID
- `AZURE_RESOURCE_GROUP` - Resource group containing AKS cluster

See `k8s/README.md` for detailed Azure/AKS setup instructions.

## Local Smoke Test (optional)

To reproduce the smoke test locally:

```bash
./mvnw -DskipTests package
OTEL_SDK_DISABLED=true java -jar target/*.jar --server.port=9000 &
APP_PID=$!
for i in {1..60}; do curl -fsS http://localhost:9000/actuator/health | grep -q '"status"\s*:\s*"UP"' && echo healthy && break; sleep 2; done
curl -fsS http://localhost:9000/.well-known/openid-configuration -o discovery.json
kill ${APP_PID}
```

## Inspecting Historical Runs

If you have the GitHub CLI authenticated:

```bash
# Summary (uses the helper script)
scripts/gh-actions-summary.sh ianlintner/bootsandcats

# Raw listings
gh run list -L 20 -R ianlintner/bootsandcats
gh run list -L 10 -R ianlintner/bootsandcats --workflow ci.yml
```

The summary script prints workflow-level pass/fail breakdowns and the slowest runs.
