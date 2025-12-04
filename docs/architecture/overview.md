# Architecture Overview

This document provides a comprehensive overview of the OAuth2 Authorization Server architecture, designed for production deployment in Kubernetes environments.

## High-Level Architecture

```mermaid
graph TB
    subgraph "External Clients"
        SPA[Web SPA]
        Mobile[Mobile App]
        Backend[Backend Services]
        Partner[Partner APIs]
    end
    
    subgraph "Load Balancer / Ingress"
        LB[Cloud Load Balancer]
        Ingress[Kubernetes Ingress]
    end
    
    subgraph "Kubernetes Cluster"
        subgraph "OAuth2 Namespace"
            Pod1[OAuth2 Server Pod 1]
            Pod2[OAuth2 Server Pod 2]
            Pod3[OAuth2 Server Pod N]
        end
        
        subgraph "Observability"
            Prometheus[Prometheus]
            Grafana[Grafana]
            OtelCollector[OpenTelemetry Collector]
            Jaeger[Jaeger / Tempo]
        end
    end
    
    subgraph "Managed Services"
        DB[(PostgreSQL)]
        Cache[(Redis)]
        Secrets[Secret Manager]
        KMS[Key Management]
    end
    
    SPA --> LB
    Mobile --> LB
    Backend --> LB
    Partner --> LB
    
    LB --> Ingress
    Ingress --> Pod1
    Ingress --> Pod2
    Ingress --> Pod3
    
    Pod1 --> DB
    Pod2 --> DB
    Pod3 --> DB
    
    Pod1 --> Cache
    Pod2 --> Cache
    Pod3 --> Cache
    
    Pod1 --> Secrets
    Pod1 --> KMS
    
    Pod1 --> Prometheus
    Pod1 --> OtelCollector
    OtelCollector --> Jaeger
    Prometheus --> Grafana
```

## Component Architecture

The current implementation is built directly on top of Spring Authorization Server and Spring
Security components. Rather than custom `*Service` classes, the application wires and
configures framework-provided building blocks.

```mermaid
graph LR
    subgraph "OAuth2 Authorization Server"
        subgraph "Web Layer"
            Filters[Spring Security Filter Chain]
            Controllers[Authorization Server Endpoints]
        end

        subgraph "Security Layer"
            ASConfig[AuthorizationServerConfig]
            JwtCustomizer[OAuth2TokenCustomizer]
            JwtDecoder[JwtDecoder]
        end

        subgraph "Identity Layer"
            UserDetailsSvc[InMemoryUserDetailsManager]
        end

        subgraph "Client Registration"
            RegRepo[JdbcRegisteredClientRepository]
        end

        subgraph "Crypto / Keys"
            JwkProvider[JwkSetProvider]
            JwkSource[JWKSource]
        end
    end

    Filters --> Controllers
    Controllers --> ASConfig

    ASConfig --> UserDetailsSvc
    ASConfig --> RegRepo
    ASConfig --> JwtCustomizer
    ASConfig --> JwtDecoder
    ASConfig --> JwkSource

    JwkSource --> JwkProvider

    RegRepo --> DB[(PostgreSQL)]

    note right of UserDetailsSvc: Demo users in-memory (non-persistent)
    note right of RegRepo: OAuth2 clients stored in PostgreSQL
```

> NOTE: User accounts and authorization data are currently held in-memory. This is suitable for
> local development, tests, and demos, but **not** for production. For production deployments,
> plan to replace the in-memory user store and token storage with persistent implementations
> backed by your identity store and database.

## Request Flow Architecture

### Token Request Flow

```mermaid
sequenceDiagram
    participant Client
    participant LB as Load Balancer
    participant Pod as OAuth2 Pod
    participant Cache as Redis Cache
    participant DB as PostgreSQL
    participant Metrics as Prometheus
    
    Client->>LB: POST /oauth2/token
    LB->>Pod: Forward request
    
    Note over Pod: Authentication Filter
    Pod->>Pod: Validate client credentials
    
    alt Invalid Credentials
        Pod->>Client: 401 Unauthorized
    else Valid Credentials
        Pod->>Cache: Check rate limit
        Cache->>Pod: Rate limit status
        
        alt Rate Limited
            Pod->>Client: 429 Too Many Requests
        else Allowed
            Pod->>DB: Validate authorization
            DB->>Pod: Authorization data
            
            Pod->>Pod: Generate JWT tokens
            Pod->>DB: Store token metadata
            Pod->>Metrics: Record token_issued
            
            Pod->>Client: 200 OK (tokens)
        end
    end
```

## Data Architecture

### Database Schema

