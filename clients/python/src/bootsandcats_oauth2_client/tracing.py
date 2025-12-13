"""
OpenTelemetry Tracing Integration for OAuth2 Client

This module provides automatic tracing for all API calls made through
the OAuth2 client SDK.
"""

from __future__ import annotations

import functools
import os
from typing import Any, Callable, Optional, Tuple, TypeVar

try:
    from opentelemetry import trace
    from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter
    from opentelemetry.sdk.resources import Resource, SERVICE_NAME, SERVICE_VERSION
    from opentelemetry.sdk.trace import TracerProvider
    from opentelemetry.sdk.trace.export import BatchSpanProcessor
    from opentelemetry.trace import Tracer, SpanKind, Status, StatusCode

    TRACING_AVAILABLE = True
except ImportError:
    TRACING_AVAILABLE = False


F = TypeVar("F", bound=Callable[..., Any])


def setup_tracing(
    service_name: str,
    service_version: str = "1.0.0",
    exporter_url: Optional[str] = None,
    enabled: bool = True,
) -> Tuple[Any, Callable[[], None]]:
    """
    Setup OpenTelemetry tracing for the OAuth2 client.

    Args:
        service_name: Name of the service for tracing
        service_version: Version of the service
        exporter_url: OTLP exporter URL (defaults to OTEL_EXPORTER_OTLP_ENDPOINT env var)
        enabled: Whether tracing is enabled

    Returns:
        Tuple of (tracer, shutdown_function)

    Raises:
        ImportError: If OpenTelemetry packages are not installed
    """
    if not TRACING_AVAILABLE:
        raise ImportError(
            "OpenTelemetry packages not installed. "
            "Install with: pip install bootsandcats-oauth2-client[tracing]"
        )

    if not enabled:
        return trace.get_tracer(service_name), lambda: None

    # Get exporter URL from environment if not provided
    if exporter_url is None:
        exporter_url = os.environ.get(
            "OTEL_EXPORTER_OTLP_ENDPOINT", "http://localhost:4318/v1/traces"
        )

    # Create resource
    resource = Resource.create(
        {
            SERVICE_NAME: service_name,
            SERVICE_VERSION: service_version,
        }
    )

    # Create provider
    provider = TracerProvider(resource=resource)

    # Create exporter
    exporter = OTLPSpanExporter(endpoint=exporter_url)

    # Add processor
    provider.add_span_processor(BatchSpanProcessor(exporter))

    # Register provider
    trace.set_tracer_provider(provider)

    # Get tracer
    tracer = trace.get_tracer(service_name, service_version)

    def shutdown() -> None:
        """Shutdown the tracer provider."""
        provider.shutdown()

    return tracer, shutdown


def traced_api_call(tracer: Any, operation_name: str) -> Callable[[F], F]:
    """
    Decorator for tracing API calls.

    Args:
        tracer: OpenTelemetry tracer instance
        operation_name: Name of the operation for the span

    Returns:
        Decorated function

    Example:
        @traced_api_call(tracer, "get-user-info")
        def get_user():
            return api.get_user_info()
    """
    if not TRACING_AVAILABLE:

        def noop_decorator(func: F) -> F:
            return func

        return noop_decorator

    def decorator(func: F) -> F:
        @functools.wraps(func)
        def wrapper(*args: Any, **kwargs: Any) -> Any:
            with tracer.start_as_current_span(
                operation_name,
                kind=SpanKind.CLIENT,
                attributes={
                    "rpc.system": "http",
                    "rpc.service": "oauth2-server",
                    "rpc.method": operation_name,
                },
            ) as span:
                try:
                    result = func(*args, **kwargs)
                    span.set_status(Status(StatusCode.OK))
                    return result
                except Exception as e:
                    span.set_status(Status(StatusCode.ERROR, str(e)))
                    span.record_exception(e)
                    raise

        return wrapper  # type: ignore

    return decorator


class TracedApiClient:
    """
    A wrapper that adds tracing to an API client.

    Example:
        api = OAuth2Api(client)
        traced_api = TracedApiClient(api, tracer, "oauth2")
        traced_api.get_user_info()  # Automatically traced
    """

    def __init__(self, api: Any, tracer: Any, api_name: str) -> None:
        """
        Initialize the traced API client.

        Args:
            api: The underlying API client
            tracer: OpenTelemetry tracer instance
            api_name: Name prefix for spans
        """
        self._api = api
        self._tracer = tracer
        self._api_name = api_name

    def __getattr__(self, name: str) -> Any:
        """Get attribute from underlying API, wrapping methods with tracing."""
        attr = getattr(self._api, name)
        if callable(attr):
            return traced_api_call(self._tracer, f"{self._api_name}.{name}")(attr)
        return attr


__all__ = [
    "setup_tracing",
    "traced_api_call",
    "TracedApiClient",
    "TRACING_AVAILABLE",
]
