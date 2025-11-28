# OAuth2 Server - Quick Reference

## 🚀 Service Status
```bash
kubectl get pods -n default -l app=oauth2-server
# Expected: 2/2 Running
```

## 🔗 Access the Service

### Port Forward (for local testing)
```bash
kubectl port-forward service/oauth2-server 9000:9000 -n default
```

### Internal Service URL
```
http://oauth2-server.default.svc.cluster.local:9000
```

## 📍 Key Endpoints

| Endpoint | URL |
|----------|-----|
| **OIDC Discovery** | `/.well-known/openid-configuration` |
| **Health** | `/actuator/health/readiness` |
| **Authorization** | `/oauth2/authorize` |
| **Token** | `/oauth2/token` |
| **JWKS** | `/oauth2/jwks` |
| **UserInfo** | `/userinfo` |
| **Metrics** | `/actuator/prometheus` |

## 🔑 Demo Credentials

### OAuth2 Clients
```
Demo Client:   demo-client / CHANGEME
M2M Client:    m2m-client / CHANGEME
Public Client: public-client (no secret)
```

### Demo Users
```
User:  user / CHANGEME
Admin: admin / CHANGEME
```

**⚠️ Change these before production use!**

## 🧪 Quick Tests

### Automated Smoke Test Suite ✅
```bash
# Run comprehensive smoke tests
./scripts/smoke-test.sh
```

**Last Test Results**: ✅ All tests passed (6/6)  
**Full Report**: `docs/deployment/smoke-test-results.md`

### Manual Tests

#### 1. Health Check
```bash
curl http://localhost:9000/actuator/health/readiness
# Expected: {"status":"UP"}
```

#### 2. OIDC Discovery
```bash
curl http://localhost:9000/.well-known/openid-configuration | jq
```

#### 3. Get Access Token
```bash
curl -X POST http://localhost:9000/oauth2/token \
  -u m2m-client:CHANGEME \
  -d "grant_type=client_credentials" \
  -d "scope=api:read"
# Expected: {"access_token":"eyJ...", "token_type":"Bearer", "expires_in":3600}
```

#### 4. Introspect Token
```bash
TOKEN="your_token_here"
curl -X POST http://localhost:9000/oauth2/introspect \
  -u m2m-client:CHANGEME \
  -d "token=$TOKEN"
# Expected: {"active":true, "client_id":"m2m-client", "scope":"api:read"}
```

#### 5. Complete Flow Test
```bash
# Get token and introspect in one command
TOKEN=$(curl -s -X POST http://localhost:9000/oauth2/token \
  -u m2m-client:CHANGEME \
  -d "grant_type=client_credentials" \
  -d "scope=api:read" | jq -r '.access_token')

curl -X POST http://localhost:9000/oauth2/introspect \
  -u m2m-client:CHANGEME \
  -d "token=$TOKEN" | jq
```

## 🐛 Troubleshooting

### Check Logs
```bash
kubectl logs -f -l app=oauth2-server -n default -c oauth2-server
```

### Restart Pod
```bash
kubectl rollout restart deployment/oauth2-server -n default
```

### Check Database
```bash
kubectl exec -it $(kubectl get pod -l app=postgres -n default -o name) -n default -c postgres -- psql -U oauth2user -d oauth2db
```

## 📊 Monitoring

### Pod Status
```bash
kubectl get pods -n default -l app=oauth2-server -o wide
```

### Resource Usage
```bash
kubectl top pods -n default -l app=oauth2-server
```

### Events
```bash
kubectl get events -n default --sort-by='.lastTimestamp' | grep oauth2-server
```

## 🔧 Common Tasks

### Update Secrets
```bash
kubectl edit secret oauth2-secrets -n default
kubectl rollout restart deployment/oauth2-server -n default
```

### Update Issuer URL
```bash
kubectl edit configmap oauth2-config -n default
kubectl rollout restart deployment/oauth2-server -n default
```

### Scale Replicas
```bash
kubectl scale deployment oauth2-server -n default --replicas=2
```

## 🎯 Next Steps

### Immediate (Security)
1. **Update Default Passwords** ⚠️ **CRITICAL**
   ```bash
   # Generate new secure passwords
   kubectl create secret generic oauth2-secrets \
     --from-literal=database-password="$(openssl rand -base64 32)" \
     --from-literal=demo-client-secret="$(openssl rand -base64 32)" \
     --from-literal=m2m-client-secret="$(openssl rand -base64 32)" \
     --from-literal=demo-user-password="$(openssl rand -base64 16)" \
     --from-literal=admin-user-password="$(openssl rand -base64 16)" \
     --from-literal=database-url="jdbc:postgresql://postgres:5432/oauth2db" \
     --from-literal=database-username="oauth2user" \
     -n default --dry-run=client -o yaml | kubectl apply -f -
   
   # Restart to apply new secrets
   kubectl rollout restart deployment/oauth2-server -n default
   kubectl rollout restart deployment/postgres -n default
   ```

2. **Update Issuer URL**
   ```bash
   # Change from example.com to your actual domain
   kubectl edit configmap oauth2-config -n default
   # Update: issuer-url: "https://oauth.yourdomain.com"
   
   kubectl rollout restart deployment/oauth2-server -n default
   ```

### Production Readiness
3. **Add Ingress for External Access**
   - Configure Istio Gateway or Ingress Controller
   - Set up TLS certificates (Let's Encrypt or custom)
   - Configure DNS records

4. **Enable Monitoring**
   - Connect Prometheus to `/actuator/prometheus`
   - Set up Grafana dashboards
   - Configure alerts for pod failures, high memory, etc.

5. **Set Up Database Backups**
   - Configure PostgreSQL backups (Azure Backup, Velero, or pg_dump)
   - Test restore procedures
   - Document recovery runbook

6. **Scale for High Availability**
   ```bash
   # When more CPU is available
   kubectl scale deployment oauth2-server -n default --replicas=3
   
   # Configure HPA
   kubectl autoscale deployment oauth2-server -n default \
     --cpu-percent=70 --min=2 --max=10
   ```

### Optional Enhancements
7. **Enable OpenTelemetry** (for distributed tracing)
   - Add missing `opentelemetry-semconv` dependency
   - Re-enable OTEL in deployment
   - Connect to Jaeger or similar backend

8. **Add Redis** (for sessions and rate limiting)
   - Deploy Redis cluster
   - Configure Spring Session with Redis
   - Implement rate limiting

9. **Customize UI**
   - Add custom login/consent pages
   - Configure branding and themes
   - Add multi-language support

10. **Create Staging Environment**
    - Deploy to separate namespace
    - Use different database
    - Test changes before production

## 📚 Documentation

- **Full Validation**: `docs/deployment/validation-complete.md`
- **Deployment Checklist**: `DEPLOYMENT-CHECKLIST.md`
- **CI/CD Pipeline**: `docs/ci.md`
- **Kubernetes Guide**: `k8s/README.md`
- **Azure Setup**: `docs/deployment/azure-setup.md`

## ✅ Current Status

**DEPLOYED & OPERATIONAL** ✅
- ✅ Application: Running (1 replica)
- ✅ Database: PostgreSQL connected
- ✅ Health Probes: Passing
- ✅ OIDC Endpoints: Responding
- ⚠️ **Action Required**: Update default credentials
- ⚠️ **Action Required**: Configure external access (Ingress)

