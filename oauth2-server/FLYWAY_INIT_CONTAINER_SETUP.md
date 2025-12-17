# Flyway Init Container Setup - Summary

## What Was Done

Added Flyway migration container as an **init container** to the oauth2-server Kubernetes deployment. This ensures database migrations run automatically before the application starts on every pod deployment/restart.

## Key Changes

### 1. Deployment Configuration

**File:** `infrastructure/k8s/apps/oauth2-server/oauth2-server-deployment.yaml`

**Changes:**
- Added `initContainers` section with `flyway-migrate` container
- Updated `SPRING_PROFILES_ACTIVE` to include `prod-no-flyway` profile
- Init container uses same secrets as main application
- Includes Flyway placeholder support for client secrets

### 2. Spring Boot Configuration

**File:** `oauth2-server/server-ui/src/main/resources/application-prod-no-flyway.properties`

**Purpose:**
- Disables Spring Boot's integrated Flyway
- Sets JPA to validation mode
- Ensures no migration conflicts

### 3. Documentation

Created comprehensive documentation:
- `FLYWAY_MIGRATION_README.md` - Main overview
- `FLYWAY_INIT_CONTAINER_VS_JOB.md` - Init container vs Job comparison
- `FLYWAY_QUICKSTART.md` - Quick start guide
- `FLYWAY_CONTAINER.md` - Full documentation

## How It Works

```
┌─────────────────────────────────────────┐
│        Pod Lifecycle with Init Container │
└─────────────────────────────────────────┘

1. Kubernetes creates Pod
   └─ Pod is in "Pending" state

2. Init Container: flyway-migrate starts
   ├─ Connects to PostgreSQL
   ├─ Runs: flyway info (check status)
   ├─ Runs: flyway migrate (apply migrations)
   ├─ Runs: flyway validate (verify)
   └─ Exits with code 0 (success)
   └─ Pod is in "Init:0/1" state

3. Main Container: oauth2-server starts
   ├─ Spring Boot starts with prod-no-flyway profile
   ├─ Flyway is disabled
   ├─ JPA validates schema
   └─ Application becomes ready
   └─ Pod is in "Running" state

4. Health checks pass
   └─ Pod is "Ready" for traffic
```

## Benefits of Init Container Approach

✅ **Automatic** - Migrations run on every deployment
✅ **Self-Healing** - Pod won't start if migrations fail
✅ **Simple** - No separate Job to manage
✅ **Atomic** - Migrations complete before app starts
✅ **Kubernetes Native** - Standard deployment pattern
✅ **Observable** - Clear pod status and logs

## Deployment Commands

### Deploy to Kubernetes

```bash
# Build and push Flyway container
cd oauth2-server
./build-flyway-container.sh --push

# Deploy (init container runs automatically)
kubectl apply -f ../infrastructure/k8s/apps/oauth2-server/oauth2-server-deployment.yaml

# Watch init container progress
kubectl get pods -l app=oauth2-server -w

# View init container logs
kubectl logs deployment/oauth2-server -c flyway-migrate

# View main container logs (after init completes)
kubectl logs deployment/oauth2-server -c oauth2-server
```

### Local Testing with Docker Compose

```bash
cd infrastructure

# Start everything (migrations run automatically)
docker-compose up

# Or run migrations separately
docker-compose up postgres
docker-compose up flyway-migrate
docker-compose up oauth2-server
```

## Monitoring & Troubleshooting

### Check Init Container Status

```bash
# View pod status (shows Init:0/1 during migration)
kubectl get pods -l app=oauth2-server

# Describe pod to see init container details
kubectl describe pod -l app=oauth2-server

# View init container logs
kubectl logs -l app=oauth2-server -c flyway-migrate
```

### Common Issues

#### Init Container Fails

**Symptoms:**
- Pod stuck in `Init:Error` or `Init:CrashLoopBackOff`
- Main container never starts

**Diagnosis:**
```bash
kubectl logs -l app=oauth2-server -c flyway-migrate
kubectl describe pod -l app=oauth2-server
```

**Common Causes:**
- PostgreSQL not accessible
- Invalid credentials
- Migration script error
- Checksum mismatch

**Solutions:**
```bash
# Check PostgreSQL connectivity
kubectl run pg-test --rm -it \
  --image=postgres:15-alpine \
  -- psql -h postgres.default.svc.cluster.local -U postgres

# Repair checksums if needed
kubectl run flyway-repair --rm \
  --image=gabby.azurecr.io/flyway-migrate:latest \
  --env="FLYWAY_URL=..." \
  -- repair
```

