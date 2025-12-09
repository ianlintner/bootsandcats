# OAuth2 Client - Python

Python client for the OAuth2 Authorization Server API.

## Installation

```bash
pip install bootsandcats-oauth2-client
```

With OpenTelemetry tracing support:

```bash
pip install bootsandcats-oauth2-client[tracing]
```

## Usage

```python
from bootsandcats_oauth2_client import ApiClient, Configuration
from bootsandcats_oauth2_client.api import OAuth2Api

config = Configuration(
    host="https://auth.example.com",
    access_token="your-access-token"
)

with ApiClient(config) as client:
    api = OAuth2Api(client)
    
    # Get user info
    user_info = api.get_user_info()
    print(user_info)
```

## OpenTelemetry Integration

```python
from bootsandcats_oauth2_client import ApiClient, Configuration
from bootsandcats_oauth2_client.api import OAuth2Api
from bootsandcats_oauth2_client.tracing import setup_tracing, traced_api_call

# Initialize tracing
tracer, shutdown = setup_tracing(
    service_name="my-app",
    exporter_url="http://localhost:4318/v1/traces"
)

config = Configuration(host="https://auth.example.com")

with ApiClient(config) as client:
    api = OAuth2Api(client)
    
    # Manually traced call
    with tracer.start_as_current_span("get-user-info"):
        user_info = api.get_user_info()
        print(user_info)
    
    # Or use the decorator
    @traced_api_call(tracer, "custom-operation")
    def get_user():
        return api.get_user_info()
    
    user = get_user()

# Cleanup on shutdown
shutdown()
```

## Configuration Options

| Option | Type | Description |
|--------|------|-------------|
| `host` | `str` | Base URL of the OAuth2 server |
| `access_token` | `str` | Bearer token for authentication |
| `username` | `str` | Username for basic auth |
| `password` | `str` | Password for basic auth |
| `api_key` | `dict` | API key configuration |
| `ssl_ca_cert` | `str` | Path to CA certificate |
| `verify_ssl` | `bool` | Whether to verify SSL certificates |

## Environment Variables

The client can be configured using environment variables:

```bash
export OAUTH2_BASE_URL=https://auth.example.com
export OAUTH2_CLIENT_ID=my-client
export OAUTH2_CLIENT_SECRET=secret
export OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318
export OTEL_SERVICE_NAME=my-app
```

## Development

```bash
# Install dev dependencies
pip install -e ".[dev]"

# Run tests
pytest

# Format code
black src tests
isort src tests

# Type checking
mypy src

# Lint
ruff check src tests
```

## License

MIT License
