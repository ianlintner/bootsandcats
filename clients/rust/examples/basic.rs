//! Example: Basic usage of the OAuth2 client
//!
//! Run with: cargo run --example basic

use bootsandcats_oauth2_client::{Configuration, DiscoveryApi, TokenApi, UserInfoApi, Error, Result};
use std::env;

#[tokio::main]
async fn main() -> Result<()> {
    // Get server URL from environment or use default
    let base_url = env::var("OAUTH2_SERVER_URL")
        .unwrap_or_else(|_| "http://localhost:9000".to_string());

    // Create configuration
    let config = Configuration::new(&base_url)
        .with_bearer_token("your-access-token"); // Replace with actual token

    println!("Configuration created:");
    println!("  Base path: {}", config.base_path);
    println!("  Has bearer token: {}", config.bearer_access_token.is_some());

    // Create API clients
    let discovery_api = DiscoveryApi::new(config.clone());
    let token_api = TokenApi::new(config.clone());
    let userinfo_api = UserInfoApi::new(config);

    println!("API clients created successfully");

    // Example: Get OpenID Configuration (discovery)
    // let openid_config = discovery_api.get_openid_configuration().await?;
    // println!("Issuer: {}", openid_config.issuer);
    // println!("Authorization endpoint: {}", openid_config.authorization_endpoint);

    // Example: Exchange authorization code for tokens
    // let tokens = token_api.exchange_code(
    //     "authorization-code",
    //     "http://localhost:3000/callback",
    //     "your-client-id",
    //     Some("your-client-secret"),
    //     None,
    // ).await?;
    // println!("Access token: {}", tokens.access_token);

    // Example: Get user info
    // let user_info = userinfo_api.get_user_info("access-token").await?;
    // println!("User: {:?}", user_info.name);

    // Suppress unused variable warnings
    let _ = discovery_api;
    let _ = token_api;
    let _ = userinfo_api;

    Ok(())
}
