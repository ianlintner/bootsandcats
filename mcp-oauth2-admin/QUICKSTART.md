# OAuth2 MCP Server - Quick Start Guide

This guide will help you quickly set up and use the OAuth2 Model Context Protocol (MCP) server to manage your OAuth2 authorization server at oauth2.cat-herding.net.

## What is This?

The OAuth2 MCP Server is a management interface that allows AI assistants (like Claude Desktop or Cline) to interact with your OAuth2 authorization server. It provides tools for:

- **Client Management**: Create, update, delete, and manage OAuth2 clients
- **Scope Management**: Define and manage authorization scopes
- **Deny Rules**: Manage client access restrictions
- **Audit Logs**: Query and monitor OAuth2 events
- **Documentation**: Access OAuth2 integration guides directly from your AI assistant

## Prerequisites

- Node.js 18 or higher
- kubectl configured for your Kubernetes cluster
- Admin access to the OAuth2 server
- jq (for the setup script)

## Installation Steps

### 1. Navigate to the MCP Server Directory

```bash
cd /Users/ianlintner/Projects/bootsandcats/mcp-oauth2-admin
```

### 2. Install Dependencies (Already Done ✓)

```bash
npm install
```

### 3. Build the Server (Already Done ✓)

```bash
npm run build
```

### 4. Create the Admin Client

Run the automated setup script:

```bash
./scripts/setup-admin-client.sh
```

This script will:
- Create the `mcp-admin-client` on your OAuth2 server
- Generate a secure random client secret
- Create a Kubernetes secret with the credentials
- Save local configuration to `.env.local`
- Verify everything is working

**Manual Setup**: If you prefer to create the client manually, see [docs/kubernetes-setup.md](docs/kubernetes-setup.md).

### 5. Test Locally (Optional)

Test the MCP server before configuring it with Claude/Cline:

```bash
# Load the environment variables
source .env.local

# Run the server in dev mode
npm start
```

The server will start and wait for MCP commands via stdio. Press Ctrl+C to stop.

## Configuration

### Option A: Claude Desktop

1. Find your Claude Desktop config file:
   - macOS: `~/Library/Application Support/Claude/claude_desktop_config.json`
   - Windows: `%APPDATA%\Claude\claude_desktop_config.json`

2. Add the MCP server configuration:

```json
{
  "mcpServers": {
    "oauth2-admin": {
      "command": "node",
      "args": [
        "/Users/ianlintner/Projects/bootsandcats/mcp-oauth2-admin/dist/index.js"
      ],
      "env": {
        "OAUTH2_SERVER_URL": "https://oauth2.cat-herding.net",
        "OAUTH2_CLIENT_ID": "mcp-admin-client",
        "OAUTH2_CLIENT_SECRET": "your-secret-from-setup-script",
        "OAUTH2_SCOPES": "admin:read admin:write"
      }
    }
  }
}
```

3. Get your client secret from `.env.local` (created by setup script)

4. Restart Claude Desktop

### Option B: Cline (VSCode Extension)

1. Open VSCode Settings (⌘+,)
2. Search for "Cline: MCP Settings"
3. Click "Edit in settings.json"
4. Add:

```json
{
  "cline.mcpServers": {
    "oauth2-admin": {
      "command": "node",
      "args": [
        "/Users/ianlintner/Projects/bootsandcats/mcp-oauth2-admin/dist/index.js"
      ],
      "env": {
        "OAUTH2_SERVER_URL": "https://oauth2.cat-herding.net",
        "OAUTH2_CLIENT_ID": "mcp-admin-client",
        "OAUTH2_CLIENT_SECRET": "your-secret-from-setup-script",
        "OAUTH2_SCOPES": "admin:read admin:write"
      }
    }
  }
}
```

5. Restart VSCode

## Using the MCP Server

Once configured, your AI assistant will have access to these tools:

### Client Management

**List all OAuth2 clients:**
```
List all OAuth2 clients
```

**Create a new client:**
```
Create a new OAuth2 client:
- Client ID: my-app
- Client Name: My Application
- Grant Types: authorization_code, refresh_token
- Redirect URIs: https://myapp.com/callback
- Scopes: openid, profile, email
```

**Update a client:**
```
Update the OAuth2 client "my-app" to add the redirect URI https://myapp.com/new-callback
```

**Enable/disable a client:**
```
Disable the OAuth2 client "old-app"
```

### Scope Management

**List all scopes:**
```
List all available OAuth2 scopes
```

