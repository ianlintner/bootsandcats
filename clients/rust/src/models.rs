//! Data models for the OAuth2 client.

use serde::{Deserialize, Serialize};

/// OAuth2 Token Response.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TokenResponse {
    /// The access token.
    pub access_token: String,
    /// The token type (usually "Bearer").
    pub token_type: String,
    /// Token expiration time in seconds.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub expires_in: Option<i64>,
    /// The refresh token (if issued).
    #[serde(skip_serializing_if = "Option::is_none")]
    pub refresh_token: Option<String>,
    /// The granted scopes.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub scope: Option<String>,
    /// The ID token (for OIDC).
    #[serde(skip_serializing_if = "Option::is_none")]
    pub id_token: Option<String>,
}

/// OpenID Connect Discovery Document.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct OpenIDConfiguration {
    /// The issuer URL.
    pub issuer: String,
    /// The authorization endpoint URL.
    pub authorization_endpoint: String,
    /// The token endpoint URL.
    pub token_endpoint: String,
    /// The userinfo endpoint URL.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub userinfo_endpoint: Option<String>,
    /// The JWKS URI.
    pub jwks_uri: String,
    /// Registration endpoint URL.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub registration_endpoint: Option<String>,
    /// Supported scopes.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub scopes_supported: Option<Vec<String>>,
    /// Supported response types.
    pub response_types_supported: Vec<String>,
    /// Supported response modes.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub response_modes_supported: Option<Vec<String>>,
    /// Supported grant types.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub grant_types_supported: Option<Vec<String>>,
    /// Supported subject types.
    pub subject_types_supported: Vec<String>,
    /// Supported ID token signing algorithms.
    pub id_token_signing_alg_values_supported: Vec<String>,
    /// Supported token endpoint authentication methods.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub token_endpoint_auth_methods_supported: Option<Vec<String>>,
    /// Supported claims.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub claims_supported: Option<Vec<String>>,
    /// Supported code challenge methods.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub code_challenge_methods_supported: Option<Vec<String>>,
}

/// User Info Response.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UserInfo {
    /// Subject identifier.
    pub sub: String,
    /// Full name.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub name: Option<String>,
    /// Given name.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub given_name: Option<String>,
    /// Family name.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub family_name: Option<String>,
    /// Preferred username.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub preferred_username: Option<String>,
    /// Email address.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub email: Option<String>,
    /// Whether email is verified.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub email_verified: Option<bool>,
    /// Profile picture URL.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub picture: Option<String>,
    /// Locale.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub locale: Option<String>,
    /// Last update timestamp.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub updated_at: Option<i64>,
}

/// OAuth2 Error Response.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct OAuth2Error {
    /// Error code.
    pub error: String,
    /// Error description.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub error_description: Option<String>,
    /// Error URI.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub error_uri: Option<String>,
    /// State parameter.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub state: Option<String>,
}

/// JSON Web Key.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct JWK {
    /// Key type.
    pub kty: String,
    /// Key use.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub use_: Option<String>,
    /// Key operations.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub key_ops: Option<Vec<String>>,
    /// Algorithm.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub alg: Option<String>,
    /// Key ID.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub kid: Option<String>,
    /// X.509 URL.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub x5u: Option<String>,
    /// X.509 certificate chain.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub x5c: Option<Vec<String>>,
    /// X.509 thumbprint.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub x5t: Option<String>,
    /// X.509 SHA-256 thumbprint.
    #[serde(rename = "x5t#S256", skip_serializing_if = "Option::is_none")]
    pub x5t_s256: Option<String>,
    // RSA specific
    /// RSA modulus.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub n: Option<String>,
    /// RSA exponent.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub e: Option<String>,
    // EC specific
    /// EC curve.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub crv: Option<String>,
    /// EC x coordinate.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub x: Option<String>,
    /// EC y coordinate.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub y: Option<String>,
}

/// JSON Web Key Set.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct JWKS {
    /// Keys in the set.
    pub keys: Vec<JWK>,
}

/// Token Introspection Response.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct IntrospectionResponse {
    /// Whether the token is active.
    pub active: bool,
    /// Token scope.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub scope: Option<String>,
    /// Client ID.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub client_id: Option<String>,
    /// Username.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub username: Option<String>,
    /// Token type.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub token_type: Option<String>,
    /// Expiration timestamp.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub exp: Option<i64>,
    /// Issued at timestamp.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub iat: Option<i64>,
    /// Not before timestamp.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub nbf: Option<i64>,
    /// Subject.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub sub: Option<String>,
    /// Audience.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub aud: Option<String>,
    /// Issuer.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub iss: Option<String>,
    /// JWT ID.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub jti: Option<String>,
}
