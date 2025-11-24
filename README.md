# OAuth2 Authorization Server

A production-ready Spring Boot OAuth2 Authorization Server with OpenID Connect (OIDC), PKCE, JWT support, and comprehensive observability features.

## Features

- **OAuth2 Authorization Server** - Full OAuth2 2.1 compliance
- **OpenID Connect (OIDC)** - OIDC 1.0 support with discovery endpoint
- **PKCE Support** - Proof Key for Code Exchange for public clients
- **JWT Tokens** - RSA-signed JWT access tokens
- **Multiple Grant Types**:
  - Authorization Code (with PKCE)
  - Client Credentials
  - Refresh Token
- **OpenTelemetry Integration** - Distributed tracing and metrics
- **Prometheus Metrics** - Ready for Grafana dashboards
- **OWASP Security** - Security best practices implemented
- **Docker Support** - Container-ready with multi-stage builds

## Quick Start

### Prerequisites

- Java 17 or higher
- Maven 3.8+
- Docker (optional)

### Running Locally

```bash
# Clone the repository
git clone https://github.com/ianlintner/bootsandcats.git
cd bootsandcats

# Build and run
./mvnw spring-boot:run

# Or build and run JAR
./mvnw package
java -jar target/oauth2-server-1.0.0-SNAPSHOT.jar
```

The server will start on `http://localhost:9000`.

### Running with Docker

```bash
# Build and run with Docker Compose
docker-compose up -d

# Or build standalone image
docker build -t oauth2-server .
docker run -p 9000:9000 oauth2-server
```

## Configuration

### Default Clients

| Client ID | Client Secret | Type | Description |
|-----------|---------------|------|-------------|
| `demo-client` | `demo-secret` | Confidential | Full OAuth2 client with authorization code and refresh token grants |
| `public-client` | N/A | Public | PKCE-enabled client for SPAs and mobile apps |
| `m2m-client` | `m2m-secret` | Confidential | Machine-to-machine client for service-to-service auth |

### Default Users

| Username | Password | Roles |
|----------|----------|-------|
| `user` | `password` | USER |
| `admin` | `admin` | USER, ADMIN |

## Endpoints

### OAuth2 Endpoints

| Endpoint | Description |
|----------|-------------|
| `/oauth2/authorize` | Authorization endpoint |
| `/oauth2/token` | Token endpoint |
| `/oauth2/jwks` | JSON Web Key Set |
| `/oauth2/introspect` | Token introspection |
| `/oauth2/revoke` | Token revocation |
| `/userinfo` | OIDC UserInfo endpoint |

### Discovery Endpoints

| Endpoint | Description |
|----------|-------------|
| `/.well-known/openid-configuration` | OIDC Discovery |

### Actuator Endpoints

| Endpoint | Description |
|----------|-------------|
| `/actuator/health` | Health check |
| `/actuator/health/liveness` | Kubernetes liveness probe |
| `/actuator/health/readiness` | Kubernetes readiness probe |
| `/actuator/prometheus` | Prometheus metrics |
| `/actuator/info` | Application info |

## Usage Examples

### Client Credentials Flow

```bash
# Get access token
curl -X POST http://localhost:9000/oauth2/token \
  -u m2m-client:m2m-secret \
  -d "grant_type=client_credentials" \
  -d "scope=api:read"

# Response:
# {
#   "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
#   "token_type": "Bearer",
#   "expires_in": 3600,
#   "scope": "api:read"
# }
```

### Authorization Code Flow with PKCE

```bash
# Generate code verifier and challenge
CODE_VERIFIER=$(openssl rand -base64 32 | tr -d /=+ | cut -c -43)
CODE_CHALLENGE=$(echo -n $CODE_VERIFIER | openssl dgst -sha256 -binary | base64 | tr -d /=+ | cut -c -43)

# Step 1: Redirect user to authorization endpoint
# GET http://localhost:9000/oauth2/authorize?
#   response_type=code
#   &client_id=public-client
#   &redirect_uri=http://localhost:3000/callback
#   &scope=openid%20profile
#   &code_challenge=$CODE_CHALLENGE
#   &code_challenge_method=S256

# Step 2: Exchange code for tokens
curl -X POST http://localhost:9000/oauth2/token \
  -d "grant_type=authorization_code" \
  -d "client_id=public-client" \
  -d "redirect_uri=http://localhost:3000/callback" \
  -d "code=$AUTHORIZATION_CODE" \
  -d "code_verifier=$CODE_VERIFIER"
```

### Token Introspection

```bash
curl -X POST http://localhost:9000/oauth2/introspect \
  -u m2m-client:m2m-secret \
  -d "token=$ACCESS_TOKEN"
```

## Testing

```bash
# Run unit tests
./mvnw test

# Run integration tests
./mvnw verify

# Run all tests with coverage report
./mvnw verify jacoco:report

# Run load tests (requires running server)
./mvnw gatling:test -Pload-test
```

## Security

### OWASP Compliance

- Security headers (CSP, HSTS, X-Frame-Options)
- BCrypt password encoding with cost factor 12
- CSRF protection
- Rate limiting ready (via Spring Security)
- OWASP Dependency Check in CI pipeline

### Security Scanning

```bash
# Run OWASP dependency check
./mvnw org.owasp:dependency-check-maven:check

# Run SpotBugs with FindSecBugs
./mvnw spotbugs:check
```

## Observability

### OpenTelemetry

Configure the OTLP exporter endpoint in `application.properties`:

```properties
otel.exporter.otlp.endpoint=http://your-collector:4317
```

### Prometheus Metrics

Metrics are exposed at `/actuator/prometheus`. Key metrics include:

- `oauth2.tokens.issued` - Number of tokens issued
- `oauth2.tokens.revoked` - Number of tokens revoked
- `oauth2.authorization.requests` - Authorization requests count
- `http_server_requests_*` - HTTP request metrics

### Grafana Dashboard

Use the provided `prometheus.yml` and `docker-compose.yml` to set up a complete observability stack.

## CI/CD

GitHub Actions workflows included:

- **build.yml** - Build and unit tests
- **lint.yml** - Code formatting and static analysis
- **docker.yml** - Docker image build and push
- **security.yml** - Security scanning (OWASP, CodeQL, Trivy)
- **load-test.yml** - Performance testing with Gatling

## Project Structure

```
bootsandcats/
├── src/
│   ├── main/
│   │   ├── java/com/bootsandcats/oauth2/
│   │   │   ├── OAuth2AuthorizationServerApplication.java
│   │   │   ├── config/
│   │   │   │   ├── AuthorizationServerConfig.java
│   │   │   │   ├── OpenTelemetryConfig.java
│   │   │   │   └── SecurityHeadersConfig.java
│   │   │   ├── controller/
│   │   │   │   ├── UserInfoController.java
│   │   │   │   └── CustomErrorController.java
│   │   │   └── service/
│   │   │       └── OAuth2MetricsService.java
│   │   └── resources/
│   │       ├── application.properties
│   │       └── application-prod.properties
│   └── test/
│       ├── java/com/bootsandcats/oauth2/
│       │   ├── config/
│       │   ├── controller/
│       │   ├── integration/
│       │   ├── load/
│       │   ├── security/
│       │   └── service/
│       └── resources/
│           └── application-test.properties
├── .github/workflows/
│   ├── build.yml
│   ├── lint.yml
│   ├── docker.yml
│   ├── security.yml
│   └── load-test.yml
├── Dockerfile
├── docker-compose.yml
├── pom.xml
└── README.md
```

## License

MIT License

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Run tests: `./mvnw verify`
5. Format code: `./mvnw spotless:apply`
6. Submit a pull request
