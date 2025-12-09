// Package oauth2client provides models for OAuth2 API requests and responses.
package oauth2client

// TokenResponse represents an OAuth2 token response.
type TokenResponse struct {
	AccessToken  string `json:"access_token"`
	TokenType    string `json:"token_type"`
	ExpiresIn    int    `json:"expires_in,omitempty"`
	RefreshToken string `json:"refresh_token,omitempty"`
	Scope        string `json:"scope,omitempty"`
	IdToken      string `json:"id_token,omitempty"`
}

// TokenRequest represents an OAuth2 token request.
type TokenRequest struct {
	GrantType    string `json:"grant_type"`
	Code         string `json:"code,omitempty"`
	RedirectUri  string `json:"redirect_uri,omitempty"`
	ClientId     string `json:"client_id,omitempty"`
	ClientSecret string `json:"client_secret,omitempty"`
	RefreshToken string `json:"refresh_token,omitempty"`
	Scope        string `json:"scope,omitempty"`
	CodeVerifier string `json:"code_verifier,omitempty"`
}

// OpenIDConfiguration represents the OpenID Connect discovery document.
type OpenIDConfiguration struct {
	Issuer                           string   `json:"issuer"`
	AuthorizationEndpoint            string   `json:"authorization_endpoint"`
	TokenEndpoint                    string   `json:"token_endpoint"`
	UserinfoEndpoint                 string   `json:"userinfo_endpoint,omitempty"`
	JwksUri                          string   `json:"jwks_uri"`
	RegistrationEndpoint             string   `json:"registration_endpoint,omitempty"`
	ScopesSupported                  []string `json:"scopes_supported,omitempty"`
	ResponseTypesSupported           []string `json:"response_types_supported"`
	ResponseModesSupported           []string `json:"response_modes_supported,omitempty"`
	GrantTypesSupported              []string `json:"grant_types_supported,omitempty"`
	SubjectTypesSupported            []string `json:"subject_types_supported"`
	IdTokenSigningAlgValuesSupported []string `json:"id_token_signing_alg_values_supported"`
	TokenEndpointAuthMethodsSupported []string `json:"token_endpoint_auth_methods_supported,omitempty"`
	ClaimsSupported                  []string `json:"claims_supported,omitempty"`
	CodeChallengeMethodsSupported    []string `json:"code_challenge_methods_supported,omitempty"`
}

// UserInfo represents a user info response.
type UserInfo struct {
	Sub               string `json:"sub"`
	Name              string `json:"name,omitempty"`
	GivenName         string `json:"given_name,omitempty"`
	FamilyName        string `json:"family_name,omitempty"`
	PreferredUsername string `json:"preferred_username,omitempty"`
	Email             string `json:"email,omitempty"`
	EmailVerified     bool   `json:"email_verified,omitempty"`
	Picture           string `json:"picture,omitempty"`
	Locale            string `json:"locale,omitempty"`
	UpdatedAt         int64  `json:"updated_at,omitempty"`
}

// OAuth2Error represents an OAuth2 error response.
type OAuth2Error struct {
	Error            string `json:"error"`
	ErrorDescription string `json:"error_description,omitempty"`
	ErrorUri         string `json:"error_uri,omitempty"`
	State            string `json:"state,omitempty"`
}

// ClientRegistrationRequest represents a client registration request.
type ClientRegistrationRequest struct {
	RedirectUris            []string               `json:"redirect_uris"`
	ClientName              string                 `json:"client_name,omitempty"`
	ClientUri               string                 `json:"client_uri,omitempty"`
	LogoUri                 string                 `json:"logo_uri,omitempty"`
	Contacts                []string               `json:"contacts,omitempty"`
	TosUri                  string                 `json:"tos_uri,omitempty"`
	PolicyUri               string                 `json:"policy_uri,omitempty"`
	JwksUri                 string                 `json:"jwks_uri,omitempty"`
	Jwks                    map[string]interface{} `json:"jwks,omitempty"`
	SoftwareId              string                 `json:"software_id,omitempty"`
	SoftwareVersion         string                 `json:"software_version,omitempty"`
	GrantTypes              []string               `json:"grant_types,omitempty"`
	ResponseTypes           []string               `json:"response_types,omitempty"`
	TokenEndpointAuthMethod string                 `json:"token_endpoint_auth_method,omitempty"`
	Scope                   string                 `json:"scope,omitempty"`
}

