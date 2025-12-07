package com.bootsandcats.oauth2;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("prod")
@Testcontainers
@TestPropertySource(
        properties = {
            "spring.session.store-type=none",
            "spring.data.redis.host=localhost",
            "spring.data.redis.port=6379",
            "oauth2.issuer-url=http://localhost:9000",
            "oauth2.demo-client-secret=test-demo-secret",
            "oauth2.m2m-client-secret=test-m2m-secret",
            "oauth2.demo-user-password=test-user-password",
            "oauth2.admin-user-password=test-admin-password",
            "azure.keyvault.enabled=false"
        })
class ProdSchemaValidationIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        POSTGRES.start();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoadsWithProdProfileAndFlywayMigrations() {
        Long registeredClients =
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM oauth2_registered_client", Long.class);
        Long appUsers = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app_users", Long.class);

        assertThat(registeredClients).isNotNull();
        assertThat(appUsers).isNotNull();
    }
}
