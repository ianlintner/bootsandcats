package com.bootsandcats.oauth2.events;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.bootsandcats.oauth2.config.AuthEventStreamProperties;
import com.bootsandcats.oauth2.model.SecurityAuditEvent;

/**
 * Publishes authentication and audit events to a Redis Stream so downstream services can react to
 * auth activity.
 */
@Component
public class AuthEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AuthEventPublisher.class);

    private final StringRedisTemplate redisTemplate;
    private final AuthEventStreamProperties properties;

    public AuthEventPublisher(
            StringRedisTemplate redisTemplate, AuthEventStreamProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    /**
     * Publish the given audit event to the configured Redis stream. Failures are logged but do not
     * prevent the caller from continuing.
     */
    public void publish(SecurityAuditEvent event) {
        if (!properties.isEnabled()) {
            return;
        }

        Map<String, String> payload = new HashMap<>();
        put(payload, "eventId", event.getEventId());
        put(payload, "eventType", event.getEventType());
        put(payload, "eventCategory", event.getEventCategory());
        put(
                payload,
                "eventTimestamp",
                event.getEventTimestamp() != null
                        ? DateTimeFormatter.ISO_INSTANT.format(event.getEventTimestamp())
                        : null);
        put(payload, "principal", event.getPrincipal());
        put(payload, "principalType", event.getPrincipalType());
        put(payload, "userId", event.getUserId());
        put(payload, "clientId", event.getClientId());
        put(payload, "result", event.getResult());
        put(payload, "grantType", event.getGrantType());
        put(payload, "tokenType", event.getTokenType());
        put(payload, "scopes", event.getScopes());
        put(payload, "authorizationCodeId", event.getAuthorizationCodeId());
        put(payload, "correlationId", event.getCorrelationId());
        put(payload, "ipAddress", event.getIpAddress());
        put(payload, "userAgent", event.getUserAgent());
        put(payload, "requestUri", event.getRequestUri());
        put(payload, "requestMethod", event.getRequestMethod());
        put(payload, "sessionId", event.getSessionId());
        put(payload, "details", event.getDetails());

        MapRecord<String, String, String> record =
                StreamRecords.mapBacked(payload).withStreamKey(properties.getStreamName());

        try {
            RecordId recordId = redisTemplate.opsForStream().add(record);
            if (properties.getMaxLength() > 0) {
                redisTemplate
                        .opsForStream()
                        .trim(properties.getStreamName(), properties.getMaxLength(), true);
            }
            log.debug(
                    "Published auth event {} ({}) to stream {} as record {}",
                    event.getEventId(),
                    event.getEventType(),
                    properties.getStreamName(),
                    recordId);
        } catch (Exception e) {
            log.warn(
                    "Failed to publish auth event {} to stream {}",
                    event.getEventId(),
                    properties.getStreamName(),
                    e);
        }
    }

    private void put(Map<String, String> map, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof UUID uuid) {
            map.put(key, uuid.toString());
        } else {
            map.put(key, value.toString());
        }
    }
}
