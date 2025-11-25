# Common Issues Runbook

This runbook covers troubleshooting procedures for common issues with the OAuth2 Authorization Server.

## Issue: Service Unavailable (503)

### Symptoms
- 503 Service Unavailable errors
- Health check failures
- No response from server

### Diagnosis

```bash
# 1. Check pod status
kubectl get pods -n oauth2-system -l app=oauth2-server

# 2. Check pod events
kubectl describe pods -n oauth2-system -l app=oauth2-server

# 3. Check readiness probe
kubectl logs -l app=oauth2-server -n oauth2-system | grep -i "readiness"

# 4. Check resource usage
kubectl top pods -n oauth2-system
```

### Resolution

**If pods are not running:**

```bash
# Check deployment status
kubectl get deployment oauth2-server -n oauth2-system

# Scale up if needed
kubectl scale deployment oauth2-server -n oauth2-system --replicas=3

# Check for resource constraints
kubectl describe nodes | grep -A 5 "Allocated resources"
```

**If pods are running but not ready:**

```bash
# Check application logs
kubectl logs -l app=oauth2-server -n oauth2-system --tail=200

# Check database connectivity
kubectl exec -it deployment/oauth2-server -n oauth2-system -- \
  curl -s http://localhost:9000/actuator/health/db
```

---

## Issue: High Error Rate (5xx)

### Symptoms
- Elevated 5xx responses
- Error rate >1%
- Failed token requests

### Diagnosis

```bash
# 1. Check error logs
kubectl logs -l app=oauth2-server -n oauth2-system | grep -i "error\|exception"

# 2. Check Prometheus metrics
curl http://oauth2-server:9000/actuator/prometheus | grep "status=\"5"

# 3. Check database connection pool
curl http://oauth2-server:9000/actuator/prometheus | grep hikaricp
```

### Common Causes and Resolutions

**Database Connection Exhaustion:**

```bash
# Check connection pool
curl http://oauth2-server:9000/actuator/prometheus | grep hikaricp_connections

# If connections pending > 0, increase pool size
# Update ConfigMap
kubectl edit configmap oauth2-server-config -n oauth2-system
# Add: SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE=20

# Restart pods
kubectl rollout restart deployment/oauth2-server -n oauth2-system
```

**Out of Memory:**

```bash
# Check memory usage
kubectl top pods -n oauth2-system

# Check GC metrics
curl http://oauth2-server:9000/actuator/prometheus | grep jvm_gc

# If memory is high, increase limits
kubectl edit deployment oauth2-server -n oauth2-system
# Update: resources.limits.memory: 4Gi
```

**External Dependency Failure:**

```bash
# Check database connectivity
kubectl exec -it deployment/oauth2-server -n oauth2-system -- \
  nc -zv $DATABASE_HOST 5432

# Check Redis connectivity
kubectl exec -it deployment/oauth2-server -n oauth2-system -- \
  nc -zv $REDIS_HOST 6379
```

---

## Issue: High Latency

### Symptoms
- P95 latency >500ms
- Slow response times
- Timeout errors

### Diagnosis

```bash
# 1. Check latency metrics
curl http://oauth2-server:9000/actuator/prometheus | grep http_server_requests_seconds

# 2. Check database query times
kubectl logs -l app=oauth2-server -n oauth2-system | grep -i "slow\|query"

# 3. Check GC pauses
curl http://oauth2-server:9000/actuator/prometheus | grep jvm_gc_pause
```

### Resolution

**Database Slow Queries:**

```bash
# Enable slow query logging
# Connect to database
psql -h $DATABASE_HOST -U $DATABASE_USER -d oauth2db

# Check slow queries
SELECT query, calls, mean_time, total_time 
FROM pg_stat_statements 
ORDER BY mean_time DESC 
LIMIT 10;

# Add missing indexes if needed
CREATE INDEX idx_oauth2_authorization_principal 
ON oauth2_authorization(principal_name);
```

**GC Issues:**

```bash
# Check GC metrics
curl http://oauth2-server:9000/actuator/prometheus | grep jvm_gc

# If excessive GC, increase heap
kubectl edit deployment oauth2-server -n oauth2-system
# Update JAVA_OPTS: -Xmx3g -Xms3g
```

**Connection Pool Starvation:**

```bash
# Check pending connections
curl http://oauth2-server:9000/actuator/prometheus | grep hikaricp_connections_pending

# Increase pool size
kubectl set env deployment/oauth2-server -n oauth2-system \
  SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE=30
```

---

## Issue: Authentication Failures

### Symptoms
- Users cannot log in
- "Invalid credentials" errors
- Password validation failing

### Diagnosis

```bash
# 1. Check authentication logs
kubectl logs -l app=oauth2-server -n oauth2-system | grep -i "auth\|login\|credential"

# 2. Check user exists
# Connect to database
psql -h $DATABASE_HOST -U $DATABASE_USER -d oauth2db

# Query users
SELECT * FROM users WHERE username = 'affected_user';
```

### Resolution

**Password Encoding Issue:**

```bash
# Verify BCrypt encoding
# Password should start with $2a$12$
SELECT password FROM users WHERE username = 'user';

# If encoding is wrong, reset password
# Generate new BCrypt hash
python -c "import bcrypt; print(bcrypt.hashpw(b'newpassword', bcrypt.gensalt(12)))"
```

**Account Locked:**

```bash
# Check account status
SELECT username, enabled, account_non_locked FROM users;

# Unlock account
UPDATE users SET account_non_locked = true WHERE username = 'user';
```

