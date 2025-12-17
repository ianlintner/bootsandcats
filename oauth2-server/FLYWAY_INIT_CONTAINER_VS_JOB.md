# Flyway Init Container vs Job

## Overview

The Flyway migration container can be deployed in two ways:

1. **Init Container** (Recommended) - Runs migrations on every pod start
2. **Kubernetes Job** (Optional) - Runs migrations once before deployment

## Init Container (Recommended)

### Advantages

✅ **Automatic** - Runs on every pod restart/scale-up
✅ **Simple** - No separate Job to manage
✅ **Self-Healing** - Migrations always run before app starts
✅ **Rolling Updates** - Works seamlessly with rolling deployments
✅ **No Hooks** - No Helm hook complexity

### Disadvantages

⚠️ **Redundant** - Runs on every pod even if no new migrations
⚠️ **Slower Startups** - Adds time to pod startup (but Flyway is fast)
⚠️ **Resource Usage** - Each pod runs migrations independently

### Configuration

The init container is configured in [oauth2-server-deployment.yaml](../infrastructure/k8s/apps/oauth2-server/oauth2-server-deployment.yaml):

```yaml
spec:
  template:
    spec:
      initContainers:
      - name: flyway-migrate
        image: gabby.azurecr.io/flyway-migrate:latest
        command: ["flyway", "migrate"]
        env:
        - name: FLYWAY_URL
          value: "jdbc:postgresql://postgres:5432/oauth2db"
        # ... other env vars
```

### How It Works

```
Pod Starts
    ↓
Init Container: flyway-migrate
    ├─ Connects to database
    ├─ Checks migration status
    ├─ Applies pending migrations (if any)
    └─ Exits (success or failure)
    ↓
Main Container: oauth2-server
    └─ Starts only if init container succeeded
```

### Best For

- ✅ Single or few replicas
- ✅ Rolling deployments
- ✅ Simple deployments without Helm
- ✅ Development and staging environments
- ✅ When you want guaranteed migrations before app starts

## Kubernetes Job (Optional)

### Advantages

✅ **Runs Once** - Migrations executed only once per deployment
✅ **Faster Pod Starts** - Pods start immediately after job completes
✅ **Centralized Logs** - Single job log to review
✅ **Pre-Deployment** - Can run before any pods are created

### Disadvantages

⚠️ **Manual Management** - Need to manage Job lifecycle
⚠️ **Helm Hooks** - Requires Helm for pre-install/pre-upgrade hooks
⚠️ **Timing Issues** - Race conditions if Job hasn't completed
⚠️ **Cleanup** - Old jobs need cleanup

### Configuration

The Job is configured in [flyway-migration-job.yaml](../infrastructure/k8s/apps/oauth2-server/flyway-migration-job.yaml):

```yaml
apiVersion: batch/v1
kind: Job
metadata:
  name: flyway-migrate
  annotations:
    helm.sh/hook: pre-install,pre-upgrade
    helm.sh/hook-weight: "-5"
```

### How It Works

```
Helm Install/Upgrade Triggered
    ↓
Pre-Hook: flyway-migration-job
    ├─ Job starts
    ├─ Applies migrations
    └─ Job completes
    ↓
Deployment Created
    └─ Pods start (migrations already done)
```

### Best For

- ✅ Large-scale deployments with many replicas
- ✅ Helm-managed deployments
- ✅ Production environments
- ✅ When you want to minimize pod startup time

## Comparison

| Feature | Init Container | Kubernetes Job |
|---------|---------------|----------------|
| **Execution** | Every pod start | Once per deployment |
| **Helm Required** | No | Yes (for hooks) |
| **Pod Startup Time** | Slower | Faster |
| **Reliability** | Self-healing | Manual retry |
| **Complexity** | Simple | More complex |
| **Resource Usage** | Higher (per pod) | Lower (one-time) |
| **Logs** | Per pod | Centralized |
| **Best For** | Dev/Staging | Production |

## Recommendations

### Use Init Container When

- You have a small number of replicas (1-3)
- You're not using Helm
- You want simplicity over optimization
- You're deploying to dev/staging
- You want guaranteed migrations on every pod

### Use Kubernetes Job When

- You have many replicas (10+)
- You're using Helm for deployments
- You want optimized pod startup times
- You're deploying to production
- You have CI/CD that manages Jobs

### Use Both When

- You want redundancy (Job for deployment, init container as backup)
- You're testing the migration strategy
- You have mixed environments (Job in prod, init container in dev)

## Migration Path

If you're currently using:

### Spring Boot Flyway → Init Container

1. Disable Spring Boot Flyway: `spring.flyway.enabled=false`
2. Add init container to deployment
3. Deploy and verify

### Spring Boot Flyway → Kubernetes Job

1. Create and deploy Job
2. Disable Spring Boot Flyway
3. Deploy application
4. Set up Helm hooks for future deployments

### Init Container → Kubernetes Job

1. Deploy the Job before upgrading
2. Remove init container from deployment
3. Set up Helm hooks
4. Deploy application

### Kubernetes Job → Init Container

1. Remove Job from deployment
2. Add init container to deployment
3. Deploy (migrations will run via init container)

## Current Configuration

**This project uses: Init Container** ✅

The oauth2-server deployment includes the Flyway init container by default. The Kubernetes Job is provided as an optional alternative for Helm-based deployments.

## Testing

### Test Init Container

```bash
# Deploy with init container
kubectl apply -f infrastructure/k8s/apps/oauth2-server/oauth2-server-deployment.yaml

# Watch init container logs
kubectl logs -f deployment/oauth2-server -c flyway-migrate

# Verify migrations ran
kubectl exec deployment/oauth2-server -- env | grep SPRING_PROFILES
# Should show: prod,prod-no-flyway
```

### Test Kubernetes Job

```bash
# Deploy job
kubectl apply -f infrastructure/k8s/apps/oauth2-server/flyway-migration-job.yaml

# Wait for completion
kubectl wait --for=condition=complete --timeout=300s job/flyway-migrate

# Check logs
kubectl logs job/flyway-migrate

# Deploy application (migrations already done)
kubectl apply -f infrastructure/k8s/apps/oauth2-server/oauth2-server-deployment.yaml
```

## Troubleshooting

### Init Container Fails

```bash
# Check init container logs
kubectl logs deployment/oauth2-server -c flyway-migrate

# Check init container status
kubectl describe pod -l app=oauth2-server

# Pod will be stuck in Init:Error or Init:CrashLoopBackOff
```

### Job Fails

```bash
# Check job status
kubectl get job flyway-migrate

# Check pod logs
kubectl logs -l component=database-migrations

# Delete and retry
kubectl delete job flyway-migrate
kubectl apply -f infrastructure/k8s/apps/oauth2-server/flyway-migration-job.yaml
```

## Conclusion

For most use cases, **init containers are recommended** due to their simplicity and reliability. Use Kubernetes Jobs only if you have specific requirements for one-time execution or are using Helm hooks for orchestration.
