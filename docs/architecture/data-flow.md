# OAuth2 Data Flow

This document provides detailed diagrams of all OAuth2 and OIDC flows supported by the Authorization Server.

## Authorization Code Flow with PKCE

The recommended flow for web applications, single-page applications (SPAs), and mobile apps.

```mermaid
sequenceDiagram
    autonumber
    participant User as User/Browser
    participant Client as Client Application
    participant Auth as Authorization Server
    participant Resource as Resource Server
    
    Note over Client: Generate PKCE parameters
    Client->>Client: code_verifier = random_string()
    Client->>Client: code_challenge = BASE64URL(SHA256(code_verifier))
    
    User->>Client: Click "Login"
    Client->>Auth: GET /oauth2/authorize<br/>?response_type=code<br/>&client_id=public-client<br/>&redirect_uri=https://app/callback<br/>&scope=openid profile<br/>&state=abc123<br/>&code_challenge=X<br/>&code_challenge_method=S256
    
    Auth->>Auth: Validate client, redirect_uri, scopes
    Auth->>User: Display login page
    User->>Auth: Enter credentials
    Auth->>Auth: Authenticate user
    
    Auth->>User: Display consent page
    User->>Auth: Grant consent
    Auth->>Auth: Generate authorization code
    Auth->>Auth: Store code_challenge with code
    
    Auth->>Client: 302 Redirect to<br/>https://app/callback?code=AUTH_CODE&state=abc123
    
    Client->>Client: Validate state parameter
    Client->>Auth: POST /oauth2/token<br/>grant_type=authorization_code<br/>&code=AUTH_CODE<br/>&redirect_uri=https://app/callback<br/>&client_id=public-client<br/>&code_verifier=VERIFIER
    
    Auth->>Auth: Validate authorization code
    Auth->>Auth: Verify SHA256(code_verifier) == code_challenge
    Auth->>Auth: Generate tokens
    
    Auth->>Client: 200 OK<br/>{access_token, id_token, refresh_token, expires_in}
    
    Client->>Resource: GET /api/resource<br/>Authorization: Bearer ACCESS_TOKEN
    Resource->>Auth: Validate token (or use JWKS)
    Resource->>Client: 200 OK {resource data}
```

### PKCE Parameter Generation

```mermaid
graph LR
    subgraph "Client Side"
        Random[Random 32 bytes] --> Base64[Base64URL encode]
        Base64 --> Verifier[code_verifier<br/>43-128 chars]
        Verifier --> SHA256[SHA-256 hash]
        SHA256 --> ChallengeEncode[Base64URL encode]
        ChallengeEncode --> Challenge[code_challenge]
    end
    
    subgraph "Server Side"
        StoredChallenge[Stored code_challenge]
        ReceivedVerifier[Received code_verifier]
        ReceivedVerifier --> ServerHash[SHA-256 hash]
        ServerHash --> ServerEncode[Base64URL encode]
        ServerEncode --> Compare{Compare}
        StoredChallenge --> Compare
        Compare -->|Match| Success[Issue tokens]
        Compare -->|No Match| Reject[Reject request]
    end
```

## Client Credentials Flow

For machine-to-machine (M2M) authentication between trusted backend services.

```mermaid
sequenceDiagram
    autonumber
    participant Service as Backend Service
    participant Auth as Authorization Server
    participant API as Protected API
    
    Note over Service: Service needs to call API
    
    Service->>Auth: POST /oauth2/token<br/>Authorization: Basic base64(client_id:client_secret)<br/>grant_type=client_credentials<br/>&scope=api:read api:write
    
    Auth->>Auth: Validate client credentials
    Auth->>Auth: Check requested scopes
    Auth->>Auth: Generate access token
    
    Auth->>Service: 200 OK<br/>{access_token, token_type, expires_in, scope}
    
    Service->>Service: Cache token until expires_in
    
    Service->>API: GET /api/data<br/>Authorization: Bearer ACCESS_TOKEN
    
    API->>API: Validate JWT signature using JWKS
    API->>API: Check token expiration
    API->>API: Validate scopes
    
    API->>Service: 200 OK {data}
```

### Client Credentials Request Flow

