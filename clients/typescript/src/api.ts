/**
 * API Client Exports
 * 
 * This file re-exports the generated API classes.
 * After running client generation, this will be replaced with actual API exports.
 * 
 * @module api
 */

import { Configuration, ConfigurationParameters } from './configuration';

/**
 * Base API client for making HTTP requests.
 * This is a placeholder until OpenAPI Generator creates the actual client.
 */
export class BaseAPI {
  protected configuration: Configuration;

  constructor(configuration?: Configuration | ConfigurationParameters) {
    if (configuration instanceof Configuration) {
      this.configuration = configuration;
    } else {
      this.configuration = new Configuration(configuration);
    }
  }

  /**
   * Get the base path for API requests.
   */
  get basePath(): string {
    return this.configuration.basePath;
  }
}

/**
 * OAuth2 API client placeholder.
 * Will be replaced by generated code.
 */
export class OAuth2Api extends BaseAPI {
  constructor(configuration?: Configuration | ConfigurationParameters) {
    super(configuration);
  }
}

/**
 * Token API client placeholder.
 * Will be replaced by generated code.
 */
export class TokenApi extends BaseAPI {
  constructor(configuration?: Configuration | ConfigurationParameters) {
    super(configuration);
  }
}

/**
 * UserInfo API client placeholder.
 * Will be replaced by generated code.
 */
export class UserInfoApi extends BaseAPI {
  constructor(configuration?: Configuration | ConfigurationParameters) {
    super(configuration);
  }
}

export { Configuration, ConfigurationParameters } from './configuration';
