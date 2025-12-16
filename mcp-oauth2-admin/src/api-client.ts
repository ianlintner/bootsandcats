import { AxiosInstance, AxiosError } from 'axios';
import { z } from 'zod';
import {
  AdminClientSummary,
  AdminClientUpsertRequest,
  AdminScopeSummary,
  AdminScopeUpsertRequest,
  AdminDenyRuleSummary,
  AdminDenyRuleUpsertRequest,
  AuditEventSummary,
  AuditSearchParams,
  PageResponse,
} from './types.js';

/**
 * Schema definitions for MCP tool inputs
 */
export const ListClientsSchema = z.object({});

export const GetClientSchema = z.object({
  clientId: z.string().describe('The OAuth2 client ID'),
});

export const CreateClientSchema = z.object({
  clientId: z.string().describe('Unique client identifier'),
  clientName: z.string().optional().describe('Human-readable client name'),
  clientSecret: z.string().optional().describe('Client secret (optional for public clients)'),
  authorizationGrantTypes: z
    .array(z.string())
    .describe('Grant types: authorization_code, refresh_token, client_credentials, etc.'),
  clientAuthenticationMethods: z
    .array(z.string())
    .describe('Auth methods: client_secret_basic, client_secret_post, none'),
  redirectUris: z.array(z.string()).describe('Authorized redirect URIs'),
  postLogoutRedirectUris: z.array(z.string()).describe('Post-logout redirect URIs'),
  scopes: z.array(z.string()).describe('Allowed scopes'),
  requireProofKey: z.boolean().describe('Require PKCE (recommended for public clients)'),
  requireAuthorizationConsent: z.boolean().describe('Require user consent'),
  enabled: z.boolean().describe('Enable/disable the client'),
  notes: z.string().optional().describe('Admin notes about the client'),
});

export const UpdateClientSchema = z.object({
  clientId: z.string().describe('The OAuth2 client ID to update'),
  clientName: z.string().optional().describe('Human-readable client name'),
  clientSecret: z.string().optional().describe('New client secret (rotates the secret)'),
  authorizationGrantTypes: z
    .array(z.string())
    .describe('Grant types: authorization_code, refresh_token, client_credentials, etc.'),
  clientAuthenticationMethods: z
    .array(z.string())
    .describe('Auth methods: client_secret_basic, client_secret_post, none'),
  redirectUris: z.array(z.string()).describe('Authorized redirect URIs'),
  postLogoutRedirectUris: z.array(z.string()).describe('Post-logout redirect URIs'),
  scopes: z.array(z.string()).describe('Allowed scopes'),
  requireProofKey: z.boolean().describe('Require PKCE'),
  requireAuthorizationConsent: z.boolean().describe('Require user consent'),
  enabled: z.boolean().describe('Enable/disable the client'),
  notes: z.string().optional().describe('Admin notes'),
});

export const DeleteClientSchema = z.object({
  clientId: z.string().describe('The OAuth2 client ID to delete'),
});

export const SetClientEnabledSchema = z.object({
  clientId: z.string().describe('The OAuth2 client ID'),
  enabled: z.boolean().describe('Enable or disable the client'),
});

export const ListScopesSchema = z.object({});

export const CreateScopeSchema = z.object({
  scope: z.string().describe('Scope identifier (e.g., read:profile, write:data)'),
  description: z.string().optional().describe('Description of what this scope allows'),
});

export const DeleteScopeSchema = z.object({
  scope: z.string().describe('The scope identifier to delete'),
});

export const ListDenyRulesSchema = z.object({});

export const CreateDenyRuleSchema = z.object({
  pattern: z.string().describe('Pattern to match (regex or exact match)'),
  ruleType: z
    .enum(['USERNAME', 'EMAIL', 'IP_ADDRESS', 'CLIENT_ID'])
    .describe('Type of deny rule'),
  enabled: z.boolean().describe('Enable the rule'),
  reason: z.string().optional().describe('Reason for the deny rule'),
});

export const UpdateDenyRuleSchema = z.object({
  id: z.number().describe('Deny rule ID'),
  pattern: z.string().describe('Pattern to match'),
  ruleType: z.enum(['USERNAME', 'EMAIL', 'IP_ADDRESS', 'CLIENT_ID']).describe('Type of rule'),
  enabled: z.boolean().describe('Enable the rule'),
  reason: z.string().optional().describe('Reason for the rule'),
});

export const DeleteDenyRuleSchema = z.object({
  id: z.number().describe('Deny rule ID to delete'),
});

export const SearchAuditEventsSchema = z.object({
  principal: z.string().optional().describe('Filter by principal (username)'),
  clientId: z.string().optional().describe('Filter by OAuth2 client ID'),
  eventType: z.string().optional().describe('Filter by event type'),
  outcome: z.enum(['SUCCESS', 'FAILURE']).optional().describe('Filter by outcome'),
  startTime: z.string().optional().describe('Start time (ISO 8601 format)'),
  endTime: z.string().optional().describe('End time (ISO 8601 format)'),
  page: z.number().optional().default(0).describe('Page number (0-based)'),
  size: z.number().optional().default(20).describe('Page size'),
});

/**
 * OAuth2 Admin API client
 */
