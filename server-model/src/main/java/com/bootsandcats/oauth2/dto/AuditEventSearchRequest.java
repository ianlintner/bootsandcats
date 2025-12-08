package com.bootsandcats.oauth2.dto;

import java.time.Instant;

import com.bootsandcats.oauth2.model.AuditEventResult;

/**
 * DTO for audit event search request parameters.
 *
 * <p>Used for programmatic search requests (e.g., from API clients).
 */
public record AuditEventSearchRequest(
        String principal,
        String clientId,
        String eventCategory,
        AuditEventResult result,
        Instant startTime,
        Instant endTime,
        int page,
        int size) {

    public AuditEventSearchRequest {
        if (page < 0) {
            page = 0;
        }
        if (size <= 0) {
            size = 20;
        }
        if (size > 100) {
            size = 100;
        }
    }
}
