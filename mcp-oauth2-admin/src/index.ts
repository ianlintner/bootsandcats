#!/usr/bin/env node

import { Server } from '@modelcontextprotocol/sdk/server/index.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import {
  CallToolRequestSchema,
  ListToolsRequestSchema,
  ListResourcesRequestSchema,
  ReadResourceRequestSchema,
} from '@modelcontextprotocol/sdk/types.js';
import { OAuth2Client } from './oauth2-client.js';
import {
  OAuth2AdminAPI,
  ListClientsSchema,
  GetClientSchema,
  CreateClientSchema,
  UpdateClientSchema,
  DeleteClientSchema,
  SetClientEnabledSchema,
  ListScopesSchema,
  CreateScopeSchema,
  DeleteScopeSchema,
  ListDenyRulesSchema,
  CreateDenyRuleSchema,
  UpdateDenyRuleSchema,
  DeleteDenyRuleSchema,
  SearchAuditEventsSchema,
} from './api-client.js';

/**
 * OAuth2 Authorization Server MCP Server
 *
 * Provides tools for managing OAuth2 clients, scopes, deny rules, and audit logs
 * on the authorization server at oauth2.cat-herding.net
 */
class OAuth2MCPServer {
  private server: Server;
  private oauth2Client: OAuth2Client;
  private api: OAuth2AdminAPI | null = null;

  constructor() {
    this.server = new Server(
      {
        name: 'oauth2-admin',
        version: '1.0.0',
      },
      {
        capabilities: {
          tools: {},
          resources: {},
        },
      }
    );

    // Get configuration from environment variables
    const serverUrl = process.env.OAUTH2_SERVER_URL || 'https://oauth2.cat-herding.net';
    const clientId = process.env.OAUTH2_CLIENT_ID || 'mcp-admin-client';
    const clientSecret = process.env.OAUTH2_CLIENT_SECRET;
    const scopes = (process.env.OAUTH2_SCOPES || 'admin:read admin:write').split(' ');

    if (!clientSecret) {
      throw new Error('OAUTH2_CLIENT_SECRET environment variable is required');
    }

    this.oauth2Client = new OAuth2Client({
      serverUrl,
      clientId,
      clientSecret,
      scopes,
    });

    this.setupHandlers();
    
    // Report errors to stderr
    this.server.onerror = (error) => {
      console.error('[MCP Error]', error);
    };

    process.on('SIGINT', async () => {
      await this.server.close();
      process.exit(0);
    });
  }

  private async getAPI(): Promise<OAuth2AdminAPI> {
    if (!this.api) {
      const client = await this.oauth2Client.createAuthenticatedClient();
      this.api = new OAuth2AdminAPI(client);
    }
    return this.api;
  }

