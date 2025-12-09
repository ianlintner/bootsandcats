"""
Example: Basic usage of the OAuth2 client
"""

import os
from bootsandcats_oauth2_client import (
    setup_tracing,
    traced_api_call,
    TracedApiClient,
)

# Note: After code generation, you would import:
# from bootsandcats_oauth2_client import ApiClient, Configuration
# from bootsandcats_oauth2_client.api import OAuth2Api


def basic_usage():
    """Basic usage example without tracing."""
    print("Basic OAuth2 Client Usage")
    print("=" * 40)
    
    # After code generation, you would use:
    # config = Configuration(
    #     host="https://auth.example.com",
    #     access_token="your-access-token"
    # )
    # 
    # with ApiClient(config) as client:
    #     api = OAuth2Api(client)
    #     user_info = api.get_user_info()
    #     print(f"User: {user_info}")
    
    print("Note: Run code generation first to use the full API client")


def usage_with_tracing():
    """Example with OpenTelemetry tracing."""
    print("\nOAuth2 Client with Tracing")
    print("=" * 40)
    
    # Initialize tracing
    tracer, shutdown = setup_tracing(
        service_name="oauth2-client-example",
        service_version="1.0.0",
        exporter_url=os.environ.get(
            "OTEL_EXPORTER_OTLP_ENDPOINT",
            "http://localhost:4318/v1/traces"
        ),
    )
    
    print("Tracing initialized successfully")
    
    # After code generation, you would use:
    # config = Configuration(host="https://auth.example.com")
    # 
    # with ApiClient(config) as client:
    #     api = OAuth2Api(client)
    #     traced_api = TracedApiClient(api, tracer, "oauth2")
    #     
    #     # This call will be automatically traced
    #     user_info = traced_api.get_user_info()
    
    # Example of manual tracing
    with tracer.start_as_current_span("example-operation") as span:
        span.set_attribute("example.key", "example.value")
        print("Span created successfully")
    
    # Cleanup
    shutdown()
    print("Tracing shutdown complete")


if __name__ == "__main__":
    basic_usage()
    
    try:
        usage_with_tracing()
    except ImportError as e:
        print(f"\nTracing example skipped: {e}")
        print("Install tracing dependencies with: pip install bootsandcats-oauth2-client[tracing]")
