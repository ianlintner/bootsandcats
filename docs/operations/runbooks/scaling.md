# Scaling Runbook

This runbook covers scaling procedures for the OAuth2 Authorization Server.

## When to Scale

### Scale Up Indicators

| Metric | Threshold | Action |
|--------|-----------|--------|
| CPU Utilization | > 70% sustained | Add replicas |
| Memory Utilization | > 80% sustained | Add replicas or increase limits |
| Request Latency P95 | > 500ms | Add replicas |
| DB Connection Pool | > 80% utilized | Increase pool or replicas |
| Pod Pending | > 0 for 5 min | Check resources or add nodes |

### Scale Down Indicators

| Metric | Threshold | Action |
|--------|-----------|--------|
| CPU Utilization | < 30% sustained | Reduce replicas |
| Memory Utilization | < 40% sustained | Reduce replicas |
| Request Rate | < 50% of capacity | Reduce replicas |

---

## Horizontal Scaling (Replicas)

### Check Current State

```bash
# Get current deployment status
kubectl get deployment oauth2-server -n oauth2-system

# Get HPA status
kubectl get hpa oauth2-server -n oauth2-system

# Get pod distribution across nodes
kubectl get pods -n oauth2-system -o wide
```

### Manual Scaling

```bash
# Scale to 5 replicas
kubectl scale deployment oauth2-server -n oauth2-system --replicas=5

# Verify scaling
kubectl get pods -n oauth2-system -w

# Check rollout status
kubectl rollout status deployment/oauth2-server -n oauth2-system
```

### Update HPA Limits

```bash
# Edit HPA
kubectl edit hpa oauth2-server -n oauth2-system

# Or apply new configuration
cat <<EOF | kubectl apply -f -
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: oauth2-server
  namespace: oauth2-system
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: oauth2-server
  minReplicas: 5
  maxReplicas: 20
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80
EOF
```

---

## Vertical Scaling (Resources)

### Check Current Resources

```bash
# Get current resource configuration
kubectl get deployment oauth2-server -n oauth2-system -o yaml | grep -A 10 resources

# Check actual usage
kubectl top pods -n oauth2-system
```

### Update Resource Limits

```bash
# Update deployment resources
kubectl patch deployment oauth2-server -n oauth2-system --type='json' -p='[
  {
    "op": "replace",
    "path": "/spec/template/spec/containers/0/resources",
    "value": {
      "requests": {
        "cpu": "1000m",
        "memory": "2Gi"
      },
      "limits": {
        "cpu": "2000m",
        "memory": "4Gi"
      }
    }
  }
]'

# Verify rollout
kubectl rollout status deployment/oauth2-server -n oauth2-system
```

### JVM Heap Sizing

```bash
# Update JVM settings
kubectl set env deployment/oauth2-server -n oauth2-system \
  JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0"

# For explicit sizing
kubectl set env deployment/oauth2-server -n oauth2-system \
  JAVA_OPTS="-Xms2g -Xmx3g -XX:+UseG1GC"
```

---

## Database Connection Pool Scaling

### Check Current Pool Status

```bash
# Get current pool metrics
curl http://oauth2-server:9000/actuator/prometheus | grep hikaricp

# Key metrics
# hikaricp_connections_active
# hikaricp_connections_idle
# hikaricp_connections_pending
# hikaricp_connections_max
```

### Scale Connection Pool

```bash
# Update pool size via environment variable
kubectl set env deployment/oauth2-server -n oauth2-system \
  SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE=30 \
  SPRING_DATASOURCE_HIKARI_MINIMUMIDLE=10

# Calculate recommended pool size
# Recommended: (core_count * 2) + effective_spindle_count
# For SSD: core_count * 4
```

### Database Server Scaling

**AWS RDS:**

```bash
# Scale RDS instance
aws rds modify-db-instance \
  --db-instance-identifier oauth2-postgres \
  --db-instance-class db.r6g.xlarge \
  --apply-immediately
```

**Azure PostgreSQL:**

```bash
# Scale Azure PostgreSQL
az postgres flexible-server update \
  --resource-group oauth2-prod-rg \
  --name oauth2-postgres \
  --sku-name Standard_D4s_v3
```

**Google Cloud SQL:**

```bash
# Scale Cloud SQL
gcloud sql instances patch oauth2-postgres \
  --tier db-custom-4-16384
```

