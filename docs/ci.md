# CI/CD Pipeline Overview

This project uses GitHub Actions with Gradle 9.0 for builds. The pipeline is optimized for fast feedback and clear separation of concerns.

## Workflows

- CI (`.github/workflows/ci.yml`)
  - Triggers: push to `main` and `feature/**`, and pull requests to `main` (docs-only changes are ignored)
  - Jobs (in order):
    1. MkDocs Build
       - Python 3.12 for documentation site
       - Builds MkDocs site and uploads artifact
    2. Build & Unit Tests
       - Java 21 via Temurin
       - Gradle 9.0 with dependency caching
       - Formatting check (`spotlessCheck`)
       - Unit tests with JaCoCo coverage
       - Uploads: test reports, JaCoCo reports, packaged JARs
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
       - Downloads packaged JARs and MkDocs site
       - Builds multi-arch Docker images (amd64/arm64)
       - Pushes to Azure Container Registry (gabby.azurecr.io)
       - Syncs K8s manifests to GitOps storage
       - Deploys oauth2-server and profile-ui to AKS cluster
       - Verifies rollout status and pod health

- Manual Load Test (`.github/workflows/load-test.yml`)
  - Trigger: manual (workflow_dispatch)
  - Parameters: `users`, `durationSeconds`
  - Runs Gatling load tests and uploads results

- Security Scan (`.github/workflows/security.yml`)
  - Triggers: weekly schedule + manual
  - Caching: OWASP NVD data for faster runs
  - Runs OWASP Dependency-Check and uploads reports

## Performance Optimizations
- Gradle dependency caching via `gradle/actions/setup-gradle@v4`
- Parallel Gradle execution (`--parallel`)
- Build cache enabled (`--build-cache`)
- Artifact reuse (JAR built once; smoke and deploy jobs reuse it)
- Docker build reuses packaged JAR (no rebuild in container)
- Concurrency control cancels superseded runs for same ref
- Path filters skip docs-only edits

## Deployment Flow (main branch)

```
Push to main
    ↓
MkDocs Build (parallel with Build & Unit Tests)
    ↓
Build & Unit Tests → package JARs → upload artifacts
    ↓
Static Analysis (after Build)
    ↓
Smoke Test (validates app boots and serves OIDC)
    ↓
Deploy (main only):
  - Download JAR artifacts + MkDocs site
  - Build multi-arch Docker images (amd64/arm64)
  - Push to ACR (gabby.azurecr.io/oauth2-server, profile-ui)
  - Sync K8s manifests to GitOps storage
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
./gradlew :server-ui:build
OTEL_SDK_DISABLED=true java -jar server-ui/build/libs/server-ui-1.0.0-SNAPSHOT.jar --server.port=9000 &
APP_PID=$!
for i in {1..60}; do curl -fsS http://localhost:9000/actuator/health | grep -q '"status"\s*:\s*"UP"' && echo healthy && break; sleep 2; done
curl -fsS http://localhost:9000/.well-known/openid-configuration -o discovery.json
kill ${APP_PID}
```

## Test Commands

### Unit Tests (Default)
```bash
# Run all unit tests (uses H2 in-memory database)
./gradlew :server-ui:test
```

### Integration Tests with Testcontainers
```bash
# Run all testcontainers tests (PostgreSQL + Redis)
./gradlew :server-ui:testcontainersTests

# Run PostgreSQL-specific tests
./gradlew :server-ui:postgresTests

# Run Redis-specific tests
./gradlew :server-ui:redisTests

# Run federated identity tests (OAuth provider mocking)
./gradlew :server-ui:federatedIdentityTests
```

### Specialized Test Tasks
```bash
# Run behavioral tests (happy path + sad path)
./gradlew :server-ui:behavioralTests

# Run contract tests
./gradlew :server-ui:contractTests

# Run security tests
./gradlew :server-ui:securityTests

# Run OAuth2-specific tests
./gradlew :server-ui:oauth2Tests

# Run fast tests (no integration or slow tags)
./gradlew :server-ui:fastTests

# Run critical tests only
./gradlew :server-ui:criticalTests
```

### JUnit 5 Tags
The project uses JUnit 5 tags for test categorization:
- `testcontainers` - Tests requiring Docker containers
- `postgres` - PostgreSQL-specific tests
- `redis` - Redis-specific tests
- `oauth-flow` - OAuth2/OIDC flow tests
- `happy` - Happy path scenarios
- `sad` - Error/edge case scenarios
- `contract` - API contract tests
- `security` - Security-focused tests
- `critical` - High-priority tests
- `integration` - Integration tests
- `slow` - Long-running tests

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