#### Pod Starts But App Fails

**Symptoms:**
- Init container succeeds
- Main container fails to start

**Diagnosis:**
```bash
kubectl logs -l app=oauth2-server -c oauth2-server
```

**Common Causes:**
- Spring profile not set correctly
- JPA validation fails
- Application configuration issue

## Configuration Details

### Init Container Environment Variables

| Variable | Value | Source |
|----------|-------|--------|
| `FLYWAY_URL` | `jdbc:postgresql://postgres...` | Static |
| `FLYWAY_USER` | Database username | Secret: `oauth2-app-secrets` |
| `FLYWAY_PASSWORD` | Database password | Secret: `oauth2-app-secrets` |
| `FLYWAY_LOCATIONS` | `filesystem:/flyway/sql` | Static |
| `FLYWAY_VALIDATE_ON_MIGRATE` | `true` | Static |
| `FLYWAY_CLEAN_DISABLED` | `true` | Static (safety) |

### Main Container Changes

```yaml
env:
- name: SPRING_PROFILES_ACTIVE
  value: "prod,prod-no-flyway"  # Added prod-no-flyway
```

This profile:
- Sets `spring.flyway.enabled=false`
- Sets `spring.jpa.hibernate.ddl-auto=validate`
- Ensures no migration conflicts

## Migration to Init Container from Other Approaches

### From Spring Boot Flyway

1. ✅ Add init container to deployment
2. ✅ Add `prod-no-flyway` to `SPRING_PROFILES_ACTIVE`
3. Deploy and verify

### From Standalone Job

1. Keep Job for one deployment cycle (redundancy)
2. Add init container to deployment
3. Deploy and verify init container works
4. Remove Job from deployment process

## Files Modified/Created

### Modified
- `infrastructure/k8s/apps/oauth2-server/oauth2-server-deployment.yaml`
  - Added initContainers section
  - Updated SPRING_PROFILES_ACTIVE

### Created
- `oauth2-server/Dockerfile.flyway`
- `oauth2-server/build-flyway-container.sh`
- `oauth2-server/FLYWAY_*.md` (documentation)
- `oauth2-server/server-ui/src/main/resources/application-prod-no-flyway.properties`
- `infrastructure/k8s/apps/oauth2-server/flyway-migration-job.yaml` (optional)
- `scripts/test-flyway-migrations.sh`
- `scripts/validate-flyway-setup.sh`

## Next Steps

1. ✅ **Validate Setup**
   ```bash
   ./scripts/validate-flyway-setup.sh
   ```

2. ✅ **Build Container**
   ```bash
   cd oauth2-server
   ./build-flyway-container.sh
   ```

3. ✅ **Test Locally**
   ```bash
   cd ../infrastructure
   docker-compose up
   ```

4. **Push to Registry**
   ```bash
   cd ../oauth2-server
   ./build-flyway-container.sh --push
   ```

5. **Deploy to Dev/Staging**
   ```bash
   kubectl apply -f ../infrastructure/k8s/apps/oauth2-server/oauth2-server-deployment.yaml
   ```

6. **Monitor First Deployment**
   ```bash
   kubectl get pods -w
   kubectl logs -f deployment/oauth2-server -c flyway-migrate
   ```

7. **Verify Application Starts**
   ```bash
   kubectl logs -f deployment/oauth2-server -c oauth2-server
   ```

8. **Deploy to Production**

## Success Criteria

✅ Init container completes successfully
✅ Main container starts after init container
✅ Application health checks pass
✅ No Spring Boot Flyway errors in logs
✅ JPA validation passes
✅ All 19 migrations applied

## Rollback Plan

If issues occur:

1. **Quick Rollback**: Revert to previous deployment
   ```bash
   kubectl rollout undo deployment/oauth2-server
   ```

2. **Remove Init Container**: Comment out initContainers section

3. **Re-enable Spring Boot Flyway**: Remove `prod-no-flyway` from profile

## Support

- Documentation: See `FLYWAY_MIGRATION_README.md`
- Troubleshooting: See `FLYWAY_CONTAINER.md`
- Init vs Job: See `FLYWAY_INIT_CONTAINER_VS_JOB.md`
