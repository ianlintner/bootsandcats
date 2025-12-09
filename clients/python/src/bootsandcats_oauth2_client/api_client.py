"""
API Client module for the OAuth2 client.

This module contains the base API client and configuration classes.
After running client generation, this will be replaced with actual implementations.
"""

from __future__ import annotations

import base64
from dataclasses import dataclass, field
from typing import Any, Callable, Dict, Optional, Union


@dataclass
class Configuration:
    """Configuration for the API client."""
    
    # Base path for API requests
    host: str = "http://localhost:9000"
    
    # Authentication
    access_token: Optional[Union[str, Callable[[], str]]] = None
    username: Optional[str] = None
    password: Optional[str] = None
    api_key: Optional[Dict[str, str]] = None
    api_key_prefix: Optional[Dict[str, str]] = None
    
    # HTTP settings
    verify_ssl: bool = True
    ssl_ca_cert: Optional[str] = None
    cert_file: Optional[str] = None
    key_file: Optional[str] = None
    proxy: Optional[str] = None
    
    # Request settings
    timeout: int = 30
    retries: int = 3
    
    # Custom headers
    default_headers: Dict[str, str] = field(default_factory=dict)
    
    def get_access_token(self) -> Optional[str]:
        """Get the access token value."""
        if callable(self.access_token):
            return self.access_token()
        return self.access_token
    
    def get_basic_auth_header(self) -> Optional[str]:
        """Get the basic auth header value."""
        if self.username and self.password:
            credentials = f"{self.username}:{self.password}"
            encoded = base64.b64encode(credentials.encode()).decode()
            return f"Basic {encoded}"
        return None


class ApiClient:
    """
    Base API client for making HTTP requests.
    
    This is a placeholder until OpenAPI Generator creates the actual client.
    """
    
    def __init__(self, configuration: Optional[Configuration] = None):
        """Initialize the API client."""
        self.configuration = configuration or Configuration()
        self._default_headers = {
            "User-Agent": "bootsandcats-oauth2-client/1.0.0/python",
            "Accept": "application/json",
        }
    
    @property
    def base_url(self) -> str:
        """Get the base URL for API requests."""
        return self.configuration.host
    
    def set_default_header(self, key: str, value: str) -> None:
        """Set a default header for all requests."""
        self._default_headers[key] = value
    
    def get_default_header(self, key: str) -> Optional[str]:
        """Get a default header value."""
        return self._default_headers.get(key)


class ApiException(Exception):
    """Exception raised for API errors."""
    
    def __init__(
        self,
        status: int = 0,
        reason: Optional[str] = None,
        body: Optional[str] = None,
        headers: Optional[Dict[str, str]] = None,
    ):
        """Initialize the exception."""
        self.status = status
        self.reason = reason
        self.body = body
        self.headers = headers or {}
        
        message = f"({status})"
        if reason:
            message += f" Reason: {reason}"
        if body:
            message += f"\n{body}"
        
        super().__init__(message)


class ApiValueError(ValueError, ApiException):
    """Exception raised for value errors."""
    pass


class ApiTypeError(TypeError, ApiException):
    """Exception raised for type errors."""
    pass


# Placeholder API classes - will be replaced by generated code


class OAuth2Api:
    """OAuth2 API client placeholder."""
    
    def __init__(self, api_client: Optional[ApiClient] = None):
        """Initialize the OAuth2 API client."""
        self.api_client = api_client or ApiClient()


class TokenApi:
    """Token API client placeholder."""
    
    def __init__(self, api_client: Optional[ApiClient] = None):
        """Initialize the Token API client."""
        self.api_client = api_client or ApiClient()


class UserInfoApi:
    """UserInfo API client placeholder."""
    
    def __init__(self, api_client: Optional[ApiClient] = None):
        """Initialize the UserInfo API client."""
        self.api_client = api_client or ApiClient()


__all__ = [
    "Configuration",
    "ApiClient",
    "ApiException",
    "ApiValueError",
    "ApiTypeError",
    "OAuth2Api",
    "TokenApi",
    "UserInfoApi",
]
