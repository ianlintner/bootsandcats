/**
 * TypeScript type definitions for OAuth2 Admin API
 */

// Client Management Types
export interface AdminClientSummary {
  clientId: string;
  clientName: string;
  enabled: boolean;
  system: boolean;
  scopes: string[];
  authorizationGrantTypes: string[];
  clientAuthenticationMethods: string[];
  redirectUris: string[];
  postLogoutRedirectUris: string[];
  requireProofKey: boolean;
  requireAuthorizationConsent: boolean;
  notes: string | null;
}

export interface AdminClientUpsertRequest {
  clientId: string;
  clientName?: string;
  clientSecret?: string;
  authorizationGrantTypes: string[];
  clientAuthenticationMethods: string[];
  redirectUris: string[];
  postLogoutRedirectUris: string[];
  scopes: string[];
  requireProofKey: boolean;
  requireAuthorizationConsent: boolean;
  enabled: boolean;
  notes?: string;
}

// Scope Management Types
export interface AdminScopeSummary {
  scope: string;
  description: string | null;
  system: boolean;
}

export interface AdminScopeUpsertRequest {
  scope: string;
  description?: string;
}

// Deny Rule Types
export interface AdminDenyRuleSummary {
  id: number;
  pattern: string;
  ruleType: 'USERNAME' | 'EMAIL' | 'IP_ADDRESS' | 'CLIENT_ID';
  enabled: boolean;
  reason: string | null;
}

export interface AdminDenyRuleUpsertRequest {
  pattern: string;
  ruleType: 'USERNAME' | 'EMAIL' | 'IP_ADDRESS' | 'CLIENT_ID';
  enabled: boolean;
  reason?: string;
}

// Audit Event Types
export interface AuditEventSummary {
  id: number;
  timestamp: string;
  eventType: string;
  principal: string;
  clientId: string | null;
  resourceId: string | null;
  outcome: 'SUCCESS' | 'FAILURE';
  details: string | null;
  ipAddress: string | null;
  userAgent: string | null;
}

export interface AuditSearchParams {
  principal?: string;
  clientId?: string;
  eventType?: string;
  outcome?: 'SUCCESS' | 'FAILURE';
  startTime?: string;
  endTime?: string;
  page?: number;
  size?: number;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

// OAuth2 Token Types
export interface TokenResponse {
  access_token: string;
  token_type: string;
  expires_in: number;
  scope?: string;
}

export interface OAuth2Config {
  serverUrl: string;
  clientId: string;
  clientSecret: string;
  scopes: string[];
}
