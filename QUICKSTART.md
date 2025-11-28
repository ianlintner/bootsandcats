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

### 1. Health Check
```bash
curl http://localhost:9000/actuator/health/readiness
```

### 2. OIDC Discovery
```bash
curl http://localhost:9000/.well-known/openid-configuration | jq
```

### 3. Get Access Token
```bash
curl -X POST http://localhost:9000/oauth2/token \
  -u m2m-client:CHANGEME \
  -d "grant_type=client_credentials" \
  -d "scope=api:read"
```

### 4. Introspect Token
```bash
curl -X POST http://localhost:9000/oauth2/introspect \
  -u m2m-client:CHANGEME \
  -d "token=YOUR_ACCESS_TOKEN"
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

## 📚 Documentation

- Full Validation: `docs/deployment/validation-complete.md`
- Deployment Guide: `DEPLOYMENT-CHECKLIST.md`
- CI/CD Overview: `docs/ci.md`
- K8s Guide: `k8s/README.md`

## ✅ Validation Status

✅ **DEPLOYED & OPERATIONAL**
- Application: Running
- Database: Connected
- Endpoints: Responding
- Health: UP

