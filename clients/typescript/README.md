# OAuth2 Client - TypeScript

TypeScript/JavaScript client for the OAuth2 Authorization Server API.

## Installation

```bash
npm install @bootsandcats/oauth2-client
# or
yarn add @bootsandcats/oauth2-client
```

## Usage

```typescript
import { Configuration, OAuth2Api } from '@bootsandcats/oauth2-client';

const config = new Configuration({
  basePath: 'https://auth.example.com',
  accessToken: 'your-access-token',
});

const api = new OAuth2Api(config);

// Get user info
const userInfo = await api.getUserInfo();
console.log(userInfo);
```

## OpenTelemetry Integration

```typescript
import { Configuration, OAuth2Api } from '@bootsandcats/oauth2-client';
import { setupTracing } from '@bootsandcats/oauth2-client/tracing';

// Initialize tracing
const { tracer, shutdown } = setupTracing({
  serviceName: 'my-app',
  exporterUrl: 'http://localhost:4318/v1/traces',
});

const config = new Configuration({
  basePath: 'https://auth.example.com',
});

const api = new OAuth2Api(config);

// Requests will be automatically traced
const userInfo = await api.getUserInfo();

// Cleanup on shutdown
process.on('SIGTERM', shutdown);
```

## Configuration Options

| Option | Type | Description |
|--------|------|-------------|
| `basePath` | `string` | Base URL of the OAuth2 server |
| `accessToken` | `string` | Bearer token for authentication |
| `username` | `string` | Username for basic auth |
| `password` | `string` | Password for basic auth |
| `apiKey` | `string \| (name: string) => string` | API key |

## Development

```bash
# Install dependencies
npm install

# Build
npm run build

# Run tests
npm test

# Lint
npm run lint
```