  private setupHandlers() {
    // List available tools
    this.server.setRequestHandler(ListToolsRequestSchema, async () => ({
      tools: [
        // Client Management Tools
        {
          name: 'list_clients',
          description: 'List all OAuth2 registered clients',
          inputSchema: {
            type: 'object',
            properties: {},
          },
        },
        {
          name: 'get_client',
          description: 'Get details of a specific OAuth2 client',
          inputSchema: {
            type: 'object',
            properties: {
              clientId: { type: 'string', description: 'The OAuth2 client ID' },
            },
            required: ['clientId'],
          },
        },
        {
          name: 'create_client',
          description: 'Create a new OAuth2 client',
          inputSchema: {
            type: 'object',
            properties: {
              clientId: { type: 'string', description: 'Unique client identifier' },
              clientName: { type: 'string', description: 'Human-readable name' },
              clientSecret: { type: 'string', description: 'Client secret (optional for public)' },
              authorizationGrantTypes: {
                type: 'array',
                items: { type: 'string' },
                description: 'Grant types (authorization_code, refresh_token, client_credentials)',
              },
              clientAuthenticationMethods: {
                type: 'array',
                items: { type: 'string' },
                description: 'Auth methods (client_secret_basic, client_secret_post, none)',
              },
              redirectUris: { type: 'array', items: { type: 'string' } },
              postLogoutRedirectUris: { type: 'array', items: { type: 'string' } },
              scopes: { type: 'array', items: { type: 'string' } },
              requireProofKey: { type: 'boolean', description: 'Require PKCE' },
              requireAuthorizationConsent: { type: 'boolean', description: 'Require consent' },
              enabled: { type: 'boolean' },
              notes: { type: 'string' },
            },
            required: [
              'clientId',
              'authorizationGrantTypes',
              'clientAuthenticationMethods',
              'redirectUris',
              'postLogoutRedirectUris',
              'scopes',
              'requireProofKey',
              'requireAuthorizationConsent',
              'enabled',
            ],
          },
        },
        {
          name: 'update_client',
          description: 'Update an existing OAuth2 client',
          inputSchema: {
            type: 'object',
            properties: {
              clientId: { type: 'string' },
              clientName: { type: 'string' },
              clientSecret: { type: 'string', description: 'New secret (rotates the secret)' },
              authorizationGrantTypes: { type: 'array', items: { type: 'string' } },
              clientAuthenticationMethods: { type: 'array', items: { type: 'string' } },
              redirectUris: { type: 'array', items: { type: 'string' } },
              postLogoutRedirectUris: { type: 'array', items: { type: 'string' } },
              scopes: { type: 'array', items: { type: 'string' } },
              requireProofKey: { type: 'boolean' },
              requireAuthorizationConsent: { type: 'boolean' },
              enabled: { type: 'boolean' },
              notes: { type: 'string' },
            },
            required: [
              'clientId',
              'authorizationGrantTypes',
              'clientAuthenticationMethods',
              'redirectUris',
              'postLogoutRedirectUris',
              'scopes',
              'requireProofKey',
              'requireAuthorizationConsent',
              'enabled',
            ],
          },
        },
        {
          name: 'delete_client',
          description: 'Delete an OAuth2 client (cannot delete system clients)',
          inputSchema: {
            type: 'object',
            properties: {
              clientId: { type: 'string' },
            },
            required: ['clientId'],
          },
        },
        {
          name: 'set_client_enabled',
          description: 'Enable or disable an OAuth2 client',
          inputSchema: {
            type: 'object',
            properties: {
              clientId: { type: 'string' },
              enabled: { type: 'boolean' },
            },
            required: ['clientId', 'enabled'],
          },
        },

        // Scope Management Tools
        {
          name: 'list_scopes',
          description: 'List all available OAuth2 scopes',
          inputSchema: {
            type: 'object',
            properties: {},
          },
        },
        {
          name: 'create_scope',
          description: 'Create a new OAuth2 scope',
          inputSchema: {
            type: 'object',
            properties: {
              scope: { type: 'string', description: 'Scope identifier (e.g., read:profile)' },
              description: { type: 'string' },
            },
            required: ['scope'],
          },
        },
        {
          name: 'delete_scope',
          description: 'Delete an OAuth2 scope (cannot delete system scopes)',
          inputSchema: {
            type: 'object',
            properties: {
              scope: { type: 'string' },
            },
            required: ['scope'],
          },
        },

        // Deny Rule Management Tools
        {
          name: 'list_deny_rules',
          description: 'List all deny rules for access control',
          inputSchema: {
            type: 'object',
            properties: {},
          },
        },
        {
          name: 'create_deny_rule',
          description: 'Create a new deny rule to block specific patterns',
          inputSchema: {
            type: 'object',
            properties: {
              pattern: { type: 'string', description: 'Pattern to match (regex or exact)' },
              ruleType: {
                type: 'string',
                enum: ['USERNAME', 'EMAIL', 'IP_ADDRESS', 'CLIENT_ID'],
              },
              enabled: { type: 'boolean' },
              reason: { type: 'string' },
            },
            required: ['pattern', 'ruleType', 'enabled'],
          },
        },
        {
          name: 'update_deny_rule',
          description: 'Update an existing deny rule',
          inputSchema: {
            type: 'object',
            properties: {
              id: { type: 'number' },
              pattern: { type: 'string' },
              ruleType: { type: 'string', enum: ['USERNAME', 'EMAIL', 'IP_ADDRESS', 'CLIENT_ID'] },
              enabled: { type: 'boolean' },
              reason: { type: 'string' },
            },
            required: ['id', 'pattern', 'ruleType', 'enabled'],
          },
        },
        {
          name: 'delete_deny_rule',
          description: 'Delete a deny rule',
          inputSchema: {
            type: 'object',
            properties: {
              id: { type: 'number' },
            },
            required: ['id'],
          },
        },

        // Audit Tools
        {
          name: 'search_audit_events',
          description: 'Search audit events with filters',
          inputSchema: {
            type: 'object',
            properties: {
              principal: { type: 'string', description: 'Filter by username' },
              clientId: { type: 'string', description: 'Filter by OAuth2 client ID' },
              eventType: { type: 'string' },
              outcome: { type: 'string', enum: ['SUCCESS', 'FAILURE'] },
              startTime: { type: 'string', description: 'ISO 8601 format' },
              endTime: { type: 'string', description: 'ISO 8601 format' },
              page: { type: 'number', default: 0 },
              size: { type: 'number', default: 20 },
            },
          },
        },
        {
          name: 'get_client_audit_events',
          description: 'Get audit events for a specific client',
          inputSchema: {
            type: 'object',
            properties: {
              clientId: { type: 'string' },
              page: { type: 'number', default: 0 },
              size: { type: 'number', default: 20 },
            },
            required: ['clientId'],
          },
        },
      ],
    }));

    // List available resources
    this.server.setRequestHandler(ListResourcesRequestSchema, async () => ({
      resources: [
        {
          uri: 'oauth2://docs/integration-guide',
          name: 'OAuth2 Integration Guide',
          description: 'Comprehensive guide for integrating applications with the OAuth2 server',
          mimeType: 'text/markdown',
        },
        {
          uri: 'oauth2://docs/grant-types',
          name: 'OAuth2 Grant Types Guide',
          description: 'Detailed documentation of supported OAuth2 grant types',
          mimeType: 'text/markdown',
        },
        {
          uri: 'oauth2://docs/scopes',
          name: 'OAuth2 Scopes Reference',
          description: 'Reference for available OAuth2 scopes',
          mimeType: 'text/markdown',
        },
      ],
    }));

    // Read resource content
    this.server.setRequestHandler(ReadResourceRequestSchema, async (request) => {
      const uri = request.params.uri.toString();

      if (uri === 'oauth2://docs/integration-guide') {
        return {
          contents: [
            {
              uri,
              mimeType: 'text/markdown',
              text: await this.getIntegrationGuide(),
            },
          ],
        };
      }

      if (uri === 'oauth2://docs/grant-types') {
        return {
          contents: [
            {
              uri,
              mimeType: 'text/markdown',
              text: await this.getGrantTypesGuide(),
            },
          ],
        };
      }

      if (uri === 'oauth2://docs/scopes') {
        return {
          contents: [
            {
              uri,
              mimeType: 'text/markdown',
              text: await this.getScopesReference(),
            },
          ],
        };
      }

      throw new Error(`Unknown resource: ${uri}`);
    });

    // Handle tool calls
    this.server.setRequestHandler(CallToolRequestSchema, async (request) => {
      const api = await this.getAPI();

      try {
        switch (request.params.name) {
          // Client Management
          case 'list_clients': {
            const clients = await api.listClients();
            return {
              content: [
                {
                  type: 'text',
                  text: JSON.stringify(clients, null, 2),
                },
              ],
            };
          }

          case 'get_client': {
            const args = GetClientSchema.parse(request.params.arguments);
            const client = await api.getClient(args.clientId);
            return {
              content: [
                {
                  type: 'text',
                  text: JSON.stringify(client, null, 2),
                },
              ],
            };
          }

          case 'create_client': {
            const args = CreateClientSchema.parse(request.params.arguments);
            const client = await api.createClient(args);
            return {
              content: [
                {
                  type: 'text',
                  text: `Successfully created client: ${client.clientId}\n\n${JSON.stringify(client, null, 2)}`,
                },
              ],
            };
          }

          case 'update_client': {
            const args = UpdateClientSchema.parse(request.params.arguments);
            const { clientId, ...updateData } = args;
            const client = await api.updateClient(clientId, { clientId, ...updateData });
            return {
              content: [
                {
                  type: 'text',
                  text: `Successfully updated client: ${client.clientId}\n\n${JSON.stringify(client, null, 2)}`,
                },
              ],
            };
          }

          case 'delete_client': {
            const args = DeleteClientSchema.parse(request.params.arguments);
            await api.deleteClient(args.clientId);
            return {
              content: [
                {
                  type: 'text',
                  text: `Successfully deleted client: ${args.clientId}`,
                },
              ],
            };
          }

          case 'set_client_enabled': {
            const args = SetClientEnabledSchema.parse(request.params.arguments);
            const client = await api.setClientEnabled(args.clientId, args.enabled);
            return {
              content: [
                {
                  type: 'text',
                  text: `Client ${client.clientId} is now ${client.enabled ? 'enabled' : 'disabled'}`,
                },
              ],
            };
          }

          // Scope Management
          case 'list_scopes': {
            const scopes = await api.listScopes();
            return {
              content: [
                {
                  type: 'text',
                  text: JSON.stringify(scopes, null, 2),
                },
              ],
            };
          }

          case 'create_scope': {
            const args = CreateScopeSchema.parse(request.params.arguments);
            const scope = await api.createScope(args);
            return {
              content: [
                {
                  type: 'text',
                  text: `Successfully created scope: ${scope.scope}\n\n${JSON.stringify(scope, null, 2)}`,
                },
              ],
            };
          }

          case 'delete_scope': {
            const args = DeleteScopeSchema.parse(request.params.arguments);
            await api.deleteScope(args.scope);
            return {
              content: [
                {
                  type: 'text',
                  text: `Successfully deleted scope: ${args.scope}`,
                },
              ],
            };
          }

          // Deny Rules
          case 'list_deny_rules': {
            const rules = await api.listDenyRules();
            return {
              content: [
                {
                  type: 'text',
                  text: JSON.stringify(rules, null, 2),
                },
              ],
            };
          }

          case 'create_deny_rule': {
            const args = CreateDenyRuleSchema.parse(request.params.arguments);
            const rule = await api.createDenyRule(args);
            return {
              content: [
                {
                  type: 'text',
                  text: `Successfully created deny rule (ID: ${rule.id})\n\n${JSON.stringify(rule, null, 2)}`,
                },
              ],
            };
          }

          case 'update_deny_rule': {
            const args = UpdateDenyRuleSchema.parse(request.params.arguments);
            const { id, ...updateData } = args;
            const rule = await api.updateDenyRule(id, updateData);
            return {
              content: [
                {
                  type: 'text',
                  text: `Successfully updated deny rule (ID: ${rule.id})\n\n${JSON.stringify(rule, null, 2)}`,
                },
              ],
            };
          }

          case 'delete_deny_rule': {
            const args = DeleteDenyRuleSchema.parse(request.params.arguments);
            await api.deleteDenyRule(args.id);
            return {
              content: [
                {
                  type: 'text',
                  text: `Successfully deleted deny rule (ID: ${args.id})`,
                },
              ],
            };
          }

          // Audit
          case 'search_audit_events': {
            const args = SearchAuditEventsSchema.parse(request.params.arguments || {});
            const result = await api.searchAuditEvents(args);
            return {
              content: [
                {
                  type: 'text',
                  text: JSON.stringify(result, null, 2),
                },
              ],
            };
          }

          case 'get_client_audit_events': {
            const args = request.params.arguments as {
              clientId: string;
              page?: number;
              size?: number;
            };
            const result = await api.getAuditEventsByClient(
              args.clientId,
              args.page,
              args.size
            );
            return {
              content: [
                {
                  type: 'text',
                  text: JSON.stringify(result, null, 2),
                },
              ],
            };
          }

          default:
            throw new Error(`Unknown tool: ${request.params.name}`);
        }
      } catch (error) {
        if (error instanceof Error) {
          return {
            content: [
              {
                type: 'text',
                text: `Error: ${error.message}`,
              },
            ],
            isError: true,
          };
        }
        throw error;
      }
    });
  }

