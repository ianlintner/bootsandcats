# Security Architecture

This document details the security architecture of the OAuth2 Authorization Server, covering authentication, authorization, token security, and infrastructure security.

## Security Model Overview

```mermaid
graph TB
    subgraph "Security Layers"
        subgraph "Layer 1: Network Security"
            TLS[TLS 1.3 Encryption]
            WAF[Web Application Firewall]
            DDOS[DDoS Protection]
            NetworkPolicy[K8s Network Policies]
        end
        
        subgraph "Layer 2: Application Security"
            CSRF[CSRF Protection]
            Headers[Security Headers]
            InputValidation[Input Validation]
            RateLimiting[Rate Limiting]
        end
        
        subgraph "Layer 3: Authentication"
            OAuth2Auth[OAuth2 Authentication]
            PKCEValidation[PKCE Validation]
            ClientAuth[Client Authentication]
            UserAuth[User Authentication]
        end
        
        subgraph "Layer 4: Authorization"
            ScopeValidation[Scope Validation]
            ConsentManagement[Consent Management]
            TokenValidation[Token Validation]
        end
        
        subgraph "Layer 5: Data Security"
            Encryption[Encryption at Rest]
            SecretManagement[Secret Management]
            KeyRotation[Key Rotation]
        end
    end
    
    TLS --> CSRF
    CSRF --> OAuth2Auth
    OAuth2Auth --> ScopeValidation
    ScopeValidation --> Encryption
```

## Authentication Security

### Password Security

```mermaid
graph LR
    Password[User Password] --> BCrypt[BCrypt Hash]
    BCrypt --> CostFactor[Cost Factor: 12]
    CostFactor --> Salt[Random Salt]
    Salt --> Hash[Stored Hash]
    
    style BCrypt fill:#90EE90
    style CostFactor fill:#90EE90
```

| Security Measure | Implementation |
|------------------|----------------|
| Password Hashing | BCrypt with cost factor 12 |
| Salt | Automatically generated per password |
| Min Length | Configurable (recommended: 12+) |
| Complexity | Configurable policies |

### Client Authentication Methods

```mermaid
graph TB
    Client[OAuth2 Client] --> Method{Auth Method}
    
    Method -->|Confidential| BasicAuth[client_secret_basic]
    Method -->|Confidential| PostAuth[client_secret_post]
    Method -->|Public| PKCE[PKCE Only]
    Method -->|High Security| PrivateKeyJWT[private_key_jwt]
    
    BasicAuth --> SecretValidation[Validate Secret]
    PostAuth --> SecretValidation
    PKCE --> PKCEValidation[Validate code_verifier]
    PrivateKeyJWT --> JWTValidation[Validate JWT Signature]
```

| Client Type | Authentication Method | Use Case |
|-------------|----------------------|----------|
| Confidential Server | `client_secret_basic` | Server-side applications |
| Confidential Server | `client_secret_post` | Legacy systems |
| Public Client | `none` + PKCE | SPAs, mobile apps |
| High Security | `private_key_jwt` | High-security integrations |

## Token Security

### JWT Token Structure

```mermaid
graph LR
    subgraph "JWT Token"
        Header[Header<br/>alg: RS256]
        Payload[Payload<br/>Claims]
        Signature[Signature<br/>RSA-SHA256]
    end
    
    Header --> Base64H[Base64URL]
    Payload --> Base64P[Base64URL]
    PrivateKey[Private Key] --> Signature
    
    Base64H --> Token[eyJhbGci...eyJzdWIi...SflKxwR...]
    Base64P --> Token
    Signature --> Token
```

### Token Claims

| Claim | Type | Description |
|-------|------|-------------|
| `iss` | Standard | Issuer identifier |
| `sub` | Standard | Subject (user/client ID) |
| `aud` | Standard | Audience (intended recipient) |
| `exp` | Standard | Expiration timestamp |
| `iat` | Standard | Issued at timestamp |
| `nbf` | Standard | Not before timestamp |
| `jti` | Standard | JWT ID (unique identifier) |
| `scope` | OAuth2 | Granted scopes |
| `client_id` | OAuth2 | Client identifier |

### Token Lifetimes

| Token Type | Default Lifetime | Recommended Production |
|------------|------------------|----------------------|
| Access Token | 15 minutes | 5-15 minutes |
| Refresh Token | 7 days | 1-24 hours |
| Authorization Code | 5 minutes | 5 minutes |
| ID Token | 15 minutes | 15 minutes |

