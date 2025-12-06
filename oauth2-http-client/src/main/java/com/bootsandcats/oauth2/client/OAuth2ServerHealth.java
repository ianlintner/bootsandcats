package com.bootsandcats.oauth2.client;

import java.util.Map;

/**
 * Simplified representation of the authorization server's health endpoint response.
 */
public record OAuth2ServerHealth(String status, Map<String, Object> details) {}
