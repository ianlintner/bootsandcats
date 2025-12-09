/**
 * OpenTelemetry Tracing Integration for OAuth2 Client
 * 
 * This module provides automatic tracing for all API calls made through
 * the OAuth2 client SDK.
 */

import { trace, context, SpanKind, SpanStatusCode, Tracer } from '@opentelemetry/api';
import { NodeTracerProvider } from '@opentelemetry/sdk-trace-node';
import { SimpleSpanProcessor } from '@opentelemetry/sdk-trace-base';
import { OTLPTraceExporter } from '@opentelemetry/exporter-trace-otlp-http';
import { Resource } from '@opentelemetry/resources';
import { SEMRESATTRS_SERVICE_NAME, SEMRESATTRS_SERVICE_VERSION } from '@opentelemetry/semantic-conventions';

export interface TracingConfig {
  serviceName: string;
  serviceVersion?: string;
  exporterUrl?: string;
  enabled?: boolean;
}

export interface TracingSetupResult {
  tracer: Tracer;
  shutdown: () => Promise<void>;
}

/**
 * Setup OpenTelemetry tracing for the OAuth2 client
 */
export function setupTracing(config: TracingConfig): TracingSetupResult {
  const {
    serviceName,
    serviceVersion = '1.0.0',
    exporterUrl = process.env.OTEL_EXPORTER_OTLP_ENDPOINT || 'http://localhost:4318/v1/traces',
    enabled = true,
  } = config;

  if (!enabled) {
    return {
      tracer: trace.getTracer(serviceName),
      shutdown: async () => {},
    };
  }

  const resource = new Resource({
    [SEMRESATTRS_SERVICE_NAME]: serviceName,
    [SEMRESATTRS_SERVICE_VERSION]: serviceVersion,
  });

  const provider = new NodeTracerProvider({ resource });

  const exporter = new OTLPTraceExporter({
    url: exporterUrl,
  });

  provider.addSpanProcessor(new SimpleSpanProcessor(exporter));
  provider.register();

  const tracer = trace.getTracer(serviceName, serviceVersion);

  const shutdown = async (): Promise<void> => {
    await provider.shutdown();
  };

  return { tracer, shutdown };
}

/**
 * Decorator for tracing async functions
 */
export function withTracing<T extends (...args: unknown[]) => Promise<unknown>>(
  tracer: Tracer,
  operationName: string,
  fn: T
): T {
  return (async (...args: Parameters<T>): Promise<ReturnType<T>> => {
    const span = tracer.startSpan(operationName, {
      kind: SpanKind.CLIENT,
      attributes: {
        'rpc.system': 'http',
        'rpc.service': 'oauth2-server',
        'rpc.method': operationName,
      },
    });

    try {
      const result = await context.with(trace.setSpan(context.active(), span), () => fn(...args));
      span.setStatus({ code: SpanStatusCode.OK });
      return result as ReturnType<T>;
    } catch (error) {
      span.setStatus({
        code: SpanStatusCode.ERROR,
        message: error instanceof Error ? error.message : 'Unknown error',
      });
      span.recordException(error instanceof Error ? error : new Error(String(error)));
      throw error;
    } finally {
      span.end();
    }
  }) as T;
}

/**
 * Create a traced API wrapper
 */
export function createTracedApi<T extends object>(tracer: Tracer, api: T, apiName: string): T {
  const tracedApi = {} as T;

  for (const key of Object.getOwnPropertyNames(Object.getPrototypeOf(api))) {
    const value = (api as Record<string, unknown>)[key];
    if (typeof value === 'function' && key !== 'constructor') {
      (tracedApi as Record<string, unknown>)[key] = withTracing(
        tracer,
        `${apiName}.${key}`,
        value.bind(api) as (...args: unknown[]) => Promise<unknown>
      );
    }
  }

  return tracedApi;
}

export default setupTracing;
