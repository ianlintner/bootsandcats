package com.bootsandcats.profileui.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bootsandcats.profileui.config.AuthEventConsumerConfiguration;
import com.bootsandcats.profileui.dto.ProfileRequest;
import com.bootsandcats.profileui.service.ProfileService;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.lettuce.core.RedisBusyException;
import io.lettuce.core.RedisClient;
import io.lettuce.core.StreamMessage;
import io.lettuce.core.XGroupCreateArgs;
import io.lettuce.core.XReadArgs;
import io.lettuce.core.api.sync.RedisCommands;

@ExtendWith(MockitoExtension.class)
class AuthEventStreamConsumerTest {

    @Mock private RedisClient redisClient;
    @Mock private RedisCommands<String, String> redisCommands;
    @Mock private ProfileService profileService;

    private AuthEventConsumerConfiguration config;
    private AuthEventStreamConsumer consumer;

    @BeforeEach
    void setUp() {
        config = new AuthEventConsumerConfiguration();
        consumer =
                new AuthEventStreamConsumer(
                        redisClient, profileService, new ObjectMapper(), config);
    }

    @Test
    void createsProfileWhenLoginSuccessAndProfileMissing() throws Exception {
        when(profileService.profileExists("alice")).thenReturn(false);

        Map<String, String> body = new HashMap<>();
        body.put("eventType", "LOGIN_SUCCESS");
        body.put("principal", "alice");
        body.put("userId", "42");
        body.put("details", "{\"email\":\"alice@example.com\",\"name\":\"Alice\"}");

        invokeHandleMessage(new StreamMessage<>(config.getStream(), "0-1", body));

        ArgumentCaptor<ProfileRequest> requestCaptor =
                ArgumentCaptor.forClass(ProfileRequest.class);
        verify(profileService).createProfile(eq("alice"), eq(42L), requestCaptor.capture());
        ProfileRequest request = requestCaptor.getValue();
        assertThat(request.getEmail()).isEqualTo("alice@example.com");
        assertThat(request.getPreferredName()).isEqualTo("Alice");
    }

    @Test
    void skipsProfileCreationWhenProfileAlreadyExists() throws Exception {
        when(profileService.profileExists("alice")).thenReturn(true);

        Map<String, String> body = new HashMap<>();
        body.put("eventType", "LOGIN_SUCCESS");
        body.put("principal", "alice");
        body.put("userId", "42");

        invokeHandleMessage(new StreamMessage<>(config.getStream(), "0-2", body));

        verify(profileService, never()).createProfile(eq("alice"), any(), any());
    }

    @Test
    void ignoresNonLoginEvents() throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("eventType", "TOKEN_ISSUED");
        body.put("principal", "alice");

        invokeHandleMessage(new StreamMessage<>(config.getStream(), "0-3", body));

        verifyNoInteractions(profileService);
    }

    @Test
    void consumerGroupNotMarkedPreparedOnFailure() throws Exception {
        Method ensureGroup =
                AuthEventStreamConsumer.class.getDeclaredMethod("ensureGroup", RedisCommands.class);
        ensureGroup.setAccessible(true);

        doThrow(new RuntimeException("boom"))
                .when(redisCommands)
                .xgroupCreate(
                        any(XReadArgs.StreamOffset.class),
                        anyString(),
                        any(XGroupCreateArgs.class));

        ensureGroup.invoke(consumer, redisCommands);

        Field preparedField = AuthEventStreamConsumer.class.getDeclaredField("groupPrepared");
        preparedField.setAccessible(true);
        assertThat((boolean) preparedField.get(consumer)).isFalse();
    }

    @Test
    void consumerGroupMarkedPreparedWhenAlreadyExists() throws Exception {
        Method ensureGroup =
                AuthEventStreamConsumer.class.getDeclaredMethod("ensureGroup", RedisCommands.class);
        ensureGroup.setAccessible(true);

        doThrow(new RedisBusyException("BUSYGROUP"))
                .when(redisCommands)
                .xgroupCreate(
                        any(XReadArgs.StreamOffset.class),
                        anyString(),
                        any(XGroupCreateArgs.class));

        ensureGroup.invoke(consumer, redisCommands);

        Field preparedField = AuthEventStreamConsumer.class.getDeclaredField("groupPrepared");
        preparedField.setAccessible(true);
        assertThat((boolean) preparedField.get(consumer)).isTrue();
    }

    private void invokeHandleMessage(StreamMessage<String, String> message) throws Exception {
        Method handleMessage =
                AuthEventStreamConsumer.class.getDeclaredMethod(
                        "handleMessage", StreamMessage.class);
        handleMessage.setAccessible(true);
        handleMessage.invoke(consumer, message);
    }
}