```mermaid
graph TB
    subgraph "Token Request"
        ClientCreds[Client Credentials] --> BasicAuth[Authorization Header<br/>Basic base64 encode]
        GrantType[grant_type=client_credentials] --> Request[HTTP POST]
        Scopes[scope=api:read] --> Request
        BasicAuth --> Request
    end
    
    subgraph "Server Processing"
        Request --> ValidateClient[Validate Client]
        ValidateClient --> CheckScopes[Validate Scopes]
        CheckScopes --> GenerateToken[Generate JWT]
        GenerateToken --> SignToken[Sign with RSA Private Key]
    end
    
    subgraph "Response"
        SignToken --> Response[Token Response]
        Response --> AccessToken[access_token]
        Response --> TokenType[token_type: Bearer]
        Response --> ExpiresIn[expires_in: 3600]
    end
```

## Refresh Token Flow

Used to obtain new access tokens without re-authentication.

```mermaid
sequenceDiagram
    autonumber
    participant Client as Client Application
    participant Auth as Authorization Server
    
    Note over Client: Access token expired
    
    Client->>Auth: POST /oauth2/token<br/>grant_type=refresh_token<br/>&refresh_token=REFRESH_TOKEN<br/>&client_id=demo-client
    
    Auth->>Auth: Validate refresh token
    Auth->>Auth: Check if token is revoked
    Auth->>Auth: Check token expiration
    
    alt Token Valid
        Auth->>Auth: Generate new access token
        Auth->>Auth: Generate new refresh token (rotation)
        Auth->>Auth: Invalidate old refresh token
        Auth->>Client: 200 OK<br/>{access_token, refresh_token, expires_in}
    else Token Invalid/Revoked
        Auth->>Client: 400 Bad Request<br/>{error: "invalid_grant"}
    end
```

### Refresh Token Rotation

```mermaid
graph TB
    subgraph "Token Rotation"
        Original[Original Refresh Token] --> Request[Refresh Request]
        Request --> Validate[Validate Token]
        Validate --> GenerateNew[Generate New Tokens]
        GenerateNew --> Invalidate[Invalidate Old Token]
        Invalidate --> Return[Return New Tokens]
    end
    
    subgraph "Security Benefit"
        Rotation[Rotation prevents] --> Replay[Token replay attacks]
        Rotation --> Stolen[Use of stolen tokens]
    end
```

## Token Introspection Flow

Allows resource servers to validate tokens and get token metadata.

```mermaid
sequenceDiagram
    autonumber
    participant Resource as Resource Server
    participant Auth as Authorization Server
    
    Resource->>Auth: POST /oauth2/introspect<br/>Authorization: Basic base64(client:secret)<br/>token=ACCESS_TOKEN
    
    Auth->>Auth: Validate introspecting client
    Auth->>Auth: Lookup token
    
    alt Token Valid
        Auth->>Resource: 200 OK<br/>{active: true, sub, scope, exp, iat, client_id}
    else Token Invalid/Expired
        Auth->>Resource: 200 OK<br/>{active: false}
    end
```

## Token Revocation Flow

Used to invalidate tokens before their natural expiration.

```mermaid
sequenceDiagram
    autonumber
    participant Client as Client Application
    participant Auth as Authorization Server
    
    Note over Client: User logs out
    
    Client->>Auth: POST /oauth2/revoke<br/>Authorization: Basic base64(client:secret)<br/>token=REFRESH_TOKEN<br/>&token_type_hint=refresh_token
    
    Auth->>Auth: Validate client
    Auth->>Auth: Find token
    Auth->>Auth: Revoke token and related tokens
    
    Auth->>Client: 200 OK
    
    Note over Client: Subsequent use of revoked token
    Client->>Auth: POST /oauth2/token<br/>grant_type=refresh_token<br/>&refresh_token=REVOKED_TOKEN
    Auth->>Client: 400 Bad Request<br/>{error: "invalid_grant"}
```

## OpenID Connect Discovery Flow

How clients discover the authorization server configuration.

```mermaid
sequenceDiagram
    autonumber
    participant Client as Client Application
    participant Auth as Authorization Server
    
    Client->>Auth: GET /.well-known/openid-configuration
    
    Auth->>Client: 200 OK<br/>{<br/>  issuer,<br/>  authorization_endpoint,<br/>  token_endpoint,<br/>  userinfo_endpoint,<br/>  jwks_uri,<br/>  scopes_supported,<br/>  response_types_supported,<br/>  ...<br/>}
    
    Client->>Auth: GET /oauth2/jwks
    
    Auth->>Client: 200 OK<br/>{keys: [{kty, alg, use, kid, n, e}]}
    
    Note over Client: Client caches JWKS for token validation
```

