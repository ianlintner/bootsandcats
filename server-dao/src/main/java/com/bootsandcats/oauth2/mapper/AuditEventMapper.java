package com.bootsandcats.oauth2.mapper;

import com.bootsandcats.oauth2.dto.AuditEventSummary;
import com.bootsandcats.oauth2.model.SecurityAuditEvent;

/**
 * Mapper utility for converting entities to DTOs.
 */
public final class AuditEventMapper {

    private AuditEventMapper() {
        // Utility class
    }

    /**
     * Create an AuditEventSummary from a SecurityAuditEvent entity.
     *
     * @param event the source audit event
     * @return the summary DTO
     */
    public static AuditEventSummary toSummary(SecurityAuditEvent event) {
        return new AuditEventSummary(
                event.getEventId(),
                event.getEventType(),
                event.getEventCategory(),
                event.getEventTimestamp(),
                event.getPrincipal(),
                event.getPrincipalType(),
                event.getClientId(),
                event.getIpAddress(),
                event.getResult(),
                event.getResultCode(),
                event.getErrorMessage(),
                event.getGrantType(),
                event.getScopes(),
                event.getTokenType());
    }
}