// ClientRegistrationResponse represents a client registration response.
type ClientRegistrationResponse struct {
	ClientId                string                 `json:"client_id"`
	ClientSecret            string                 `json:"client_secret,omitempty"`
	ClientIdIssuedAt        int64                  `json:"client_id_issued_at,omitempty"`
	ClientSecretExpiresAt   int64                  `json:"client_secret_expires_at,omitempty"`
	RedirectUris            []string               `json:"redirect_uris"`
	ClientName              string                 `json:"client_name,omitempty"`
	ClientUri               string                 `json:"client_uri,omitempty"`
	LogoUri                 string                 `json:"logo_uri,omitempty"`
	Contacts                []string               `json:"contacts,omitempty"`
	TosUri                  string                 `json:"tos_uri,omitempty"`
	PolicyUri               string                 `json:"policy_uri,omitempty"`
	JwksUri                 string                 `json:"jwks_uri,omitempty"`
	Jwks                    map[string]interface{} `json:"jwks,omitempty"`
	SoftwareId              string                 `json:"software_id,omitempty"`
	SoftwareVersion         string                 `json:"software_version,omitempty"`
	GrantTypes              []string               `json:"grant_types,omitempty"`
	ResponseTypes           []string               `json:"response_types,omitempty"`
	TokenEndpointAuthMethod string                 `json:"token_endpoint_auth_method,omitempty"`
	Scope                   string                 `json:"scope,omitempty"`
}

// JWK represents a JSON Web Key.
type JWK struct {
	Kty    string   `json:"kty"`
	Use    string   `json:"use,omitempty"`
	KeyOps []string `json:"key_ops,omitempty"`
	Alg    string   `json:"alg,omitempty"`
	Kid    string   `json:"kid,omitempty"`
	X5u    string   `json:"x5u,omitempty"`
	X5c    []string `json:"x5c,omitempty"`
	X5t    string   `json:"x5t,omitempty"`
	X5tS256 string  `json:"x5t#S256,omitempty"`
	// RSA specific
	N string `json:"n,omitempty"`
	E string `json:"e,omitempty"`
	// EC specific
	Crv string `json:"crv,omitempty"`
	X   string `json:"x,omitempty"`
	Y   string `json:"y,omitempty"`
}

// JWKS represents a JSON Web Key Set.
type JWKS struct {
	Keys []JWK `json:"keys"`
}

// IntrospectionRequest represents a token introspection request.
type IntrospectionRequest struct {
	Token         string `json:"token"`
	TokenTypeHint string `json:"token_type_hint,omitempty"`
}

// IntrospectionResponse represents a token introspection response.
type IntrospectionResponse struct {
	Active    bool   `json:"active"`
	Scope     string `json:"scope,omitempty"`
	ClientId  string `json:"client_id,omitempty"`
	Username  string `json:"username,omitempty"`
	TokenType string `json:"token_type,omitempty"`
	Exp       int64  `json:"exp,omitempty"`
	Iat       int64  `json:"iat,omitempty"`
	Nbf       int64  `json:"nbf,omitempty"`
	Sub       string `json:"sub,omitempty"`
	Aud       string `json:"aud,omitempty"`
	Iss       string `json:"iss,omitempty"`
	Jti       string `json:"jti,omitempty"`
}

// RevocationRequest represents a token revocation request.
type RevocationRequest struct {
	Token         string `json:"token"`
	TokenTypeHint string `json:"token_type_hint,omitempty"`
}

// GenericOpenAPIError represents a generic OpenAPI error.
type GenericOpenAPIError struct {
	body  []byte
	error string
	model interface{}
}

// Error returns the error message.
func (e GenericOpenAPIError) Error() string {
	return e.error
}

// Body returns the raw response body.
func (e GenericOpenAPIError) Body() []byte {
	return e.body
}

// Model returns the unpacked model of the error.
func (e GenericOpenAPIError) Model() interface{} {
	return e.model
}
