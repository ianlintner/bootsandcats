# OAuth2 Endpoints

This document provides comprehensive documentation for all OAuth2 endpoints exposed by the Authorization Server.

## Endpoints Overview

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/oauth2/authorize` | GET | Authorization endpoint for user consent |
| `/oauth2/token` | POST | Token endpoint for obtaining tokens |
| `/oauth2/jwks` | GET | JSON Web Key Set for JWT verification |
| `/oauth2/introspect` | POST | Token introspection endpoint |
| `/oauth2/revoke` | POST | Token revocation endpoint |
| `/oauth2/device_authorization` | POST | Device authorization endpoint for constrained devices |
| `/oauth2/device_verification` | GET | User verification page for device flow |

## Authorization Endpoint

### `GET /oauth2/authorize`

Initiates the OAuth2 authorization flow. This endpoint is used for Authorization Code grants.

#### Request Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `response_type` | string | Yes | Must be `code` for authorization code flow |
| `client_id` | string | Yes | The registered client identifier |
| `redirect_uri` | string | Yes | Must match a registered redirect URI |
| `scope` | string | No | Space-separated list of scopes |
| `state` | string | Recommended | Opaque value for CSRF protection |
| `code_challenge` | string | Conditional | PKCE code challenge (required for public clients) |
| `code_challenge_method` | string | Conditional | Must be `S256` when using PKCE |
| `nonce` | string | No | OpenID Connect nonce value |
| `prompt` | string | No | `consent`, `login`, or `none` |

#### Example Request

```http
GET /oauth2/authorize?
    response_type=code&
    client_id=public-client&
    redirect_uri=https://app.example.com/callback&
    scope=openid%20profile%20email&
    state=af0ifjsldkj&
    code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&
    code_challenge_method=S256&
    nonce=n-0S6_WzA2Mj
HTTP/1.1
Host: auth.example.com
```

#### Success Response

After successful authentication and consent, redirects to:

```http
HTTP/1.1 302 Found
Location: https://app.example.com/callback?
    code=SplxlOBeZQQYbYS6WxSbIA&
    state=af0ifjsldkj
```

#### Error Response

```http
HTTP/1.1 302 Found
Location: https://app.example.com/callback?
    error=access_denied&
    error_description=The+resource+owner+denied+the+request&
    state=af0ifjsldkj
