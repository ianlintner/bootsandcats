# Flyway Migration Container Quick Start

This guide helps you get started with the standalone Flyway migration container.

## Prerequisites

- Docker installed
- Access to PostgreSQL database
- (For Kubernetes) kubectl configured

## Quick Start - Docker Compose

The easiest way to test the migration container:

```bash
# 1. Navigate to infrastructure directory
cd infrastructure

# 2. Start PostgreSQL and run migrations
docker-compose up postgres flyway-migrate

# 3. Verify migrations were applied
docker-compose run flyway-migrate info
```

That's it! The migrations will run automatically and the oauth2-server will start with a fully migrated database.

## Quick Start - Build and Test

```bash
# 1. Build the Flyway container
cd oauth2-server
./build-flyway-container.sh

# 2. Test locally with docker-compose
cd ../infrastructure
docker-compose up flyway-migrate

# 3. View migration status
docker-compose run flyway-migrate info
```

## Quick Start - Kubernetes Deployment

```bash
# 1. Build and push to registry
cd oauth2-server
./build-flyway-container.sh --push

# 2. Deploy migration job
kubectl apply -f ../infrastructure/k8s/apps/oauth2-server/flyway-migration-job.yaml

# 3. Check job status
kubectl get job flyway-migrate

# 4. View logs
kubectl logs -l component=database-migrations
```

## Common Tasks

### Show Migration Status

```bash
# Docker Compose
docker-compose run flyway-migrate info

# Kubernetes
kubectl run flyway-info --rm -it \
  --image=gabby.azurecr.io/flyway-migrate:latest \
  --env="FLYWAY_URL=jdbc:postgresql://postgres.default.svc.cluster.local:5432/oauth2db" \
  --env="FLYWAY_USER=postgres" \
  --env="FLYWAY_PASSWORD=yourpassword" \
  -- info
```

### Validate Migrations

```bash
# Docker Compose
docker-compose run flyway-migrate validate

# Kubernetes
kubectl run flyway-validate --rm -it \
  --image=gabby.azurecr.io/flyway-migrate:latest \
  --env="FLYWAY_URL=jdbc:postgresql://postgres.default.svc.cluster.local:5432/oauth2db" \
  --env="FLYWAY_USER=postgres" \
  --env="FLYWAY_PASSWORD=yourpassword" \
  -- validate
```

### Repair Checksums (if migration files were modified)

```bash
# Docker Compose
docker-compose run flyway-migrate repair

# Kubernetes
kubectl run flyway-repair --rm -it \
  --image=gabby.azurecr.io/flyway-migrate:latest \
  --env="FLYWAY_URL=jdbc:postgresql://postgres.default.svc.cluster.local:5432/oauth2db" \
  --env="FLYWAY_USER=postgres" \
  --env="FLYWAY_PASSWORD=yourpassword" \
  -- repair
```

## Directory Structure

```
oauth2-server/
├── Dockerfile.flyway                    # Flyway container definition
├── build-flyway-container.sh           # Build script
├── FLYWAY_CONTAINER.md                 # Full documentation
├── FLYWAY_QUICKSTART.md                # This file
└── server-dao/
    └── src/main/resources/db/migration/  # SQL migration files
        ├── V1__create_app_users_table.sql
        ├── V2__create_oauth2_registered_client_table.sql
        └── ...

infrastructure/
├── docker-compose.yml                   # Includes flyway-migrate service
└── k8s/apps/oauth2-server/
    ├── flyway-migration-job.yaml       # Kubernetes Job
    └── kustomization.yaml               # References the job

scripts/
└── test-flyway-migrations.sh           # CI/CD test script
```

## Environment Variables

Key environment variables for the Flyway container:

```bash
FLYWAY_URL=jdbc:postgresql://postgres:5432/oauth2db
FLYWAY_USER=postgres
FLYWAY_PASSWORD=postgres
FLYWAY_LOCATIONS=filesystem:/flyway/sql
FLYWAY_BASELINE_ON_MIGRATE=false
FLYWAY_VALIDATE_ON_MIGRATE=true
```

## Disabling Spring Boot Flyway

To prevent conflicts, disable Flyway in your Spring Boot application:

**application-prod.yml:**
```yaml
spring:
  flyway:
    enabled: false
  jpa:
    hibernate:
      ddl-auto: validate  # Use validate instead of update
```

## Troubleshooting

### Container Build Fails

```bash
# Ensure you're in the oauth2-server directory
cd oauth2-server

# Check migration files exist
ls -l server-dao/src/main/resources/db/migration/

# Build with verbose output
docker build -f Dockerfile.flyway -t flyway-migrate:test . --no-cache
```

### Migration Fails

```bash
# Check database connectivity
docker-compose run flyway-migrate sh -c "flyway info || echo 'Connection failed'"

# View detailed logs
docker-compose run flyway-migrate migrate -X

# Check PostgreSQL logs
docker-compose logs postgres
```

### Checksum Mismatch

If you modified a migration that was already applied:

```bash
# Repair the schema history
docker-compose run flyway-migrate repair

# Then run migrations again
docker-compose run flyway-migrate migrate
```

## Next Steps

- Read the full documentation: [FLYWAY_CONTAINER.md](FLYWAY_CONTAINER.md)
- Review migration scripts: `server-dao/src/main/resources/db/migration/`
- Check the Kubernetes deployment: `infrastructure/k8s/apps/oauth2-server/`

## Support

For issues or questions:
1. Check the logs: `kubectl logs -l component=database-migrations`
2. Review Flyway docs: https://documentation.red-gate.com/flyway
3. Validate your connection: `docker-compose run flyway-migrate info`
