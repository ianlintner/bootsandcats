# Flyway Migration Container - Implementation Summary

## Overview

This implementation provides a standalone Flyway migration container that runs database migrations independently from the Spring Boot application. This resolves deployment issues where Spring Boot's integrated Flyway wasn't working reliably.

## Problem Statement

- Spring Boot Flyway integration was failing during deployments
- Needed a separate, reliable migration mechanism
- Required better separation of concerns between application and database schema management

## Solution

Created a containerized Flyway setup that:
1. Runs migrations as a separate container/job
2. Uses the existing migration scripts from `server-dao/src/main/resources/db/migration/`
3. Can be deployed before the application starts (Kubernetes Job with hook)
4. Provides better observability and control over migrations

## Files Created/Modified

### New Files

1. **oauth2-server/Dockerfile.flyway**
   - Dockerfile for Flyway migration container
   - Based on official Flyway image (11.2.0-alpine)
   - Copies migration scripts into container

2. **oauth2-server/build-flyway-container.sh**
   - Build script for the Flyway container
   - Supports building, testing, and pushing to registry
   - Usage: `./build-flyway-container.sh [--push] [--tag TAG]`

3. **oauth2-server/FLYWAY_CONTAINER.md**
   - Comprehensive documentation
   - Usage examples for Docker Compose and Kubernetes
   - Troubleshooting guide

4. **oauth2-server/FLYWAY_QUICKSTART.md**
   - Quick start guide for common tasks
   - Step-by-step instructions
   - Common commands reference

5. **oauth2-server/.dockerignore.flyway**
   - Optimizes Docker build by excluding unnecessary files
   - Keeps only migration scripts in the image

6. **infrastructure/k8s/apps/oauth2-server/flyway-migration-job.yaml**
   - Kubernetes Job definition
   - Runs as pre-install/pre-upgrade hook
   - Includes init container to wait for PostgreSQL

7. **oauth2-server/server-ui/src/main/resources/application-prod-no-flyway.properties**
   - Spring Boot configuration profile for containerized Flyway
   - Disables Spring Boot Flyway integration
   - Uses JPA validation mode

8. **scripts/test-flyway-migrations.sh**
   - CI/CD test script
   - Validates migrations can be applied
   - Can be integrated into GitHub Actions/Azure DevOps

### Modified Files

1. **infrastructure/docker-compose.yml**
   - Added `flyway-migrate` service
   - Configured dependencies so migrations run before oauth2-server

2. **infrastructure/k8s/apps/oauth2-server/kustomization.yaml**
   - Added reference to flyway-migration-job.yaml

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                   Deployment Flow (Init Container)           │
└─────────────────────────────────────────────────────────────┘

1. Pod creation starts
   ↓
2. Init Container: flyway-migrate
   - Connects to PostgreSQL
   - Applies all pending migrations
   - Validates schema
   - Exits on completion
   ↓
3. Main Container: OAuth2 Server starts
   - Spring Boot Flyway disabled (prod-no-flyway profile)
   - JPA validates schema matches entities
   - Application ready

┌─────────────────────────────────────────────────────────────┐
│                      Container Structure                     │
└─────────────────────────────────────────────────────────────┘

flyway-migrate:latest
├── /flyway/
│   ├── sql/                 # Migration scripts
│   │   ├── V1__create_app_users_table.sql
│   │   ├── V2__create_oauth2_registered_client_table.sql
│   │   └── ... (19 total migrations)
│   └── health.sh            # Health check script
└── flyway CLI (11.2.0)
```

## Usage

### Local Development (Docker Compose)

```bash
# Start everything (migrations run automatically)
cd infrastructure
docker-compose up

# Run migrations only
docker-compose up flyway-migrate

# Check migration status
docker-compose run flyway-migrate info

# Validate migrations
docker-compose run flyway-migrate validate
```

### Kubernetes Deployment

**Primary Method: Init Container**

The deployment now includes Flyway as an init container:

```bash
# Deploy (migrations run automatically as init container)
kubectl apply -f infrastructure/k8s/apps/oauth2-server/oauth2-server-deployment.yaml

# Watch init container logs
kubectl logs -f deployment/oauth2-server -c flyway-migrate

# Check pod status (will show Init:0/1 while migrating)
kubectl get pods -l app=oauth2-server
```

**Alternative Method: Kubernetes Job** (optional, for Helm)

```bash
# Run standalone job before deployment
kubectl apply -f infrastructure/k8s/apps/oauth2-server/flyway-migration-job.yaml

