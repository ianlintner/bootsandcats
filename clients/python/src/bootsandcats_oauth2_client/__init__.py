"""
Bootsandcats OAuth2 Client SDK

A Python client for the OAuth2 Authorization Server API with OpenTelemetry support.
"""

from __future__ import annotations

__version__ = "1.0.0"
__author__ = "Bootsandcats Team"

# API client and configuration
from bootsandcats_oauth2_client.api_client import (
    ApiClient,
    ApiException,
    ApiTypeError,
    ApiValueError,
    Configuration,
    OAuth2Api,
    TokenApi,
    UserInfoApi,
)

# Models
from bootsandcats_oauth2_client.models import (
    ClientRegistrationRequest,
    ClientRegistrationResponse,
    IntrospectionRequest,
    IntrospectionResponse,
    JWKS,
    JWK,
    OAuth2Error,
    OpenIDConfiguration,
    RevocationRequest,
    TokenRequest,
    TokenResponse,
    UserInfo,
)

# Tracing utilities
from bootsandcats_oauth2_client.tracing import (
    setup_tracing,
    traced_api_call,
    TracedApiClient,
    TRACING_AVAILABLE,
)

__all__ = [
    "__version__",
    "__author__",
    # API Client
    "ApiClient",
    "ApiException",
    "ApiTypeError",
    "ApiValueError",
    "Configuration",
    "OAuth2Api",
    "TokenApi",
    "UserInfoApi",
    # Models
    "ClientRegistrationRequest",
    "ClientRegistrationResponse",
    "IntrospectionRequest",
    "IntrospectionResponse",
    "JWKS",
    "JWK",
    "OAuth2Error",
    "OpenIDConfiguration",
    "RevocationRequest",
    "TokenRequest",
    "TokenResponse",
    "UserInfo",
    # Tracing
    "setup_tracing",
    "traced_api_call",
    "TracedApiClient",
    "TRACING_AVAILABLE",
]
