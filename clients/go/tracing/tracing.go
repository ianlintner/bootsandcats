// Package tracing provides OpenTelemetry tracing integration for the OAuth2 client.
package tracing

import (
	"context"
	"net/http"
	"os"

	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/codes"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracehttp"
	"go.opentelemetry.io/otel/propagation"
	"go.opentelemetry.io/otel/sdk/resource"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	semconv "go.opentelemetry.io/otel/semconv/v1.21.0"
	"go.opentelemetry.io/otel/trace"
)

const (
	tracerName = "github.com/bootsandcats/oauth2-client-go"
)

// Config holds the configuration for tracing setup.
type Config struct {
	// ServiceName is the name of the service for tracing
	ServiceName string
	// ServiceVersion is the version of the service
	ServiceVersion string
	// ExporterURL is the OTLP exporter URL
	ExporterURL string
	// Enabled controls whether tracing is enabled
	Enabled bool
}

// SetupTracing initializes OpenTelemetry tracing for the OAuth2 client.
// It returns a shutdown function that should be called when the application exits.
func SetupTracing(ctx context.Context, cfg Config) (func(context.Context) error, error) {
	if !cfg.Enabled {
		// Return a no-op shutdown function
		return func(context.Context) error { return nil }, nil
	}

	// Get exporter URL from environment if not provided
	exporterURL := cfg.ExporterURL
	if exporterURL == "" {
		exporterURL = os.Getenv("OTEL_EXPORTER_OTLP_ENDPOINT")
		if exporterURL == "" {
			exporterURL = "http://localhost:4318"
		}
	}

	// Create OTLP trace exporter
	exporter, err := otlptracehttp.New(ctx,
		otlptracehttp.WithEndpoint(exporterURL),
		otlptracehttp.WithInsecure(),
	)
	if err != nil {
		return nil, err
	}

	// Create resource
	res, err := resource.Merge(
		resource.Default(),
		resource.NewWithAttributes(
			semconv.SchemaURL,
			semconv.ServiceName(cfg.ServiceName),
			semconv.ServiceVersion(cfg.ServiceVersion),
		),
	)
	if err != nil {
		return nil, err
	}

	// Create trace provider
	tp := sdktrace.NewTracerProvider(
		sdktrace.WithBatcher(exporter),
		sdktrace.WithResource(res),
	)

	// Set global tracer provider
	otel.SetTracerProvider(tp)

	// Set global propagator
	otel.SetTextMapPropagator(propagation.NewCompositeTextMapPropagator(
		propagation.TraceContext{},
		propagation.Baggage{},
	))

	return tp.Shutdown, nil
}

// GetTracer returns a tracer for the OAuth2 client.
func GetTracer() trace.Tracer {
	return otel.Tracer(tracerName)
}

// TracedRoundTripper is an http.RoundTripper that adds tracing to HTTP requests.
type TracedRoundTripper struct {
	base http.RoundTripper
}

// RoundTrip implements the http.RoundTripper interface.
func (t *TracedRoundTripper) RoundTrip(req *http.Request) (*http.Response, error) {
	ctx := req.Context()
	tracer := GetTracer()

	ctx, span := tracer.Start(ctx, req.Method+" "+req.URL.Path,
		trace.WithSpanKind(trace.SpanKindClient),
		trace.WithAttributes(
			semconv.HTTPMethod(req.Method),
			semconv.HTTPURL(req.URL.String()),
			semconv.HTTPScheme(req.URL.Scheme),
			attribute.String("http.host", req.URL.Host),
			attribute.String("http.path", req.URL.Path),
		),
	)
	defer span.End()

	// Inject trace context into headers
	otel.GetTextMapPropagator().Inject(ctx, propagation.HeaderCarrier(req.Header))

	// Create a new request with the updated context
	req = req.WithContext(ctx)

	// Perform the request
	resp, err := t.base.RoundTrip(req)
	if err != nil {
		span.RecordError(err)
		span.SetStatus(codes.Error, err.Error())
		return nil, err
	}

	// Record response attributes
	span.SetAttributes(semconv.HTTPStatusCode(resp.StatusCode))

	if resp.StatusCode >= 400 {
		span.SetStatus(codes.Error, resp.Status)
	} else {
		span.SetStatus(codes.Ok, "")
	}

	return resp, nil
}

// InstrumentedHTTPClient returns an http.Client with tracing instrumentation.
func InstrumentedHTTPClient() *http.Client {
	return &http.Client{
		Transport: &TracedRoundTripper{
			base: http.DefaultTransport,
		},
	}
}

// WithTracedClient wraps an existing http.Client with tracing.
func WithTracedClient(client *http.Client) *http.Client {
	if client == nil {
		client = http.DefaultClient
	}
	transport := client.Transport
	if transport == nil {
		transport = http.DefaultTransport
	}
	return &http.Client{
		Transport:     &TracedRoundTripper{base: transport},
		CheckRedirect: client.CheckRedirect,
		Jar:           client.Jar,
		Timeout:       client.Timeout,
	}
}

// SpanFromContext returns the current span from the context.
func SpanFromContext(ctx context.Context) trace.Span {
	return trace.SpanFromContext(ctx)
}

// StartSpan starts a new span with the given name.
func StartSpan(ctx context.Context, name string, opts ...trace.SpanStartOption) (context.Context, trace.Span) {
	return GetTracer().Start(ctx, name, opts...)
}
