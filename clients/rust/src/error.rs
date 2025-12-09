//! Error types for the OAuth2 client.

use thiserror::Error;

/// Errors that can occur when using the OAuth2 client.
#[derive(Error, Debug)]
pub enum Error {
    /// HTTP request failed
    #[error("HTTP request failed: {0}")]
    Reqwest(#[from] reqwest::Error),

    /// JSON serialization/deserialization failed
    #[error("JSON error: {0}")]
    Serde(#[from] serde_json::Error),

    /// URL parsing failed
    #[error("URL error: {0}")]
    Url(#[from] url::ParseError),

    /// API returned an error response
    #[error("API error: status={status}, message={message}")]
    Api { status: u16, message: String },

    /// Authentication error
    #[error("Authentication error: {0}")]
    Auth(String),

    /// Configuration error
    #[error("Configuration error: {0}")]
    Config(String),

    /// IO error
    #[error("IO error: {0}")]
    Io(#[from] std::io::Error),
}

/// Result type for OAuth2 client operations.
pub type Result<T> = std::result::Result<T, Error>;
