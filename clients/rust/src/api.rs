//! API client implementations for the OAuth2 client.

use crate::configuration::Configuration;
use crate::error::{Error, Result};
use crate::models::*;
use reqwest::header::{AUTHORIZATION, CONTENT_TYPE};

/// Discovery API client for OpenID Connect discovery endpoints.
pub struct DiscoveryApi {
    config: Configuration,
}

impl DiscoveryApi {
    /// Create a new Discovery API client.
    pub fn new(config: Configuration) -> Self {
        Self { config }
    }

    /// Get the OpenID Connect discovery document.
    #[cfg(feature = "tracing")]
    #[tracing::instrument(skip(self))]
    pub async fn get_openid_configuration(&self) -> Result<OpenIDConfiguration> {
        self.get_openid_configuration_impl().await
    }

    /// Get the OpenID Connect discovery document.
    #[cfg(not(feature = "tracing"))]
    pub async fn get_openid_configuration(&self) -> Result<OpenIDConfiguration> {
        self.get_openid_configuration_impl().await
    }

    async fn get_openid_configuration_impl(&self) -> Result<OpenIDConfiguration> {
        let url = format!(
            "{}/.well-known/openid-configuration",
            self.config.base_path
        );

        let response = self
            .config
            .client
            .get(&url)
            .send()
            .await?;

        if response.status().is_success() {
            let config: OpenIDConfiguration = response.json().await?;
            Ok(config)
        } else {
            Err(Error::Api {
                status: response.status().as_u16(),
                message: response.text().await.unwrap_or_default(),
            })
        }
    }

    /// Get the JSON Web Key Set (JWKS).
    pub async fn get_jwks(&self) -> Result<JWKS> {
        let config = self.get_openid_configuration().await?;
        let response = self.config.client.get(&config.jwks_uri).send().await?;

        if response.status().is_success() {
            let jwks: JWKS = response.json().await?;
            Ok(jwks)
        } else {
            Err(Error::Api {
                status: response.status().as_u16(),
                message: response.text().await.unwrap_or_default(),
            })
        }
    }
}

/// Token API client for OAuth2 token operations.
pub struct TokenApi {
    config: Configuration,
}

impl TokenApi {
    /// Create a new Token API client.
    pub fn new(config: Configuration) -> Self {
        Self { config }
    }

    /// Exchange an authorization code for tokens.
    #[cfg(feature = "tracing")]
    #[tracing::instrument(skip(self, client_secret, code_verifier))]
    pub async fn exchange_code(
        &self,
        code: &str,
        redirect_uri: &str,
        client_id: &str,
        client_secret: Option<&str>,
        code_verifier: Option<&str>,
    ) -> Result<TokenResponse> {
        self.exchange_code_impl(code, redirect_uri, client_id, client_secret, code_verifier)
            .await
    }

    #[cfg(not(feature = "tracing"))]
    pub async fn exchange_code(
        &self,
        code: &str,
        redirect_uri: &str,
        client_id: &str,
        client_secret: Option<&str>,
        code_verifier: Option<&str>,
    ) -> Result<TokenResponse> {
        self.exchange_code_impl(code, redirect_uri, client_id, client_secret, code_verifier)
            .await
    }

    async fn exchange_code_impl(
        &self,
        code: &str,
        redirect_uri: &str,
        client_id: &str,
        client_secret: Option<&str>,
        code_verifier: Option<&str>,
    ) -> Result<TokenResponse> {
        let discovery = DiscoveryApi::new(self.config.clone())
            .get_openid_configuration()
            .await?;

        let mut params = vec![
            ("grant_type", "authorization_code"),
            ("code", code),
            ("redirect_uri", redirect_uri),
            ("client_id", client_id),
        ];

        if let Some(secret) = client_secret {
            params.push(("client_secret", secret));
        }

        if let Some(verifier) = code_verifier {
            params.push(("code_verifier", verifier));
        }

        let response = self
            .config
            .client
            .post(&discovery.token_endpoint)
            .header(CONTENT_TYPE, "application/x-www-form-urlencoded")
            .form(&params)
            .send()
            .await?;

        if response.status().is_success() {
            let token: TokenResponse = response.json().await?;
            Ok(token)
        } else {
            Err(Error::Api {
                status: response.status().as_u16(),
                message: response.text().await.unwrap_or_default(),
            })
        }
    }

    /// Refresh an access token.
    pub async fn refresh_token(
        &self,
        refresh_token: &str,
        client_id: &str,
        client_secret: Option<&str>,
    ) -> Result<TokenResponse> {
        let discovery = DiscoveryApi::new(self.config.clone())
            .get_openid_configuration()
            .await?;

        let mut params = vec![
            ("grant_type", "refresh_token"),
            ("refresh_token", refresh_token),
            ("client_id", client_id),
        ];

        if let Some(secret) = client_secret {
            params.push(("client_secret", secret));
        }

        let response = self
            .config
            .client
            .post(&discovery.token_endpoint)
            .header(CONTENT_TYPE, "application/x-www-form-urlencoded")
            .form(&params)
            .send()
            .await?;

        if response.status().is_success() {
            let token: TokenResponse = response.json().await?;
            Ok(token)
        } else {
            Err(Error::Api {
                status: response.status().as_u16(),
                message: response.text().await.unwrap_or_default(),
            })
        }
    }

    /// Get tokens using client credentials grant.
    pub async fn client_credentials(
        &self,
        client_id: &str,
        client_secret: &str,
        scope: Option<&str>,
    ) -> Result<TokenResponse> {
        let discovery = DiscoveryApi::new(self.config.clone())
            .get_openid_configuration()
            .await?;

        let mut params = vec![
            ("grant_type", "client_credentials"),
            ("client_id", client_id),
            ("client_secret", client_secret),
        ];

        if let Some(s) = scope {
            params.push(("scope", s));
        }

        let response = self
            .config
            .client
            .post(&discovery.token_endpoint)
            .header(CONTENT_TYPE, "application/x-www-form-urlencoded")
            .form(&params)
            .send()
            .await?;

        if response.status().is_success() {
            let token: TokenResponse = response.json().await?;
            Ok(token)
        } else {
            Err(Error::Api {
                status: response.status().as_u16(),
                message: response.text().await.unwrap_or_default(),
            })
        }
    }
}

/// UserInfo API client.
pub struct UserInfoApi {
    config: Configuration,
}

impl UserInfoApi {
    /// Create a new UserInfo API client.
    pub fn new(config: Configuration) -> Self {
        Self { config }
    }

    /// Get user information for the authenticated user.
    pub async fn get_user_info(&self, access_token: &str) -> Result<UserInfo> {
        let discovery = DiscoveryApi::new(self.config.clone())
            .get_openid_configuration()
            .await?;

        let userinfo_endpoint = discovery.userinfo_endpoint.ok_or(Error::Config(
            "UserInfo endpoint not available".to_string(),
        ))?;

        let response = self
            .config
            .client
            .get(&userinfo_endpoint)
            .header(AUTHORIZATION, format!("Bearer {}", access_token))
            .send()
            .await?;

        if response.status().is_success() {
            let user_info: UserInfo = response.json().await?;
            Ok(user_info)
        } else {
            Err(Error::Api {
                status: response.status().as_u16(),
                message: response.text().await.unwrap_or_default(),
            })
        }
    }
}
