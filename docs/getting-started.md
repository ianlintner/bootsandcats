# Getting Started

This guide will help you get the OAuth2 Authorization Server up and running quickly.

## Prerequisites

- **Java 21 (LTS)** or higher
- **Gradle 9.0+** (or use the included Gradle wrapper)
- **Docker** (optional, for containerized deployment)
- **PostgreSQL 15** (for production, H2 is used for development)

## Quick Start

### Option 1: Run Locally

```bash
# Clone the repository
git clone https://github.com/ianlintner/bootsandcats.git
cd bootsandcats

# Build and run
./gradlew :server-ui:bootRun

# Or build JAR and run
./gradlew build
java -jar server-ui/build/libs/server-ui-1.0.0-SNAPSHOT.jar
```

The server will start on `http://localhost:9000`.

### Option 2: Run with Docker

```bash
# Build and run with Docker Compose (includes PostgreSQL, Prometheus, Grafana)
docker-compose up -d

# Or build standalone image
docker build -t oauth2-server .
docker run -p 9000:9000 oauth2-server
```

### Option 3: Run with Docker Compose (Full Stack)

```bash
# Start all services
docker-compose up -d

# Check service status
docker-compose ps

# View logs
docker-compose logs -f oauth2-server
```

This will start:

| Service | URL | Description |
|---------|-----|-------------|
| OAuth2 Server | http://localhost:9000 | Authorization server |
| PostgreSQL | localhost:5432 | Database |
| Prometheus | http://localhost:9090 | Metrics |
| Grafana | http://localhost:3000 | Dashboards |
| OpenTelemetry Collector | localhost:4317 | Tracing |

## Verify Installation

### Check Health Endpoint

```bash
curl http://localhost:9000/actuator/health
```

Expected response:

```json
{
  "status": "UP",
  "groups": ["liveness", "readiness"]
}
```

### Check OIDC Discovery

```bash
curl http://localhost:9000/.well-known/openid-configuration
```

Expected response includes:

```json
{
  "issuer": "http://localhost:9000",
  "authorization_endpoint": "http://localhost:9000/oauth2/authorize",
  "token_endpoint": "http://localhost:9000/oauth2/token",
  "jwks_uri": "http://localhost:9000/oauth2/jwks",
  "userinfo_endpoint": "http://localhost:9000/userinfo",
  "scopes_supported": ["openid", "profile", "email"],
  "response_types_supported": ["code"],
  "grant_types_supported": ["authorization_code", "client_credentials", "refresh_token"]
}
```

## Default Clients

!!! warning "Demo Credentials"
    The credentials shown below are for demonstration purposes only. 
    In production, replace these with secure values loaded from environment variables or a secrets management system.

| Client ID | Client Secret | Type | Description |
|-----------|---------------|------|-------------|
| `demo-client` | `demo-secret` | Confidential | Full OAuth2 client with authorization code and refresh token grants |
| `public-client` | N/A | Public | PKCE-enabled client for SPAs and mobile apps |
| `m2m-client` | `m2m-secret` | Confidential | Machine-to-machine client for service-to-service auth |

## Default Users

!!! warning "Demo Credentials"
    These are demo credentials only. In production, implement proper user management.

| Username | Password | Roles |
|----------|----------|-------|
| `user` | `password` | USER |
| `admin` | `admin` | USER, ADMIN |

## Test OAuth2 Flows

### Client Credentials Flow

```bash
# Get access token
curl -X POST http://localhost:9000/oauth2/token \
  -u m2m-client:m2m-secret \
  -d "grant_type=client_credentials" \
  -d "scope=api:read"
```

Response:

```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "api:read"
}
```

### Authorization Code Flow with PKCE

#### Step 1: Generate PKCE Parameters