  async run() {
    const transport = new StdioServerTransport();
    await this.server.connect(transport);
    console.error('OAuth2 Admin MCP Server running on stdio');
  }

  // Documentation methods
  private async getIntegrationGuide(): Promise<string> {
    return `# OAuth2 Integration Guide

## Overview

The OAuth2 Authorization Server at **oauth2.cat-herding.net** provides secure authentication and authorization for your applications using industry-standard OAuth 2.1 and OpenID Connect protocols.

## Quick Start

### 1. Register Your Client

Use the \`create_client\` tool to register your application:

\`\`\`json
{
  "clientId": "my-app",
  "clientName": "My Application",
  "clientSecret": "your-secret-here",
  "authorizationGrantTypes": ["authorization_code", "refresh_token"],
  "clientAuthenticationMethods": ["client_secret_basic"],
  "redirectUris": ["https://myapp.example.com/callback"],
  "postLogoutRedirectUris": ["https://myapp.example.com"],
  "scopes": ["openid", "profile", "email"],
  "requireProofKey": true,
  "requireAuthorizationConsent": true,
  "enabled": true
}
\`\`\`

### 2. Implement OAuth2 Flow

#### Authorization Code Flow (Recommended for Web Apps)

1. **Authorization Request**: Redirect users to:
   \`\`\`
   https://oauth2.cat-herding.net/oauth2/authorize?
     response_type=code&
     client_id=YOUR_CLIENT_ID&
     redirect_uri=YOUR_REDIRECT_URI&
     scope=openid%20profile%20email&
     state=RANDOM_STATE&
     code_challenge=PKCE_CHALLENGE&
     code_challenge_method=S256
   \`\`\`

2. **Token Exchange**: Exchange authorization code for tokens:
   \`\`\`bash
   curl -X POST https://oauth2.cat-herding.net/oauth2/token \\
     -H "Content-Type: application/x-www-form-urlencoded" \\
     -u "YOUR_CLIENT_ID:YOUR_CLIENT_SECRET" \\
     -d "grant_type=authorization_code" \\
     -d "code=AUTH_CODE" \\
     -d "redirect_uri=YOUR_REDIRECT_URI" \\
     -d "code_verifier=PKCE_VERIFIER"
   \`\`\`

3. **Response**:
   \`\`\`json
   {
     "access_token": "eyJhbGci...",
     "token_type": "Bearer",
     "expires_in": 3600,
     "refresh_token": "eyJhbGci...",
     "scope": "openid profile email"
   }
   \`\`\`

#### Client Credentials Flow (Machine-to-Machine)

For service-to-service authentication:

\`\`\`bash
curl -X POST https://oauth2.cat-herding.net/oauth2/token \\
  -H "Content-Type: application/x-www-form-urlencoded" \\
  -u "YOUR_CLIENT_ID:YOUR_CLIENT_SECRET" \\
  -d "grant_type=client_credentials" \\
  -d "scope=api:read api:write"
\`\`\`

### 3. Use Access Token

Include the access token in API requests:

\`\`\`bash
curl -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \\
  https://api.example.com/resource
\`\`\`

## Endpoints

- **Authorization**: \`https://oauth2.cat-herding.net/oauth2/authorize\`
- **Token**: \`https://oauth2.cat-herding.net/oauth2/token\`
- **UserInfo**: \`https://oauth2.cat-herding.net/userinfo\`
- **JWKS**: \`https://oauth2.cat-herding.net/oauth2/jwks\`
- **Discovery**: \`https://oauth2.cat-herding.net/.well-known/openid-configuration\`
- **Introspection**: \`https://oauth2.cat-herding.net/oauth2/introspect\`
- **Revocation**: \`https://oauth2.cat-herding.net/oauth2/revoke\`

## Security Best Practices

1. **Always use HTTPS** in production
2. **Enable PKCE** for public clients (SPAs, mobile apps)
3. **Rotate secrets** regularly
4. **Use short-lived access tokens** (default: 1 hour)
5. **Validate tokens** on every request
6. **Store refresh tokens securely**
7. **Implement proper logout** using \`/connect/logout\`

## Testing

Default test users (for development only):
- Username: \`user\` / Password: \`password\` (USER role)
- Username: \`admin\` / Password: \`admin\` (ADMIN role)

## Troubleshooting

- **401 Unauthorized**: Check client credentials
- **403 Forbidden**: Check scopes and permissions
- **invalid_grant**: Authorization code expired or invalid
- **invalid_scope**: Requested scope not allowed for client

For more details, see the Grant Types and Scopes guides.
`;
  }