### Key Management

```mermaid
graph TB
    subgraph "Key Rotation Strategy"
        Current[Current Key Pair] -->|Signs new tokens| NewTokens[New Tokens]
        Previous[Previous Key Pair] -->|Validates existing| OldTokens[Existing Tokens]
        
        subgraph "JWKS Endpoint"
            CurrentPub[Current Public Key]
            PreviousPub[Previous Public Key]
        end
    end
    
    subgraph "Key Storage"
        KMS[Cloud KMS / HSM]
        SecretManager[Secret Manager]
    end
    
    Current --> KMS
    Previous --> KMS
    KMS --> SecretManager
```

!!! warning "Key Rotation"
    In the default configuration, RSA keys are generated on startup. For production, 
    implement persistent key storage with regular rotation (recommended: 90 days).

## Security Headers

The server implements comprehensive security headers:

```yaml
# Security Headers Configuration
Security-Headers:
  Content-Security-Policy: |
    default-src 'self';
    script-src 'self';
    style-src 'self' 'unsafe-inline';
    img-src 'self' data:;
    font-src 'self';
    frame-ancestors 'none';
    base-uri 'self';
    form-action 'self'
  
  Strict-Transport-Security: max-age=31536000; includeSubDomains; preload
  X-Content-Type-Options: nosniff
  X-Frame-Options: DENY
  X-XSS-Protection: 1; mode=block
  Referrer-Policy: strict-origin-when-cross-origin
  Permissions-Policy: camera=(), microphone=(), geolocation=()
```

## PKCE Security

PKCE (Proof Key for Code Exchange) prevents authorization code interception attacks:

```mermaid
sequenceDiagram
    participant Client
    participant AuthServer as Authorization Server
    
    Note over Client: Generate random<br/>code_verifier
    Client->>Client: code_verifier = random(43-128 chars)
    Client->>Client: code_challenge = SHA256(code_verifier)
    
    Client->>AuthServer: /authorize?code_challenge=X&code_challenge_method=S256
    AuthServer->>AuthServer: Store code_challenge
    AuthServer->>Client: authorization_code
    
    Client->>AuthServer: /token?code=Y&code_verifier=Z
    AuthServer->>AuthServer: Verify: SHA256(code_verifier) == stored_challenge
    
    alt Verification Success
        AuthServer->>Client: Tokens
    else Verification Failed
        AuthServer->>Client: 400 Invalid Grant
    end
```

| Parameter | Requirements |
|-----------|-------------|
| `code_verifier` | 43-128 characters, [A-Z], [a-z], [0-9], `-`, `.`, `_`, `~` |
| `code_challenge` | Base64URL-encoded SHA256 hash of code_verifier |
| `code_challenge_method` | `S256` (plain not recommended) |

## CSRF Protection

```mermaid
graph LR
    subgraph "CSRF Protection"
        Form[Login Form] -->|CSRF Token| Server[Server]
        Server -->|Validate Token| Session[Session]
    end
    
    subgraph "Excluded Endpoints"
        Token[/oauth2/token]
        Introspect[/oauth2/introspect]
        Revoke[/oauth2/revoke]
    end
    
    Note[Token endpoints use<br/>client authentication instead]
```

| Endpoint | CSRF Protection | Reason |
|----------|----------------|--------|
| `/oauth2/authorize` | ✅ Enabled | Browser-based flow |
| `/login` | ✅ Enabled | Form-based login |
| `/oauth2/token` | ❌ Disabled | Uses client authentication |
| `/oauth2/introspect` | ❌ Disabled | Uses client authentication |
| `/oauth2/revoke` | ❌ Disabled | Uses client authentication |

## Rate Limiting

Implement rate limiting to prevent abuse:

```mermaid
graph TB
    Request[Incoming Request] --> RateLimit{Rate Limit Check}
    
    RateLimit -->|Under Limit| Process[Process Request]
    RateLimit -->|Over Limit| Reject[429 Too Many Requests]
    
    subgraph "Rate Limit Rules"
        TokenEndpoint[Token Endpoint: 100/min per client]
        AuthEndpoint[Authorize Endpoint: 30/min per IP]
        LoginEndpoint[Login Endpoint: 10/min per IP]
    end
```

| Endpoint | Rate Limit | Window | Key |
|----------|------------|--------|-----|
| `/oauth2/token` | 100 requests | 1 minute | Client ID |
| `/oauth2/authorize` | 30 requests | 1 minute | IP Address |
| `/login` | 10 requests | 1 minute | IP Address |
| `/oauth2/introspect` | 500 requests | 1 minute | Client ID |