# Check status
kubectl get job flyway-migrate
kubectl logs job/flyway-migrate

# Then deploy application
kubectl apply -f infrastructure/k8s/apps/oauth2-server/oauth2-server-deployment.yaml
```

See [FLYWAY_INIT_CONTAINER_VS_JOB.md](FLYWAY_INIT_CONTAINER_VS_JOB.md) for comparison.

### CI/CD Integration

```bash
# Test migrations in pipeline
./scripts/test-flyway-migrations.sh
```

## Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `FLYWAY_URL` | `jdbc:postgresql://postgres:5432/oauth2db` | Database JDBC URL |
| `FLYWAY_USER` | `postgres` | Database username |
| `FLYWAY_PASSWORD` | `postgres` | Database password |
| `FLYWAY_LOCATIONS` | `filesystem:/flyway/sql` | Migration scripts location |
| `FLYWAY_BASELINE_ON_MIGRATE` | `false` | Baseline existing database |
| `FLYWAY_VALIDATE_ON_MIGRATE` | `true` | Validate before migrating |
| `FLYWAY_OUT_OF_ORDER` | `false` | Allow out-of-order migrations |
| `FLYWAY_CLEAN_DISABLED` | `true` | Disable clean (safety) |

### Spring Boot Configuration

For deployments using the containerized Flyway:

```properties
# Use the prod-no-flyway profile
SPRING_PROFILES_ACTIVE=prod,prod-no-flyway
```

This disables Spring Boot's Flyway integration and sets JPA to validate mode.

## Migration Scripts

All 19 existing migration scripts are used:

1. V1__create_app_users_table.sql
2. V2__create_oauth2_registered_client_table.sql
3. V3__create_security_audit_table.sql
4. V4__create_oauth2_authorization_tables.sql
5. V5__add_profile_ui_client.sql
6. V6__add_profile_service_client.sql
7. V7__fix_profile_service_client_settings.sql
8. V8__admin_clients_scopes_deny_rules.sql
9. V9__seed_admin_tables_from_existing_clients.sql
10. V10__add_github_review_service_client.sql
11. V11__update_github_review_service_client_secret.sql
12. V12__align_oauth2_clients_for_envoy_filter.sql
13. V13__add_chat_service_client.sql
14. V14__update_chat_service_client_secret.sql
15. V15__add_slop_detector_client.sql
16. V16__update_slop_detector_client_secret.sql
17. V17__add_security_agency_client.sql
18. V18__update_security_agency_client_secret.sql
19. V19__update_profile_service_client_secret.sql

## Benefits

1. **Reliability**: Migrations run in a dedicated container with clear success/failure states
2. **Observability**: Easy to check migration status and logs
3. **Separation**: Database changes are decoupled from application deployment
4. **Kubernetes Native**: Uses Job pattern with hooks for proper ordering
5. **Testability**: Can test migrations independently in CI/CD
6. **Flexibility**: Same container works for Docker Compose and Kubernetes
7. **No Code Changes**: Uses existing migration scripts without modification

## Troubleshooting

### Common Issues

1. **Checksum Mismatch**
   ```bash
   docker-compose run flyway-migrate repair
   ```

2. **Connection Failed**
   ```bash
   # Test database connectivity
   docker-compose run flyway-migrate info
   ```

3. **Job Won't Complete**
   ```bash
   # Check logs
   kubectl logs -l component=database-migrations
   
   # Check init container
   kubectl describe pod -l component=database-migrations
   ```

## Next Steps

1. **Update CI/CD Pipeline**
   - Add Flyway container build step
   - Run test-flyway-migrations.sh in tests

2. **Update Deployment Documentation**
   - Reference FLYWAY_QUICKSTART.md
   - Add to deployment checklist

3. **Monitor First Deployment**
   - Watch Job logs
   - Verify oauth2-server starts correctly
   - Confirm schema validation passes

## Testing Checklist

- [ ] Build Flyway container locally
- [ ] Test with docker-compose
- [ ] Push to Azure Container Registry
- [ ] Deploy to dev/staging environment
- [ ] Verify migrations apply correctly
- [ ] Confirm oauth2-server starts with disabled Flyway
- [ ] Run full integration tests
- [ ] Deploy to production

## References

- Flyway Documentation: https://documentation.red-gate.com/flyway
- Flyway Docker Image: https://hub.docker.com/r/flyway/flyway
- Kubernetes Jobs: https://kubernetes.io/docs/concepts/workloads/controllers/job/
- Helm Hooks: https://helm.sh/docs/topics/charts_hooks/
