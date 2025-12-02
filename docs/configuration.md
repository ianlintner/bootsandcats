# Configuration Reference

This document provides a comprehensive reference for all configuration options available for the OAuth2 Authorization Server.

## Configuration Sources

The application supports configuration from multiple sources (in order of precedence):

1. Command-line arguments
2. Environment variables
3. `application-{profile}.properties`
4. `application.properties`

---

## Core Configuration

### Server Settings

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | `9000` | HTTP port |
| `server.servlet.context-path` | `/` | Context path |
| `server.tomcat.max-threads` | `200` | Max request threads |
| `server.tomcat.max-connections` | `8192` | Max connections |

```properties
# Server configuration
server.port=9000
server.servlet.context-path=/
server.tomcat.max-threads=200
server.tomcat.max-connections=8192
server.tomcat.accept-count=100
server.shutdown=graceful
spring.lifecycle.timeout-per-shutdown-phase=30s
```

### Application Settings

| Property | Default | Description |
|----------|---------|-------------|
| `spring.application.name` | `oauth2-authorization-server` | Application name |
| `spring.profiles.active` | - | Active profile(s) |

```properties
spring.application.name=oauth2-authorization-server
spring.profiles.active=prod
```

---

## OAuth2 Configuration

### Issuer Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `oauth2.issuer-url` | `http://localhost:9000` | Token issuer URL |

```properties
# OAuth2 issuer - MUST match external URL
oauth2.issuer-url=${OAUTH2_ISSUER_URL:http://localhost:9000}
```

### Client Secrets

!!! warning "Security Note"
    Always use environment variables or secret management for credentials in production.

| Property | Default | Description |
|----------|---------|-------------|
| `oauth2.demo-client-secret` | `demo-secret` | Demo client secret |
| `oauth2.m2m-client-secret` | `m2m-secret` | M2M client secret |

```properties
# Client secrets - use environment variables in production
oauth2.demo-client-secret=${OAUTH2_DEMO_CLIENT_SECRET}
oauth2.m2m-client-secret=${OAUTH2_M2M_CLIENT_SECRET}
```

### User Passwords

| Property | Default | Description |
|----------|---------|-------------|
| `oauth2.demo-user-password` | `password` | Demo user password |
| `oauth2.admin-user-password` | `admin` | Admin user password |

```properties
# User passwords - use environment variables in production
oauth2.demo-user-password=${OAUTH2_DEMO_USER_PASSWORD}
oauth2.admin-user-password=${OAUTH2_ADMIN_USER_PASSWORD}
```

---

## Database Configuration

### PostgreSQL (Production)

| Property | Default | Description |
|----------|---------|-------------|
| `spring.datasource.url` | - | JDBC connection URL |
| `spring.datasource.username` | - | Database username |
| `spring.datasource.password` | - | Database password |
| `spring.jpa.hibernate.ddl-auto` | `validate` | Schema management |

```properties
# PostgreSQL configuration
spring.datasource.url=${DATABASE_URL:jdbc:postgresql://localhost:5432/oauth2db}
spring.datasource.username=${DATABASE_USERNAME:postgres}
spring.datasource.password=${DATABASE_PASSWORD}
spring.datasource.driverClassName=org.postgresql.Driver
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.hibernate.ddl-auto=validate
```

### H2 (Development)

```properties
# H2 in-memory database for development
spring.datasource.url=jdbc:h2:mem:oauth2db;DB_CLOSE_DELAY=-1
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.jpa.hibernate.ddl-auto=create-drop
spring.h2.console.enabled=true
spring.h2.console.path=/h2-console
```

### Connection Pool (HikariCP)

| Property | Default | Description |
|----------|---------|-------------|
| `spring.datasource.hikari.maximum-pool-size` | `10` | Max pool size |
| `spring.datasource.hikari.minimum-idle` | `5` | Min idle connections |
| `spring.datasource.hikari.connection-timeout` | `30000` | Connection timeout (ms) |
| `spring.datasource.hikari.idle-timeout` | `600000` | Idle timeout (ms) |
| `spring.datasource.hikari.max-lifetime` | `1800000` | Max connection lifetime (ms) |

