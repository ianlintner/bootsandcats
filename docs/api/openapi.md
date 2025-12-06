# OpenAPI Documentation

The OAuth2 Authorization Server provides comprehensive OpenAPI (formerly Swagger) documentation for interactive API exploration and client code generation.

## Interactive API Documentation

Access the interactive Swagger UI at:

```
http://localhost:9000/swagger-ui.html
```

This interface allows you to:

- **Explore all API endpoints** with detailed descriptions
- **Try out API calls** directly from the browser
- **Authenticate** using OAuth2 flows
- **View request/response schemas** with examples

## OpenAPI Specification

The raw OpenAPI specification is available at:

| Format | URL |
|--------|-----|
| JSON | `/v3/api-docs` |
| YAML | `/v3/api-docs.yaml` |

### Downloading the Specification

```bash
# Download JSON format
curl -o openapi.json http://localhost:9000/v3/api-docs

# Download YAML format
curl -o openapi.yaml http://localhost:9000/v3/api-docs.yaml
```

## Features

### OAuth2 Security Schemes

The OpenAPI documentation includes configured OAuth2 security schemes:

- **Authorization Code Flow** - For user-facing applications
- **Client Credentials Flow** - For machine-to-machine authentication
- **Bearer Token** - For API access with JWT tokens

### Interactive Authentication

You can authenticate directly in Swagger UI:

1. Click the **Authorize** button
2. Choose your authentication method:
   - **oauth2** - For full OAuth2 flows
   - **bearerAuth** - For direct JWT token entry
3. Enter your credentials or token
4. Click **Authorize**

### Try It Out

Each endpoint includes a "Try it out" button that allows you to:

1. Fill in request parameters
2. Modify request bodies
3. Execute the request
4. View the response

## Client Code Generation

Generate client SDKs in multiple languages using the OpenAPI specification:

### Using OpenAPI Generator CLI

```bash
# Install OpenAPI Generator
npm install @openapitools/openapi-generator-cli -g

# Generate clients for different languages
openapi-generator-cli generate \
  -i http://localhost:9000/v3/api-docs \
  -g <generator-name> \
  -o ./generated-client
```

### Available Generators

| Language | Generator Name |
|----------|---------------|
| Java | `java` |
| Python | `python` |
| TypeScript (Axios) | `typescript-axios` |
| TypeScript (Fetch) | `typescript-fetch` |
| Go | `go` |
| C# | `csharp` |
| Ruby | `ruby` |
| PHP | `php` |

### Example: Generate TypeScript Client

```bash
openapi-generator-cli generate \
  -i http://localhost:9000/v3/api-docs \
  -g typescript-axios \
  -o ./oauth2-client \
  --additional-properties=npmName=oauth2-client,supportsES6=true
```

## Configuration

### Application Properties

The OpenAPI documentation is configured via `application.properties`:

```properties
# OpenAPI / Swagger UI Configuration
springdoc.api-docs.path=/v3/api-docs
springdoc.swagger-ui.path=/swagger-ui.html
springdoc.swagger-ui.enabled=true
springdoc.swagger-ui.operationsSorter=method
springdoc.swagger-ui.tagsSorter=alpha
springdoc.swagger-ui.doc-expansion=none
springdoc.swagger-ui.try-it-out-enabled=true
springdoc.swagger-ui.filter=true
springdoc.show-actuator=false
```

### Disabling in Production

If you prefer to disable Swagger UI in production:

```properties
# application-prod.properties
springdoc.swagger-ui.enabled=false
springdoc.api-docs.enabled=false
```

## API Overview

The OpenAPI documentation covers:

### User Info Endpoint

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/userinfo` | Get authenticated user information |

!!! note "OAuth2 Standard Endpoints"
    Standard OAuth2 endpoints (`/oauth2/token`, `/oauth2/authorize`, etc.) are provided by Spring Authorization Server and follow RFC specifications. See [OAuth2 Endpoints](oauth2-endpoints.md) for detailed documentation.

## Integration with CI/CD

### Validate OpenAPI Spec in CI

```yaml
# .github/workflows/openapi.yml
name: OpenAPI Validation

on: [push, pull_request]

jobs:
  validate:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Set up Java
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          
      - name: Start Application
        run: ./gradlew :server-ui:bootRun &
        
      - name: Wait for Application
        run: sleep 30
        
      - name: Download OpenAPI Spec
        run: curl -f http://localhost:9000/v3/api-docs -o openapi.json
        
      - name: Validate Spec
        uses: char0n/swagger-cli@v2
        with:
          args: validate openapi.json
```

### Generate Clients in CI

```yaml
- name: Generate TypeScript Client
  run: |
    npx @openapitools/openapi-generator-cli generate \
      -i openapi.json \
      -g typescript-axios \
      -o ./sdk/typescript
    
- name: Publish SDK
  run: cd ./sdk/typescript && npm publish
```

## Next Steps

- [Client Onboarding Guide](client-onboarding.md) - Language-specific integration guides
- [OAuth2 Endpoints](oauth2-endpoints.md) - Detailed OAuth2 endpoint documentation
- [OIDC Endpoints](oidc-endpoints.md) - OpenID Connect endpoint documentation
