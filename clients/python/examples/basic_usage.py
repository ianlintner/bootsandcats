"""
Example: Basic usage of the OAuth2 client
"""

import os
from bootsandcats_oauth2_client import (
    Configuration,
    ApiClient,
    DiscoveryApi,
    TokenApi,
    UserInfoApi,
    TokenResponse,
    UserInfo,
)
from bootsandcats_oauth2_client.tracing import (
    setup_tracing,
    traced_api_call,
)


def basic_usage():
    """Basic usage example without tracing."""
    print("Basic OAuth2 Client Usage")
    print("=" * 40)
    
    # Create configuration
    config = Configuration(
        base_url=os.environ.get("OAUTH2_SERVER_URL", "http://localhost:9000"),
        access_token="your-access-token",  # Replace with actual token
    )
    
    # Create API client
    client = ApiClient(config)
    
    # Create API instances
    discovery_api = DiscoveryApi(client)
    token_api = TokenApi(client)
    userinfo_api = UserInfoApi(client)
    
    print(f"API client created for: {config.base_url}")
    
    # Example: Get OpenID Configuration (discovery)
    # openid_config = discovery_api.get_openid_configuration()
    # print(f"Issuer: {openid_config.issuer}")
    # print(f"Authorization endpoint: {openid_config.authorization_endpoint}")
    
    # Example: Exchange authorization code for tokens
    # tokens = token_api.exchange_code(
    #     code="authorization-code",
    #     redirect_uri="http://localhost:3000/callback",
    #     client_id="your-client-id",
    # )
    # print(f"Access token: {tokens.access_token}")
    
    # Example: Get user info
    # user_info = userinfo_api.get_user_info()
    # print(f"User: {user_info.name}")


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
    
    # Create configuration
    config = Configuration(
        base_url=os.environ.get("OAUTH2_SERVER_URL", "http://localhost:9000"),
    )
    
    # Create traced API client
    client = ApiClient(config)
    discovery_api = DiscoveryApi(client)
    
    # Use traced API call decorator
    @traced_api_call("get_openid_configuration")
    def get_openid_config():
        # return discovery_api.get_openid_configuration()
        pass  # Uncomment above when API is implemented
    
    print("Traced API client created")
    
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
