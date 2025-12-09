/**
 * Example: Basic usage of the OAuth2 client with OpenTelemetry tracing
 */

import { setupTracing, createTracedApi } from '../src/tracing';

// Note: After code generation, you would import:
// import { Configuration, OAuth2Api } from '../src';

async function basicUsage(): Promise<void> {
  console.log('Basic OAuth2 Client Usage');
  console.log('='.repeat(40));

  // After code generation, you would use:
  // const config = new Configuration({
  //   basePath: 'https://auth.example.com',
  //   accessToken: 'your-access-token',
  // });
  //
  // const api = new OAuth2Api(config);
  // const userInfo = await api.getUserInfo();
  // console.log('User:', userInfo);

  console.log('Note: Run code generation first to use the full API client');
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

  // After code generation, you would use:
  // const config = new Configuration({
  //   basePath: 'https://auth.example.com',
  // });
  //
  // const api = new OAuth2Api(config);
  // const tracedApi = createTracedApi(tracer, api, 'OAuth2Api');
  //
  // // This call will be automatically traced
  // const userInfo = await tracedApi.getUserInfo();

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
