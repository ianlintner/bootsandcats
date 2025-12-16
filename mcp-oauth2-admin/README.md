# OAuth2 Admin MCP Server

Model Context Protocol (MCP) server for managing the OAuth2 Authorization Server at **oauth2.cat-herding.net**.

## Features

- **Client Management**: Create, update, delete, and list OAuth2 clients
- **Scope Management**: Define and manage OAuth2 scopes
- **Deny Rules**: Configure access control deny rules
- **Audit Logs**: Query security audit events
- **Documentation**: Built-in OAuth2 integration guides

## Installation

```bash
cd mcp-oauth2-admin
npm install
npm run build
```

## Configuration

The MCP server requires the following environment variables:

```bash
# OAuth2 Server Configuration
OAUTH2_SERVER_URL=https://oauth2.cat-herding.net
OAUTH2_CLIENT_ID=mcp-admin-client
OAUTH2_CLIENT_SECRET=your-secret-here
OAUTH2_SCOPES=admin:read admin:write
```

### Creating the Admin Client

Before using the MCP server, you need to create an admin client in the OAuth2 server:

```bash
# Using the admin console at https://oauth2.cat-herding.net/admin
# Or via API:
curl -X POST https://oauth2.cat-herding.net/api/admin/clients \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "clientId": "mcp-admin-client",
    "clientName": "MCP Admin Client",
    "clientSecret": "generate-secure-secret-here",
    "authorizationGrantTypes": ["client_credentials"],
    "clientAuthenticationMethods": ["client_secret_basic"],
    "redirectUris": [],
    "postLogoutRedirectUris": [],
    "scopes": ["admin:read", "admin:write"],
    "requireProofKey": false,
    "requireAuthorizationConsent": false,
    "enabled": true,
    "notes": "MCP server for OAuth2 management"
  }'
```

## Usage

### Running Standalone

```bash
export OAUTH2_CLIENT_SECRET=your-secret
npm start
```

### Using with Claude Desktop

Add to your Claude Desktop config (`~/Library/Application Support/Claude/claude_desktop_config.json`):

```json
{
  "mcpServers": {
    "oauth2-admin": {
      "command": "node",
      "args": ["/path/to/bootsandcats/mcp-oauth2-admin/dist/index.js"],
      "env": {
        "OAUTH2_SERVER_URL": "https://oauth2.cat-herding.net",
        "OAUTH2_CLIENT_ID": "mcp-admin-client",
        "OAUTH2_CLIENT_SECRET": "your-secret-here",
        "OAUTH2_SCOPES": "admin:read admin:write"
      }
    }
  }
}
```

### Using with Cline VSCode Extension

Add to Cline MCP settings:

```json
{
  "oauth2-admin": {
    "command": "node",
    "args": ["/path/to/bootsandcats/mcp-oauth2-admin/dist/index.js"],
    "env": {
      "OAUTH2_SERVER_URL": "https://oauth2.cat-herding.net",
      "OAUTH2_CLIENT_ID": "mcp-admin-client",
      "OAUTH2_CLIENT_SECRET": "your-secret-here"
    }
  }
}
```

## Available Tools

### Client Management

#### `list_clients`
List all registered OAuth2 clients.

**Example**:
```
Use the list_clients tool
```

#### `get_client`
Get details of a specific client.

**Parameters**:
- `clientId` (string): The OAuth2 client ID

**Example**:
```
Use get_client with clientId "demo-client"
```

#### `create_client`
Create a new OAuth2 client.

**Parameters**:
- `clientId` (string): Unique identifier
- `clientName` (string, optional): Human-readable name
- `clientSecret` (string, optional): Secret for confidential clients
- `authorizationGrantTypes` (array): Grant types (authorization_code, refresh_token, client_credentials)
- `clientAuthenticationMethods` (array): Auth methods (client_secret_basic, client_secret_post, none)
- `redirectUris` (array): Authorized redirect URIs
- `postLogoutRedirectUris` (array): Post-logout redirect URIs
- `scopes` (array): Allowed scopes
- `requireProofKey` (boolean): Require PKCE
- `requireAuthorizationConsent` (boolean): Require user consent
- `enabled` (boolean): Enable the client
- `notes` (string, optional): Admin notes

**Example**:
```
Create a client with:
- clientId: "my-spa"
- clientName: "My SPA Application"
- authorizationGrantTypes: ["authorization_code", "refresh_token"]
- clientAuthenticationMethods: ["none"]
- redirectUris: ["https://myapp.com/callback"]
- postLogoutRedirectUris: ["https://myapp.com"]
- scopes: ["openid", "profile", "email"]
- requireProofKey: true
- requireAuthorizationConsent: true
- enabled: true
```

#### `update_client`
Update an existing client.