```properties
# HikariCP connection pool
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.idle-timeout=600000
spring.datasource.hikari.max-lifetime=1800000
spring.datasource.hikari.leak-detection-threshold=60000
```

---

## Actuator Configuration

### Endpoint Exposure

| Property | Default | Description |
|----------|---------|-------------|
| `management.endpoints.web.exposure.include` | `health,info,prometheus` | Exposed endpoints |
| `management.endpoint.health.show-details` | `never` | Health detail visibility |
| `management.endpoint.health.probes.enabled` | `true` | Kubernetes probes |

```properties
# Actuator endpoints
management.endpoints.web.exposure.include=health,info,prometheus
management.endpoint.health.show-details=when-authorized
management.endpoint.health.probes.enabled=true
management.health.livenessState.enabled=true
management.health.readinessState.enabled=true

# Application info
management.info.env.enabled=true
info.app.name=OAuth2 Authorization Server
info.app.description=Spring Boot OAuth2 Authorization Server
info.app.version=1.0.0
```

### Prometheus Metrics

```properties
# Prometheus metrics
management.prometheus.metrics.export.enabled=true
management.metrics.tags.application=oauth2-authorization-server
management.metrics.distribution.percentiles-histogram.http.server.requests=true
```

---

## OpenTelemetry Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `otel.exporter.otlp.endpoint` | `http://localhost:4317` | OTLP collector endpoint |
| `otel.service.name` | - | Service name for traces |
| `otel.traces.exporter` | `otlp` | Trace exporter |
| `otel.metrics.exporter` | `prometheus` | Metrics exporter |

```properties
# OpenTelemetry configuration
otel.exporter.otlp.endpoint=${OTEL_EXPORTER_ENDPOINT:http://localhost:4317}
otel.exporter.otlp.protocol=grpc
otel.service.name=oauth2-authorization-server
otel.traces.exporter=otlp
otel.metrics.exporter=prometheus
otel.logs.exporter=otlp
otel.resource.attributes=service.namespace=oauth2,deployment.environment=prod
```

---

## Session Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `server.servlet.session.timeout` | `30m` | Session timeout |
| `server.servlet.session.cookie.secure` | `true` (prod) | Secure cookies |
| `server.servlet.session.cookie.http-only` | `true` | HTTP-only cookies |
| `server.servlet.session.cookie.same-site` | `lax` | SameSite policy |

```properties
# Session configuration
server.servlet.session.timeout=30m
server.servlet.session.cookie.secure=true
server.servlet.session.cookie.http-only=true
server.servlet.session.cookie.same-site=strict
```

---

## Logging Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `logging.level.root` | `INFO` | Root log level |
| `logging.level.com.bootsandcats` | `INFO` | Application log level |
| `logging.level.org.springframework.security` | `WARN` | Security log level |

```properties
# Logging levels
logging.level.root=INFO
logging.level.com.bootsandcats=INFO
logging.level.org.springframework.security=WARN
logging.level.org.springframework.security.oauth2=WARN
logging.level.org.hibernate.SQL=WARN

# Console pattern (development)
logging.pattern.console=%d{HH:mm:ss.SSS} %-5level [%thread] %logger{36} - %msg%n

# JSON logging (production)
logging.config=classpath:logback-spring.xml
```

---

## Security Configuration

### Error Handling

```properties
# Error handling - don't expose details
server.error.include-message=never
server.error.include-stacktrace=never
server.error.include-exception=false
server.error.include-binding-errors=never
```

### SSL/TLS (Optional)

```properties
# SSL configuration
server.ssl.enabled=true
server.ssl.key-store=classpath:keystore.p12
server.ssl.key-store-password=${SSL_KEYSTORE_PASSWORD}
server.ssl.key-store-type=PKCS12
server.ssl.key-alias=oauth2-server
```

---

## Key Management (JWT Signing)

Tokens are signed with **ES256 (P-256 elliptic curve)** keys. In production, store the JSON Web Key Set
inside Azure Key Vault so the private key never resides on disk.