```

#### Error Codes

| Error | Description |
|-------|-------------|
| `invalid_request` | Missing or invalid parameters |
| `unauthorized_client` | Client not authorized for this grant type |
| `access_denied` | User denied consent |
| `unsupported_response_type` | Response type not supported |
| `invalid_scope` | Requested scope is invalid |
| `server_error` | Internal server error |
| `temporarily_unavailable` | Server is temporarily unavailable |

---

## Token Endpoint

### `POST /oauth2/token`

Exchange authorization codes for tokens or request tokens directly.

#### Request Headers

| Header | Value | Required |
|--------|-------|----------|
| `Content-Type` | `application/x-www-form-urlencoded` | Yes |
| `Authorization` | `Basic {base64(client_id:client_secret)}` | For confidential clients |

#### Grant Types

##### Authorization Code Grant

Exchange an authorization code for tokens.

**Request Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `grant_type` | string | Yes | Must be `authorization_code` |
| `code` | string | Yes | The authorization code received |
| `redirect_uri` | string | Yes | Must match the original request |
| `client_id` | string | Conditional | Required for public clients |
| `code_verifier` | string | Conditional | PKCE verifier (required if code_challenge was used) |

**Example Request:**

```http
POST /oauth2/token HTTP/1.1
Host: auth.example.com
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code&
code=SplxlOBeZQQYbYS6WxSbIA&
redirect_uri=https://app.example.com/callback&
client_id=public-client&
code_verifier=dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk
```

**Example Response:**

```json
{
    "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
    "token_type": "Bearer",
    "expires_in": 900,
    "refresh_token": "GEbRxBN...edjnXbL",
    "scope": "openid profile email",
    "id_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

##### Client Credentials Grant

Request an access token using client credentials.

**Request Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `grant_type` | string | Yes | Must be `client_credentials` |
| `scope` | string | No | Space-separated list of scopes |

**Example Request:**

```http
POST /oauth2/token HTTP/1.1
Host: auth.example.com
Authorization: Basic bTJtLWNsaWVudDptMm0tc2VjcmV0
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials&
scope=api:read%20api:write
```

**Example Response:**

```json
{
    "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
    "token_type": "Bearer",
    "expires_in": 3600,
    "scope": "api:read api:write"
}
```

##### Refresh Token Grant

Exchange a refresh token for new tokens.

**Request Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `grant_type` | string | Yes | Must be `refresh_token` |
| `refresh_token` | string | Yes | The refresh token |
| `scope` | string | No | Subset of originally granted scopes |

**Example Request:**

```http
POST /oauth2/token HTTP/1.1
Host: auth.example.com
Content-Type: application/x-www-form-urlencoded

grant_type=refresh_token&
refresh_token=GEbRxBN...edjnXbL&
client_id=public-client
```

**Example Response:**

```json
{
    "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
    "token_type": "Bearer",
    "expires_in": 900,
    "refresh_token": "tGzv3JO...O3Tr5QO",
    "scope": "openid profile email"
}
```

#### Error Responses

```json
{
    "error": "invalid_grant",
    "error_description": "The authorization code has expired"
}
```

| Error Code | HTTP Status | Description |
|------------|-------------|-------------|
| `invalid_request` | 400 | Missing or invalid parameters |
| `invalid_client` | 401 | Client authentication failed |
| `invalid_grant` | 400 | Invalid authorization code or refresh token |
| `unauthorized_client` | 401 | Client not authorized for grant type |
| `unsupported_grant_type` | 400 | Grant type not supported |
| `invalid_scope` | 400 | Invalid or unknown scope |

---

## JWKS Endpoint

### `GET /oauth2/jwks`

Returns the JSON Web Key Set containing public keys for JWT verification.

#### Request

```http
GET /oauth2/jwks HTTP/1.1
Host: auth.example.com
```

#### Response

```json
{
    "keys": [
        {
            "kty": "RSA",
            "e": "AQAB",
            "use": "sig",
            "kid": "key-id-1",
            "alg": "RS256",
            "n": "sXchDaQeb...LLnDYNYhQ"
        }
    ]
}
```

#### Usage

Resource servers should:

1. Fetch the JWKS at startup
2. Cache the keys (respect `Cache-Control` headers)
3. Refresh when encountering an unknown `kid`
4. Use the matching key to verify JWT signatures

---

## Token Introspection Endpoint

### `POST /oauth2/introspect`

Introspect an access or refresh token to determine its state and metadata.

#### Request Headers

| Header | Value | Required |
|--------|-------|----------|
| `Content-Type` | `application/x-www-form-urlencoded` | Yes |
| `Authorization` | `Basic {base64(client_id:client_secret)}` | Yes |

#### Request Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `token` | string | Yes | The token to introspect |
| `token_type_hint` | string | No | `access_token` or `refresh_token` |

#### Example Request

```http
POST /oauth2/introspect HTTP/1.1
Host: auth.example.com
Authorization: Basic bTJtLWNsaWVudDptMm0tc2VjcmV0
Content-Type: application/x-www-form-urlencoded

token=eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
```

#### Active Token Response

```json
{
    "active": true,
    "sub": "user123",
    "client_id": "demo-client",
    "username": "john.doe",
    "scope": "openid profile email",
    "token_type": "Bearer",
    "exp": 1699876543,
    "iat": 1699872943,
    "nbf": 1699872943,
    "iss": "https://auth.example.com",
    "aud": ["demo-client", "api-service"]
}
```

#### Inactive Token Response

```json
{
    "active": false
}
```

---

## Token Revocation Endpoint

### `POST /oauth2/revoke`

Revoke an access or refresh token.

#### Request Headers

| Header | Value | Required |
|--------|-------|----------|
| `Content-Type` | `application/x-www-form-urlencoded` | Yes |
| `Authorization` | `Basic {base64(client_id:client_secret)}` | Yes (for confidential clients) |

#### Request Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `token` | string | Yes | The token to revoke |
| `token_type_hint` | string | No | `access_token` or `refresh_token` |
| `client_id` | string | Conditional | Required for public clients |

#### Example Request

```http
POST /oauth2/revoke HTTP/1.1
Host: auth.example.com
Authorization: Basic bTJtLWNsaWVudDptMm0tc2VjcmV0
Content-Type: application/x-www-form-urlencoded

token=GEbRxBN...edjnXbL&
token_type_hint=refresh_token
```

#### Response

```http
HTTP/1.1 200 OK
```

!!! note "Revocation Behavior"
    The endpoint returns `200 OK` even if the token was already revoked or invalid.
    This prevents token enumeration attacks.

---

## Device Authorization Flow

The authorization server supports the OAuth 2.0 Device Authorization Grant for constrained
input devices (smart TVs, consoles, CLI tools, etc.).

### `POST /oauth2/device_authorization`

Initiates the device flow by creating a user code and device code pair.

#### Request Headers

| Header | Value | Required |
|--------|-------|----------|
| `Content-Type` | `application/x-www-form-urlencoded` | Yes |
| `Authorization` | `Basic {base64(client_id:client_secret)}` | For confidential clients |

#### Request Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `client_id` | string | Conditional | Required for public clients |
| `scope` | string | No | Space-separated list of scopes |

#### Example Request

```http
POST /oauth2/device_authorization HTTP/1.1
Host: auth.example.com
Authorization: Basic bTJtLWNsaWVudDptMm0tc2VjcmV0
Content-Type: application/x-www-form-urlencoded

scope=openid%20profile%20email
```

#### Example Response

```json
{
    "device_code": "GmRhmhcxhwAzkoEqiMEg_DnyEysNkuNhszIySk9eS",
    "user_code": "WDJB-MJHT",
    "verification_uri": "https://auth.example.com/activate",
    "verification_uri_complete": "https://auth.example.com/activate?user_code=WDJB-MJHT",
    "expires_in": 1800,
    "interval": 5
}
```

### `GET /oauth2/device_verification`

Displays the verification UI where the user enters the `user_code` and approves the device
authorization request.

The specific HTML UI is implementation‑defined, but the endpoint URL is fixed by
`AuthorizationServerSettings`.

---

## Device Token Polling

The device (or application on the constrained device) polls the token endpoint to obtain an
access token once the user has completed verification.

```mermaid
sequenceDiagram
        participant User
        participant Device as Device Client
        participant Auth as Auth Server

        Device->>Auth: POST /oauth2/device_authorization
        Auth-->>Device: user_code, device_code, verification_uri

        Note over Device: Display user_code & verification_uri

        Device->>Auth: POST /oauth2/token (poll)
        Auth-->>Device: error=authorization_pending

        User->>Auth: GET /oauth2/device_verification
        Auth-->>User: Prompt for user_code
        User->>Auth: Submit user_code & approve

        Device->>Auth: POST /oauth2/token (poll)
        Auth-->>Device: access_token, refresh_token
```

---

## Access Token Format

Access tokens are JWTs signed with RS256. Example decoded payload:

```json
{
    "iss": "https://auth.example.com",
    "sub": "user123",
    "aud": ["demo-client"],
    "exp": 1699876543,
    "iat": 1699872943,
    "nbf": 1699872943,
    "jti": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "scope": "openid profile email read",
    "client_id": "demo-client"
}
```

### Verifying Access Tokens

```java
// Example using Spring Security
@Bean
public JwtDecoder jwtDecoder() {
    return NimbusJwtDecoder
        .withJwkSetUri("https://auth.example.com/oauth2/jwks")
        .build();
}
```

```python
# Example using PyJWT
import jwt
from jwt import PyJWKClient

jwks_client = PyJWKClient("https://auth.example.com/oauth2/jwks")
signing_key = jwks_client.get_signing_key_from_jwt(token)
payload = jwt.decode(token, signing_key.key, algorithms=["RS256"], audience="demo-client")
```

---

## Rate Limits

| Endpoint | Rate Limit | Window |
|----------|------------|--------|
| `/oauth2/authorize` | 30 requests | 1 minute per IP |
| `/oauth2/token` | 100 requests | 1 minute per client |
| `/oauth2/introspect` | 500 requests | 1 minute per client |
| `/oauth2/revoke` | 100 requests | 1 minute per client |
| `/oauth2/jwks` | 1000 requests | 1 minute per IP |

---

## Common Flows

### Browser-Based SPA (PKCE)

```mermaid
sequenceDiagram
    participant SPA
    participant Auth as Authorization Server
    participant API as Resource API
    
    SPA->>SPA: Generate code_verifier, code_challenge
    SPA->>Auth: GET /oauth2/authorize (with code_challenge)
    Auth->>SPA: Redirect with authorization code
    SPA->>Auth: POST /oauth2/token (with code_verifier)
    Auth->>SPA: Access token, ID token, Refresh token
    SPA->>API: API request with access token
    API->>SPA: API response
```

### Service-to-Service (Client Credentials)

```mermaid
sequenceDiagram
    participant Service
    participant Auth as Authorization Server
    participant API as Resource API
    
    Service->>Auth: POST /oauth2/token (client credentials)
    Auth->>Service: Access token
    Service->>API: API request with access token
    API->>API: Verify token with JWKS
    API->>Service: API response
```

## Next Steps

- [OIDC Endpoints](oidc-endpoints.md) - OpenID Connect specific endpoints
- [Actuator Endpoints](actuator-endpoints.md) - Health and metrics endpoints
- [Security Best Practices](../security.md) - Secure implementation guide
