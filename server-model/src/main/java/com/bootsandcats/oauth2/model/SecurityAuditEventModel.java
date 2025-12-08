package com.bootsandcats.oauth2.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Base security audit event model interface for the OAuth2 server. This represents the core audit
 * event data without JPA dependencies.
 */
public interface SecurityAuditEventModel {

    Long getId();

    void setId(Long id);

    UUID getEventId();

    void setEventId(UUID eventId);

    AuditEventType getEventType();

    void setEventType(AuditEventType eventType);

    String getEventCategory();

    void setEventCategory(String eventCategory);

    Instant getEventTimestamp();

    void setEventTimestamp(Instant eventTimestamp);

    String getPrincipal();

    void setPrincipal(String principal);

    String getPrincipalType();

    void setPrincipalType(String principalType);

    Long getUserId();

    void setUserId(Long userId);

    String getClientId();

    void setClientId(String clientId);

    String getIpAddress();

    void setIpAddress(String ipAddress);

    String getUserAgent();

    void setUserAgent(String userAgent);

    String getRequestUri();

    void setRequestUri(String requestUri);

    String getRequestMethod();

    void setRequestMethod(String requestMethod);

    String getSessionId();

    void setSessionId(String sessionId);

    String getCorrelationId();

    void setCorrelationId(String correlationId);

    AuditEventResult getResult();

    void setResult(AuditEventResult result);

    String getResultCode();

    void setResultCode(String resultCode);

    String getErrorMessage();

    void setErrorMessage(String errorMessage);
}
