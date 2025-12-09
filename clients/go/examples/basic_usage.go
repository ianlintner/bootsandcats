// Example: Basic usage of the OAuth2 client with OpenTelemetry tracing
package main

import (
	"context"
	"fmt"
	"os"
	"time"

	"github.com/bootsandcats/oauth2-client-go/tracing"
)

// Note: After code generation, you would import:
// oauth2 "github.com/bootsandcats/oauth2-client-go"

func basicUsage() {
	fmt.Println("Basic OAuth2 Client Usage")
	fmt.Println("========================================")

	// After code generation, you would use:
	// config := oauth2.NewConfiguration()
	// config.Servers = oauth2.ServerConfigurations{
	// 	{URL: "https://auth.example.com"},
	// }
	//
	// ctx := context.WithValue(context.Background(), oauth2.ContextAccessToken, "your-access-token")
	// client := oauth2.NewAPIClient(config)
	//
	// userInfo, _, err := client.OAuth2Api.GetUserInfo(ctx).Execute()
	// if err != nil {
	// 	log.Fatalf("Error: %v", err)
	// }
	// fmt.Printf("User: %+v\n", userInfo)

	fmt.Println("Note: Run code generation first to use the full API client")
}

func usageWithTracing() error {
	fmt.Println("\nOAuth2 Client with Tracing")
	fmt.Println("========================================")

	ctx := context.Background()

	// Get exporter URL from environment
	exporterURL := os.Getenv("OTEL_EXPORTER_OTLP_ENDPOINT")
	if exporterURL == "" {
		exporterURL = "http://localhost:4318"
	}

	// Initialize tracing
	shutdown, err := tracing.SetupTracing(ctx, tracing.Config{
		ServiceName:    "oauth2-client-example",
		ServiceVersion: "1.0.0",
		ExporterURL:    exporterURL,
		Enabled:        true,
	})
	if err != nil {
		return fmt.Errorf("failed to setup tracing: %w", err)
	}
	defer shutdown(ctx)

	fmt.Println("Tracing initialized successfully")

	// After code generation, you would use:
	// config := oauth2.NewConfiguration()
	// config.Servers = oauth2.ServerConfigurations{
	// 	{URL: "https://auth.example.com"},
	// }
	// config.HTTPClient = tracing.InstrumentedHTTPClient()
	//
	// client := oauth2.NewAPIClient(config)
	//
	// ctx, span := tracing.StartSpan(ctx, "get-user-info")
	// defer span.End()
	//
	// userInfo, _, err := client.OAuth2Api.GetUserInfo(ctx).Execute()
	// if err != nil {
	// 	span.RecordError(err)
	// 	return err
	// }
	// fmt.Printf("User: %+v\n", userInfo)

	// Example of manual tracing
	tracer := tracing.GetTracer()
	ctx, span := tracer.Start(ctx, "example-operation")
	span.SetAttributes()
	fmt.Println("Span created successfully")

	// Simulate some work
	time.Sleep(100 * time.Millisecond)

	span.End()

	fmt.Println("Tracing shutdown complete")
	return nil
}

func main() {
	basicUsage()

	if err := usageWithTracing(); err != nil {
		fmt.Printf("\nTracing example failed: %v\n", err)
		fmt.Println("Make sure OpenTelemetry collector is running")
	}
}
