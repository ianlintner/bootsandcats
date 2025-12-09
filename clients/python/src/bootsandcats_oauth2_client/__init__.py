"""
Bootsandcats OAuth2 Client SDK

A Python client for the OAuth2 Authorization Server API with OpenTelemetry support.
"""

from __future__ import annotations

__version__ = "1.0.0"
__author__ = "Bootsandcats Team"

# Note: The following imports will be available after code generation
# from bootsandcats_oauth2_client.api_client import ApiClient
# from bootsandcats_oauth2_client.configuration import Configuration
# from bootsandcats_oauth2_client.exceptions import (
#     ApiException,
#     ApiValueError,
#     ApiTypeError,
# )

# Tracing utilities (available immediately)
from bootsandcats_oauth2_client.tracing import (
    setup_tracing,
    traced_api_call,
    TracedApiClient,
    TRACING_AVAILABLE,
)

__all__ = [
    "__version__",
    "__author__",
    # Tracing
    "setup_tracing",
    "traced_api_call",
    "TracedApiClient",
    "TRACING_AVAILABLE",
]
