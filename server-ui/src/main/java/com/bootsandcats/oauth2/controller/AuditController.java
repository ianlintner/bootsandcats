package com.bootsandcats.oauth2.controller;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bootsandcats.oauth2.dto.AuditEventSummary;
import com.bootsandcats.oauth2.model.AuditEventResult;
import com.bootsandcats.oauth2.model.SecurityAuditEvent;
import com.bootsandcats.oauth2.service.SecurityAuditService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST controller for querying security audit events.
 *
 * <p>Provides endpoints for compliance officers and administrators to search and view audit
 * records.
 */
@RestController
@RequestMapping("/api/audit")
@Tag(name = "Audit", description = "Security audit event management")
public class AuditController {

    private final SecurityAuditService securityAuditService;

    public AuditController(SecurityAuditService securityAuditService) {
        this.securityAuditService = securityAuditService;
    }

    /**
     * Get recent audit events.
     *
     * @param page page number (0-based)
     * @param size page size
     * @return page of recent audit events
     */
    @GetMapping("/events")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Get recent audit events",
            description = "Returns a paginated list of recent security audit events")
    @ApiResponse(responseCode = "200", description = "Audit events retrieved successfully")
    @ApiResponse(responseCode = "403", description = "Access denied - requires ADMIN role")
    public ResponseEntity<Page<AuditEventSummary>> getRecentEvents(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0")
                    int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<SecurityAuditEvent> events = securityAuditService.findRecentEvents(pageable);
        return ResponseEntity.ok(events.map(AuditEventSummary::from));
    }

    /**
     * Search audit events with filters.
     *
     * @param principal filter by principal
     * @param clientId filter by client ID
     * @param category filter by event category
     * @param result filter by result
     * @param startTime start of time range
     * @param endTime end of time range
     * @param page page number
     * @param size page size
     * @return page of matching audit events
     */
    @GetMapping("/events/search")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Search audit events",
            description = "Search security audit events with optional filters")
    @ApiResponse(responseCode = "200", description = "Search completed successfully")
    @ApiResponse(responseCode = "403", description = "Access denied - requires ADMIN role")
    public ResponseEntity<Page<AuditEventSummary>> searchEvents(
            @Parameter(description = "Filter by principal (username/client)")
                    @RequestParam(required = false)
                    String principal,
            @Parameter(description = "Filter by OAuth2 client ID") @RequestParam(required = false)
                    String clientId,
            @Parameter(description = "Filter by event category (AUTHENTICATION, TOKEN, etc.)")
                    @RequestParam(required = false)
                    String category,
            @Parameter(description = "Filter by result (SUCCESS, FAILURE, DENIED)")
                    @RequestParam(required = false)
                    AuditEventResult result,
            @Parameter(description = "Start of time range (ISO-8601)")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant startTime,
            @Parameter(description = "End of time range (ISO-8601)")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant endTime,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0")
                    int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {

        // Default time range to last 30 days if not specified
        if (startTime == null) {
            startTime = Instant.now().minus(30, ChronoUnit.DAYS);
        }
        if (endTime == null) {
            endTime = Instant.now();
        }

        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<SecurityAuditEvent> events =
                securityAuditService.searchAuditEvents(
                        principal, clientId, category, result, startTime, endTime, pageable);
        return ResponseEntity.ok(events.map(AuditEventSummary::from));
    }

    /**
     * Get audit events for a specific principal.
     *
     * @param principal the principal to search for
     * @param page page number
     * @param size page size
     * @return page of audit events for the principal
     */
    @GetMapping("/events/principal/{principal}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Get audit events by principal",
            description = "Returns audit events for a specific user or client")
    public ResponseEntity<Page<AuditEventSummary>> getEventsByPrincipal(
            @PathVariable String principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<SecurityAuditEvent> events = securityAuditService.findByPrincipal(principal, pageable);
        return ResponseEntity.ok(events.map(AuditEventSummary::from));
    }

    /**
     * Get audit events for a specific OAuth2 client.
     *
     * @param clientId the client ID to search for
     * @param page page number
     * @param size page size
     * @return page of audit events for the client
     */
    @GetMapping("/events/client/{clientId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Get audit events by client",
            description = "Returns audit events for a specific OAuth2 client")
    public ResponseEntity<Page<AuditEventSummary>> getEventsByClient(
            @PathVariable String clientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<SecurityAuditEvent> events = securityAuditService.findByClientId(clientId, pageable);
        return ResponseEntity.ok(events.map(AuditEventSummary::from));
    }
}
