# OpenID Connect Endpoints

This document provides comprehensive documentation for all OpenID Connect (OIDC) endpoints.

## Endpoints Overview

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/.well-known/openid-configuration` | GET | OIDC Discovery document |
| `/userinfo` | GET/POST | User claims endpoint |
| `/connect/logout` | GET | RP-Initiated Logout |
| `/connect/register` | POST | Dynamic Client Registration |

---

## OIDC Discovery Endpoint

### `GET /.well-known/openid-configuration`

Returns the OpenID Provider Configuration document. This is the starting point for OIDC clients.

#### Request

```http
GET /.well-known/openid-configuration HTTP/1.1
Host: auth.example.com
```

#### Response

```json
{
    "issuer": "https://auth.example.com",
    "authorization_endpoint": "https://auth.example.com/oauth2/authorize",
    "token_endpoint": "https://auth.example.com/oauth2/token",
    "userinfo_endpoint": "https://auth.example.com/userinfo",
    "jwks_uri": "https://auth.example.com/oauth2/jwks",
    "end_session_endpoint": "https://auth.example.com/connect/logout",
    "registration_endpoint": "https://auth.example.com/connect/register",
    "introspection_endpoint": "https://auth.example.com/oauth2/introspect",
    "revocation_endpoint": "https://auth.example.com/oauth2/revoke",
    "scopes_supported": [
        "openid",
        "profile",
        "email",
        "address",
        "phone",
        "offline_access"
    ],
    "response_types_supported": [
        "code"
    ],
    "response_modes_supported": [
        "query",
        "fragment",
        "form_post"
    ],
    "grant_types_supported": [
        "authorization_code",
        "client_credentials",
        "refresh_token"
    ],
    "subject_types_supported": [
        "public"
    ],
    "id_token_signing_alg_values_supported": [
        "RS256"
    ],
    "token_endpoint_auth_methods_supported": [
        "client_secret_basic",
        "client_secret_post",
        "none"
    ],
    "code_challenge_methods_supported": [
        "S256",
        "plain"
    ],
    "claims_supported": [
        "sub",
        "iss",
        "aud",
        "exp",
        "iat",
        "name",
        "given_name",
        "family_name",
        "preferred_username",
        "email",
        "email_verified",
        "locale"
    ],
    "request_parameter_supported": true,
    "request_uri_parameter_supported": false,
    "require_request_uri_registration": false,
    "claims_parameter_supported": false
}
```

#### Key Configuration Values

| Field | Description |
|-------|-------------|
| `issuer` | Authorization server identifier (must match JWT `iss` claim) |
| `authorization_endpoint` | URL for authorization requests |
| `token_endpoint` | URL for token requests |
| `userinfo_endpoint` | URL for user claims |
| `jwks_uri` | URL for JSON Web Key Set |
| `scopes_supported` | Available OAuth2 scopes |
| `response_types_supported` | Supported response types |
| `token_endpoint_auth_methods_supported` | Client authentication methods |

---

## UserInfo Endpoint

### `GET /userinfo`

Returns claims about the authenticated user using the access token.

#### Request Headers

| Header | Value | Required |
|--------|-------|----------|
| `Authorization` | `Bearer {access_token}` | Yes |

#### Request

=== "HTTP"

    ```http
    GET /userinfo HTTP/1.1
    Host: auth.example.com
    Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
    ```

=== "Curl"

    ```bash
    curl -X GET http://localhost:9000/userinfo \
      -H "Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."
    ```

=== "Python"

    ```python
    import requests

    url = "http://localhost:9000/userinfo"
    headers = {"Authorization": "Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."}

    response = requests.get(url, headers=headers)
    print(response.json())
    ```

#### Response

```json
{
    "sub": "user123",
    "name": "John Doe",
    "given_name": "John",
    "family_name": "Doe",
    "preferred_username": "johndoe",
    "email": "john.doe@example.com",
    "email_verified": true,
    "locale": "en-US",
    "updated_at": 1699872943
}
```

#### Scope-Based Claims

| Scope | Claims Returned |
|-------|-----------------|
| `openid` | `sub` |
| `profile` | `name`, `given_name`, `family_name`, `preferred_username`, `locale`, `updated_at` |
| `email` | `email`, `email_verified` |
| `address` | `address` (structured) |
| `phone` | `phone_number`, `phone_number_verified` |

#### Error Responses

| HTTP Status | Error | Description |
|-------------|-------|-------------|
| 401 | `invalid_token` | Token is missing, expired, or invalid |
| 403 | `insufficient_scope` | Token doesn't include `openid` scope |

```json
{
    "error": "invalid_token",
    "error_description": "The access token has expired"
}
```

---

## ID Token

The ID Token is returned during the token exchange and contains identity claims.

### ID Token Structure

```json
{
    "iss": "https://auth.example.com",
    "sub": "user123",
    "aud": "demo-client",
    "exp": 1699876543,
    "iat": 1699872943,
    "auth_time": 1699872900,
    "nonce": "n-0S6_WzA2Mj",
    "acr": "urn:mace:incommon:iap:silver",
    "amr": ["pwd"],
    "azp": "demo-client",
    "at_hash": "HK6E_P6Dh8Y93mRNtsDB1Q",
    "name": "John Doe",
    "email": "john.doe@example.com",
    "email_verified": true
}
```

### ID Token Claims

| Claim | Type | Description |
|-------|------|-------------|
| `iss` | string | Issuer identifier |
| `sub` | string | Subject identifier (unique user ID) |
| `aud` | string/array | Audience (client ID) |
| `exp` | number | Expiration time (Unix timestamp) |
| `iat` | number | Issued at time |
| `auth_time` | number | Time of user authentication |
| `nonce` | string | Nonce from authorization request |
| `acr` | string | Authentication context class reference |
| `amr` | array | Authentication methods used |
| `azp` | string | Authorized party |
| `at_hash` | string | Access token hash |

### Validating ID Tokens

```javascript
// JavaScript example using jose library
import { jwtVerify, createRemoteJWKSet } from 'jose';

