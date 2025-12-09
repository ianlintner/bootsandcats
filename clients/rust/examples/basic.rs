//! Example: Basic usage of the OAuth2 client
//!
//! Run with: cargo run --example basic

use bootsandcats_oauth2_client::{Configuration, Error};

#[tokio::main]
async fn main() -> Result<(), Error> {
    // Create configuration
    let config = Configuration::new("https://auth.example.com")
        .with_bearer_token("your-access-token");

    println!("Configuration created:");
    println!("  Base path: {}", config.base_path);
    println!("  Has bearer token: {}", config.bearer_access_token.is_some());

    // In a real application, you would use the generated API clients here:
    // let api = OAuth2Api::new(config);
    // let user_info = api.get_user_info().await?;

    Ok(())
}
