# OAuth2 Client - Go

Go client for the OAuth2 Authorization Server API.

## Installation

```bash
go get github.com/bootsandcats/oauth2-client-go
```

## Usage

```go
package main

import (
    "context"
    "fmt"
    "log"

    oauth2 "github.com/bootsandcats/oauth2-client-go"
)

func main() {
    ctx := context.Background()
    
    config := oauth2.NewConfiguration()
    config.Servers = oauth2.ServerConfigurations{
        {URL: "https://auth.example.com"},
    }
    
    // Set access token
    ctx = context.WithValue(ctx, oauth2.ContextAccessToken, "your-access-token")
    
    client := oauth2.NewAPIClient(config)
    
    // Get user info
    userInfo, resp, err := client.OAuth2Api.GetUserInfo(ctx).Execute()
    if err != nil {
        log.Fatalf("Error getting user info: %v", err)
    }
    
    fmt.Printf("User: %+v\n", userInfo)
    fmt.Printf("Response status: %s\n", resp.Status)
}
```

## OpenTelemetry Integration

```go
package main

import (
    "context"
    "fmt"
    "log"

    oauth2 "github.com/bootsandcats/oauth2-client-go"
    "github.com/bootsandcats/oauth2-client-go/tracing"
)

func main() {
    ctx := context.Background()
    
    // Initialize tracing
    shutdown, err := tracing.SetupTracing(ctx, tracing.Config{
        ServiceName:    "my-app",
        ServiceVersion: "1.0.0",
        ExporterURL:    "http://localhost:4318/v1/traces",
    })
    if err != nil {
        log.Fatalf("Failed to setup tracing: %v", err)
    }
    defer shutdown(ctx)
    
    // Create traced client
    config := oauth2.NewConfiguration()
    config.Servers = oauth2.ServerConfigurations{
        {URL: "https://auth.example.com"},
    }
    config.HTTPClient = tracing.InstrumentedHTTPClient()
    
    client := oauth2.NewAPIClient(config)
    
    // Get user info with tracing
    tracer := tracing.GetTracer()
    ctx, span := tracer.Start(ctx, "get-user-info")
    defer span.End()
    
    userInfo, _, err := client.OAuth2Api.GetUserInfo(ctx).Execute()
    if err != nil {
        span.RecordError(err)
        log.Fatalf("Error getting user info: %v", err)
    }
    
    fmt.Printf("User: %+v\n", userInfo)
}
```

## Configuration Options

| Option | Type | Description |
|--------|------|-------------|
| `Servers` | `ServerConfigurations` | List of server URLs |
| `HTTPClient` | `*http.Client` | Custom HTTP client |
| `Debug` | `bool` | Enable debug logging |
| `DefaultHeader` | `map[string]string` | Default headers |
| `UserAgent` | `string` | Custom user agent |

## Context Values

| Context Key | Type | Description |
|-------------|------|-------------|
| `ContextAccessToken` | `string` | Bearer token |
| `ContextBasicAuth` | `BasicAuth` | Basic auth credentials |
| `ContextAPIKey` | `APIKey` | API key |
| `ContextServerIndex` | `int` | Server to use |
| `ContextServerVariables` | `map[string]string` | Server variables |

## Environment Variables

```bash
export OAUTH2_BASE_URL=https://auth.example.com
export OAUTH2_CLIENT_ID=my-client
export OAUTH2_CLIENT_SECRET=secret
export OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318
export OTEL_SERVICE_NAME=my-app
```

## Development

```bash
# Install dependencies
go mod download

# Run tests
go test ./...

# Run tests with coverage
go test -cover ./...

# Build
go build ./...

# Lint
golangci-lint run
```

## License

MIT License
