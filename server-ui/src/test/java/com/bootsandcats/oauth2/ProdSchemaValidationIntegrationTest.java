package com.bootsandcats.oauth2;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
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
@Tag("testcontainers")
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

    private static final String STATIC_JWK_JSON =
            "{\"keys\":[{\"kty\":\"EC\",\"d\":\"mwhKr9BIDjuB-OajeULLA4RORdJLUL8816YenVZwlMs\",\"use\":\"sig\",\"crv\":\"P-256\",\"kid\":\"586e7c9b-a4dc-4ea9-9cf7-197c3fae5d7f\",\"x\":\"0yuOTwftybMpxjSc1liSpftWHi5-YyyqvdlYclgF4zw\",\"y\":\"qboYXttcfjXXSYFlUEMkBOVmsMMDATyRv-UN4AR8Fl0\",\"alg\":\"ES256\"}]}";

    @Container
    @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("testdb")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        // Container is auto-started by @Container annotation before this method is called
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .baselineOnMigrate(true)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("azure.keyvault.static-jwk", () -> STATIC_JWK_JSON);
    }

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoadsWithProdProfileAndFlywayMigrations() {
        Long registeredClients =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM oauth2_registered_client", Long.class);
        Long appUsers = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app_users", Long.class);

        assertThat(registeredClients).isNotNull();
        assertThat(appUsers).isNotNull();
    }
}