---

## Issue: Token Validation Failures

### Symptoms
- Resource servers reject tokens
- "Invalid token" errors
- JWKS endpoint issues

### Diagnosis

```bash
# 1. Check JWKS endpoint
curl http://oauth2-server:9000/oauth2/jwks

# 2. Verify token structure
# Decode token at jwt.io

# 3. Check issuer URL
curl http://oauth2-server:9000/.well-known/openid-configuration | jq '.issuer'
```

### Resolution

**JWKS Key Mismatch:**

```bash
# If keys rotated, resource servers need updated JWKS
# Force JWKS refresh on resource servers
# Clear JWKS cache

# Check if pod restarted (generates new keys)
kubectl get pods -n oauth2-system -o wide

# For production, ensure persistent key storage
```

**Issuer Mismatch:**

```bash
# Check issuer URL configuration
kubectl get configmap oauth2-server-config -n oauth2-system -o yaml | grep ISSUER

# Update if incorrect
kubectl set env deployment/oauth2-server -n oauth2-system \
  OAUTH2_ISSUER_URL=https://auth.example.com
```

---

## Issue: Client Authentication Failures

### Symptoms
- "Invalid client" errors
- Client credentials rejected
- 401 on token endpoint

### Diagnosis

```bash
# 1. Check client registration
# Connect to database
psql -h $DATABASE_HOST -U $DATABASE_USER -d oauth2db

# Query clients
SELECT client_id, client_secret_expires_at 
FROM oauth2_registered_client 
WHERE client_id = 'affected_client';

# 2. Check authentication method
curl -X POST http://oauth2-server:9000/oauth2/token \
  -u client_id:client_secret \
  -d "grant_type=client_credentials" -v
```

### Resolution

**Incorrect Client Secret:**

```bash
# Verify secret is correctly encoded
# Secret should be BCrypt encoded in database

# Reset client secret
UPDATE oauth2_registered_client 
SET client_secret = '$2a$12$...' 
WHERE client_id = 'client_id';
```

**Wrong Authentication Method:**

```bash
# Check registered authentication methods
SELECT client_authentication_methods 
FROM oauth2_registered_client 
WHERE client_id = 'client_id';

# Update if needed
UPDATE oauth2_registered_client 
SET client_authentication_methods = 'client_secret_basic,client_secret_post'
WHERE client_id = 'client_id';
```

---

## Issue: CORS Errors

### Symptoms
- Browser CORS errors
- "Access-Control-Allow-Origin" missing
- Preflight request failures

### Diagnosis

```bash
# 1. Test CORS headers
curl -X OPTIONS http://oauth2-server:9000/oauth2/token \
  -H "Origin: https://app.example.com" \
  -H "Access-Control-Request-Method: POST" -v

# 2. Check allowed origins in configuration
kubectl get configmap oauth2-server-config -n oauth2-system -o yaml
```

### Resolution

```bash
# Update CORS configuration
kubectl set env deployment/oauth2-server -n oauth2-system \
  CORS_ALLOWED_ORIGINS=https://app.example.com,https://other.example.com

# Or update SecurityConfig.java to allow origins
```

---

## Issue: Rate Limiting

### Symptoms
- 429 Too Many Requests
- Clients getting blocked
- Legitimate traffic rejected

### Diagnosis

```bash
# 1. Check rate limit metrics
curl http://oauth2-server:9000/actuator/prometheus | grep rate_limit

# 2. Check ingress rate limiting
kubectl describe ingress oauth2-server -n oauth2-system
```

### Resolution

```bash
# Increase rate limits in ingress
kubectl edit ingress oauth2-server -n oauth2-system
# Update: nginx.ingress.kubernetes.io/rate-limit: "200"

# Or configure application-level rate limiting
```

---

## Issue: Certificate Expiry

### Symptoms
- TLS errors
- "Certificate expired" messages
- HTTPS failing

### Diagnosis

```bash
# 1. Check certificate expiry
kubectl get certificate -n oauth2-system
kubectl describe certificate oauth2-server-tls -n oauth2-system

# 2. Check cert-manager logs
kubectl logs -l app=cert-manager -n cert-manager
```

### Resolution

```bash
# Force certificate renewal
kubectl delete certificate oauth2-server-tls -n oauth2-system

# Wait for cert-manager to recreate
kubectl get certificate -n oauth2-system -w

# If manual certificate, update secret
kubectl create secret tls oauth2-server-tls \
  --cert=new-cert.pem \
  --key=new-key.pem \
  -n oauth2-system \
  --dry-run=client -o yaml | kubectl apply -f -
```

---

## Quick Reference Commands

```bash
# Pod status
kubectl get pods -n oauth2-system

# Recent logs
kubectl logs -l app=oauth2-server -n oauth2-system --tail=100

# Health check
curl http://oauth2-server:9000/actuator/health

# Restart pods
kubectl rollout restart deployment/oauth2-server -n oauth2-system

# Scale up
kubectl scale deployment oauth2-server -n oauth2-system --replicas=5

# Check events
kubectl get events -n oauth2-system --sort-by='.lastTimestamp'

# Port forward for debugging
kubectl port-forward svc/oauth2-server 9000:80 -n oauth2-system
```

---

## Next Steps

- [Scaling Runbook](scaling.md) - Scaling procedures
- [Backup & Restore](backup-restore.md) - Data recovery
- [Certificate Rotation](certificate-rotation.md) - Certificate management
- [Incident Response](../incident-response.md) - Incident procedures
