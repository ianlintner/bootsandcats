# Security Best Practices

This document provides comprehensive security best practices for deploying and operating the OAuth2 Authorization Server in production.

## Security Checklist

### Pre-Deployment

- [ ] All default credentials replaced
- [ ] Secrets stored in secret manager
- [ ] TLS/HTTPS enabled
- [ ] CORS configured correctly
- [ ] Rate limiting configured
- [ ] Network policies applied
- [ ] Security scanning passed
- [ ] Penetration testing completed

### Production

- [ ] Running as non-root user
- [ ] Read-only root filesystem
- [ ] Resource limits set
- [ ] Audit logging enabled
- [ ] Monitoring and alerting active
- [ ] Backup and recovery tested
- [ ] Incident response plan documented

---

## Credential Management

### Replace Default Credentials

!!! danger "Critical"
    Never use default credentials in production. All examples in this documentation use demo credentials that must be replaced.

```bash
# Generate secure secrets
openssl rand -base64 32  # For client secrets
openssl rand -base64 24  # For passwords

# Store in secret manager
aws secretsmanager create-secret \
  --name oauth2/demo-client-secret \
  --secret-string "$(openssl rand -base64 32)"
```

### Secret Rotation

| Secret Type | Rotation Frequency | Procedure |
|-------------|-------------------|-----------|
| Client Secrets | 90 days | [Certificate Rotation Runbook](operations/runbooks/certificate-rotation.md) |
| JWT Signing Keys | 90 days | [Certificate Rotation Runbook](operations/runbooks/certificate-rotation.md) |
| Database Passwords | 90 days | Cloud provider secret rotation |
| TLS Certificates | Annually | cert-manager auto-renewal |

### Environment Variables

```yaml
# Never commit secrets to code
# Use Kubernetes secrets
env:
  - name: DATABASE_PASSWORD
    valueFrom:
      secretKeyRef:
        name: oauth2-secrets
        key: database-password
  - name: OAUTH2_CLIENT_SECRET
    valueFrom:
      secretKeyRef:
        name: oauth2-secrets
        key: client-secret
```

---

## Network Security

### TLS Configuration

```yaml
# Require TLS 1.2+
server.ssl.enabled-protocols=TLSv1.3,TLSv1.2
server.ssl.ciphers=TLS_AES_256_GCM_SHA384,TLS_AES_128_GCM_SHA256

# HSTS header
Strict-Transport-Security: max-age=31536000; includeSubDomains; preload
```

### Network Policies

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: oauth2-server
spec:
  podSelector:
    matchLabels:
      app: oauth2-server
  policyTypes:
    - Ingress
    - Egress
  ingress:
    # Only allow from ingress controller
    - from:
        - namespaceSelector:
            matchLabels:
              name: ingress-nginx
      ports:
        - port: 9000
    # Allow Prometheus scraping
    - from:
        - namespaceSelector:
            matchLabels:
              name: monitoring
      ports:
        - port: 9000
  egress:
    # Only allow to database
    - to:
        - namespaceSelector:
            matchLabels:
              name: database
      ports:
        - port: 5432
    # Allow DNS
    - to:
        - namespaceSelector: {}
      ports:
        - port: 53
          protocol: UDP
```

### Firewall Rules

| Source | Destination | Port | Purpose |
|--------|-------------|------|---------|
| Internet | Load Balancer | 443 | HTTPS traffic |
| Load Balancer | OAuth2 Pods | 9000 | Application traffic |
| OAuth2 Pods | PostgreSQL | 5432 | Database |
| OAuth2 Pods | Redis | 6379 | Cache |
| Prometheus | OAuth2 Pods | 9000 | Metrics scraping |

---

## Container Security

### Pod Security Standards

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: oauth2-server
spec:
  securityContext:
    runAsNonRoot: true
    runAsUser: 1001
    runAsGroup: 1001
    fsGroup: 1001
    seccompProfile:
      type: RuntimeDefault
  containers:
    - name: oauth2-server
      securityContext:
        allowPrivilegeEscalation: false
        readOnlyRootFilesystem: true
        capabilities:
          drop:
            - ALL
      volumeMounts:
        - name: tmp
          mountPath: /tmp
  volumes:
    - name: tmp
      emptyDir: {}
```

### Image Security

```dockerfile
# Use minimal base image
FROM eclipse-temurin:21-jre-alpine

# Run as non-root
RUN addgroup -g 1001 appgroup && \
    adduser -u 1001 -G appgroup -s /bin/sh -D appuser
USER appuser

# No secrets in image
# Use environment variables or mounted secrets
```

### Vulnerability Scanning

```bash
# Scan with Trivy
trivy image oauth2-server:latest

# Scan with Grype
grype oauth2-server:latest

# In CI/CD pipeline
- name: Scan image
  run: |
    trivy image --exit-code 1 --severity HIGH,CRITICAL oauth2-server:latest
```

---

## OAuth2 Security

### PKCE Enforcement

PKCE is **required** for public clients (SPAs, mobile apps):

```java
// Client configuration
ClientSettings.builder()
    .requireProofKey(true)  // Enforce PKCE
    .build()
```

### Token Security

| Setting | Recommendation | Reason |
|---------|----------------|--------|
| Access Token Lifetime | 5-15 minutes | Minimize exposure window |
| Refresh Token Lifetime | 1-24 hours | Balance security and UX |
| Refresh Token Rotation | Enabled | Detect token theft |
| Token Binding | Consider | Prevent token export |

```java
TokenSettings.builder()
    .accessTokenTimeToLive(Duration.ofMinutes(15))
    .refreshTokenTimeToLive(Duration.ofHours(24))
    .reuseRefreshTokens(false)  // Rotate refresh tokens
    .build()
```

### Scope Validation

