# Client Onboarding Guide

This guide provides step-by-step instructions for integrating with the OAuth2 Authorization Server across various programming languages and frameworks.

## Overview

The OAuth2 Authorization Server supports the following grant types:

- **Authorization Code with PKCE** - For web applications, SPAs, and mobile apps
- **Client Credentials** - For machine-to-machine (M2M) authentication
- **Refresh Token** - For obtaining new access tokens

## Quick Links

- [OpenAPI Documentation](/swagger-ui.html) - Interactive API documentation
- [OpenAPI Specification](/v3/api-docs) - JSON specification for code generation

---

## Client Configuration Examples

=== "Java (Spring Boot)"

    ### Using Spring Security OAuth2 Client

    Add the dependency to your `build.gradle.kts`:

    ```kotlin
    dependencies {
        implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
        implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    }
    ```

    Configure your `application.yml`:

    ```yaml
    spring:
      security:
        oauth2:
          client:
            registration:
              oauth2-server:
                client-id: demo-client
                client-secret: ${OAUTH2_CLIENT_SECRET}
                authorization-grant-type: authorization_code
                redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
                scope: openid,profile,email
            provider:
              oauth2-server:
                issuer-uri: http://localhost:9000
    ```

=== "Python"

    ### Using Authlib

    Install the library:

    ```bash
    pip install Authlib requests
    ```

    Configure the client:

    ```python
    from authlib.integrations.requests_client import OAuth2Session

    client_id = 'demo-client'
    client_secret = 'demo-secret'
    authorization_base_url = 'http://localhost:9000/oauth2/authorize'
    token_url = 'http://localhost:9000/oauth2/token'

    client = OAuth2Session(client_id, client_secret, scope='openid profile email')
    
    # 1. Redirect user to provider
    uri, state = client.create_authorization_url(authorization_base_url)
    print(f'Please go to {uri} and authorize access.')

    # 2. Get the authorization response URL
    authorization_response = input('Enter the full callback URL: ')

    # 3. Fetch the access token
    token = client.fetch_token(token_url, authorization_response=authorization_response)
    print(token)
    ```

=== "JavaScript (Node.js)"

    ### Using openid-client

    Install the library:

    ```bash
    npm install openid-client
    ```

    Configure the client:

    ```javascript
    const { Issuer } = require('openid-client');

    (async () => {
      const issuer = await Issuer.discover('http://localhost:9000');
      
      const client = new issuer.Client({
        client_id: 'demo-client',
        client_secret: 'demo-secret',
        redirect_uris: ['http://localhost:3000/cb'],
        response_types: ['code'],
      });

      // 1. Get authorization URL
      const url = client.authorizationUrl({
        scope: 'openid profile email',
      });
      console.log('Authorize at:', url);

      // 2. After callback, exchange code for token
      // const tokenSet = await client.callback('http://localhost:3000/cb', params, { code_verifier });
    })();
    ```
        provider:
          oauth2-server:
            issuer-uri: http://localhost:9000
      resourceserver:
        jwt:
          issuer-uri: http://localhost:9000
```

### Client Credentials Flow (M2M)

```java
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.stereotype.Service;

@Service
public class OAuth2ClientService {
    
    private final OAuth2AuthorizedClientManager clientManager;
    
    public OAuth2ClientService(OAuth2AuthorizedClientManager clientManager) {
        this.clientManager = clientManager;
    }
    
    public String getAccessToken() {
        OAuth2AuthorizeRequest request = OAuth2AuthorizeRequest
            .withClientRegistrationId("m2m-client")
            .principal("m2m-client")
            .build();
        
        return clientManager.authorize(request)
            .getAccessToken()
            .getTokenValue();
    }
}
```

### Validating JWT Tokens

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
public class JwtConfig {
    
    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder
            .withJwkSetUri("http://localhost:9000/oauth2/jwks")
            .build();
    }
}
```

---

## Python

### Using `requests-oauthlib`

Install dependencies:

```bash
pip install requests-oauthlib pyjwt cryptography
```

#### Client Credentials Flow

```python
from requests_oauthlib import OAuth2Session
from oauthlib.oauth2 import BackendApplicationClient

# Client Credentials Flow
client_id = "m2m-client"
client_secret = "m2m-secret"  # Use environment variable in production

client = BackendApplicationClient(client_id=client_id)
oauth = OAuth2Session(client=client)

token = oauth.fetch_token(
    token_url="http://localhost:9000/oauth2/token",
    client_id=client_id,
    client_secret=client_secret,
    scope=["api:read", "api:write"]
)

access_token = token["access_token"]
print(f"Access Token: {access_token}")

# Make authenticated requests
response = oauth.get("https://api.example.com/resource")
```

