//! Example: OAuth2 client with OpenTelemetry tracing
//!
//! Run with: cargo run --example with_tracing --features tracing

#[cfg(feature = "tracing")]
use bootsandcats_oauth2_client::{
    tracing::{init_tracing, shutdown_tracing},
    Configuration, Error,
};

#[cfg(feature = "tracing")]
#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    // Initialize tracing
    init_tracing(
        "oauth2-client-example",
        "1.0.0",
        "http://localhost:4318/v1/traces",
    )?;

    // Create configuration with tracing enabled
    let config = Configuration::new("https://auth.example.com")
        .with_bearer_token("your-access-token")
        .with_tracing(true);

    println!("Configuration created with tracing enabled:");
    println!("  Base path: {}", config.base_path);
    println!("  Tracing enabled: {}", config.tracing_enabled);

    // In a real application, you would use the generated API clients here:
    // let api = OAuth2Api::new(config);
    // let user_info = api.get_user_info().await?;

    // Simulate some work
    tokio::time::sleep(tokio::time::Duration::from_secs(1)).await;

    // Shutdown tracing
    shutdown_tracing();

    Ok(())
}

#[cfg(not(feature = "tracing"))]
fn main() {
    eprintln!("This example requires the 'tracing' feature.");
    eprintln!("Run with: cargo run --example with_tracing --features tracing");
}
