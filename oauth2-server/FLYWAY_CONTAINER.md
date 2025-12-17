# Flyway Migration Container

This directory contains a standalone Flyway migration container for running database migrations independently from the Spring Boot application.

## Why a Separate Flyway Container?

- **Deployment Reliability**: Migrations run before the application starts, ensuring schema is ready
- **Separation of Concerns**: Database changes are isolated from application deployment
- **Kubernetes-Friendly**: Can be run as a pre-deployment Job
- **Troubleshooting**: Easier to debug migration issues without restarting the application
- **CI/CD Integration**: Migrations can be tested and validated independently

## Container Structure

```
Dockerfile.flyway          # Flyway container definition
server-dao/src/main/resources/db/migration/  # Migration scripts (copied into container)
```

## Local Development with Docker Compose

The Flyway migration service is integrated into `infrastructure/docker-compose.yml`:

```bash
# Start all services (migrations run automatically before oauth2-server starts)
cd infrastructure
docker-compose up

# Run migrations independently
docker-compose up flyway-migrate

# Validate migrations without running them
docker-compose run flyway-migrate validate

# Show migration status
docker-compose run flyway-migrate info

# Repair migration checksums (use with caution)
docker-compose run flyway-migrate repair

# Clean database (DANGEROUS - only for development)
docker-compose run flyway-migrate -cleanDisabled=false clean
```

## Building the Container

```bash
# Build the Flyway migration container
cd oauth2-server
docker build -f Dockerfile.flyway -t flyway-migrate:latest .

# Or build with docker-compose
cd infrastructure
docker-compose build flyway-migrate
```

## Kubernetes Deployment

### Using the Pre-Deployment Job

The Kubernetes Job in `infrastructure/k8s/apps/oauth2-server/flyway-migration-job.yaml` runs as a Helm hook before the main application deployment:

```bash
# Apply the migration job manually
kubectl apply -f infrastructure/k8s/apps/oauth2-server/flyway-migration-job.yaml

# Check job status
kubectl get jobs -l component=database-migrations

# View migration logs
kubectl logs -l component=database-migrations

# Delete completed job
kubectl delete job flyway-migrate
```

### Manual Migration Execution

You can also run migrations manually as a one-off Pod:

```bash
# Run migrations
kubectl run flyway-migrate \
  --image=gabby.azurecr.io/flyway-migrate:latest \
  --restart=Never \
  --env="FLYWAY_URL=jdbc:postgresql://postgres.default.svc.cluster.local:5432/oauth2db" \
  --env="FLYWAY_USER=postgres" \
  --env="FLYWAY_PASSWORD=yourpassword" \
  -- migrate

# Check migration status
kubectl run flyway-info \
  --image=gabby.azurecr.io/flyway-migrate:latest \
  --restart=Never \
  --env="FLYWAY_URL=jdbc:postgresql://postgres.default.svc.cluster.local:5432/oauth2db" \
  --env="FLYWAY_USER=postgres" \
  --env="FLYWAY_PASSWORD=yourpassword" \
  -- info

# View logs
kubectl logs flyway-migrate
kubectl logs flyway-info
```

## Environment Variables

The Flyway container supports the following environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `FLYWAY_URL` | `jdbc:postgresql://postgres:5432/oauth2db` | JDBC connection URL |
| `FLYWAY_USER` | `postgres` | Database username |
| `FLYWAY_PASSWORD` | `postgres` | Database password |
| `FLYWAY_LOCATIONS` | `filesystem:/flyway/sql` | Migration script location |
| `FLYWAY_BASELINE_ON_MIGRATE` | `false` | Baseline on first run |
| `FLYWAY_VALIDATE_ON_MIGRATE` | `true` | Validate migrations before running |
| `FLYWAY_OUT_OF_ORDER` | `false` | Allow out-of-order migrations |
| `FLYWAY_CLEAN_DISABLED` | `true` | Disable clean command (safety) |

### Flyway Placeholders

For secret values in migration scripts, use Flyway placeholders:

```bash
# Set placeholder values
FLYWAY_PLACEHOLDERS_PROFILE_SERVICE_CLIENT_SECRET=secret123
FLYWAY_PLACEHOLDERS_CHAT_SERVICE_CLIENT_SECRET=secret456
```