#### Authorization Code Flow with PKCE

```python
import secrets
import hashlib
import base64
from requests_oauthlib import OAuth2Session

# Generate PKCE code verifier and challenge
code_verifier = secrets.token_urlsafe(32)
code_challenge = base64.urlsafe_b64encode(
    hashlib.sha256(code_verifier.encode()).digest()
).decode().rstrip("=")

client_id = "public-client"
redirect_uri = "http://localhost:3000/callback"
authorization_base_url = "http://localhost:9000/oauth2/authorize"
token_url = "http://localhost:9000/oauth2/token"

oauth = OAuth2Session(client_id, redirect_uri=redirect_uri, scope=["openid", "profile", "email"])

# Step 1: Get authorization URL
authorization_url, state = oauth.authorization_url(
    authorization_base_url,
    code_challenge=code_challenge,
    code_challenge_method="S256"
)
print(f"Visit this URL to authorize: {authorization_url}")

# Step 2: User authorizes and gets redirected with code
# authorization_response = input("Enter the full callback URL: ")

# Step 3: Exchange code for tokens
# token = oauth.fetch_token(
#     token_url,
#     authorization_response=authorization_response,
#     code_verifier=code_verifier
# )
```

#### Validating JWT Tokens

```python
import jwt
from jwt import PyJWKClient

jwks_url = "http://localhost:9000/oauth2/jwks"
jwks_client = PyJWKClient(jwks_url)

def validate_token(token: str) -> dict:
    signing_key = jwks_client.get_signing_key_from_jwt(token)
    
    payload = jwt.decode(
        token,
        signing_key.key,
        algorithms=["RS256"],
        audience="demo-client",
        issuer="http://localhost:9000"
    )
    return payload

# Example usage
# claims = validate_token(access_token)
# print(f"Subject: {claims['sub']}")
```

---

## Go

### Using `golang.org/x/oauth2`

Install dependencies:

```bash
go get golang.org/x/oauth2
go get github.com/golang-jwt/jwt/v5
```

#### Client Credentials Flow

```go
package main

import (
    "context"
    "fmt"
    "log"

    "golang.org/x/oauth2"
    "golang.org/x/oauth2/clientcredentials"
)

func main() {
    config := &clientcredentials.Config{
        ClientID:     "m2m-client",
        ClientSecret: "m2m-secret", // Use environment variable in production
        TokenURL:     "http://localhost:9000/oauth2/token",
        Scopes:       []string{"api:read", "api:write"},
    }

    token, err := config.Token(context.Background())
    if err != nil {
        log.Fatalf("Failed to get token: %v", err)
    }

    fmt.Printf("Access Token: %s\n", token.AccessToken)

    // Create HTTP client with token
    client := config.Client(context.Background())
    
    // Make authenticated requests
    resp, err := client.Get("https://api.example.com/resource")
    if err != nil {
        log.Fatalf("Request failed: %v", err)
    }
    defer resp.Body.Close()
}
```

#### Authorization Code Flow with PKCE

```go
package main

import (
    "context"
    "crypto/rand"
    "crypto/sha256"
    "encoding/base64"
    "fmt"
    "log"

    "golang.org/x/oauth2"
)

func generatePKCE() (verifier, challenge string) {
    b := make([]byte, 32)
    rand.Read(b)
    verifier = base64.RawURLEncoding.EncodeToString(b)
    
    h := sha256.Sum256([]byte(verifier))
    challenge = base64.RawURLEncoding.EncodeToString(h[:])
    return
}

func main() {
    config := &oauth2.Config{
        ClientID:    "public-client",
        RedirectURL: "http://localhost:3000/callback",
        Scopes:      []string{"openid", "profile", "email"},
        Endpoint: oauth2.Endpoint{
            AuthURL:  "http://localhost:9000/oauth2/authorize",
            TokenURL: "http://localhost:9000/oauth2/token",
        },
    }

    verifier, challenge := generatePKCE()

    // Step 1: Generate authorization URL
    url := config.AuthCodeURL(
        "random-state",
        oauth2.SetAuthURLParam("code_challenge", challenge),
        oauth2.SetAuthURLParam("code_challenge_method", "S256"),
    )
    fmt.Printf("Visit this URL: %s\n", url)

    // Step 2: Exchange code for tokens (after user authorization)
    // code := "authorization_code_from_callback"
    // token, err := config.Exchange(
    //     context.Background(),
    //     code,
    //     oauth2.SetAuthURLParam("code_verifier", verifier),
    // )
}
```

