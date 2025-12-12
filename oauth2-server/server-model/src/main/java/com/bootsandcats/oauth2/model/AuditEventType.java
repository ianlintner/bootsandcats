package com.bootsandcats.oauth2.model;

/**
 * Enumeration of security audit event types for OAuth2 compliance logging.
 *
 * <p>These event types cover authentication, authorization, token lifecycle, and administrative
 * actions required for security compliance and audit trails.
 */
public enum AuditEventType {

    // Authentication Events
    LOGIN_SUCCESS("AUTHENTICATION", "User login successful"),
    LOGIN_FAILURE("AUTHENTICATION", "User login failed"),
    LOGIN_DENIED("AUTHENTICATION", "User login denied by policy"),
    LOGOUT("AUTHENTICATION", "User logged out"),
    FEDERATED_LOGIN_SUCCESS("AUTHENTICATION", "Federated identity login successful"),
    FEDERATED_LOGIN_FAILURE("AUTHENTICATION", "Federated identity login failed"),
    FEDERATED_LOGIN_DENIED("AUTHENTICATION", "Federated identity login denied by policy"),
    SESSION_CREATED("AUTHENTICATION", "Session created"),
    SESSION_EXPIRED("AUTHENTICATION", "Session expired"),
    SESSION_INVALIDATED("AUTHENTICATION", "Session invalidated"),

    // Authorization Events
    AUTHORIZATION_REQUEST("AUTHORIZATION", "Authorization request initiated"),
    AUTHORIZATION_CODE_ISSUED("AUTHORIZATION", "Authorization code issued"),
    AUTHORIZATION_DENIED("AUTHORIZATION", "Authorization denied by user"),
    AUTHORIZATION_FAILURE("AUTHORIZATION", "Authorization request failed"),
    CONSENT_GRANTED("AUTHORIZATION", "User granted consent"),
    CONSENT_DENIED("AUTHORIZATION", "User denied consent"),

    // Token Events
    TOKEN_ISSUED("TOKEN", "Token issued"),
    ACCESS_TOKEN_ISSUED("TOKEN", "Access token issued"),
    REFRESH_TOKEN_ISSUED("TOKEN", "Refresh token issued"),
    ID_TOKEN_ISSUED("TOKEN", "ID token issued"),
    TOKEN_REFRESHED("TOKEN", "Token refreshed"),
    TOKEN_REVOKED("TOKEN", "Token revoked"),
    TOKEN_INTROSPECTION("TOKEN", "Token introspected"),
    TOKEN_INTROSPECTION_ACTIVE("TOKEN", "Token introspection - active"),
    TOKEN_INTROSPECTION_INACTIVE("TOKEN", "Token introspection - inactive"),
    TOKEN_VALIDATION_SUCCESS("TOKEN", "Token validation successful"),
    TOKEN_VALIDATION_FAILURE("TOKEN", "Token validation failed"),
    TOKEN_EXPIRED("TOKEN", "Token expired"),

    // Client Credentials Flow
    CLIENT_CREDENTIALS_SUCCESS("TOKEN", "Client credentials grant successful"),
    CLIENT_CREDENTIALS_FAILURE("TOKEN", "Client credentials grant failed"),

    // PKCE Events
    PKCE_CHALLENGE_VERIFIED("AUTHORIZATION", "PKCE challenge verified"),
    PKCE_CHALLENGE_FAILED("AUTHORIZATION", "PKCE challenge verification failed"),

    // Device Authorization Flow
    DEVICE_AUTHORIZATION_REQUEST("DEVICE", "Device authorization request"),
    DEVICE_CODE_ISSUED("DEVICE", "Device code issued"),
    DEVICE_CODE_VERIFIED("DEVICE", "Device code verified"),
    DEVICE_CODE_DENIED("DEVICE", "Device code denied"),
    DEVICE_CODE_EXPIRED("DEVICE", "Device code expired"),
    DEVICE_CODE_POLLING("DEVICE", "Device code polling"),

    // Client Management Events
    CLIENT_REGISTERED("CLIENT", "Client registered"),
    CLIENT_UPDATED("CLIENT", "Client updated"),
    CLIENT_DELETED("CLIENT", "Client deleted"),
    CLIENT_SECRET_ROTATED("CLIENT", "Client secret rotated"),
    CLIENT_AUTHENTICATION_SUCCESS("CLIENT", "Client authentication successful"),
    CLIENT_AUTHENTICATION_FAILURE("CLIENT", "Client authentication failed"),

    // User Management Events
    USER_CREATED("USER", "User created"),
    USER_UPDATED("USER", "User updated"),
    USER_DELETED("USER", "User deleted"),
    USER_LOCKED("USER", "User account locked"),
    USER_UNLOCKED("USER", "User account unlocked"),
    PASSWORD_CHANGED("USER", "Password changed"),
    PASSWORD_RESET_REQUESTED("USER", "Password reset requested"),
    PASSWORD_RESET_COMPLETED("USER", "Password reset completed"),

    // Administrative Events
    CONFIGURATION_CHANGED("ADMIN", "Configuration changed"),
    JWK_ROTATED("ADMIN", "JWK rotated"),
    SCOPE_CREATED("ADMIN", "Scope created"),
    SCOPE_DELETED("ADMIN", "Scope deleted"),
    DENY_RULE_CREATED("ADMIN", "Deny rule created"),
    DENY_RULE_UPDATED("ADMIN", "Deny rule updated"),
    DENY_RULE_DELETED("ADMIN", "Deny rule deleted"),

    // Security Events
    SUSPICIOUS_ACTIVITY("SECURITY", "Suspicious activity detected"),
    RATE_LIMIT_EXCEEDED("SECURITY", "Rate limit exceeded"),
    INVALID_REQUEST("SECURITY", "Invalid request detected"),
    UNAUTHORIZED_ACCESS("SECURITY", "Unauthorized access attempt");

    private final String category;
    private final String description;

    AuditEventType(String category, String description) {
        this.category = category;
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }
}