## Network Security

### Kubernetes Network Policies

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: oauth2-server-policy
spec:
  podSelector:
    matchLabels:
      app: oauth2-server
  policyTypes:
    - Ingress
    - Egress
  ingress:
    - from:
        - namespaceSelector:
            matchLabels:
              name: ingress-nginx
        - podSelector:
            matchLabels:
              app: prometheus
      ports:
        - port: 9000
  egress:
    - to:
        - namespaceSelector:
            matchLabels:
              name: database
      ports:
        - port: 5432
    - to:
        - namespaceSelector:
            matchLabels:
              name: redis
      ports:
        - port: 6379
```

### TLS Configuration

```mermaid
graph LR
    Client -->|TLS 1.3| LB[Load Balancer]
    LB -->|mTLS| Pod[OAuth2 Pod]
    Pod -->|TLS| DB[(Database)]
```

| Connection | TLS Version | Certificate |
|------------|-------------|-------------|
| Client → Load Balancer | TLS 1.3 | Public CA |
| Load Balancer → Pod | TLS 1.2+ / mTLS | Internal CA |
| Pod → Database | TLS 1.2+ | Cloud Provider CA |
| Pod → Redis | TLS 1.2+ | Cloud Provider CA |

## Secret Management

### Production Secret Hierarchy

```mermaid
graph TB
    subgraph "Secret Sources"
        KMS[Cloud KMS]
        SecretManager[Secret Manager]
        EnvVars[Environment Variables]
    end
    
    subgraph "Secrets"
        DBCreds[Database Credentials]
        ClientSecrets[Client Secrets]
        JWKKeys[JWK Signing Keys]
        TLSCerts[TLS Certificates]
    end
    
    subgraph "Application"
        Config[Spring Configuration]
        Runtime[Runtime Access]
    end
    
    KMS -->|Encrypt| SecretManager
    SecretManager -->|Inject| EnvVars
    EnvVars -->|Load| Config
    
    SecretManager --> DBCreds
    SecretManager --> ClientSecrets
    KMS --> JWKKeys
    SecretManager --> TLSCerts
```

### Required Secrets

| Secret | Type | Rotation Period | Storage |
|--------|------|-----------------|---------|
| `DATABASE_PASSWORD` | Credential | 90 days | Secret Manager |
| `OAUTH2_DEMO_CLIENT_SECRET` | Credential | 90 days | Secret Manager |
| `OAUTH2_M2M_CLIENT_SECRET` | Credential | 90 days | Secret Manager |
| `JWK_PRIVATE_KEY` | Cryptographic | 90 days | Cloud KMS |
| `TLS_PRIVATE_KEY` | Certificate | 1 year | Certificate Manager |

## Security Scanning

### OWASP Dependency Check

```bash
# Run OWASP dependency check
./mvnw org.owasp:dependency-check-maven:check
```

### SpotBugs with FindSecBugs

```bash
# Run security-focused static analysis
./mvnw spotbugs:check
```

### Container Scanning

```bash
# Trivy container scanning
trivy image oauth2-server:latest
```

## Compliance Considerations

| Standard | Relevant Controls |
|----------|-------------------|
| **OAuth2 2.1** | PKCE mandatory for public clients, refresh token rotation |
| **OWASP Top 10** | A01-A10 security controls implemented |
| **SOC 2** | Access control, encryption, monitoring |
| **GDPR** | Consent management, data minimization |
| **PCI DSS** | Strong cryptography, access logging |

## Security Checklist

- [ ] TLS 1.3 enabled for all external connections
- [ ] PKCE required for all public clients
- [ ] BCrypt password hashing with cost factor ≥ 12
- [ ] JWT signing keys stored in KMS/HSM
- [ ] All secrets loaded from Secret Manager
- [ ] Rate limiting configured
- [ ] Security headers enabled
- [ ] Network policies applied
- [ ] Container runs as non-root user
- [ ] Read-only root filesystem
- [ ] Security scanning in CI/CD pipeline
- [ ] Audit logging enabled
- [ ] Key rotation scheduled

## Next Steps

- [Data Flow](data-flow.md) - OAuth2 flow diagrams
- [Configuration](../configuration.md) - Security configuration options
- [Incident Response](../operations/incident-response.md) - Security incident procedures