#### Validating JWT Tokens

```go
package main

import (
    "context"
    "fmt"
    "log"

    "github.com/golang-jwt/jwt/v5"
    "github.com/lestrrat-go/jwx/v2/jwk"
)

func validateToken(tokenString string) (*jwt.Token, error) {
    jwksURL := "http://localhost:9000/oauth2/jwks"
    
    // Fetch JWKS
    set, err := jwk.Fetch(context.Background(), jwksURL)
    if err != nil {
        return nil, fmt.Errorf("failed to fetch JWKS: %w", err)
    }

    token, err := jwt.Parse(tokenString, func(token *jwt.Token) (interface{}, error) {
        kid, ok := token.Header["kid"].(string)
        if !ok {
            return nil, fmt.Errorf("missing kid in token header")
        }

        key, found := set.LookupKeyID(kid)
        if !found {
            return nil, fmt.Errorf("key not found in JWKS")
        }

        var pubKey interface{}
        if err := key.Raw(&pubKey); err != nil {
            return nil, fmt.Errorf("failed to get public key: %w", err)
        }
        return pubKey, nil
    })

    return token, err
}
```

---

## Node.js / TypeScript

### Using `openid-client`

Install dependencies:

```bash
npm install openid-client
# or with yarn
yarn add openid-client
```

#### Client Credentials Flow

```typescript
import { Issuer, Client } from 'openid-client';

async function getAccessToken(): Promise<string> {
  const issuer = await Issuer.discover('http://localhost:9000');
  
  const client = new issuer.Client({
    client_id: 'm2m-client',
    client_secret: process.env.OAUTH2_CLIENT_SECRET || 'm2m-secret',
  });

  const tokenSet = await client.grant({
    grant_type: 'client_credentials',
    scope: 'api:read api:write',
  });

  console.log('Access Token:', tokenSet.access_token);
  return tokenSet.access_token!;
}

getAccessToken().catch(console.error);
```

#### Authorization Code Flow with PKCE

```typescript
import { Issuer, generators, Client } from 'openid-client';

async function startAuthFlow() {
  const issuer = await Issuer.discover('http://localhost:9000');
  
  const client = new issuer.Client({
    client_id: 'public-client',
    token_endpoint_auth_method: 'none',
  });

  // Generate PKCE verifier and challenge
  const codeVerifier = generators.codeVerifier();
  const codeChallenge = generators.codeChallenge(codeVerifier);
  const state = generators.state();

  // Step 1: Generate authorization URL
  const authUrl = client.authorizationUrl({
    scope: 'openid profile email',
    redirect_uri: 'http://localhost:3000/callback',
    code_challenge: codeChallenge,
    code_challenge_method: 'S256',
    state,
  });

  console.log('Visit this URL:', authUrl);

  // Store codeVerifier and state in session for the callback

  // Step 2: In callback handler
  // const params = client.callbackParams(req);
  // const tokenSet = await client.callback(
  //   'http://localhost:3000/callback',
  //   params,
  //   { code_verifier: codeVerifier, state }
  // );
}

startAuthFlow().catch(console.error);
```

#### Express.js Middleware for JWT Validation

```typescript
import express, { Request, Response, NextFunction } from 'express';
import { Issuer } from 'openid-client';

interface AuthenticatedRequest extends Request {
  user?: {
    sub: string;
    scope: string;
    [key: string]: unknown;
  };
}

async function createJwtMiddleware() {
  const issuer = await Issuer.discover('http://localhost:9000');
  
  return async (req: AuthenticatedRequest, res: Response, next: NextFunction) => {
    const authHeader = req.headers.authorization;
    
    if (!authHeader?.startsWith('Bearer ')) {
      return res.status(401).json({ error: 'Missing bearer token' });
    }

    const token = authHeader.substring(7);

    try {
      const keyStore = await issuer.keystore();
      // Use jose library for token verification
      // const verified = await jwtVerify(token, keyStore);
      // req.user = verified.payload;
      next();
    } catch (error) {
      return res.status(401).json({ error: 'Invalid token' });
    }
  };
}

const app = express();

// Protected route
app.get('/api/protected', async (req, res) => {
  res.json({ message: 'Protected resource' });
});

app.listen(3000);
```

---

## .NET / C#

### Using Microsoft.Identity.Web

Install the NuGet package:

```bash
dotnet add package Microsoft.Identity.Web
```

#### Configuration in `appsettings.json`

```json
{
  "OAuth2": {
    "Authority": "http://localhost:9000",
    "ClientId": "demo-client",
    "ClientSecret": "demo-secret",
    "Scopes": ["openid", "profile", "email"]
  }
}
```