**Parameters**: Same as `create_client`, with `clientSecret` optional for secret rotation.

#### `delete_client`
Delete a client (cannot delete system clients).

**Parameters**:
- `clientId` (string): Client ID to delete

#### `set_client_enabled`
Enable or disable a client.

**Parameters**:
- `clientId` (string): Client ID
- `enabled` (boolean): Enable or disable

### Scope Management

#### `list_scopes`
List all available OAuth2 scopes.

#### `create_scope`
Create a new scope.

**Parameters**:
- `scope` (string): Scope identifier (e.g., "read:profile")
- `description` (string, optional): Description

#### `delete_scope`
Delete a scope (cannot delete system scopes).

**Parameters**:
- `scope` (string): Scope identifier

### Deny Rules

#### `list_deny_rules`
List all deny rules.

#### `create_deny_rule`
Create a new deny rule.

**Parameters**:
- `pattern` (string): Pattern to match
- `ruleType` (enum): USERNAME, EMAIL, IP_ADDRESS, or CLIENT_ID
- `enabled` (boolean): Enable the rule
- `reason` (string, optional): Reason for the rule

#### `update_deny_rule`
Update an existing deny rule.

**Parameters**:
- `id` (number): Rule ID
- `pattern`, `ruleType`, `enabled`, `reason`: Same as create

#### `delete_deny_rule`
Delete a deny rule.

**Parameters**:
- `id` (number): Rule ID

### Audit Logs

#### `search_audit_events`
Search audit events with filters.

**Parameters** (all optional):
- `principal` (string): Filter by username
- `clientId` (string): Filter by client ID
- `eventType` (string): Filter by event type
- `outcome` (enum): SUCCESS or FAILURE
- `startTime` (string): ISO 8601 format
- `endTime` (string): ISO 8601 format
- `page` (number): Page number (0-based)
- `size` (number): Page size

#### `get_client_audit_events`
Get audit events for a specific client.

**Parameters**:
- `clientId` (string): Client ID
- `page` (number, optional): Page number
- `size` (number, optional): Page size

## Available Resources

The MCP server provides documentation resources:

### `oauth2://docs/integration-guide`
Comprehensive guide for integrating applications with the OAuth2 server.

### `oauth2://docs/grant-types`
Detailed documentation of supported OAuth2 grant types.

### `oauth2://docs/scopes`
Reference for available OAuth2 scopes (dynamically generated).

## Kubernetes Deployment

See [kubernetes-setup.md](docs/kubernetes-setup.md) for Kubernetes deployment instructions.

## Example Workflows

### Creating a Web Application Client

```
Create a new OAuth2 client for my web app:
- Client ID: my-web-app
- Name: My Web Application
- Grant types: authorization_code and refresh_token
- Redirect URI: https://myapp.example.com/callback
- Scopes: openid, profile, email
- Enable PKCE
- Require consent
```

### Creating a Machine-to-Machine Client

```
Create an M2M client:
- Client ID: api-service
- Name: API Service
- Grant type: client_credentials
- Scopes: api:read, api:write
- No PKCE needed
- No consent needed
```

### Reviewing Audit Logs

```
Search for failed login attempts in the last 24 hours for user "john.doe"
```

### Managing Scopes

```
1. List all current scopes
2. Create a new scope "reports:read" with description "Read access to reports"
```

## Troubleshooting

### Authentication Errors

If you get authentication errors:

1. Verify the client secret is correct
2. Check that the admin client has the required scopes (`admin:read`, `admin:write`)
3. Ensure the OAuth2 server is accessible
4. Check that the client is enabled in the admin console

### Connection Issues

If the MCP server can't connect:

1. Verify `OAUTH2_SERVER_URL` is correct
2. Check network connectivity to the OAuth2 server
3. Verify TLS/SSL certificates are valid

### Permission Denied

If operations fail with permission denied:

1. Ensure the admin client has `admin:read` and `admin:write` scopes
2. Check that the user account has ADMIN role (if using user credentials)
3. Verify the client is not trying to delete system resources

## Security Considerations

1. **Protect the client secret**: Store securely, never commit to version control
2. **Limit scope access**: Only grant necessary scopes to the admin client
3. **Rotate secrets regularly**: Use `update_client` to rotate the secret
4. **Monitor audit logs**: Regularly review administrative actions
5. **Use HTTPS only**: Never use over unencrypted connections

## Development

```bash
# Install dependencies
npm install

# Build
npm run build

# Watch mode for development
npm run dev

# Lint
npm run lint

# Format code
npm run format
```

## License

MIT

## Support

For issues or questions:
- Check the integration guide: Use the `oauth2://docs/integration-guide` resource
- Review audit logs for errors
- Contact the platform team