## UserInfo Endpoint Flow

Retrieving user claims using an access token.

```mermaid
sequenceDiagram
    autonumber
    participant Client as Client Application
    participant Auth as Authorization Server
    
    Client->>Auth: GET /userinfo<br/>Authorization: Bearer ACCESS_TOKEN
    
    Auth->>Auth: Validate access token
    Auth->>Auth: Check 'openid' scope
    Auth->>Auth: Retrieve user claims
    
    alt Token Valid with openid scope
        Auth->>Client: 200 OK<br/>{sub, name, email, email_verified, ...}
    else Invalid Token
        Auth->>Client: 401 Unauthorized
    else Missing openid scope
        Auth->>Client: 403 Forbidden
    end
```

## Complete Authentication Flow

End-to-end flow from user login to API access.

```mermaid
sequenceDiagram
    autonumber
    participant User as User
    participant Browser as Browser
    participant SPA as Single Page App
    participant Auth as Authorization Server
    participant API as Resource API
    
    User->>Browser: Access application
    Browser->>SPA: Load SPA
    SPA->>SPA: Generate PKCE verifier/challenge
    SPA->>SPA: Store verifier in session storage
    
    SPA->>Browser: Redirect to authorize
    Browser->>Auth: GET /oauth2/authorize
    Auth->>Browser: Login page
    Browser->>User: Display login form
    User->>Browser: Enter credentials
    Browser->>Auth: POST credentials
    
    Auth->>Auth: Validate credentials
    Auth->>Browser: Consent page
    User->>Browser: Click "Allow"
    Browser->>Auth: Submit consent
    
    Auth->>Browser: 302 Redirect with code
    Browser->>SPA: Callback with code
    
    SPA->>Auth: POST /oauth2/token
    Auth->>SPA: {access_token, id_token, refresh_token}
    
    SPA->>SPA: Store tokens securely
    SPA->>SPA: Parse id_token for user info
    
    SPA->>API: GET /api/data + Bearer token
    API->>API: Validate JWT
    API->>SPA: {data}
    SPA->>Browser: Render data
    Browser->>User: Display content
```

## Error Flows

### Invalid Client Error

```mermaid
sequenceDiagram
    participant Client
    participant Auth as Authorization Server
    
    Client->>Auth: POST /oauth2/token<br/>client_id=invalid<br/>&client_secret=wrong
    
    Auth->>Auth: Lookup client
    Auth->>Auth: Client not found or secret invalid
    
    Auth->>Client: 401 Unauthorized<br/>{error: "invalid_client",<br/> error_description: "Client authentication failed"}
```

### Invalid Grant Error

```mermaid
sequenceDiagram
    participant Client
    participant Auth as Authorization Server
    
    Client->>Auth: POST /oauth2/token<br/>grant_type=authorization_code<br/>&code=EXPIRED_CODE
    
    Auth->>Auth: Lookup authorization code
    Auth->>Auth: Code expired or already used
    
    Auth->>Client: 400 Bad Request<br/>{error: "invalid_grant",<br/> error_description: "Authorization code expired"}
```

### Invalid Scope Error

```mermaid
sequenceDiagram
    participant Client
    participant Auth as Authorization Server
    
    Client->>Auth: POST /oauth2/token<br/>grant_type=client_credentials<br/>&scope=admin:full
    
    Auth->>Auth: Validate requested scopes
    Auth->>Auth: Scope not allowed for client
    
    Auth->>Client: 400 Bad Request<br/>{error: "invalid_scope",<br/> error_description: "Requested scope is invalid"}
```

## Flow Selection Guide

| Use Case | Recommended Flow | Reason |
|----------|------------------|--------|
| Web Application (SPA) | Authorization Code + PKCE | No client secret exposure |
| Mobile Application | Authorization Code + PKCE | Cannot store secrets securely |
| Server-side Web App | Authorization Code | Can store client secret |
| Microservice to Microservice | Client Credentials | No user context needed |
| Refresh expired tokens | Refresh Token | Avoid re-authentication |
| Logout | Token Revocation | Invalidate tokens |

## Next Steps

- [OAuth2 API Reference](../api/oauth2-endpoints.md) - Detailed endpoint documentation
- [Security Architecture](security.md) - Token security details
- [Configuration](../configuration.md) - OAuth2 settings
