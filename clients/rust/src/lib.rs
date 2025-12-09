//! # Bootsandcats OAuth2 Client
//!
//! A Rust client for the OAuth2 Authorization Server API with OpenTelemetry support.
//!
//! ## Example
//!
//! ```rust,no_run
//! use bootsandcats_oauth2_client::{Configuration, DiscoveryApi, TokenApi, UserInfoApi};
//!
//! #[tokio::main]
//! async fn main() -> Result<(), Box<dyn std::error::Error>> {
//!     let config = Configuration::new("https://auth.example.com");
//!     
//!     // Get OpenID Connect discovery document
//!     let discovery = DiscoveryApi::new(config.clone());
//!     let openid_config = discovery.get_openid_configuration().await?;
//!     println!("Issuer: {}", openid_config.issuer);
//!     
//!     // Exchange authorization code for tokens
//!     let token_api = TokenApi::new(config.clone());
//!     let tokens = token_api.exchange_code(
//!         "auth_code",
//!         "https://app.example.com/callback",
//!         "client_id",
//!         Some("client_secret"),
//!         None,
//!     ).await?;
//!     
//!     // Get user info
//!     let userinfo_api = UserInfoApi::new(config);
//!     let user = userinfo_api.get_user_info(&tokens.access_token).await?;
//!     println!("User: {:?}", user.name);
//!     
//!     Ok(())
//! }
//! ```

pub mod api;
pub mod configuration;
pub mod error;
pub mod models;

#[cfg(feature = "tracing")]
pub mod tracing;

// Re-exports
pub use api::{DiscoveryApi, TokenApi, UserInfoApi};
pub use configuration::Configuration;
pub use error::{Error, Result};
pub use models::*;