In your SQL migration:
```sql
-- Use ${profile_service_client_secret} in the migration
UPDATE oauth2_registered_client 
SET client_secret = '${profile_service_client_secret}'
WHERE client_id = 'profile-service';
```

## Flyway Commands

The container supports all standard Flyway commands:

- `migrate` - Apply pending migrations (default)
- `info` - Show migration status
- `validate` - Validate applied migrations against available ones
- `baseline` - Baseline an existing database
- `repair` - Repair the schema history table

## CI/CD Integration

### GitHub Actions Example

```yaml
- name: Run Database Migrations
  run: |
    docker build -f oauth2-server/Dockerfile.flyway -t flyway-migrate:${{ github.sha }} oauth2-server
    docker run --rm \
      --network host \
      -e FLYWAY_URL="${{ secrets.DATABASE_URL }}" \
      -e FLYWAY_USER="${{ secrets.DATABASE_USER }}" \
      -e FLYWAY_PASSWORD="${{ secrets.DATABASE_PASSWORD }}" \
      flyway-migrate:${{ github.sha }} migrate
```

### Azure DevOps Example

```yaml
- task: Docker@2
  displayName: 'Build Flyway Migration Image'
  inputs:
    command: build
    dockerfile: oauth2-server/Dockerfile.flyway
    tags: $(Build.BuildId)

- task: Docker@2
  displayName: 'Run Migrations'
  inputs:
    command: run
    arguments: >
      --rm
      -e FLYWAY_URL="$(DATABASE_URL)"
      -e FLYWAY_USER="$(DATABASE_USER)"
      -e FLYWAY_PASSWORD="$(DATABASE_PASSWORD)"
      flyway-migrate:$(Build.BuildId) migrate
```

## Troubleshooting

### Migration Checksums Don't Match

If you've modified a migration file that was already applied:

```bash
# Repair the schema history (updates checksums)
docker-compose run flyway-migrate repair

# Or in Kubernetes
kubectl run flyway-repair \
  --image=gabby.azurecr.io/flyway-migrate:latest \
  --restart=Never \
  --env="FLYWAY_URL=..." \
  -- repair
```

### Out-of-Order Migrations

Enable out-of-order migrations temporarily:

```bash
docker-compose run -e FLYWAY_OUT_OF_ORDER=true flyway-migrate migrate
```

### Connection Issues

Check PostgreSQL connectivity:

```bash
# Test database connection
docker-compose run flyway-migrate sh -c "flyway info || echo 'Connection failed'"

# In Kubernetes
kubectl run pg-test --rm -it \
  --image=postgres:15-alpine \
  --env="PGPASSWORD=yourpassword" \
  -- psql -h postgres.default.svc.cluster.local -U postgres -d oauth2db
```

### View Migration History

```bash
# Show all applied migrations
docker-compose run flyway-migrate info

# Query the Flyway schema history table directly
docker-compose exec postgres psql -U postgres -d oauth2db \
  -c "SELECT * FROM flyway_schema_history ORDER BY installed_rank;"
```

## Migration Script Guidelines

When creating new migration scripts in `server-dao/src/main/resources/db/migration/`:

1. **Naming Convention**: `V{version}__{description}.sql` (e.g., `V20__add_new_client.sql`)
2. **Idempotent**: Use `IF NOT EXISTS` and `IF EXISTS` clauses
3. **Atomic**: Each migration should be a single logical change
4. **Reversible**: Consider creating corresponding undo migrations
5. **Tested**: Test migrations in a development environment first
6. **Documented**: Add comments explaining the purpose and any dependencies

## Disabling Spring Boot Flyway

To avoid conflicts with the containerized migrations, disable Flyway in the Spring Boot application:

```yaml
# application.yml or application-prod.yml
spring:
  flyway:
    enabled: false
```

This ensures migrations are only run by the dedicated Flyway container.

## References

- [Flyway Documentation](https://documentation.red-gate.com/flyway)
- [Flyway Docker Hub](https://hub.docker.com/r/flyway/flyway)
- [Spring Boot Flyway Integration](https://docs.spring.io/spring-boot/docs/current/reference/html/howto.html#howto.data-initialization.migration-tool.flyway)
