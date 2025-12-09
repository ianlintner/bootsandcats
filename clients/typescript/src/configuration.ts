/**
 * API Configuration
 * 
 * This file contains configuration classes for the API client.
 * 
 * @module configuration
 */

/**
 * Configuration parameters for the API client.
 */
export interface ConfigurationParameters {
  /**
   * Base path for API requests.
   * @default 'http://localhost:9000'
   */
  basePath?: string;

  /**
   * Bearer access token for authentication.
   */
  accessToken?: string | (() => string) | (() => Promise<string>);

  /**
   * Username for basic authentication.
   */
  username?: string;

  /**
   * Password for basic authentication.
   */
  password?: string;

  /**
   * API key for authentication.
   * Can be a string or a function that returns a string.
   */
  apiKey?: string | ((name: string) => string) | ((name: string) => Promise<string>);

  /**
   * Custom headers to include in all requests.
   */
  headers?: Record<string, string>;

  /**
   * Custom credentials mode for fetch requests.
   */
  credentials?: RequestCredentials;
}

/**
 * Configuration class for API clients.
 */
export class Configuration {
  /**
   * Base path for API requests.
   */
  basePath: string;

  /**
   * Bearer access token for authentication.
   */
  accessToken?: string | (() => string) | (() => Promise<string>);

  /**
   * Username for basic authentication.
   */
  username?: string;

  /**
   * Password for basic authentication.
   */
  password?: string;

  /**
   * API key for authentication.
   */
  apiKey?: string | ((name: string) => string) | ((name: string) => Promise<string>);

  /**
   * Custom headers to include in all requests.
   */
  headers?: Record<string, string>;

  /**
   * Custom credentials mode for fetch requests.
   */
  credentials?: RequestCredentials;

  constructor(params: ConfigurationParameters = {}) {
    this.basePath = params.basePath ?? 'http://localhost:9000';
    this.accessToken = params.accessToken;
    this.username = params.username;
    this.password = params.password;
    this.apiKey = params.apiKey;
    this.headers = params.headers;
    this.credentials = params.credentials;
  }

  /**
   * Get the access token value.
   * Handles both string and function-based token providers.
   */
  async getAccessToken(): Promise<string | undefined> {
    if (typeof this.accessToken === 'function') {
      return await this.accessToken();
    }
    return this.accessToken;
  }

  /**
   * Get the API key value.
   * Handles both string and function-based key providers.
   */
  async getApiKey(name: string): Promise<string | undefined> {
    if (typeof this.apiKey === 'function') {
      return await this.apiKey(name);
    }
    return this.apiKey;
  }

  /**
   * Check if basic authentication is configured.
   */
  isBasicAuthConfigured(): boolean {
    return !!this.username && !!this.password;
  }

  /**
   * Get the basic auth header value.
   */
  getBasicAuthHeader(): string | undefined {
    if (!this.isBasicAuthConfigured()) {
      return undefined;
    }
    const credentials = `${this.username}:${this.password}`;
    return `Basic ${Buffer.from(credentials).toString('base64')}`;
  }
}