#### Client Credentials Flow

```csharp
using System.Net.Http.Headers;

public class OAuth2Service
{
    private readonly HttpClient _httpClient;
    private readonly IConfiguration _configuration;

    public OAuth2Service(HttpClient httpClient, IConfiguration configuration)
    {
        _httpClient = httpClient;
        _configuration = configuration;
    }

    public async Task<string> GetAccessTokenAsync()
    {
        var tokenEndpoint = $"{_configuration["OAuth2:Authority"]}/oauth2/token";
        
        var request = new HttpRequestMessage(HttpMethod.Post, tokenEndpoint);
        request.Content = new FormUrlEncodedContent(new Dictionary<string, string>
        {
            ["grant_type"] = "client_credentials",
            ["client_id"] = _configuration["OAuth2:ClientId"],
            ["client_secret"] = _configuration["OAuth2:ClientSecret"],
            ["scope"] = "api:read api:write"
        });

        var response = await _httpClient.SendAsync(request);
        response.EnsureSuccessStatusCode();

        var tokenResponse = await response.Content.ReadFromJsonAsync<TokenResponse>();
        return tokenResponse?.AccessToken ?? throw new Exception("No access token received");
    }

    private record TokenResponse(
        [property: JsonPropertyName("access_token")] string AccessToken,
        [property: JsonPropertyName("token_type")] string TokenType,
        [property: JsonPropertyName("expires_in")] int ExpiresIn
    );
}
```

#### JWT Validation in ASP.NET Core

```csharp
using Microsoft.AspNetCore.Authentication.JwtBearer;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddJwtBearer(options =>
    {
        options.Authority = "http://localhost:9000";
        options.TokenValidationParameters = new TokenValidationParameters
        {
            ValidateIssuer = true,
            ValidIssuer = "http://localhost:9000",
            ValidateAudience = true,
            ValidAudience = "demo-client",
            ValidateLifetime = true
        };
        options.MetadataAddress = "http://localhost:9000/.well-known/openid-configuration";
    });

builder.Services.AddAuthorization();

var app = builder.Build();

app.UseAuthentication();
app.UseAuthorization();

app.MapGet("/api/protected", () => "Protected resource")
    .RequireAuthorization();

app.Run();
```

---

## Code Generation with OpenAPI

Generate client SDKs from the OpenAPI specification:

### Using OpenAPI Generator

```bash
# Install OpenAPI Generator
npm install @openapitools/openapi-generator-cli -g

# Generate TypeScript client
openapi-generator-cli generate \
  -i http://localhost:9000/v3/api-docs \
  -g typescript-axios \
  -o ./generated/typescript-client

# Generate Python client
openapi-generator-cli generate \
  -i http://localhost:9000/v3/api-docs \
  -g python \
  -o ./generated/python-client

# Generate Java client
openapi-generator-cli generate \
  -i http://localhost:9000/v3/api-docs \
  -g java \
  -o ./generated/java-client

# Generate Go client
openapi-generator-cli generate \
  -i http://localhost:9000/v3/api-docs \
  -g go \
  -o ./generated/go-client
```

---

## Common Issues and Troubleshooting

### CORS Issues

If you're accessing the authorization server from a browser-based application, ensure CORS is properly configured for your domain.

### Token Expiration

Access tokens expire after 15 minutes by default. Always implement token refresh logic:

```typescript
// Example refresh logic
async function refreshTokenIfNeeded(tokenSet: TokenSet): Promise<TokenSet> {
  if (tokenSet.expired()) {
    return await client.refresh(tokenSet.refresh_token!);
  }
  return tokenSet;
}
```

### Invalid Client Error

Ensure your client credentials are correct and the client is registered with the authorization server.

### PKCE Verification Failed

Make sure you're using the same `code_verifier` that was used to generate the `code_challenge` sent in the authorization request.

---

## Security Best Practices

1. **Never store client secrets in client-side code** - Use Backend-For-Frontend (BFF) pattern for SPAs
2. **Always use PKCE** for authorization code flow
3. **Validate tokens on every request** - Don't trust tokens without verification
4. **Use short-lived access tokens** - Rely on refresh tokens for extended sessions
5. **Implement token revocation** - Allow users to revoke access
6. **Use HTTPS** - Always use TLS in production

---

## Next Steps

- [OAuth2 Endpoints Reference](oauth2-endpoints.md)
- [OIDC Endpoints Reference](oidc-endpoints.md)
- [Security Best Practices](../security.md)
