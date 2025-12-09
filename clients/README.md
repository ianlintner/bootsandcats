# OAuth2 Server Client SDKs

This directory contains auto-generated client SDKs for the OAuth2 Authorization Server and Profile Service APIs.

## Overview

Client SDKs are automatically generated using [OpenAPI Generator](https://openapi-generator.tech/) from the OpenAPI specifications exposed by our services:

- **OAuth2 Server**: `/v3/api-docs` - OAuth2/OIDC endpoints, token management, client registration
- **Profile Service**: `/swagger/api-docs` - User profile management

## Supported Languages

| Language   | Directory       | Package Manager | HTTP Client |
|------------|-----------------|-----------------|-------------|
| TypeScript | `typescript/`   | npm/yarn        | Axios       |
| Python     | `python/`       | pip/poetry      | urllib3     |
| Go         | `go/`           | go modules      | net/http    |
| Rust       | `rust/`         | cargo           | reqwest     |

## Directory Structure

```
clients/
├── README.md                    # This file
├── .gitignore                   # Ignore generated files
├── specs/                       # Downloaded OpenAPI specifications
│   ├── oauth2-server.json
│   └── profile-service.json
├── config/                      # OpenAPI Generator configurations
│   ├── typescript.yaml
│   ├── python.yaml
│   ├── go.yaml
│   └── rust.yaml
├── typescript/                  # TypeScript/JavaScript SDK
│   ├── package.json
│   ├── tsconfig.json
│   └── src/
├── python/                      # Python SDK
│   ├── setup.py
│   ├── pyproject.toml
│   └── src/
├── go/                          # Go SDK
│   ├── go.mod
│   └── *.go
└── rust/                        # Rust SDK
    ├── Cargo.toml
    └── src/
```

## Quick Start

### Prerequisites

- [OpenAPI Generator CLI](https://openapi-generator.tech/docs/installation) v7.x
- Docker (alternative to local installation)

### Install OpenAPI Generator

```bash
# Using Homebrew (macOS)
brew install openapi-generator

# Using npm
npm install -g @openapitools/openapi-generator-cli

# Using Docker
docker pull openapitools/openapi-generator-cli:v7.10.0
```

### Generate Clients Locally

```bash
# Download specs from running services
./scripts/download-specs.sh

# Generate all clients
./scripts/generate-clients.sh

# Or generate specific language
./scripts/generate-clients.sh typescript
./scripts/generate-clients.sh python
./scripts/generate-clients.sh go
./scripts/generate-clients.sh rust
```

## Automated Generation

Clients are automatically generated via GitHub Actions when:
- OpenAPI specs change in the repository
- Manual workflow dispatch is triggered
- Pull requests modify API-related code

See `.github/workflows/generate-clients.yml` for the workflow configuration.

## Using the Clients

### TypeScript

```typescript
import { Configuration, OAuth2Api, TokenApi } from '@bootsandcats/oauth2-client';
import { trace } from '@opentelemetry/api';

const config = new Configuration({
  basePath: 'https://auth.example.com',
  accessToken: 'your-access-token',
});

const oauth2Api = new OAuth2Api(config);

// With OpenTelemetry tracing
const tracer = trace.getTracer('oauth2-client');
const span = tracer.startSpan('get-token');
try {
  const token = await oauth2Api.getToken({ grantType: 'client_credentials' });
  console.log(token);
} finally {
  span.end();
}
```

### Python

```python
from bootsandcats_oauth2_client import ApiClient, Configuration, OAuth2Api
from opentelemetry import trace

config = Configuration(
    host="https://auth.example.com",
    access_token="your-access-token"
)

tracer = trace.get_tracer(__name__)

with ApiClient(config) as client:
    api = OAuth2Api(client)
    
    with tracer.start_as_current_span("get-token"):
        token = api.get_token(grant_type="client_credentials")
        print(token)
```

### Go

```go
package main

import (
    "context"
    "fmt"
    
    oauth2 "github.com/bootsandcats/oauth2-client-go"
    "go.opentelemetry.io/otel"
)

func main() {
    ctx := context.Background()
    tracer := otel.Tracer("oauth2-client")
    
    config := oauth2.NewConfiguration()
    config.Servers = oauth2.ServerConfigurations{
        {URL: "https://auth.example.com"},
    }
    
    client := oauth2.NewAPIClient(config)
    
    ctx, span := tracer.Start(ctx, "get-token")
    defer span.End()
    
    token, _, err := client.OAuth2Api.GetToken(ctx).Execute()
    if err != nil {
        panic(err)
    }
    fmt.Println(token)
}
```

### Rust

```rust
use bootsandcats_oauth2_client::{apis::configuration::Configuration, apis::oauth2_api};
use opentelemetry::global;
use tracing::{instrument, Span};

#[instrument]
async fn get_token() -> Result<(), Box<dyn std::error::Error>> {
    let config = Configuration {
        base_path: "https://auth.example.com".to_string(),
        bearer_access_token: Some("your-access-token".to_string()),
        ..Default::default()
    };
    
    let token = oauth2_api::get_token(&config).await?;
    println!("{:?}", token);
    Ok(())
}
```

## Configuration Options

### Environment Variables

All clients support configuration via environment variables:

| Variable | Description |
|----------|-------------|
| `OAUTH2_BASE_URL` | Base URL of the OAuth2 server |
| `OAUTH2_CLIENT_ID` | OAuth2 client ID |
| `OAUTH2_CLIENT_SECRET` | OAuth2 client secret |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | OpenTelemetry collector endpoint |
| `OTEL_SERVICE_NAME` | Service name for tracing |

## Development

### Regenerating Clients

To regenerate clients after spec changes:

1. Update specs in `specs/` directory
2. Run `./scripts/generate-clients.sh`
3. Test the generated code
4. Commit changes

### Custom Templates

Custom Mustache templates can be placed in `templates/` to override default generation:

```
templates/
├── typescript/
│   └── apiInner.mustache
├── python/
│   └── api.mustache
└── ...
```

### Adding New Languages

1. Create configuration in `config/<language>.yaml`
2. Add template directory if needed
3. Update `generate-clients.sh` script
4. Add to GitHub Actions workflow

## Versioning

Client versions follow the API version with a patch number for client-specific fixes:

- API Version: `1.0.0`
- Client Version: `1.0.0.1` (first client release for API 1.0.0)

## License

These client SDKs are generated from the OAuth2 Authorization Server project and are subject to the same license terms.

## Support

For issues with:
- Generated client code: Open an issue in this repository
- API behavior: Contact the API team
- OpenAPI Generator: See [OpenAPI Generator Issues](https://github.com/OpenAPITools/openapi-generator/issues)