---

## Node Pool Scaling

### Check Node Capacity

```bash
# Get node resource usage
kubectl top nodes

# Get node allocatable resources
kubectl describe nodes | grep -A 5 "Allocatable:"

# Check pending pods
kubectl get pods -n oauth2-system -o wide | grep Pending
```

### Scale Node Pool

**AWS EKS:**

```bash
# Scale managed node group
aws eks update-nodegroup-config \
  --cluster-name oauth2-eks-cluster \
  --nodegroup-name oauth2-ng \
  --scaling-config minSize=5,maxSize=15,desiredSize=8
```

**Azure AKS:**

```bash
# Scale AKS node pool
az aks nodepool update \
  --resource-group oauth2-prod-rg \
  --cluster-name oauth2-aks-cluster \
  --name default \
  --min-count 5 \
  --max-count 15
```

**Google GKE:**

```bash
# Scale GKE node pool
gcloud container clusters resize oauth2-gke-cluster \
  --node-pool default-pool \
  --num-nodes 8 \
  --region us-central1
```

---

## Pre-Scaling Checklist

### Before Scale Up

- [ ] Verify database can handle additional connections
- [ ] Check node capacity for new pods
- [ ] Verify PodDisruptionBudget won't block scaling
- [ ] Check if dependent services can handle increased load
- [ ] Review current error rate and latency

### Before Scale Down

- [ ] Verify traffic has decreased
- [ ] Check no pending deployments
- [ ] Verify PodDisruptionBudget is respected
- [ ] Ensure minimum replicas for HA
- [ ] Check maintenance windows

---

## Scaling for Events

### Planned Traffic Spike

1. **24 hours before:**
   ```bash
   # Pre-warm by scaling up
   kubectl scale deployment oauth2-server -n oauth2-system --replicas=10
   
   # Increase HPA limits
   kubectl patch hpa oauth2-server -n oauth2-system \
     -p '{"spec":{"maxReplicas":20}}'
   ```

2. **During event:**
   - Monitor dashboards closely
   - Be ready for manual intervention
   - Keep on-call team informed

3. **After event:**
   ```bash
   # Return to normal scaling
   kubectl patch hpa oauth2-server -n oauth2-system \
     -p '{"spec":{"maxReplicas":10}}'
   
   # Allow HPA to scale down naturally
   ```

### Emergency Scaling

```bash
# Immediate scale up during incident
kubectl scale deployment oauth2-server -n oauth2-system --replicas=15

# Disable HPA temporarily if needed
kubectl delete hpa oauth2-server -n oauth2-system

# Re-enable after stabilization
kubectl apply -f hpa.yaml
```

---

## Monitoring Scaling

### During Scale Up

```bash
# Watch pod creation
kubectl get pods -n oauth2-system -w

# Check rollout progress
kubectl rollout status deployment/oauth2-server -n oauth2-system

# Monitor metrics
watch "curl -s http://oauth2-server:9000/actuator/prometheus | grep -E 'cpu|memory|request'"
```

### Verify Scaling Success

```bash
# Check all pods are ready
kubectl get pods -n oauth2-system | grep -v Running

# Verify load distribution
curl http://oauth2-server:9000/actuator/prometheus | grep http_server_requests

# Check latency improvement
curl http://oauth2-server:9000/actuator/prometheus | grep http_server_requests_seconds
```

---

## Scaling Limits

### Recommended Limits

| Environment | Min Replicas | Max Replicas | CPU per Pod | Memory per Pod |
|-------------|--------------|--------------|-------------|----------------|
| Development | 1 | 2 | 250m | 512Mi |
| Staging | 2 | 5 | 500m | 1Gi |
| Production | 3 | 20 | 1000m | 2Gi |

### Hard Limits

| Resource | Limit | Reason |
|----------|-------|--------|
| Max Replicas | 50 | Database connection limits |
| Max CPU per Pod | 4000m | Diminishing returns |
| Max Memory per Pod | 8Gi | JVM efficiency |
| DB Connections per Pod | 20 | Pool efficiency |

---

## Next Steps

- [Common Issues](common-issues.md) - Troubleshooting
- [Backup & Restore](backup-restore.md) - Data recovery
- [SLOs](../slos.md) - Service Level Objectives
