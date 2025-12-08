package com.bootsandcats.oauth2.model;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Entity representing a security audit event for OAuth2 compliance logging.
 *
 * <p>This entity captures authentication, authorization, token lifecycle, and other
 * security-related events for compliance and audit purposes. All fields are designed to support
 * comprehensive security auditing requirements.
 */
@Entity
@Table(
        name = "security_audit_events",
        indexes = {
            @Index(name = "idx_audit_event_type", columnList = "event_type"),
            @Index(name = "idx_audit_event_category", columnList = "event_category"),
            @Index(name = "idx_audit_event_timestamp", columnList = "event_timestamp"),
            @Index(name = "idx_audit_principal", columnList = "principal"),
            @Index(name = "idx_audit_client_id", columnList = "client_id"),
            @Index(name = "idx_audit_user_id", columnList = "user_id"),
            @Index(name = "idx_audit_result", columnList = "result"),
            @Index(name = "idx_audit_ip_address", columnList = "ip_address"),
            @Index(name = "idx_audit_session_id", columnList = "session_id")
        })
public class SecurityAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, updatable = false)
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 100)
    private AuditEventType eventType;

    @Column(name = "event_category", nullable = false, length = 50)
    private String eventCategory;

    @Column(name = "event_timestamp", nullable = false)
    private Instant eventTimestamp;

    // Actor information
    @Column(name = "principal", length = 255)
    private String principal;

    @Column(name = "principal_type", length = 50)
    private String principalType;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "client_id", length = 100)
    private String clientId;

    // Request context
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "request_uri", columnDefinition = "TEXT")
    private String requestUri;

    @Column(name = "request_method", length = 10)
    private String requestMethod;

    @Column(name = "session_id", length = 255)
    private String sessionId;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    // Event result
    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 20)
    private AuditEventResult result;

    @Column(name = "result_code", length = 50)
    private String resultCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    // OAuth2 specific fields
    @Column(name = "grant_type", length = 50)
    private String grantType;

    @Column(name = "scopes", columnDefinition = "TEXT")
    private String scopes;

    @Column(name = "token_type", length = 50)
    private String tokenType;

    @Column(name = "authorization_code_id", length = 255)
    private String authorizationCodeId;

    // Additional metadata as JSON
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", columnDefinition = "jsonb")
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (eventId == null) {
            eventId = UUID.randomUUID();
        }
        if (eventTimestamp == null) {
            eventTimestamp = Instant.now();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (eventCategory == null && eventType != null) {
            eventCategory = eventType.getCategory();
        }
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public AuditEventType getEventType() {
        return eventType;
    }

    public void setEventType(AuditEventType eventType) {
        this.eventType = eventType;
        if (eventType != null) {
            this.eventCategory = eventType.getCategory();
        }
    }

    public String getEventCategory() {
        return eventCategory;
    }

    public void setEventCategory(String eventCategory) {
        this.eventCategory = eventCategory;
    }

    public Instant getEventTimestamp() {
        return eventTimestamp;
    }

    public void setEventTimestamp(Instant eventTimestamp) {
        this.eventTimestamp = eventTimestamp;
    }

    public String getPrincipal() {
        return principal;
    }

    public void setPrincipal(String principal) {
        this.principal = principal;
    }

    public String getPrincipalType() {
        return principalType;
    }

    public void setPrincipalType(String principalType) {
        this.principalType = principalType;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getRequestUri() {
        return requestUri;
    }

    public void setRequestUri(String requestUri) {
        this.requestUri = requestUri;
    }

    public String getRequestMethod() {
        return requestMethod;
    }

    public void setRequestMethod(String requestMethod) {
        this.requestMethod = requestMethod;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public AuditEventResult getResult() {
        return result;
    }

    public void setResult(AuditEventResult result) {
        this.result = result;
    }

    public String getResultCode() {
        return resultCode;
    }

    public void setResultCode(String resultCode) {
        this.resultCode = resultCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getGrantType() {
        return grantType;
    }

    public void setGrantType(String grantType) {
        this.grantType = grantType;
    }

    public String getScopes() {
        return scopes;
    }

    public void setScopes(String scopes) {
        this.scopes = scopes;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public String getAuthorizationCodeId() {
        return authorizationCodeId;
    }

    public void setAuthorizationCodeId(String authorizationCodeId) {
        this.authorizationCodeId = authorizationCodeId;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "SecurityAuditEvent{"
                + "id="
                + id
                + ", eventId="
                + eventId
                + ", eventType="
                + eventType
                + ", principal='"
                + principal
                + '\''
                + ", result="
                + result
                + ", eventTimestamp="
                + eventTimestamp
                + '}';
    }
}
