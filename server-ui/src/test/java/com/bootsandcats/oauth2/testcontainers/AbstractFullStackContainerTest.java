package com.bootsandcats.oauth2.testcontainers;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Abstract base class for full-stack integration tests requiring both PostgreSQL and Redis.
 *
 * <p>Provides production-like infrastructure for end-to-end testing of the OAuth2 server. Both
 * containers are started once and reused across all tests extending this class.
 *
 * <p>Usage: Extend this class and add @SpringBootTest with @ActiveProfiles("testcontainers-full")
 *
 * <p>AI-Agent Notes:
 *
 * <ul>
 *   <li>Full stack tests are slower (~15-20s startup) but more realistic
 *   <li>Use for testing session persistence across database operations
 *   <li>Validates production configuration compatibility
 *   <li>Recommended for pre-deployment validation
 * </ul>
 */
@Testcontainers
public abstract class AbstractFullStackContainerTest {

    /** Shared PostgreSQL container using PostgreSQL 16 Alpine image. */
    @Container
    protected static final PostgreSQLContainer<?> postgresContainer =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("oauth2_test")
                    .withUsername("testuser")
                    .withPassword("testpass")
                    .withReuse(true);

    /** Shared Redis container using Redis 7 Alpine image. */
    @Container
    protected static final GenericContainer<?> redisContainer =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379)
                    .withReuse(true);

    /** Configures all infrastructure properties from containers. */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL configuration
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add(
                "spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");

        // Redis configuration
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
        registry.add("spring.session.store-type", () -> "redis");
        registry.add("management.health.redis.enabled", () -> "true");
    }
}