| Property | Default | Description |
|----------|---------|-------------|
| `azure.keyvault.enabled` | `false` | Enable Azure Key Vault integration for JWT keys |
| `azure.keyvault.vault-uri` | - | Vault URI (e.g., `https://my-vault.vault.azure.net/`) |
| `azure.keyvault.jwk-secret-name` | `oauth2-jwk` | Secret name containing the JWK Set |
| `azure.keyvault.cache-ttl` | `PT10M` | How long to cache the fetched JWK Set before reloading |

```properties
# Azure Key Vault backed EC key
azure.keyvault.enabled=${AZURE_KEYVAULT_ENABLED:false}
azure.keyvault.vault-uri=${AZURE_KEYVAULT_URI:}
azure.keyvault.jwk-secret-name=${AZURE_JWK_SECRET_NAME:oauth2-jwk}
azure.keyvault.cache-ttl=${AZURE_JWK_CACHE_TTL:PT10M}
```

---

## Environment Variables

### Required (Production)

| Variable | Description |
|----------|-------------|
| `DATABASE_URL` | PostgreSQL JDBC URL |
| `DATABASE_USERNAME` | Database username |
| `DATABASE_PASSWORD` | Database password |
| `OAUTH2_ISSUER_URL` | Public issuer URL |
| `OAUTH2_DEMO_CLIENT_SECRET` | Demo client secret |
| `OAUTH2_M2M_CLIENT_SECRET` | M2M client secret |
| `AZURE_KEYVAULT_ENABLED` | Set to `true` to load the EC JWK from Azure Key Vault |
| `AZURE_KEYVAULT_URI` | Vault URI (required when Key Vault is enabled) |
| `AZURE_JWK_SECRET_NAME` | Secret name containing the EC JWK set (defaults to `oauth2-jwk`) |

### Optional

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_PROFILES_ACTIVE` | - | Active profiles |
| `SERVER_PORT` | `9000` | Server port |
| `OTEL_EXPORTER_ENDPOINT` | `http://localhost:4317` | OpenTelemetry endpoint |
| `JAVA_OPTS` | - | JVM options |
| `AZURE_JWK_CACHE_TTL` | `PT10M` | Overrides cache duration for the JWK Set |
| `AZURE_TENANT_ID` | - | Tenant used by `DefaultAzureCredential` when running outside Azure |
| `AZURE_CLIENT_ID` | - | Client/Managed Identity ID for Key Vault access |
| `AZURE_CLIENT_SECRET` | - | Client secret when using an app registration |

---

## Profile-Specific Configuration

### Development Profile

```properties
# application-dev.properties
spring.h2.console.enabled=true
logging.level.com.bootsandcats=DEBUG
management.endpoint.health.show-details=always
```

### Production Profile

```properties
# application-prod.properties
spring.h2.console.enabled=false
logging.level.root=INFO
management.endpoint.health.show-details=never
server.servlet.session.cookie.secure=true
```

### Test Profile

```properties
# application-test.properties
spring.datasource.url=jdbc:h2:mem:testdb
spring.jpa.hibernate.ddl-auto=create-drop
logging.level.root=WARN
```

---

## Docker/Kubernetes Configuration

### Environment Variables in Kubernetes

```yaml
env:
  - name: SPRING_PROFILES_ACTIVE
    value: "prod"
  - name: DATABASE_URL
    valueFrom:
      secretKeyRef:
        name: oauth2-secrets
        key: database-url
  - name: DATABASE_PASSWORD
    valueFrom:
      secretKeyRef:
        name: oauth2-secrets
        key: database-password
  - name: JAVA_OPTS
    value: "-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
```

### JVM Options for Containers

```bash
JAVA_OPTS="-XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -XX:InitialRAMPercentage=50.0 \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=100 \
  -Djava.security.egd=file:/dev/./urandom"
```

---

## Configuration Validation

```bash
# Validate configuration
./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.config.location=classpath:application.properties"

# Check effective configuration
curl http://localhost:9000/actuator/configprops

# Check environment
curl http://localhost:9000/actuator/env
```

---

## Next Steps

- [Security](security.md) - Security best practices
- [Deployment Overview](deployment/overview.md) - Deployment guide
- [Observability](observability/overview.md) - Monitoring setup
