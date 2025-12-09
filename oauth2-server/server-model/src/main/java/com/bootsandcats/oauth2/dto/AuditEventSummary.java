package com.bootsandcats.oauth2.dto;

import java.time.Instant;
import java.util.UUID;

import com.bootsandcats.oauth2.model.AuditEventResult;
import com.bootsandcats.oauth2.model.AuditEventType;

/**
 * DTO for audit event summary information.
 *
 * <p>Provides a simplified view of security audit events for API responses.
 */
public record AuditEventSummary(
        UUID eventId,
        AuditEventType eventType,
        String eventCategory,
        Instant eventTimestamp,
        String principal,
        String principalType,
        String clientId,
        String ipAddress,
        AuditEventResult result,
        String resultCode,
        String errorMessage,
        String grantType,
        String scopes,
        String tokenType) {}
