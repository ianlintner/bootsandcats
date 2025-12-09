//! Configuration for the OAuth2 client.

use std::time::Duration;

/// Configuration for API client.
#[derive(Debug, Clone)]
pub struct Configuration {
    /// Base URL for the API
    pub base_path: String,
    /// User agent string
    pub user_agent: Option<String>,
    /// Bearer access token
    pub bearer_access_token: Option<String>,
    /// Basic auth credentials
    pub basic_auth: Option<BasicAuth>,
    /// API key configuration
    pub api_key: Option<ApiKey>,
    /// HTTP client
    pub client: reqwest::Client,
    /// Request timeout
    pub timeout: Option<Duration>,
    /// Enable tracing
    #[cfg(feature = "tracing")]
    pub tracing_enabled: bool,
}

/// Basic authentication credentials.
#[derive(Debug, Clone)]
pub struct BasicAuth {
    pub username: String,
    pub password: Option<String>,
}

/// API key configuration.
#[derive(Debug, Clone)]
pub struct ApiKey {
    pub prefix: Option<String>,
    pub key: String,
}

impl Configuration {
    /// Create a new configuration with the given base path.
    pub fn new(base_path: impl Into<String>) -> Self {
        Self {
            base_path: base_path.into(),
            user_agent: Some(format!(
                "bootsandcats-oauth2-client/{}",
                env!("CARGO_PKG_VERSION")
            )),
            bearer_access_token: None,
            basic_auth: None,
            api_key: None,
            client: reqwest::Client::new(),
            timeout: Some(Duration::from_secs(30)),
            #[cfg(feature = "tracing")]
            tracing_enabled: false,
        }
    }

    /// Set the bearer access token.
    pub fn with_bearer_token(mut self, token: impl Into<String>) -> Self {
        self.bearer_access_token = Some(token.into());
        self
    }

    /// Set basic authentication credentials.
    pub fn with_basic_auth(
        mut self,
        username: impl Into<String>,
        password: Option<impl Into<String>>,
    ) -> Self {
        self.basic_auth = Some(BasicAuth {
            username: username.into(),
            password: password.map(|p| p.into()),
        });
        self
    }

    /// Set API key.
    pub fn with_api_key(
        mut self,
        key: impl Into<String>,
        prefix: Option<impl Into<String>>,
    ) -> Self {
        self.api_key = Some(ApiKey {
            key: key.into(),
            prefix: prefix.map(|p| p.into()),
        });
        self
    }

    /// Set custom user agent.
    pub fn with_user_agent(mut self, user_agent: impl Into<String>) -> Self {
        self.user_agent = Some(user_agent.into());
        self
    }

    /// Set request timeout.
    pub fn with_timeout(mut self, timeout: Duration) -> Self {
        self.timeout = Some(timeout);
        self
    }

    /// Set custom HTTP client.
    pub fn with_client(mut self, client: reqwest::Client) -> Self {
        self.client = client;
        self
    }

    /// Enable or disable tracing.
    #[cfg(feature = "tracing")]
    pub fn with_tracing(mut self, enabled: bool) -> Self {
        self.tracing_enabled = enabled;
        self
    }
}

impl Default for Configuration {
    fn default() -> Self {
        Self::new("http://localhost:8080")
    }
}
