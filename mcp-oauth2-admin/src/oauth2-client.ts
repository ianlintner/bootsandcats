import axios, { AxiosInstance, AxiosError } from 'axios';
import { TokenResponse, OAuth2Config } from './types.js';

/**
 * OAuth2 client for authenticating with the authorization server
 * using client credentials grant
 */
export class OAuth2Client {
  private axiosInstance: AxiosInstance;
  private accessToken: string | null = null;
  private tokenExpiry: number = 0;
  private readonly config: OAuth2Config;

  constructor(config: OAuth2Config) {
    this.config = config;
    this.axiosInstance = axios.create({
      baseURL: config.serverUrl,
      headers: {
        'Content-Type': 'application/json',
      },
    });
  }

  /**
   * Get a valid access token, refreshing if necessary
   */
  async getAccessToken(): Promise<string> {
    const now = Date.now() / 1000;
    
    // If token exists and is not expired (with 60 second buffer), return it
    if (this.accessToken && this.tokenExpiry > now + 60) {
      return this.accessToken;
    }

    // Fetch a new token
    await this.fetchToken();
    
    if (!this.accessToken) {
      throw new Error('Failed to obtain access token');
    }
    
    return this.accessToken;
  }

  /**
   * Fetch a new access token using client credentials grant
   */
  private async fetchToken(): Promise<void> {
    try {
      const params = new URLSearchParams();
      params.append('grant_type', 'client_credentials');
      params.append('scope', this.config.scopes.join(' '));

      const response = await axios.post<TokenResponse>(
        `${this.config.serverUrl}/oauth2/token`,
        params,
        {
          headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
          },
          auth: {
            username: this.config.clientId,
            password: this.config.clientSecret,
          },
        }
      );

      this.accessToken = response.data.access_token;
      this.tokenExpiry = Date.now() / 1000 + response.data.expires_in;
    } catch (error) {
      if (axios.isAxiosError(error)) {
        const axiosError = error as AxiosError;
        throw new Error(
          `OAuth2 token request failed: ${axiosError.response?.status} - ${JSON.stringify(axiosError.response?.data)}`
        );
      }
      throw error;
    }
  }

  /**
   * Create an authenticated axios instance for API calls
   */
  async createAuthenticatedClient(): Promise<AxiosInstance> {
    const token = await this.getAccessToken();
    
    return axios.create({
      baseURL: this.config.serverUrl,
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
    });
  }

  /**
   * Reset token (useful for testing or forcing refresh)
   */
  resetToken(): void {
    this.accessToken = null;
    this.tokenExpiry = 0;
  }
}