```bash
# Generate code verifier and challenge
CODE_VERIFIER=$(openssl rand -base64 32 | tr -d /=+ | cut -c -43)
CODE_CHALLENGE=$(echo -n $CODE_VERIFIER | openssl dgst -sha256 -binary | base64 | tr -d /=+ | cut -c -43)

echo "Code Verifier: $CODE_VERIFIER"
echo "Code Challenge: $CODE_CHALLENGE"
```

#### Step 2: Initiate Authorization

Open in browser:

```
http://localhost:9000/oauth2/authorize?
  response_type=code&
  client_id=public-client&
  redirect_uri=http://localhost:3000/callback&
  scope=openid%20profile&
  code_challenge=${CODE_CHALLENGE}&
  code_challenge_method=S256&
  state=random_state_value
```

#### Step 3: Exchange Code for Tokens

After user authenticates and consents, exchange the authorization code:

```bash
curl -X POST http://localhost:9000/oauth2/token \
  -d "grant_type=authorization_code" \
  -d "client_id=public-client" \
  -d "redirect_uri=http://localhost:3000/callback" \
  -d "code=${AUTHORIZATION_CODE}" \
  -d "code_verifier=${CODE_VERIFIER}"
```

### Token Introspection

```bash
curl -X POST http://localhost:9000/oauth2/introspect \
  -u m2m-client:m2m-secret \
  -d "token=${ACCESS_TOKEN}"
```

### Token Revocation

```bash
curl -X POST http://localhost:9000/oauth2/revoke \
  -u m2m-client:m2m-secret \
  -d "token=${REFRESH_TOKEN}" \
  -d "token_type_hint=refresh_token"
```

## JWT Signing Keys (ES256)

The authorization server now signs all tokens with an **elliptic-curve (P-256) key** using the `ES256`
algorithm. For local development, a transient key is generated on startup. For production we recommend
loading the JSON Web Key (JWK) from **Azure Key Vault**:

1. Generate a new EC JWK (includes private key):

    The EcJwkGenerator utility is available in the server-logic module. The easiest way to generate the JWK:

    ```bash
    # Option 1: Build the full application and run the utility
    ./gradlew build
    java -jar server-ui/build/libs/server-ui-*.jar \
      --spring.main.web-application-type=none \
      --spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration \
      com.bootsandcats.oauth2.tools.EcJwkGenerator > jwk.json
    
    # Option 2: Use the Azure Key Vault pre-generated key (recommended for production)
    # Keys are automatically loaded from Azure Key Vault when AZURE_KEYVAULT_ENABLED=true
    ```

2. Upload the JWK set to Key Vault (replace `KEY_VAULT_NAME` with your vault):

    ```bash
    az keyvault secret set \
      --vault-name "$KEY_VAULT_NAME" \
      --name oauth2-jwk \
      --file jwk.json
    ```

3. Configure the application to read from Key Vault:

    ```bash
    export AZURE_KEYVAULT_ENABLED=true
    export AZURE_KEYVAULT_URI="https://$KEY_VAULT_NAME.vault.azure.net/"
    export AZURE_JWK_SECRET_NAME=oauth2-jwk
    ```

4. Restart the service. The JWKS endpoint (`/oauth2/jwks`) will now expose the uploaded key (public
   material only) while the private key remains in Key Vault.

## Next Steps

1. **[Architecture Overview](architecture/overview.md)** - Understand the system design
2. **[Deployment Guide](deployment/overview.md)** - Deploy to Kubernetes
3. **[Configuration Reference](configuration.md)** - Customize settings
4. **[Security Best Practices](security.md)** - Secure your deployment
5. **[Observability](observability/overview.md)** - Set up monitoring

## Troubleshooting

### Common Issues

| Issue | Solution |
|-------|----------|
| Port 9000 already in use | Change port in `application.properties` or stop conflicting service |
| Database connection failed | Check PostgreSQL is running and credentials are correct |
| JWT verification fails | Ensure JWKS endpoint is accessible to resource servers |
| CORS errors | Configure allowed origins in security settings |

For more troubleshooting help, see the [Common Issues Runbook](operations/runbooks/common-issues.md).