**Create a new scope:**
```
Create a new OAuth2 scope:
- Scope: api:write
- Description: Write access to API
```

### Audit Logs

**Search audit events:**
```
Show me the last 10 OAuth2 audit events
```

**Get client-specific audit logs:**
```
Show me all audit events for the client "my-app"
```

### Documentation

**Access integration guides:**
```
Show me the OAuth2 integration guide
```

The AI assistant can access comprehensive documentation about:
- OAuth2 grant type flows (Authorization Code, Client Credentials, Device, Refresh Token)
- Integration patterns and best practices
- Available scopes and their purposes

## Example Workflows

### Creating a Web Application Client

```
Create a complete OAuth2 setup for a new web application:
- Client ID: my-web-app
- Redirect URI: https://mywebapp.com/oauth/callback
- Needs OpenID Connect with user profile access
- Use authorization code flow with PKCE
```

The AI assistant will:
1. Create the client with appropriate grant types
2. Configure redirect URIs
3. Assign necessary scopes
4. Enable PKCE for security
5. Provide you with the integration code

### Creating a Machine-to-Machine Client

```
Create an OAuth2 client for a backend service that needs to access our API:
- Client ID: backend-service
- Needs admin API access
```

The AI assistant will:
1. Create a client with `client_credentials` grant
2. Assign appropriate scopes
3. Provide credentials and integration examples

### Monitoring and Troubleshooting

```
Show me any failed authentication attempts in the last hour
```

or

```
Are there any issues with client "my-app"?
```

## Troubleshooting

### Authentication Errors

If you see authentication errors:

1. **Verify the client secret**:
   ```bash
   cat .env.local | grep CLIENT_SECRET
   ```

2. **Test the credentials manually**:
   ```bash
   curl -X POST https://oauth2.cat-herding.net/oauth2/token \
     -u "mcp-admin-client:YOUR_SECRET" \
     -d "grant_type=client_credentials" \
     -d "scope=admin:read admin:write"
   ```

3. **Check the client is enabled**:
   Visit https://oauth2.cat-herding.net/admin and verify the client status

### Connection Errors

If the MCP server can't connect to the OAuth2 server:

1. **Verify the server URL**:
   ```bash
   curl -I https://oauth2.cat-herding.net
   ```

2. **Check network connectivity**:
   ```bash
   ping oauth2.cat-herding.net
   ```

3. **Verify Kubernetes port forwarding** (if using):
   ```bash
   kubectl get svc -n oauth2-system
   ```

### Permission Denied

If you get "Insufficient scope" errors:

1. **Verify the client has admin scopes**:
   - Go to https://oauth2.cat-herding.net/admin
   - Find `mcp-admin-client`
   - Verify it has `admin:read` and `admin:write` scopes

2. **Re-run the setup script**:
   ```bash
   ./scripts/setup-admin-client.sh
   ```

## Security Notes

⚠️ **IMPORTANT**: The client secret provides admin-level access to your OAuth2 server.

- **Never commit** `.env.local` to version control
- **Rotate the secret regularly** (recommended: every 90 days)
- **Use Kubernetes secrets** in production environments
- **Monitor audit logs** for unauthorized access
- **Review client permissions** periodically

## Next Steps

1. **Explore the tools**: Ask your AI assistant to list available OAuth2 management tools
2. **Read the integration guide**: Ask for the OAuth2 integration documentation
3. **Create test clients**: Practice creating and managing OAuth2 clients
4. **Review audit logs**: Monitor OAuth2 server activity
5. **Automate workflows**: Use the MCP server to streamline OAuth2 management

## Resources

- **Detailed Documentation**: [README.md](README.md)
- **Kubernetes Setup**: [docs/kubernetes-setup.md](docs/kubernetes-setup.md)
- **Example Configs**: [examples/](examples/)
- **OAuth2 Server Admin UI**: https://oauth2.cat-herding.net/admin

## Support

For issues or questions:

1. Check the troubleshooting sections in [README.md](README.md)
2. Review the Kubernetes setup guide for infrastructure issues
3. Check audit logs for security or permission issues
4. Verify the OAuth2 server is running: https://oauth2.cat-herding.net

## Building and Development

The server is already built, but if you make changes:

```bash
# Development mode with auto-reload
npm run dev

# Build for production
npm run build

# Format code
npm run format

# Lint code
npm run lint
```

---

**Ready to go!** Your OAuth2 MCP Server is installed and ready to use. Configure it with Claude Desktop or Cline to start managing your OAuth2 authorization server with AI assistance.
