/**
 * Example: Basic usage of the OAuth2 client with OpenTelemetry tracing
 */

import { Configuration, DiscoveryApi, TokenApi, UserInfoApi } from '../src';
import { setupTracing, createTracedApi } from '../src/tracing';

async function basicUsage(): Promise<void> {
  console.log('Basic OAuth2 Client Usage');
  console.log('='.repeat(40));

  // Create configuration
  const config = new Configuration({
    basePath: process.env.OAUTH2_SERVER_URL || 'http://localhost:9000',
    accessToken: 'your-access-token', // Replace with actual token
  });

  // Create API clients
  const discoveryApi = new DiscoveryApi(config);
  const tokenApi = new TokenApi(config);
  const userInfoApi = new UserInfoApi(config);

  console.log('API clients created successfully');
  console.log('Base path:', config.basePath);

  // Example: Get OpenID Configuration (discovery)
  // const openIdConfig = await discoveryApi.getOpenIDConfiguration();
  // console.log('Issuer:', openIdConfig.issuer);
  // console.log('Authorization endpoint:', openIdConfig.authorization_endpoint);

  // Example: Exchange authorization code for tokens
  // const tokens = await tokenApi.exchangeCode({
  //   code: 'authorization-code',
  //   redirect_uri: 'http://localhost:3000/callback',
  //   client_id: 'your-client-id',
  // });
  // console.log('Access token:', tokens.access_token);

  // Example: Get user info
  // const userInfo = await userInfoApi.getUserInfo();
  // console.log('User:', userInfo);
}

async function usageWithTracing(): Promise<void> {
  console.log('\nOAuth2 Client with Tracing');
  console.log('='.repeat(40));

  // Initialize tracing
  const { tracer, shutdown } = setupTracing({
    serviceName: 'oauth2-client-example',
    serviceVersion: '1.0.0',
    exporterUrl: process.env.OTEL_EXPORTER_OTLP_ENDPOINT || 'http://localhost:4318/v1/traces',
  });

  console.log('Tracing initialized successfully');

  // Create configuration
  const config = new Configuration({
    basePath: process.env.OAUTH2_SERVER_URL || 'http://localhost:9000',
  });

  // Create API and wrap with tracing
  const discoveryApi = new DiscoveryApi(config);
  const tracedDiscoveryApi = createTracedApi(tracer, discoveryApi, 'DiscoveryApi');

  console.log('Traced API client created');

  // Example of manual tracing
  const span = tracer.startSpan('example-operation');
  span.setAttribute('example.key', 'example.value');
  console.log('Span created successfully');
  span.end();

  // Cleanup
  await shutdown();
  console.log('Tracing shutdown complete');
}

async function main(): Promise<void> {
  await basicUsage();

  try {
    await usageWithTracing();
  } catch (error) {
    console.log(`\nTracing example skipped: ${error}`);
    console.log('Make sure OpenTelemetry dependencies are installed');
  }
}

main().catch(console.error);