  private async getGrantTypesGuide(): Promise<string> {
    return `# OAuth2 Grant Types Guide

## Supported Grant Types

### 1. Authorization Code (authorization_code)

**Use for**: Web applications with a backend

**Flow**:
1. User redirected to authorization endpoint
2. User authenticates and consents
3. Authorization code returned to redirect URI
4. Backend exchanges code for tokens

**PKCE Required**: Yes (recommended for all clients)

**Example**:
\`\`\`bash
# Step 1: Authorization
GET /oauth2/authorize?
  response_type=code&
  client_id=my-web-app&
  redirect_uri=https://myapp.com/callback&
  scope=openid profile email&
  state=xyz&
  code_challenge=CHALLENGE&
  code_challenge_method=S256

# Step 2: Token Exchange
POST /oauth2/token
Content-Type: application/x-www-form-urlencoded
Authorization: Basic BASE64(client_id:client_secret)

grant_type=authorization_code&
code=AUTH_CODE&
redirect_uri=https://myapp.com/callback&
code_verifier=VERIFIER
\`\`\`

### 2. Refresh Token (refresh_token)

**Use for**: Getting new access tokens without re-authentication

**Example**:
\`\`\`bash
POST /oauth2/token
Content-Type: application/x-www-form-urlencoded
Authorization: Basic BASE64(client_id:client_secret)

grant_type=refresh_token&
refresh_token=REFRESH_TOKEN
\`\`\`

### 3. Client Credentials (client_credentials)

**Use for**: Machine-to-machine, service accounts

**Flow**:
1. Service authenticates directly with client credentials
2. No user interaction required

**Example**:
\`\`\`bash
POST /oauth2/token
Content-Type: application/x-www-form-urlencoded
Authorization: Basic BASE64(client_id:client_secret)

grant_type=client_credentials&
scope=api:read api:write
\`\`\`

### 4. Device Authorization (urn:ietf:params:oauth:grant-type:device_code)

**Use for**: Smart TVs, IoT devices, CLI tools

**Flow**:
1. Device requests device code
2. User visits verification URL on another device
3. User enters code and authenticates
4. Device polls for token

**Example**:
\`\`\`bash
# Step 1: Request device code
POST /oauth2/device_authorization
Content-Type: application/x-www-form-urlencoded

client_id=my-device-app&
scope=openid profile

# Response:
{
  "device_code": "GmRh...",
  "user_code": "WDJB-MJHT",
  "verification_uri": "https://oauth2.cat-herding.net/device",
  "expires_in": 1800,
  "interval": 5
}

# Step 2: Poll for token
POST /oauth2/token
Content-Type: application/x-www-form-urlencoded

grant_type=urn:ietf:params:oauth:grant-type:device_code&
device_code=GmRh...&
client_id=my-device-app
\`\`\`

## Grant Type Selection Matrix

| Use Case | Grant Type | PKCE | Consent |
|----------|------------|------|---------|
| Web app with backend | authorization_code | Yes | Yes |
| SPA (Single Page App) | authorization_code | Yes | Yes |
| Mobile app | authorization_code | Yes | Yes |
| Service-to-service | client_credentials | No | No |
| CLI tool | device_code | No | Yes |
| IoT device | device_code | No | Yes |

## Token Lifetimes

- **Access Token**: 1 hour (3600 seconds)
- **Refresh Token**: 30 days
- **Authorization Code**: 5 minutes
- **Device Code**: 30 minutes

## Client Authentication Methods

1. **client_secret_basic**: Client credentials in Authorization header (recommended)
2. **client_secret_post**: Client credentials in request body
3. **none**: For public clients (must use PKCE)
`;
  }

