// Example: Basic usage of the OAuth2 client with OpenTelemetry tracing
package main

import (
	"context"
	"fmt"
	"os"
	"time"

	oauth2 "github.com/bootsandcats/oauth2-client-go"
	"github.com/bootsandcats/oauth2-client-go/tracing"
)

func basicUsage() {
	fmt.Println("Basic OAuth2 Client Usage")
	fmt.Println("========================================")

	// Create configuration
	baseURL := os.Getenv("OAUTH2_SERVER_URL")
	if baseURL == "" {
		baseURL = "http://localhost:9000"
	}

	config := oauth2.NewConfiguration()
	config.BasePath = baseURL
	config.AccessToken = "your-access-token" // Replace with actual token

	// Create API client
	client := oauth2.NewAPIClient(config)
	
	fmt.Printf("API client created for: %s\n", config.BasePath)

	// Create context with access token
	ctx := context.Background()

	// Example: Get OpenID Configuration (discovery)
	// openIDConfig, err := client.DiscoveryApi.GetOpenIDConfiguration(ctx)
	// if err != nil {
	// 	log.Fatalf("Error: %v", err)
	// }
	// fmt.Printf("Issuer: %s\n", openIDConfig.Issuer)

	// Example: Exchange authorization code for tokens
	// tokens, err := client.TokenApi.ExchangeCode(ctx, oauth2.TokenRequest{
	// 	Code:        "authorization-code",
	// 	RedirectUri: "http://localhost:3000/callback",
	// 	ClientId:    "your-client-id",
	// })
	// if err != nil {
	// 	log.Fatalf("Error: %v", err)
	// }
	// fmt.Printf("Access token: %s\n", tokens.AccessToken)

	// Example: Get user info
	// userInfo, err := client.UserInfoApi.GetUserInfo(ctx)
	// if err != nil {
	// 	log.Fatalf("Error: %v", err)
	// }
	// fmt.Printf("User: %s\n", userInfo.Name)

	_ = ctx // Suppress unused variable warning
	_ = client
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

	// Create configuration with instrumented HTTP client
	baseURL := os.Getenv("OAUTH2_SERVER_URL")
	if baseURL == "" {
		baseURL = "http://localhost:9000"
	}

	config := oauth2.NewConfiguration()
	config.BasePath = baseURL
	config.HTTPClient = tracing.InstrumentedHTTPClient()

	// Create API client
	client := oauth2.NewAPIClient(config)

	fmt.Printf("Traced API client created for: %s\n", config.BasePath)

	// Example: Use traced API calls
	// ctx, span := tracing.StartSpan(ctx, "get-openid-configuration")
	// defer span.End()
	//
	// openIDConfig, err := client.DiscoveryApi.GetOpenIDConfiguration(ctx)
	// if err != nil {
	// 	span.RecordError(err)
	// 	return err
	// }

	// Example of manual tracing
	tracer := tracing.GetTracer()
	_, span := tracer.Start(ctx, "example-operation")
	fmt.Println("Span created successfully")

	// Simulate some work
	time.Sleep(100 * time.Millisecond)

	span.End()

	_ = client // Suppress unused variable warning

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
