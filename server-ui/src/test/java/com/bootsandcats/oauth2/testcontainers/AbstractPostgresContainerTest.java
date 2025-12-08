package com.bootsandcats.oauth2.testcontainers;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Abstract base class for integration tests requiring a PostgreSQL database.
 *
 * <p>Provides a shared PostgreSQL container that is reused across all tests extending this class.
 * The container is started once and kept alive for the duration of the test suite.
 *
 * <p>Usage: Extend this class and add @SpringBootTest with @ActiveProfiles("testcontainers")
 *
 * <p>AI-Agent Notes:
 * <ul>
 *   <li>Container startup takes ~5-10 seconds on first test</li>
 *   <li>Tests run against PostgreSQL 16-alpine for production parity</li>
 *   <li>Flyway migrations are applied automatically</li>
 *   <li>Each test class gets a fresh database schema</li>
 * </ul>
 */
@Testcontainers
public abstract class AbstractPostgresContainerTest {

    /**
     * Shared PostgreSQL container using PostgreSQL 16 Alpine image.
     * Matches production configuration for realistic testing.
     */
    @Container
    protected static final PostgreSQLContainer<?> postgresContainer =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("oauth2_test")
                    .withUsername("testuser")
                    .withPassword("testpass")
                    .withReuse(true); // Enable container reuse between test runs

    /**
     * Dynamically configures Spring datasource properties from the container.
     * This ensures tests use the actual PostgreSQL container.
     */
    @DynamicPropertySource
    static void configurePostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none"); // Flyway handles migrations
        registry.add("spring.flyway.enabled", () -> "true");
    }
}
