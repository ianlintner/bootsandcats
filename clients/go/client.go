// Package oauth2client provides a Go client for the OAuth2 Authorization Server API.
//
// This package includes models, API clients, and OpenTelemetry tracing support.
//
// Example usage:
//
//	import "github.com/bootsandcats/oauth2-client-go"
//
//	func main() {
//		config := oauth2client.NewConfiguration()
//		config.BasePath = "https://auth.example.com"
//
//		client := oauth2client.NewAPIClient(config)
//
//		// Make API calls
//		discovery, _, err := client.DiscoveryApi.GetOpenIDConfiguration(context.Background())
//		if err != nil {
//			log.Fatal(err)
//		}
//		fmt.Println(discovery.Issuer)
//	}
package oauth2client

import (
	"net/http"
	"time"
)

// Configuration holds the configuration for API clients.
type Configuration struct {
	// BasePath is the base URL for API requests.
	BasePath string `json:"basePath,omitempty"`
	// Host is the hostname (without scheme) for API requests.
	Host string `json:"host,omitempty"`
	// Scheme is the URL scheme (http or https).
	Scheme string `json:"scheme,omitempty"`
	// DefaultHeader contains headers to include in all requests.
	DefaultHeader map[string]string `json:"defaultHeader,omitempty"`
	// UserAgent is the user agent string for requests.
	UserAgent string `json:"userAgent,omitempty"`
	// HTTPClient is the HTTP client to use for requests.
	HTTPClient *http.Client
	// Debug enables debug output.
	Debug bool `json:"debug,omitempty"`
	// AccessToken is the bearer access token for authentication.
	AccessToken string `json:"accessToken,omitempty"`
	// Username is the username for basic authentication.
	Username string `json:"username,omitempty"`
	// Password is the password for basic authentication.
	Password string `json:"password,omitempty"`
}

// NewConfiguration creates a new Configuration with default values.
func NewConfiguration() *Configuration {
	return &Configuration{
		BasePath:      "http://localhost:9000",
		Scheme:        "http",
		DefaultHeader: make(map[string]string),
		UserAgent:     "bootsandcats-oauth2-client-go/1.0.0",
		HTTPClient: &http.Client{
			Timeout: 30 * time.Second,
		},
	}
}

// AddDefaultHeader adds a default header to the configuration.
func (c *Configuration) AddDefaultHeader(key string, value string) {
	c.DefaultHeader[key] = value
}

// APIClient is the main client for making API requests.
type APIClient struct {
	cfg *Configuration

	// API endpoints
	DiscoveryApi *DiscoveryApiService
	TokenApi     *TokenApiService
	UserInfoApi  *UserInfoApiService
}

// NewAPIClient creates a new API client.
func NewAPIClient(cfg *Configuration) *APIClient {
	if cfg == nil {
		cfg = NewConfiguration()
	}

	c := &APIClient{cfg: cfg}
	c.DiscoveryApi = &DiscoveryApiService{client: c}
	c.TokenApi = &TokenApiService{client: c}
	c.UserInfoApi = &UserInfoApiService{client: c}

	return c
}

// GetConfig returns the client configuration.
func (c *APIClient) GetConfig() *Configuration {
	return c.cfg
}

// DiscoveryApiService handles discovery-related API calls.
type DiscoveryApiService struct {
	client *APIClient
}

// TokenApiService handles token-related API calls.
type TokenApiService struct {
	client *APIClient
}

// UserInfoApiService handles user info-related API calls.
type UserInfoApiService struct {
	client *APIClient
}
