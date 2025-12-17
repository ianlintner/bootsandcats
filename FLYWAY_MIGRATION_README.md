# Flyway Migration Container Solution

## Overview

This solution provides a standalone Flyway migration container for running database migrations independently from the Spring Boot application. This addresses deployment issues where Spring Boot's integrated Flyway wasn't working reliably.

## Quick Links

- **Quick Start**: [FLYWAY_QUICKSTART.md](oauth2-server/FLYWAY_QUICKSTART.md)
- **Full Documentation**: [FLYWAY_CONTAINER.md](oauth2-server/FLYWAY_CONTAINER.md)
- **Implementation Details**: [FLYWAY_MIGRATION_IMPLEMENTATION.md](oauth2-server/FLYWAY_MIGRATION_IMPLEMENTATION.md)

## What's Included

### Core Components

1. **Flyway Container** ([oauth2-server/Dockerfile.flyway](oauth2-server/Dockerfile.flyway))
   - Based on Flyway 11.2.0-alpine
   - Contains all 19 database migration scripts
   - Configurable via environment variables

2. **Init Container Deployment** ([infrastructure/k8s/apps/oauth2-server/oauth2-server-deployment.yaml](infrastructure/k8s/apps/oauth2-server/oauth2-server-deployment.yaml))
   - Flyway runs as init container (recommended approach)
   - Automatic migration on every pod start
   - Self-healing and simple to manage

3. **Docker Compose Integration** ([infrastructure/docker-compose.yml](infrastructure/docker-compose.yml))
   - `flyway-migrate` service runs before oauth2-server
   - Automatic dependency management
   - Easy local testing

4. **Kubernetes Job** ([infrastructure/k8s/apps/oauth2-server/flyway-migration-job.yaml](infrastructure/k8s/apps/oauth2-server/flyway-migration-job.yaml)) - Optional
   - Alternative to init container for Helm deployments
   - Runs as pre-install/pre-upgrade hook
   - Better for large-scale deployments

4. **Build & Test Scripts**
   - [oauth2-server/build-flyway-container.sh](oauth2-server/build-flyway-container.sh) - Build and push container
   - [scripts/test-flyway-migrations.sh](scripts/test-flyway-migrations.sh) - Test migrations in CI/CD
   - [scripts/validate-flyway-setup.sh](scripts/validate-flyway-setup.sh) - Validate setup

5. **Spring Boot Configuration**
   - [application-prod-no-flyway.properties](oauth2-server/server-ui/src/main/resources/application-prod-no-flyway.properties) - Config for containerized Flyway

### Documentation

- **[FLYWAY_QUICKSTART.md](oauth2-server/FLYWAY_QUICKSTART.md)** - Get started quickly
- **[FLYWAY_CONTAINER.md](oauth2-server/FLYWAY_CONTAINER.md)** - Comprehensive documentation
- **[FLYWAY_MIGRATION_IMPLEMENTATION.md](oauth2-server/FLYWAY_MIGRATION_IMPLEMENTATION.md)** - Implementation details
- **[FLYWAY_INIT_CONTAINER_VS_JOB.md](oauth2-server/FLYWAY_INIT_CONTAINER_VS_JOB.md)** - Init container vs Job comparison

## Getting Started

### Prerequisites

- Docker and Docker Compose installed
- Access to PostgreSQL database
- (For Kubernetes) kubectl configured and access to the cluster

### Validate Setup

```bash
./scripts/validate-flyway-setup.sh
```

This checks that all required files are in place and ready to use.

### Local Development

```bash
# 1. Start everything (migrations run automatically)
cd infrastructure
docker-compose up

# Or run migrations separately
docker-compose up postgres
docker-compose up flyway-migrate
docker-compose up oauth2-server
```

### Build Container

```bash
cd oauth2-server
./build-flyway-container.sh
```

### Push to Registry

```bash
cd oauth2-server
./build-flyway-container.sh --push
```

### Deploy to Kubernetes

```bash
# The deployment includes the init container by default
kubectl apply -f infrastructure/k8s/apps/oauth2-server/oauth2-server-deployment.yaml

# Watch the init container logs during deployment
kubectl logs -f deployment/oauth2-server -c flyway-migrate

# Check deployment status
kubectl get pods -l app=oauth2-server

# Optional: Use the standalone Job instead (for Helm deployments)
kubectl apply -f infrastructure/k8s/apps/oauth2-server/flyway-migration-job.yaml
kubectl wait --for=condition=complete job/flyway-migrate
kubectl logs job/flyway-migrate
```

## Architecture

```
┌─────────────────────────────────────────┐
│      Deployment with Init Container     │
└─────────────────────────────────────────┘

Pod Starts
    ↓
Init Container: flyway-migrate
    ├─ Connects to PostgreSQL
    ├─ Checks migration status
    ├─ Applies pending migrations
    └─ Exits (success)
    ↓
Main Container: oauth2-server
    └─ Starts with validated schema
```

### Container Structure

```
flyway-migrate:latest
├── Flyway CLI 11.2.0
└── /flyway/sql/
    ├── V1__create_app_users_table.sql
    ├── V2__create_oauth2_registered_client_table.sql
    ├── V3__create_security_audit_table.sql
    └── ... (19 total migrations)
```

## Key Features

✅ **Init Container Pattern** - Migrations run automatically before app starts
✅ **Self-Healing** - Every pod restart ensures migrations are applied
✅ **Separation of Concerns** - Database migrations decoupled from application
✅ **Kubernetes Native** - Works with standard Deployment resources
✅ **Docker Compose Support** - Easy local development and testing
✅ **CI/CD Ready** - Scripts for automated testing and deployment
✅ **Observable** - Clear init container status and logs
✅ **Safe** - Clean command disabled, validation enabled
✅ **Flexible** - Same container works as init container or Job

## Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `FLYWAY_URL` | `jdbc:postgresql://postgres:5432/oauth2db` | Database URL |
| `FLYWAY_USER` | `postgres` | Database username |
| `FLYWAY_PASSWORD` | `postgres` | Database password |
| `FLYWAY_LOCATIONS` | `filesystem:/flyway/sql` | Migration location |
| `FLYWAY_VALIDATE_ON_MIGRATE` | `true` | Validate before running |

### Spring Boot

When using the containerized Flyway, disable Spring Boot's Flyway:

```bash
SPRING_PROFILES_ACTIVE=prod,prod-no-flyway
```

This profile:
- Disables Spring Boot Flyway integration
- Sets JPA to validate mode
- Ensures schema matches entity definitions

## Migration Scripts

All 19 existing migration scripts are included:

- V1-V4: Core tables (users, OAuth2, audit)
- V5-V7: Profile service client
- V8-V9: Admin features
- V10-V11: GitHub review service
- V12: Envoy filter alignment
- V13-V14: Chat service
- V15-V16: Slop detector
- V17-V18: Security agency
- V19: Profile service secret update

## Common Tasks

### Check Migration Status

```bash
# Docker Compose
docker-compose run flyway-migrate info

# Kubernetes
kubectl run flyway-info --rm -it \
  --image=gabby.azurecr.io/flyway-migrate:latest \
  --env="FLYWAY_URL=jdbc:postgresql://postgres:5432/oauth2db" \
  --env="FLYWAY_USER=postgres" \
  --env="FLYWAY_PASSWORD=yourpassword" \
  -- info
```

### Repair Checksums

```bash
# Docker Compose
docker-compose run flyway-migrate repair

# Kubernetes
kubectl run flyway-repair --rm \
  --image=gabby.azurecr.io/flyway-migrate:latest \
  --env="FLYWAY_URL=..." \
  -- repair
```

### Validate Migrations

```bash
# Docker Compose
docker-compose run flyway-migrate validate

# CI/CD
./scripts/test-flyway-migrations.sh
```

## Troubleshooting

### Migration Fails

1. Check PostgreSQL connectivity
2. Verify credentials
3. Check logs: `kubectl logs -l component=database-migrations`
4. Validate migration scripts

### Checksum Mismatch

```bash
docker-compose run flyway-migrate repair
```

### Job Won't Start

1. Check PostgreSQL is ready: `kubectl get pods`
2. Check secrets exist: `kubectl get secrets oauth2-app-secrets`
3. Check init container logs: `kubectl describe pod -l component=database-migrations`

## CI/CD Integration

Example GitHub Actions workflow: [.github/workflows/flyway-migrations.yml.example](.github/workflows/flyway-migrations.yml.example)

```yaml
- name: Build Flyway Container
  run: |
    cd oauth2-server
    docker build -f Dockerfile.flyway -t flyway-migrate:${{ github.sha }} .

- name: Test Migrations
  run: ./scripts/test-flyway-migrations.sh

- name: Push to Registry
  run: |
    docker push gabby.azurecr.io/flyway-migrate:${{ github.sha }}
```

## Benefits vs Spring Boot Flyway

| Feature | Containerized Flyway | Spring Boot Flyway |
|---------|---------------------|-------------------|
| **Reliability** | ✅ Dedicated container | ⚠️ Coupled to app startup |
| **Observability** | ✅ Job status & logs | ⚠️ In app logs |
| **Kubernetes** | ✅ Native Job pattern | ⚠️ Init container workarounds |
| **Testing** | ✅ Independent tests | ⚠️ Requires app startup |
| **Rollback** | ✅ Job-based rollback | ⚠️ Complex |

## File Structure

```
.
├── oauth2-server/
│   ├── Dockerfile.flyway              # Container definition
│   ├── .dockerignore.flyway           # Build optimization
│   ├── build-flyway-container.sh      # Build script
│   ├── FLYWAY_CONTAINER.md            # Full docs
│   ├── FLYWAY_QUICKSTART.md           # Quick start
│   ├── FLYWAY_MIGRATION_IMPLEMENTATION.md  # Implementation
│   ├── server-dao/src/main/resources/db/migration/  # Migrations
│   └── server-ui/src/main/resources/
│       └── application-prod-no-flyway.properties
├── infrastructure/
│   ├── docker-compose.yml             # Compose with flyway-migrate
│   └── k8s/apps/oauth2-server/
│       ├── flyway-migration-job.yaml  # K8s Job
│       └── kustomization.yaml
├── scripts/
│   ├── test-flyway-migrations.sh      # CI/CD test
│   └── validate-flyway-setup.sh       # Validate setup
└── .github/workflows/
    └── flyway-migrations.yml.example  # CI/CD workflow
```

## Next Steps

1. ✅ Validate setup: `./scripts/validate-flyway-setup.sh`
2. Build container: `cd oauth2-server && ./build-flyway-container.sh`
3. Test locally: `cd ../infrastructure && docker-compose up flyway-migrate`
4. Push to registry: `cd ../oauth2-server && ./build-flyway-container.sh --push`
5. Deploy to dev/staging
6. Monitor first deployment
7. Update CI/CD pipeline
8. Deploy to production

## Support & References

- **Flyway Documentation**: https://documentation.red-gate.com/flyway
- **Flyway Docker Hub**: https://hub.docker.com/r/flyway/flyway
- **Kubernetes Jobs**: https://kubernetes.io/docs/concepts/workloads/controllers/job/

## License

Part of the bootsandcats OAuth2 Authorization Server project.