```mermaid
erDiagram
    OAUTH2_AUTHORIZATION {
        varchar id PK
        varchar registered_client_id FK
        varchar principal_name
        varchar authorization_grant_type
        varchar authorized_scopes
        text attributes
        varchar state
        timestamp authorization_code_issued_at
        timestamp authorization_code_expires_at
        varchar authorization_code_value
        text access_token_value
        timestamp access_token_issued_at
        timestamp access_token_expires_at
        text refresh_token_value
        timestamp refresh_token_issued_at
        timestamp refresh_token_expires_at
    }
    
    OAUTH2_REGISTERED_CLIENT {
        varchar id PK
        varchar client_id UK
        timestamp client_id_issued_at
        varchar client_secret
        timestamp client_secret_expires_at
        varchar client_name
        varchar client_authentication_methods
        varchar authorization_grant_types
        varchar redirect_uris
        varchar post_logout_redirect_uris
        varchar scopes
        text client_settings
        text token_settings
    }
    
    OAUTH2_AUTHORIZATION_CONSENT {
        varchar registered_client_id PK,FK
        varchar principal_name PK
        varchar authorities
    }
    
    OAUTH2_REGISTERED_CLIENT ||--o{ OAUTH2_AUTHORIZATION : has
    OAUTH2_REGISTERED_CLIENT ||--o{ OAUTH2_AUTHORIZATION_CONSENT : has
```

## Security Architecture

For detailed security architecture, see [Security Architecture](security.md).

## Deployment Architecture

### Multi-Region Deployment

```mermaid
graph TB
    subgraph "Global"
        DNS[DNS / GeoDNS]
        CDN[CDN for Static Assets]
    end
    
    subgraph "Region 1 - US East"
        LB1[Load Balancer]
        subgraph "K8s Cluster 1"
            Pods1[OAuth2 Pods x3]
        end
        DB1[(PostgreSQL Primary)]
    end
    
    subgraph "Region 2 - US West"
        LB2[Load Balancer]
        subgraph "K8s Cluster 2"
            Pods2[OAuth2 Pods x3]
        end
        DB2[(PostgreSQL Replica)]
    end
    
    subgraph "Region 3 - EU"
        LB3[Load Balancer]
        subgraph "K8s Cluster 3"
            Pods3[OAuth2 Pods x3]
        end
        DB3[(PostgreSQL Replica)]
    end
    
    DNS --> LB1
    DNS --> LB2
    DNS --> LB3
    
    LB1 --> Pods1
    LB2 --> Pods2
    LB3 --> Pods3
    
    Pods1 --> DB1
    Pods2 --> DB2
    Pods3 --> DB3
    
    DB1 -.->|Replication| DB2
    DB1 -.->|Replication| DB3
```

## Scalability Considerations

### Horizontal Pod Autoscaling

```yaml
# HPA Configuration
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: oauth2-server-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: oauth2-server
  minReplicas: 3
  maxReplicas: 10
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
```

### Resource Recommendations

| Environment | CPU Request | CPU Limit | Memory Request | Memory Limit | Replicas |
|-------------|-------------|-----------|----------------|--------------|----------|
| Development | 250m | 500m | 512Mi | 1Gi | 1 |
| Staging | 500m | 1000m | 1Gi | 2Gi | 2 |
| Production | 1000m | 2000m | 2Gi | 4Gi | 3-10 |

## Technology Decisions

### Why Spring Authorization Server?

| Aspect | Decision | Rationale |
|--------|----------|-----------|
| **Framework** | Spring Authorization Server | Official Spring OAuth2 implementation, active development, strong community |
| **Token Format** | JWT | Stateless, self-contained, supports cryptographic signatures |
| **Signing Algorithm** | RS256 | Asymmetric encryption allows public key distribution |
| **Database** | PostgreSQL | ACID compliance, strong consistency, excellent Kubernetes support |
| **Caching** | Redis | Distributed caching, session management, rate limiting |
| **Observability** | OpenTelemetry | Vendor-neutral, wide ecosystem support |

### Key Design Principles

1. **Stateless Design**: JWT tokens enable horizontal scaling without session affinity
2. **Defense in Depth**: Multiple security layers (network, application, data)
3. **Observability First**: Built-in metrics, tracing, and logging
4. **Cloud Native**: Container-ready, Kubernetes-native health probes
5. **Separation of Concerns**: Clear boundaries between components

## Next Steps

- [Security Architecture](security.md) - Deep dive into security design
- [Data Flow](data-flow.md) - Detailed OAuth2 flow diagrams
- [Deployment Overview](../deployment/overview.md) - Kubernetes deployment guide
