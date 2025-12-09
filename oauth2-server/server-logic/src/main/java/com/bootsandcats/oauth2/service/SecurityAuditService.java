package com.bootsandcats.oauth2.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bootsandcats.oauth2.model.AuditEventResult;
import com.bootsandcats.oauth2.model.AuditEventType;
import com.bootsandcats.oauth2.model.SecurityAuditEvent;
import com.bootsandcats.oauth2.repository.SecurityAuditEventRepository;
import com.bootsandcats.oauth2.events.AuthEventPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Service for recording and querying security audit events.
 *
 * <p>This service provides methods to create, store, and retrieve security audit events for OAuth2
 * compliance logging. Events are recorded asynchronously to minimize impact on request processing.
 */
@Service
public class SecurityAuditService {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditService.class);

    private final SecurityAuditEventRepository auditEventRepository;
    private final ObjectMapper objectMapper;
    private final AuthEventPublisher authEventPublisher;

    public SecurityAuditService(
            SecurityAuditEventRepository auditEventRepository,
            ObjectMapper objectMapper,
            AuthEventPublisher authEventPublisher) {
        this.auditEventRepository = auditEventRepository;
        this.objectMapper = objectMapper;
        this.authEventPublisher = authEventPublisher;
    }

    /**
     * Record a security audit event asynchronously.
     *
     * @param event the audit event to record
     */
    @Async("auditTaskExecutor")
    @Transactional
    public void recordEventAsync(SecurityAuditEvent event) {
        SecurityAuditEvent saved = persistEvent(event);
        publishEvent(saved);
    }

    /**
     * Record a security audit event synchronously.
     *
     * @param event the audit event to record
     * @return the saved audit event
     */
    @Transactional
    public SecurityAuditEvent recordEvent(SecurityAuditEvent event) {
        SecurityAuditEvent saved = persistEvent(event);
        publishEvent(saved);
        return saved;
    }

    private SecurityAuditEvent persistEvent(SecurityAuditEvent event) {
        try {
            SecurityAuditEvent saved = auditEventRepository.save(event);
            log.debug(
                    "Recorded audit event: type={}, principal={}, result={}",
                    event.getEventType(),
                    event.getPrincipal(),
                    event.getResult());
            return saved;
        } catch (Exception e) {
            log.error("Failed to record audit event: {}", event, e);
            throw e;
        }
    }

    private void publishEvent(SecurityAuditEvent event) {
        try {
            authEventPublisher.publish(event);
        } catch (Exception e) {
            // Publishing failures should not interrupt auth flows
            log.warn(
                    "Auth event publishing failed for event {} of type {}",
                    event.getEventId(),
                    event.getEventType(),
                    e);
        }
    }

    /**
     * Create and record a login success event.
     *
     * @param principal the authenticated user/client
     * @param request the HTTP request
     * @param userId optional user ID
     * @param additionalDetails additional event details
     */
    public void recordLoginSuccess(
            String principal,
            HttpServletRequest request,
            Long userId,
            Map<String, Object> additionalDetails) {
        SecurityAuditEvent event =
                createBaseEvent(AuditEventType.LOGIN_SUCCESS, AuditEventResult.SUCCESS, request);
        event.setPrincipal(principal);
        event.setPrincipalType("USER");
        event.setUserId(userId);
        event.setDetails(toJson(additionalDetails));
        recordEventAsync(event);
    }

    /**
     * Create and record a login failure event.
     *
     * @param principal the attempted user/client
     * @param request the HTTP request
     * @param errorMessage the failure reason
     * @param additionalDetails additional event details
     */
    public void recordLoginFailure(
            String principal,
            HttpServletRequest request,
            String errorMessage,
            Map<String, Object> additionalDetails) {
        SecurityAuditEvent event =
                createBaseEvent(AuditEventType.LOGIN_FAILURE, AuditEventResult.FAILURE, request);
        event.setPrincipal(principal);
        event.setPrincipalType("USER");
        event.setErrorMessage(errorMessage);
        event.setDetails(toJson(additionalDetails));
        recordEventAsync(event);
    }

    /**
     * Create and record a federated login success event.
     *
     * @param principal the authenticated user
     * @param provider the identity provider (e.g., github, google)
     * @param request the HTTP request
     * @param userId optional user ID
     * @param additionalDetails additional event details
     */
    public void recordFederatedLoginSuccess(
            String principal,
            String provider,
            HttpServletRequest request,
            Long userId,
            Map<String, Object> additionalDetails) {
        SecurityAuditEvent event =
                createBaseEvent(
                        AuditEventType.FEDERATED_LOGIN_SUCCESS, AuditEventResult.SUCCESS, request);
        event.setPrincipal(principal);
        event.setPrincipalType("FEDERATED_USER");
        event.setUserId(userId);

        Map<String, Object> details =
                additionalDetails != null ? new HashMap<>(additionalDetails) : new HashMap<>();
        details.put("provider", provider);
        event.setDetails(toJson(details));
        recordEventAsync(event);
    }

    /**
     * Create and record an authorization request event.
     *
     * @param principal the requesting user/client
     * @param clientId the OAuth2 client ID
     * @param request the HTTP request
     * @param scopes the requested scopes
     * @param additionalDetails additional event details
     */
    public void recordAuthorizationRequest(
            String principal,
            String clientId,
            HttpServletRequest request,
            String scopes,
            Map<String, Object> additionalDetails) {
        SecurityAuditEvent event =
                createBaseEvent(
                        AuditEventType.AUTHORIZATION_REQUEST, AuditEventResult.SUCCESS, request);
        event.setPrincipal(principal);
        event.setClientId(clientId);
        event.setScopes(scopes);
        event.setDetails(toJson(additionalDetails));
        recordEventAsync(event);
    }

    /**
     * Create and record a token issuance event.
     *
     * @param principal the token recipient
     * @param clientId the OAuth2 client ID
     * @param request the HTTP request (may be null for non-HTTP contexts)
     * @param grantType the OAuth2 grant type
     * @param tokenType the type of token issued
     * @param scopes the granted scopes
     * @param additionalDetails additional event details
     */
    public void recordTokenIssued(
            String principal,
            String clientId,
            HttpServletRequest request,
            String grantType,
            String tokenType,
            String scopes,
            Map<String, Object> additionalDetails) {
        SecurityAuditEvent event =
                createBaseEvent(AuditEventType.TOKEN_ISSUED, AuditEventResult.SUCCESS, request);
        event.setPrincipal(principal);
        event.setClientId(clientId);
        event.setGrantType(grantType);
        event.setTokenType(tokenType);
        event.setScopes(scopes);
        event.setDetails(toJson(additionalDetails));
        recordEventAsync(event);
    }

    /**
     * Create and record an access token issuance event.
     *
     * @param principal the token recipient
     * @param clientId the OAuth2 client ID
     * @param request the HTTP request
     * @param grantType the OAuth2 grant type
     * @param scopes the granted scopes
     * @param additionalDetails additional event details
     */
    public void recordAccessTokenIssued(
            String principal,
            String clientId,
            HttpServletRequest request,
            String grantType,
            String scopes,
            Map<String, Object> additionalDetails) {
        SecurityAuditEvent event =
                createBaseEvent(
                        AuditEventType.ACCESS_TOKEN_ISSUED, AuditEventResult.SUCCESS, request);
        event.setPrincipal(principal);
        event.setClientId(clientId);
        event.setGrantType(grantType);
        event.setTokenType("ACCESS_TOKEN");
        event.setScopes(scopes);
        event.setDetails(toJson(additionalDetails));
        recordEventAsync(event);
    }

    /**
     * Create and record a token refresh event.
     *
     * @param principal the token recipient
     * @param clientId the OAuth2 client ID
     * @param request the HTTP request
     * @param scopes the granted scopes
     * @param additionalDetails additional event details
     */
    public void recordTokenRefreshed(
            String principal,
            String clientId,
            HttpServletRequest request,
            String scopes,
            Map<String, Object> additionalDetails) {
        SecurityAuditEvent event =
                createBaseEvent(AuditEventType.TOKEN_REFRESHED, AuditEventResult.SUCCESS, request);
        event.setPrincipal(principal);
        event.setClientId(clientId);
        event.setGrantType("refresh_token");
        event.setScopes(scopes);
        event.setDetails(toJson(additionalDetails));
        recordEventAsync(event);
    }

    /**
     * Create and record a token revocation event.
     *
     * @param principal the user/client revoking
     * @param clientId the OAuth2 client ID
     * @param request the HTTP request
     * @param tokenType the type of token revoked
     * @param additionalDetails additional event details
     */
    public void recordTokenRevoked(
            String principal,
            String clientId,
            HttpServletRequest request,
            String tokenType,
            Map<String, Object> additionalDetails) {
        SecurityAuditEvent event =
                createBaseEvent(AuditEventType.TOKEN_REVOKED, AuditEventResult.SUCCESS, request);
        event.setPrincipal(principal);
        event.setClientId(clientId);
        event.setTokenType(tokenType);
        event.setDetails(toJson(additionalDetails));
        recordEventAsync(event);
    }

    /**
     * Create and record a token introspection event.
     *
     * @param clientId the inspecting client ID
     * @param request the HTTP request
     * @param tokenActive whether the token was active
     * @param additionalDetails additional event details
     */
    public void recordTokenIntrospection(
            String clientId,
            HttpServletRequest request,
            boolean tokenActive,
            Map<String, Object> additionalDetails) {
        AuditEventType eventType =
                tokenActive
                        ? AuditEventType.TOKEN_INTROSPECTION_ACTIVE
                        : AuditEventType.TOKEN_INTROSPECTION_INACTIVE;
        SecurityAuditEvent event = createBaseEvent(eventType, AuditEventResult.SUCCESS, request);
        event.setClientId(clientId);
        event.setDetails(toJson(additionalDetails));
        recordEventAsync(event);
    }

    /**
     * Create and record an authorization code issuance event.
     *
     * @param principal the authenticated user
     * @param clientId the OAuth2 client ID
     * @param request the HTTP request
     * @param scopes the granted scopes
     * @param authorizationCodeId identifier for the auth code
     * @param additionalDetails additional event details
     */
    public void recordAuthorizationCodeIssued(
            String principal,
            String clientId,
            HttpServletRequest request,
            String scopes,
            String authorizationCodeId,
            Map<String, Object> additionalDetails) {
        SecurityAuditEvent event =
                createBaseEvent(
                        AuditEventType.AUTHORIZATION_CODE_ISSUED,
                        AuditEventResult.SUCCESS,
                        request);
        event.setPrincipal(principal);
        event.setClientId(clientId);
        event.setScopes(scopes);
        event.setAuthorizationCodeId(authorizationCodeId);
        event.setGrantType("authorization_code");
        event.setDetails(toJson(additionalDetails));
        recordEventAsync(event);
    }

    /**
     * Create and record a client credentials grant success event.
     *
     * @param clientId the OAuth2 client ID
     * @param request the HTTP request
     * @param scopes the granted scopes
     * @param additionalDetails additional event details
     */
    public void recordClientCredentialsSuccess(
            String clientId,
            HttpServletRequest request,
            String scopes,
            Map<String, Object> additionalDetails) {
        SecurityAuditEvent event =
                createBaseEvent(
                        AuditEventType.CLIENT_CREDENTIALS_SUCCESS,
                        AuditEventResult.SUCCESS,
                        request);
        event.setPrincipal(clientId);
        event.setPrincipalType("CLIENT");
        event.setClientId(clientId);
        event.setGrantType("client_credentials");
        event.setScopes(scopes);
        event.setDetails(toJson(additionalDetails));
        recordEventAsync(event);
    }

    /**
     * Create and record a client credentials grant failure event.
     *
     * @param clientId the OAuth2 client ID
     * @param request the HTTP request
     * @param errorMessage the failure reason
     * @param additionalDetails additional event details
     */
    public void recordClientCredentialsFailure(
            String clientId,
            HttpServletRequest request,
            String errorMessage,
            Map<String, Object> additionalDetails) {
        SecurityAuditEvent event =
                createBaseEvent(
                        AuditEventType.CLIENT_CREDENTIALS_FAILURE,
                        AuditEventResult.FAILURE,
                        request);
        event.setPrincipal(clientId);
        event.setPrincipalType("CLIENT");
        event.setClientId(clientId);
        event.setGrantType("client_credentials");
        event.setErrorMessage(errorMessage);
        event.setDetails(toJson(additionalDetails));
        recordEventAsync(event);
    }

    /**
     * Create and record a client authentication success event.
     *
     * @param clientId the OAuth2 client ID
     * @param request the HTTP request
     * @param authMethod the authentication method used
     * @param additionalDetails additional event details
     */
    public void recordClientAuthenticationSuccess(
            String clientId,
            HttpServletRequest request,
            String authMethod,
            Map<String, Object> additionalDetails) {
        SecurityAuditEvent event =
                createBaseEvent(
                        AuditEventType.CLIENT_AUTHENTICATION_SUCCESS,
                        AuditEventResult.SUCCESS,
                        request);
        event.setPrincipal(clientId);
        event.setPrincipalType("CLIENT");
        event.setClientId(clientId);

        Map<String, Object> details =
                additionalDetails != null ? new HashMap<>(additionalDetails) : new HashMap<>();
        details.put("authenticationMethod", authMethod);
        event.setDetails(toJson(details));
        recordEventAsync(event);
    }

    /**
     * Create and record a client authentication failure event.
     *
     * @param clientId the OAuth2 client ID
     * @param request the HTTP request
     * @param errorMessage the failure reason
     * @param additionalDetails additional event details
     */
    public void recordClientAuthenticationFailure(
            String clientId,
            HttpServletRequest request,
            String errorMessage,
            Map<String, Object> additionalDetails) {
        SecurityAuditEvent event =
                createBaseEvent(
                        AuditEventType.CLIENT_AUTHENTICATION_FAILURE,
                        AuditEventResult.FAILURE,
                        request);
        event.setPrincipal(clientId);
        event.setPrincipalType("CLIENT");
        event.setClientId(clientId);
        event.setErrorMessage(errorMessage);
        event.setDetails(toJson(additionalDetails));
        recordEventAsync(event);
    }

    /**
     * Create and record a consent granted event.
     *
     * @param principal the user granting consent
     * @param clientId the OAuth2 client ID
     * @param request the HTTP request
     * @param scopes the consented scopes
     * @param additionalDetails additional event details
     */
    public void recordConsentGranted(
            String principal,
            String clientId,
            HttpServletRequest request,
            String scopes,
            Map<String, Object> additionalDetails) {
        SecurityAuditEvent event =
                createBaseEvent(AuditEventType.CONSENT_GRANTED, AuditEventResult.SUCCESS, request);
        event.setPrincipal(principal);
        event.setPrincipalType("USER");
        event.setClientId(clientId);
        event.setScopes(scopes);
        event.setDetails(toJson(additionalDetails));
        recordEventAsync(event);
    }

    /**
     * Create and record a consent denied event.
     *
     * @param principal the user denying consent
     * @param clientId the OAuth2 client ID
     * @param request the HTTP request
     * @param scopes the requested scopes
     * @param additionalDetails additional event details
     */
    public void recordConsentDenied(
            String principal,
            String clientId,
            HttpServletRequest request,
            String scopes,
            Map<String, Object> additionalDetails) {
        SecurityAuditEvent event =
                createBaseEvent(AuditEventType.CONSENT_DENIED, AuditEventResult.DENIED, request);
        event.setPrincipal(principal);
        event.setPrincipalType("USER");
        event.setClientId(clientId);
        event.setScopes(scopes);
        event.setDetails(toJson(additionalDetails));
        recordEventAsync(event);
    }

    /**
     * Create and record a logout event.
     *
     * @param principal the user logging out
     * @param request the HTTP request
     * @param additionalDetails additional event details
     */
    public void recordLogout(
            String principal, HttpServletRequest request, Map<String, Object> additionalDetails) {
        SecurityAuditEvent event =
                createBaseEvent(AuditEventType.LOGOUT, AuditEventResult.SUCCESS, request);
        event.setPrincipal(principal);
        event.setPrincipalType("USER");
        event.setDetails(toJson(additionalDetails));
        recordEventAsync(event);
    }

    /**
     * Create and record a generic audit event.
     *
     * @param eventType the event type
     * @param result the event result
     * @param principal the principal
     * @param request the HTTP request
     * @param additionalDetails additional event details
     */
    public void recordGenericEvent(
            AuditEventType eventType,
            AuditEventResult result,
            String principal,
            HttpServletRequest request,
            Map<String, Object> additionalDetails) {
        SecurityAuditEvent event = createBaseEvent(eventType, result, request);
        event.setPrincipal(principal);
        event.setDetails(toJson(additionalDetails));
        recordEventAsync(event);
    }

    // Query methods

    /**
     * Find audit events for a principal.
     *
     * @param principal the principal
     * @param pageable pagination info
     * @return page of audit events
     */
    @Transactional(readOnly = true)
    public Page<SecurityAuditEvent> findByPrincipal(String principal, Pageable pageable) {
        return auditEventRepository.findByPrincipalOrderByEventTimestampDesc(principal, pageable);
    }

    /**
     * Find audit events for a client.
     *
     * @param clientId the client ID
     * @param pageable pagination info
     * @return page of audit events
     */
    @Transactional(readOnly = true)
    public Page<SecurityAuditEvent> findByClientId(String clientId, Pageable pageable) {
        return auditEventRepository.findByClientIdOrderByEventTimestampDesc(clientId, pageable);
    }

    /**
     * Find audit events within a time range.
     *
     * @param startTime start time
     * @param endTime end time
     * @param pageable pagination info
     * @return page of audit events
     */
    @Transactional(readOnly = true)
    public Page<SecurityAuditEvent> findByTimeRange(
            Instant startTime, Instant endTime, Pageable pageable) {
        return auditEventRepository.findByEventTimestampBetweenOrderByEventTimestampDesc(
                startTime, endTime, pageable);
    }

    /**
     * Find recent audit events.
     *
     * @param pageable pagination info
     * @return page of audit events
     */
    @Transactional(readOnly = true)
    public Page<SecurityAuditEvent> findRecentEvents(Pageable pageable) {
        return auditEventRepository.findAllByOrderByEventTimestampDesc(pageable);
    }

    /**
     * Search audit events with multiple criteria.
     *
     * @param principal optional principal filter
     * @param clientId optional client ID filter
     * @param eventCategory optional category filter
     * @param result optional result filter
     * @param startTime start time
     * @param endTime end time
     * @param pageable pagination info
     * @return page of matching audit events
     */
    @Transactional(readOnly = true)
    public Page<SecurityAuditEvent> searchAuditEvents(
            String principal,
            String clientId,
            String eventCategory,
            AuditEventResult result,
            Instant startTime,
            Instant endTime,
            Pageable pageable) {
        return auditEventRepository.searchAuditEvents(
                principal, clientId, eventCategory, result, startTime, endTime, pageable);
    }

    // Helper methods

    private SecurityAuditEvent createBaseEvent(
            AuditEventType eventType, AuditEventResult result, HttpServletRequest request) {
        SecurityAuditEvent event = new SecurityAuditEvent();
        event.setEventId(UUID.randomUUID());
        event.setEventType(eventType);
        event.setEventCategory(eventType.getCategory());
        event.setResult(result);
        event.setEventTimestamp(Instant.now());

        if (request != null) {
            event.setIpAddress(getClientIpAddress(request));
            event.setUserAgent(request.getHeader("User-Agent"));
            event.setRequestUri(request.getRequestURI());
            event.setRequestMethod(request.getMethod());
            event.setSessionId(
                    request.getSession(false) != null ? request.getSession(false).getId() : null);
        }

        return event;
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }

    private String toJson(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize audit details to JSON", e);
            return null;
        }
    }
}
