package com.bootsandcats.oauth2.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.bootsandcats.oauth2.config.AuthEventStreamProperties;
import com.bootsandcats.oauth2.model.AuditEventResult;
import com.bootsandcats.oauth2.model.AuditEventType;
import com.bootsandcats.oauth2.model.SecurityAuditEvent;

@ExtendWith(MockitoExtension.class)
class AuthEventPublisherTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private StreamOperations<String, Object, Object> streamOperations;

    private AuthEventStreamProperties properties;
    private AuthEventPublisher publisher;

    @BeforeEach
    void setUp() {
        properties = new AuthEventStreamProperties();
        properties.setStreamName("auth:events");
        properties.setMaxLength(1000);
        properties.setEnabled(true);
        lenient().when(redisTemplate.opsForStream()).thenReturn(streamOperations);

        publisher = new AuthEventPublisher(redisTemplate, properties);
    }

    @Test
    void publishesWithApproximateMaxLength() {
        SecurityAuditEvent event = sampleEvent();
        when(streamOperations.add(any(MapRecord.class))).thenReturn(RecordId.autoGenerate());

        publisher.publish(event);

        ArgumentCaptor<MapRecord<String, String, String>> recordCaptor =
                ArgumentCaptor.forClass(MapRecord.class);
        verify(streamOperations).add(recordCaptor.capture());
        verify(streamOperations).trim("auth:events", 1000L, true);

        MapRecord<String, String, String> record = recordCaptor.getValue();
        assertThat(record.getStream()).isEqualTo("auth:events");
        assertThat(record.getValue().get("eventId")).isEqualTo(event.getEventId().toString());
        assertThat(record.getValue().get("eventType")).isEqualTo(event.getEventType().name());
    }

    @Test
    void skipsPublishingWhenDisabled() {
        properties.setEnabled(false);
        publisher = new AuthEventPublisher(redisTemplate, properties);

        publisher.publish(sampleEvent());

        verify(redisTemplate, never()).opsForStream();
        verifyNoInteractions(streamOperations);
    }

    @Test
    void swallowsPublishFailures() {
        SecurityAuditEvent event = sampleEvent();
        doThrow(new RuntimeException("boom")).when(streamOperations).add(any(MapRecord.class));

        assertThatNoException().isThrownBy(() -> publisher.publish(event));
    }

    private SecurityAuditEvent sampleEvent() {
        SecurityAuditEvent event = new SecurityAuditEvent();
        event.setEventId(UUID.randomUUID());
        event.setEventType(AuditEventType.LOGIN_SUCCESS);
        event.setEventCategory(event.getEventType().getCategory());
        event.setEventTimestamp(Instant.parse("2024-01-01T00:00:00Z"));
        event.setPrincipal("alice");
        event.setResult(AuditEventResult.SUCCESS);
        event.setDetails("{\"email\":\"alice@example.com\"}");
        return event;
    }
}
