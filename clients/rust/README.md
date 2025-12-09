# OAuth2 Client - Rust

Rust client for the OAuth2 Authorization Server API.

## Installation

Add to your `Cargo.toml`:

```toml
[dependencies]
bootsandcats-oauth2-client = "1.0"
```

With OpenTelemetry tracing support:

```toml
[dependencies]
bootsandcats-oauth2-client = { version = "1.0", features = ["tracing"] }
```

## Usage

```rust
use bootsandcats_oauth2_client::{Configuration, OAuth2Api};

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let config = Configuration::new("https://auth.example.com")
        .with_bearer_token("your-access-token");
    
    let api = OAuth2Api::new(config);
    
    // Get user info
    let user_info = api.get_user_info().await?;
    println!("User: {:?}", user_info);
    
    Ok(())
}
```

## OpenTelemetry Integration

```rust
use bootsandcats_oauth2_client::{Configuration, OAuth2Api};
use bootsandcats_oauth2_client::tracing::{init_tracing, shutdown_tracing};

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    // Initialize tracing
    init_tracing("my-app", "1.0.0", "http://localhost:4318/v1/traces")?;
    
    let config = Configuration::new("https://auth.example.com")
        .with_bearer_token("your-access-token")
        .with_tracing(true);
    
    let api = OAuth2Api::new(config);
    
    // Get user info (automatically traced)
    let user_info = api.get_user_info().await?;
    println!("User: {:?}", user_info);
    
    // Shutdown tracing
    shutdown_tracing();
    
    Ok(())
}
```

## Configuration Options

```rust
let config = Configuration::new("https://auth.example.com")
    // Authentication
    .with_bearer_token("token")
    .with_basic_auth("username", "password")
    .with_api_key("header-name", "api-key")
    
    // HTTP settings
    .with_timeout(Duration::from_secs(30))
    .with_user_agent("my-app/1.0")
    
    // TLS settings
    .with_danger_accept_invalid_certs(false)
    
    // Tracing
    .with_tracing(true);
```

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
# Build
cargo build

# Run tests
cargo test

# Run tests with all features
cargo test --all-features

# Build documentation
cargo doc --all-features --open

# Format code
cargo fmt

# Lint
cargo clippy --all-features
```

## License

MIT License
