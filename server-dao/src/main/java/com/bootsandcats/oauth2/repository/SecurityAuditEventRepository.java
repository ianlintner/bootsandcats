package com.bootsandcats.oauth2.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.bootsandcats.oauth2.model.AuditEventResult;
import com.bootsandcats.oauth2.model.AuditEventType;
import com.bootsandcats.oauth2.model.SecurityAuditEvent;

/**
 * Repository for security audit events.
 *
 * <p>Provides methods to query and persist security audit records for compliance purposes.
 */
@Repository
public interface SecurityAuditEventRepository extends JpaRepository<SecurityAuditEvent, Long> {

    /**
     * Find an audit event by its unique event ID.
     *
     * @param eventId the UUID of the event
     * @return the audit event if found
     */
    Optional<SecurityAuditEvent> findByEventId(UUID eventId);

    /**
     * Find all audit events for a specific principal (user/client).
     *
     * @param principal the principal identifier
     * @param pageable pagination information
     * @return page of audit events
     */
    Page<SecurityAuditEvent> findByPrincipalOrderByEventTimestampDesc(
            String principal, Pageable pageable);

    /**
     * Find all audit events for a specific user ID.
     *
     * @param userId the user ID
     * @param pageable pagination information
     * @return page of audit events
     */
    Page<SecurityAuditEvent> findByUserIdOrderByEventTimestampDesc(Long userId, Pageable pageable);

    /**
     * Find all audit events for a specific client ID.
     *
     * @param clientId the OAuth2 client ID
     * @param pageable pagination information
     * @return page of audit events
     */
    Page<SecurityAuditEvent> findByClientIdOrderByEventTimestampDesc(
            String clientId, Pageable pageable);

    /**
     * Find all audit events of a specific type.
     *
     * @param eventType the event type
     * @param pageable pagination information
     * @return page of audit events
     */
    Page<SecurityAuditEvent> findByEventTypeOrderByEventTimestampDesc(
            AuditEventType eventType, Pageable pageable);

    /**
     * Find all audit events within a specific category.
     *
     * @param eventCategory the event category (e.g., AUTHENTICATION, TOKEN)
     * @param pageable pagination information
     * @return page of audit events
     */
    Page<SecurityAuditEvent> findByEventCategoryOrderByEventTimestampDesc(
            String eventCategory, Pageable pageable);

    /**
     * Find all audit events with a specific result.
     *
     * @param result the event result
     * @param pageable pagination information
     * @return page of audit events
     */
    Page<SecurityAuditEvent> findByResultOrderByEventTimestampDesc(
            AuditEventResult result, Pageable pageable);

    /**
     * Find all audit events within a time range.
     *
     * @param startTime start of the time range
     * @param endTime end of the time range
     * @param pageable pagination information
     * @return page of audit events
     */
    Page<SecurityAuditEvent> findByEventTimestampBetweenOrderByEventTimestampDesc(
            Instant startTime, Instant endTime, Pageable pageable);

    /**
     * Find all audit events for a session.
     *
     * @param sessionId the session ID
     * @param pageable pagination information
     * @return page of audit events
     */
    Page<SecurityAuditEvent> findBySessionIdOrderByEventTimestampDesc(
            String sessionId, Pageable pageable);

    /**
     * Find all audit events from a specific IP address.
     *
     * @param ipAddress the IP address
     * @param pageable pagination information
     * @return page of audit events
     */
    Page<SecurityAuditEvent> findByIpAddressOrderByEventTimestampDesc(
            String ipAddress, Pageable pageable);

    /**
     * Find failed authentication events for a principal within a time window.
     *
     * @param principal the principal identifier
     * @param result the result to filter by
     * @param eventTypes the event types to include
     * @param since the start time
     * @return list of matching audit events
     */
    @Query(
            "SELECT e FROM SecurityAuditEvent e "
                    + "WHERE e.principal = :principal "
                    + "AND e.result = :result "
                    + "AND e.eventType IN :eventTypes "
                    + "AND e.eventTimestamp >= :since "
                    + "ORDER BY e.eventTimestamp DESC")
    List<SecurityAuditEvent> findByPrincipalAndResultAndEventTypeInAndEventTimestampAfter(
            @Param("principal") String principal,
            @Param("result") AuditEventResult result,
            @Param("eventTypes") List<AuditEventType> eventTypes,
            @Param("since") Instant since);

    /**
     * Find failed login attempts from an IP address within a time window.
     *
     * @param ipAddress the IP address
     * @param result the result to filter by
     * @param eventTypes the event types to include
     * @param since the start time
     * @return list of matching audit events
     */
    @Query(
            "SELECT e FROM SecurityAuditEvent e "
                    + "WHERE e.ipAddress = :ipAddress "
                    + "AND e.result = :result "
                    + "AND e.eventType IN :eventTypes "
                    + "AND e.eventTimestamp >= :since "
                    + "ORDER BY e.eventTimestamp DESC")
    List<SecurityAuditEvent> findByIpAddressAndResultAndEventTypeInAndEventTimestampAfter(
            @Param("ipAddress") String ipAddress,
            @Param("result") AuditEventResult result,
            @Param("eventTypes") List<AuditEventType> eventTypes,
            @Param("since") Instant since);

    /**
     * Count events by type within a time range.
     *
     * @param eventType the event type
     * @param startTime start of the time range
     * @param endTime end of the time range
     * @return count of matching events
     */
    @Query(
            "SELECT COUNT(e) FROM SecurityAuditEvent e "
                    + "WHERE e.eventType = :eventType "
                    + "AND e.eventTimestamp BETWEEN :startTime AND :endTime")
    long countByEventTypeAndTimestampBetween(
            @Param("eventType") AuditEventType eventType,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime);

    /**
     * Count failed events by category within a time range.
     *
     * @param category the event category
     * @param result the result to filter by
     * @param startTime start of the time range
     * @param endTime end of the time range
     * @return count of matching events
     */
    @Query(
            "SELECT COUNT(e) FROM SecurityAuditEvent e "
                    + "WHERE e.eventCategory = :category "
                    + "AND e.result = :result "
                    + "AND e.eventTimestamp BETWEEN :startTime AND :endTime")
    long countByCategoryAndResultAndTimestampBetween(
            @Param("category") String category,
            @Param("result") AuditEventResult result,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime);

    /**
     * Find recent audit events (for dashboard/monitoring).
     *
     * @param pageable pagination information
     * @return page of recent audit events
     */
    Page<SecurityAuditEvent> findAllByOrderByEventTimestampDesc(Pageable pageable);

    /**
     * Complex search query for audit events.
     *
     * @param principal optional principal filter
     * @param clientId optional client ID filter
     * @param eventCategory optional category filter
     * @param result optional result filter
     * @param startTime start of time range
     * @param endTime end of time range
     * @param pageable pagination information
     * @return page of matching audit events
     */
    @Query(
            "SELECT e FROM SecurityAuditEvent e "
                    + "WHERE (:principal IS NULL OR e.principal = :principal) "
                    + "AND (:clientId IS NULL OR e.clientId = :clientId) "
                    + "AND (:eventCategory IS NULL OR e.eventCategory = :eventCategory) "
                    + "AND (:result IS NULL OR e.result = :result) "
                    + "AND e.eventTimestamp BETWEEN :startTime AND :endTime "
                    + "ORDER BY e.eventTimestamp DESC")
    Page<SecurityAuditEvent> searchAuditEvents(
            @Param("principal") String principal,
            @Param("clientId") String clientId,
            @Param("eventCategory") String eventCategory,
            @Param("result") AuditEventResult result,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime,
            Pageable pageable);
}
