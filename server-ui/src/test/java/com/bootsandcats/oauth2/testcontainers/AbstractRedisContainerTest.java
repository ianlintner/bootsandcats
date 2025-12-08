package com.bootsandcats.oauth2.testcontainers;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Abstract base class for integration tests requiring a Redis instance.
 *
 * <p>Provides a shared Redis container for session management and caching tests. The container is
 * started once and reused across all tests extending this class.
 *
 * <p>Usage: Extend this class and add @SpringBootTest with @ActiveProfiles("testcontainers-redis")
 *
 * <p>AI-Agent Notes:
 *
 * <ul>
 *   <li>Container uses Redis 7-alpine for production parity
 *   <li>Spring Session is automatically configured to use this Redis instance
 *   <li>Tests can verify session persistence, expiration, and failover
 * </ul>
 */
@Testcontainers
public abstract class AbstractRedisContainerTest {

    /** Shared Redis container using Redis 7 Alpine image. */
    @Container
    protected static final GenericContainer<?> redisContainer =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379)
                    .withReuse(true);

    /**
     * Dynamically configures Spring Redis properties from the container. This ensures tests use the
     * actual Redis container for session storage.
     */
    @DynamicPropertySource
    static void configureRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
        registry.add("spring.session.store-type", () -> "redis");
        registry.add("management.health.redis.enabled", () -> "true");
    }
}
