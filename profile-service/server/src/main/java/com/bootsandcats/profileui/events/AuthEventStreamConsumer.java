package com.bootsandcats.profileui.events;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bootsandcats.profileui.config.AuthEventConsumerConfiguration;
import com.bootsandcats.profileui.dto.ProfileRequest;
import com.bootsandcats.profileui.service.ProfileService;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.lettuce.core.Consumer;
import io.lettuce.core.RedisBusyException;
import io.lettuce.core.StreamMessage;
import io.lettuce.core.XGroupCreateArgs;
import io.lettuce.core.XReadArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.RedisClient;
import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;

/**
 * Redis Stream consumer that listens for auth activity and auto-creates basic profiles for new
 * users.
 */
@Singleton
@Requires(property = "auth.events.consumer.enabled", notEquals = "false")
public class AuthEventStreamConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuthEventStreamConsumer.class);

    private final RedisClient redisClient;
    private final ProfileService profileService;
    private final ObjectMapper objectMapper;
    private final AuthEventConsumerConfiguration config;

    private StatefulRedisConnection<String, String> connection;
    private boolean groupPrepared = false;

    public AuthEventStreamConsumer(
            RedisClient redisClient,
            ProfileService profileService,
            ObjectMapper objectMapper,
            AuthEventConsumerConfiguration config) {
        this.redisClient = redisClient;
        this.profileService = profileService;
        this.objectMapper = objectMapper;
        this.config = config;
    }

    @PostConstruct
    void init() {
        if (config.isEnabled()) {
            ensureConnection();
        }
    }

    @PreDestroy
    void shutdown() {
        closeConnection();
    }

    @Scheduled(fixedDelay = "${auth.events.consumer.poll-interval:5s}")
    void pollStream() {
        if (!config.isEnabled() || !ensureConnection()) {
            return;
        }

        try {
            RedisCommands<String, String> commands = connection.sync();
            ensureGroup(commands);

            List<StreamMessage<String, String>> messages =
                    commands.xreadgroup(
                            Consumer.from(config.getGroup(), config.getConsumerName()),
                            XReadArgs.Builder.block(adjustedBlockDuration())
                                    .count(config.getBatchSize()),
                            XReadArgs.StreamOffset.lastConsumed(config.getStream()));

            if (messages == null || messages.isEmpty()) {
                return;
            }

            for (StreamMessage<String, String> message : messages) {
                try {
                    handleMessage(message);
                } catch (Exception e) {
                    log.warn(
                            "Failed to process auth event message {} from stream {}",
                            message.getId(),
                            config.getStream(),
                            e);
                } finally {
                    ack(commands, message);
                }
            }
        } catch (Exception e) {
            log.warn("Auth event stream polling failed for {}", config.getStream(), e);
            closeConnection();
        }
    }

    private void ensureGroup(RedisCommands<String, String> commands) {
        if (groupPrepared || !config.isCreateGroup()) {
            return;
        }

        try {
            commands.xgroupCreate(
                    XReadArgs.StreamOffset.from(config.getStream(), "0-0"),
                    config.getGroup(),
                    XGroupCreateArgs.Builder.mkstream(true));
            log.info(
                    "Created Redis consumer group '{}' for stream '{}'",
                    config.getGroup(),
                    config.getStream());
        } catch (RedisBusyException alreadyExists) {
            log.debug(
                    "Redis consumer group '{}' already exists on stream '{}'",
                    config.getGroup(),
                    config.getStream());
        } catch (Exception e) {
            log.warn(
                    "Unable to create consumer group '{}' on stream '{}'",
                    config.getGroup(),
                    config.getStream(),
                    e);
        }

        groupPrepared = true;
    }

    private void handleMessage(StreamMessage<String, String> message) {
        AuthActivityEvent event = AuthActivityEvent.fromMap(message.getBody(), objectMapper);
        if (event == null) {
            return;
        }

        if (event.isLoginSuccess()) {
            handleProfileBootstrap(event);
        }
    }

    private void handleProfileBootstrap(AuthActivityEvent event) {
        String subject = event.getPrincipal();
        if (subject == null || subject.isBlank()) {
            return;
        }

        if (profileService.profileExists(subject)) {
            return;
        }

        ProfileRequest request = new ProfileRequest();
        event.detail("email").ifPresent(request::setEmail);
        event.detail("name").ifPresent(request::setPreferredName);
        event.detail("username")
                .ifPresent(
                        username -> {
                            if (request.getPreferredName() == null) {
                                request.setPreferredName(username);
                            }
                        });
        event.detail("picture_url").ifPresent(request::setPictureUrl);
        event.detail("avatar_url").ifPresent(request::setPictureUrl);
        event.detail("picture").ifPresent(request::setPictureUrl);

        if (request.getPreferredName() == null && event.getPrincipal() != null) {
            request.setPreferredName(event.getPrincipal());
        }

        profileService.createProfile(subject, event.getUserId(), request);
        log.info(
                "Created profile for subject '{}' from auth event {}",
                subject,
                event.getEventType());
    }

    private void ack(
            RedisCommands<String, String> commands, StreamMessage<String, String> message) {
        try {
            commands.xack(config.getStream(), config.getGroup(), message.getId());
        } catch (Exception e) {
            log.warn(
                    "Failed to ack message {} on stream {}",
                    message.getId(),
                    config.getStream(),
                    e);
        }
    }

    private boolean ensureConnection() {
        try {
            if (connection == null || !connection.isOpen()) {
                connection = redisClient.connect();
                groupPrepared = false;
                log.info(
                        "Connected to Redis for auth event consumption. Stream={}, group={}",
                        config.getStream(),
                        config.getGroup());
            }
            return true;
        } catch (Exception e) {
            log.debug("Redis connection unavailable for auth event consumer", e);
            closeConnection();
            return false;
        }
    }

    private void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception ignored) {
                // ignore
            }
        }
        connection = null;
    }

    private Duration adjustedBlockDuration() {
        Duration interval = config.getPollInterval();
        if (interval == null || interval.isNegative() || interval.isZero()) {
            return Duration.ofSeconds(1);
        }
        // Slightly shorter than poll interval so scheduled trigger cadence still applies
        return interval.minusMillis(Math.min(500, interval.toMillis() / 2));
    }
}