const JWKS = createRemoteJWKSet(
    new URL('https://auth.example.com/oauth2/jwks')
);

async function validateIdToken(idToken, clientId) {
    const { payload } = await jwtVerify(idToken, JWKS, {
        issuer: 'https://auth.example.com',
        audience: clientId,
    });
    
    // Validate nonce if used
    if (expectedNonce && payload.nonce !== expectedNonce) {
        throw new Error('Invalid nonce');
    }
    
    return payload;
}
```

---

## End Session Endpoint (Logout)

### `GET /connect/logout`

Initiates RP-Initiated Logout per OIDC specification.

#### Request Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `id_token_hint` | string | Recommended | Previously issued ID token |
| `post_logout_redirect_uri` | string | No | Redirect after logout (must be registered) |
| `state` | string | No | Opaque state value |
| `client_id` | string | Conditional | Required if `id_token_hint` is not provided |

#### Example Request

```http
GET /connect/logout?
    id_token_hint=eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...&
    post_logout_redirect_uri=https://app.example.com/&
    state=logout123
HTTP/1.1
Host: auth.example.com
```

#### Response

```http
HTTP/1.1 302 Found
Location: https://app.example.com/?state=logout123
```

---

## Dynamic Client Registration

### `POST /connect/register`

Register a new OAuth2 client dynamically.

!!! warning "Security Note"
    Dynamic client registration should be protected or disabled in production environments.

#### Request

```http
POST /connect/register HTTP/1.1
Host: auth.example.com
Content-Type: application/json
Authorization: Bearer {initial_access_token}

{
    "client_name": "My Application",
    "redirect_uris": [
        "https://app.example.com/callback"
    ],
    "post_logout_redirect_uris": [
        "https://app.example.com/"
    ],
    "grant_types": [
        "authorization_code",
        "refresh_token"
    ],
    "response_types": [
        "code"
    ],
    "scope": "openid profile email",
    "token_endpoint_auth_method": "client_secret_basic"
}
```

#### Response

```json
{
    "client_id": "generated-client-id",
    "client_secret": "generated-client-secret",
    "client_secret_expires_at": 0,
    "client_name": "My Application",
    "redirect_uris": [
        "https://app.example.com/callback"
    ],
    "grant_types": [
        "authorization_code",
        "refresh_token"
    ],
    "response_types": [
        "code"
    ],
    "scope": "openid profile email",
    "token_endpoint_auth_method": "client_secret_basic",
    "registration_access_token": "reg-access-token",
    "registration_client_uri": "https://auth.example.com/connect/register?client_id=generated-client-id"
}
```

---

## OIDC Scopes

### Standard Scopes

| Scope | Description | Claims |
|-------|-------------|--------|
| `openid` | Required for OIDC | `sub` |
| `profile` | Basic profile info | `name`, `family_name`, `given_name`, `middle_name`, `nickname`, `preferred_username`, `profile`, `picture`, `website`, `gender`, `birthdate`, `zoneinfo`, `locale`, `updated_at` |
| `email` | Email address | `email`, `email_verified` |
| `address` | Postal address | `address` |
| `phone` | Phone number | `phone_number`, `phone_number_verified` |
| `offline_access` | Refresh tokens | - |

### Custom Scopes

| Scope | Description |
|-------|-------------|
| `read` | Read access to user resources |
| `write` | Write access to user resources |
| `api:read` | Read access to APIs |
| `api:write` | Write access to APIs |

---

## Authentication Methods

### Supported `amr` Values

| Value | Description |
|-------|-------------|
| `pwd` | Password authentication |
| `mfa` | Multi-factor authentication |
| `otp` | One-time password |
| `sms` | SMS verification |
| `email` | Email verification |
| `face` | Facial recognition |
| `fpt` | Fingerprint |
| `hwk` | Hardware key |

---

## Integration Examples

### React SPA with OIDC Client

```javascript
import { UserManager } from 'oidc-client-ts';

const userManager = new UserManager({
    authority: 'https://auth.example.com',
    client_id: 'public-client',
    redirect_uri: 'https://app.example.com/callback',
    post_logout_redirect_uri: 'https://app.example.com/',
    scope: 'openid profile email',
    response_type: 'code',
});

// Login
await userManager.signinRedirect();

// Callback handling
const user = await userManager.signinRedirectCallback();
console.log('User:', user.profile);

// Logout
await userManager.signoutRedirect();
```

### Spring Security Resource Server

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/**").authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwkSetUri("https://auth.example.com/oauth2/jwks")
                )
            );
        return http.build();
    }
}
```

### Python Flask with Authlib

```python
from authlib.integrations.flask_client import OAuth

oauth = OAuth(app)
oauth.register(
    name='auth',
    server_metadata_url='https://auth.example.com/.well-known/openid-configuration',
    client_kwargs={'scope': 'openid profile email'}
)

@app.route('/login')
def login():
    return oauth.auth.authorize_redirect(redirect_uri=url_for('callback', _external=True))

@app.route('/callback')
def callback():
    token = oauth.auth.authorize_access_token()
    userinfo = oauth.auth.userinfo()
    session['user'] = userinfo
    return redirect('/')
```

---

## Next Steps

- [OAuth2 Endpoints](oauth2-endpoints.md) - OAuth2 core endpoints
- [Actuator Endpoints](actuator-endpoints.md) - Health and metrics
- [Security Architecture](../architecture/security.md) - Security design