```java
// Only grant requested scopes if client is authorized
if (!client.getScopes().containsAll(requestedScopes)) {
    throw new OAuth2AuthorizationException("invalid_scope");
}
```

---

## Input Validation

### Request Validation

```java
// Validate redirect URI
if (!registeredRedirectUris.contains(requestedRedirectUri)) {
    throw new OAuth2AuthorizationException("invalid_redirect_uri");
}

// Validate client ID format
if (!clientId.matches("^[a-zA-Z0-9-_]{8,64}$")) {
    throw new OAuth2AuthorizationException("invalid_client");
}
```

### Content Security Policy

```java
// Security headers configuration
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) {
    http.headers(headers -> headers
        .contentSecurityPolicy(csp -> csp
            .policyDirectives("default-src 'self'; " +
                "script-src 'self'; " +
                "style-src 'self' 'unsafe-inline'; " +
                "img-src 'self' data:; " +
                "frame-ancestors 'none'"))
        .frameOptions(frame -> frame.deny())
        .xssProtection(xss -> xss.block(true))
        .contentTypeOptions(contentType -> {})
    );
    return http.build();
}
```

---

## Rate Limiting

### Application Level

```yaml
# Using Resilience4j or Spring Cloud Gateway
resilience4j.ratelimiter:
  instances:
    tokenEndpoint:
      limitForPeriod: 100
      limitRefreshPeriod: 60s
      timeoutDuration: 0s
    authorizeEndpoint:
      limitForPeriod: 30
      limitRefreshPeriod: 60s
```

### Infrastructure Level (Ingress)

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  annotations:
    nginx.ingress.kubernetes.io/limit-rps: "10"
    nginx.ingress.kubernetes.io/limit-connections: "5"
    nginx.ingress.kubernetes.io/limit-rpm: "100"
```

### WAF Rules

```bash
# AWS WAF rate limiting
aws wafv2 create-rule \
  --name rate-limit-oauth \
  --action Block \
  --statement '{"RateBasedStatement": {"Limit": 1000, "AggregateKeyType": "IP"}}'
```

---

## Audit Logging

### Security Events to Log

| Event | Level | Data to Log |
|-------|-------|-------------|
| Login success | INFO | Username, client, IP, timestamp |
| Login failure | WARN | Username, client, IP, reason |
| Token issued | INFO | Client, grant type, scopes |
| Token revoked | INFO | Client, token type |
| Authorization denied | WARN | Client, reason |
| Invalid client | WARN | Attempted client ID, IP |

### Log Format

```json
{
  "timestamp": "2024-01-15T10:30:00.000Z",
  "level": "WARN",
  "event": "login_failure",
  "username": "user@example.com",
  "client_id": "demo-client",
  "ip_address": "192.168.1.100",
  "reason": "invalid_credentials",
  "trace_id": "abc123"
}
```

### What NOT to Log

- Passwords (even hashed)
- Full access tokens
- Full refresh tokens
- Authorization codes
- Client secrets
- Personal identifiable information (PII)

---

## Compliance

### OWASP Top 10 Mitigations

| Risk | Mitigation |
|------|------------|
| A01 Broken Access Control | Role-based access, scope validation |
| A02 Cryptographic Failures | TLS 1.3, AES-256, RSA-2048+ |
| A03 Injection | Parameterized queries, input validation |
| A04 Insecure Design | Threat modeling, security reviews |
| A05 Security Misconfiguration | Hardened defaults, security scanning |
| A06 Vulnerable Components | Dependency scanning, updates |
| A07 Authentication Failures | PKCE, rate limiting, lockout |
| A08 Integrity Failures | Signed tokens, code signing |
| A09 Logging Failures | Comprehensive audit logging |
| A10 SSRF | URL validation, network policies |

### OAuth2 Security Best Practices (RFC 6819)

- Use authorization code flow with PKCE
- Validate redirect URIs exactly
- Use short-lived access tokens
- Rotate refresh tokens
- Validate token binding
- Implement token revocation
- Use secure token storage

---

## Incident Response

### Security Incident Indicators

| Indicator | Severity | Action |
|-----------|----------|--------|
| Spike in failed logins | Medium | Investigate, consider lockout |
| Token from unknown IP | Low | Log for review |
| Mass token revocation | Medium | Investigate compromise |
| JWKS endpoint attack | High | Rate limit, block IPs |
| SQL injection attempt | High | Block IP, review logs |

### Response Procedures

1. **Detect** - Monitor alerts and anomalies
2. **Contain** - Block malicious actors, revoke tokens
3. **Investigate** - Analyze logs and traces
4. **Eradicate** - Remove threat, patch vulnerabilities
5. **Recover** - Restore service, rotate credentials
6. **Learn** - Post-incident review, improve defenses

See [Incident Response](operations/incident-response.md) for detailed procedures.

---

## Security Testing

### Regular Testing

| Test Type | Frequency | Scope |
|-----------|-----------|-------|
| Dependency scanning | Daily | All dependencies |
| Container scanning | On build | Docker images |
| SAST | On commit | Source code |
| DAST | Weekly | Running application |
| Penetration testing | Quarterly | Full application |

### Testing Commands

```bash
# OWASP Dependency Check
./gradlew dependencyCheckAnalyze

# SpotBugs with FindSecBugs
./gradlew spotbugsMain

# Container scan
trivy image oauth2-server:latest

# OWASP ZAP scan
docker run -t owasp/zap2docker-stable zap-baseline.py \
  -t https://auth.example.com
```

---

## Next Steps

- [Architecture Security](architecture/security.md) - Detailed security architecture
- [Configuration](configuration.md) - Security configuration options
- [Incident Response](operations/incident-response.md) - Security incident procedures
- [Certificate Rotation](operations/runbooks/certificate-rotation.md) - Key management