export class OAuth2AdminAPI {
  constructor(private client: AxiosInstance) {}

  /**
   * Handle API errors consistently
   */
  private handleError(error: unknown, operation: string): never {
    if (error instanceof AxiosError) {
      const status = error.response?.status;
      const data = error.response?.data;
      throw new Error(
        `${operation} failed: ${status || 'Unknown'} - ${JSON.stringify(data) || error.message}`
      );
    }
    throw error;
  }

  // Client Management
  async listClients(): Promise<AdminClientSummary[]> {
    try {
      const response = await this.client.get<AdminClientSummary[]>('/api/admin/clients');
      return response.data;
    } catch (error) {
      return this.handleError(error, 'List clients');
    }
  }

  async getClient(clientId: string): Promise<AdminClientSummary> {
    try {
      const response = await this.client.get<AdminClientSummary>(
        `/api/admin/clients/${encodeURIComponent(clientId)}`
      );
      return response.data;
    } catch (error) {
      return this.handleError(error, 'Get client');
    }
  }

  async createClient(request: AdminClientUpsertRequest): Promise<AdminClientSummary> {
    try {
      const response = await this.client.post<AdminClientSummary>('/api/admin/clients', request);
      return response.data;
    } catch (error) {
      return this.handleError(error, 'Create client');
    }
  }

  async updateClient(
    clientId: string,
    request: AdminClientUpsertRequest
  ): Promise<AdminClientSummary> {
    try {
      const response = await this.client.put<AdminClientSummary>(
        `/api/admin/clients/${encodeURIComponent(clientId)}`,
        request
      );
      return response.data;
    } catch (error) {
      return this.handleError(error, 'Update client');
    }
  }

  async deleteClient(clientId: string): Promise<void> {
    try {
      await this.client.delete(`/api/admin/clients/${encodeURIComponent(clientId)}`);
    } catch (error) {
      return this.handleError(error, 'Delete client');
    }
  }

  async setClientEnabled(clientId: string, enabled: boolean): Promise<AdminClientSummary> {
    try {
      const response = await this.client.post<AdminClientSummary>(
        `/api/admin/clients/${encodeURIComponent(clientId)}/enabled`,
        null,
        { params: { enabled } }
      );
      return response.data;
    } catch (error) {
      return this.handleError(error, 'Set client enabled');
    }
  }

  // Scope Management
  async listScopes(): Promise<AdminScopeSummary[]> {
    try {
      const response = await this.client.get<AdminScopeSummary[]>('/api/admin/scopes');
      return response.data;
    } catch (error) {
      return this.handleError(error, 'List scopes');
    }
  }

  async createScope(request: AdminScopeUpsertRequest): Promise<AdminScopeSummary> {
    try {
      const response = await this.client.post<AdminScopeSummary>('/api/admin/scopes', request);
      return response.data;
    } catch (error) {
      return this.handleError(error, 'Create scope');
    }
  }

  async deleteScope(scope: string): Promise<void> {
    try {
      await this.client.delete(`/api/admin/scopes/${encodeURIComponent(scope)}`);
    } catch (error) {
      return this.handleError(error, 'Delete scope');
    }
  }

  // Deny Rule Management
  async listDenyRules(): Promise<AdminDenyRuleSummary[]> {
    try {
      const response = await this.client.get<AdminDenyRuleSummary[]>('/api/admin/deny-rules');
      return response.data;
    } catch (error) {
      return this.handleError(error, 'List deny rules');
    }
  }

  async createDenyRule(request: AdminDenyRuleUpsertRequest): Promise<AdminDenyRuleSummary> {
    try {
      const response = await this.client.post<AdminDenyRuleSummary>(
        '/api/admin/deny-rules',
        request
      );
      return response.data;
    } catch (error) {
      return this.handleError(error, 'Create deny rule');
    }
  }

  async updateDenyRule(
    id: number,
    request: AdminDenyRuleUpsertRequest
  ): Promise<AdminDenyRuleSummary> {
    try {
      const response = await this.client.put<AdminDenyRuleSummary>(
        `/api/admin/deny-rules/${id}`,
        request
      );
      return response.data;
    } catch (error) {
      return this.handleError(error, 'Update deny rule');
    }
  }

  async deleteDenyRule(id: number): Promise<void> {
    try {
      await this.client.delete(`/api/admin/deny-rules/${id}`);
    } catch (error) {
      return this.handleError(error, 'Delete deny rule');
    }
  }

  // Audit Events
  async searchAuditEvents(params: AuditSearchParams): Promise<PageResponse<AuditEventSummary>> {
    try {
      const response = await this.client.get<PageResponse<AuditEventSummary>>('/api/audit/search', {
        params,
      });
      return response.data;
    } catch (error) {
      return this.handleError(error, 'Search audit events');
    }
  }

  async getAuditEventsByClient(
    clientId: string,
    page = 0,
    size = 20
  ): Promise<PageResponse<AuditEventSummary>> {
    try {
      const response = await this.client.get<PageResponse<AuditEventSummary>>(
        `/api/audit/client/${encodeURIComponent(clientId)}`,
        { params: { page, size } }
      );
      return response.data;
    } catch (error) {
      return this.handleError(error, 'Get audit events by client');
    }
  }
}