  private async getScopesReference(): Promise<string> {
    const api = await this.getAPI();
    const scopes = await api.listScopes();

    let markdown = `# OAuth2 Scopes Reference

## Overview

Scopes define the level of access that a client application can request. The authorization server will only grant scopes that:
1. The client is allowed to request
2. The user has consented to (for user-facing flows)

## Available Scopes

`;

    for (const scope of scopes) {
      markdown += `### \`${scope.scope}\`\n\n`;
      if (scope.description) {
        markdown += `${scope.description}\n\n`;
      }
      if (scope.system) {
        markdown += `**System Scope**: Cannot be deleted\n\n`;
      }
      markdown += `---\n\n`;
    }

    markdown += `## OpenID Connect Scopes

The following scopes are part of OpenID Connect:

- **openid**: Required for OIDC authentication. Returns the \`sub\` claim.
- **profile**: Access to profile claims (\`name\`, \`given_name\`, \`family_name\`, \`preferred_username\`, \`picture\`, \`locale\`, \`updated_at\`)
- **email**: Access to email claims (\`email\`, \`email_verified\`)

## Requesting Scopes

Include scopes in the authorization request:

\`\`\`
/oauth2/authorize?
  ...&
  scope=openid%20profile%20email%20custom:scope
\`\`\`

Space-separated scopes will be URL-encoded.

## Scope Validation

1. Client must be registered with requested scopes
2. User must consent (unless consent is disabled for the client)
3. Granted scopes appear in:
   - Access token JWT claims
   - Token introspection response
   - UserInfo endpoint response

## Best Practices

1. **Request minimum required scopes**
2. **Use specific scopes** (e.g., \`read:profile\` instead of \`admin\`)
3. **Document your custom scopes** clearly
4. **Review granted scopes** in access tokens
5. **Validate scopes** on every API request
`;

    return markdown;
  }
}

// Start the server
const server = new OAuth2MCPServer();
server.run().catch((error) => {
  console.error('Fatal error:', error);
  process.exit(1);
});
