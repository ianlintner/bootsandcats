package com.bootsandcats.oauth2.config;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Ensures Flyway is available during tests even when the auto-configuration is disabled by default.
 * This loads migrations from the H2-specific classpath location to create the OAuth tables in the
 * in-memory H2 database. The H2 migrations are compatible with H2 database syntax.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestFlywayConfig {

    @Bean
    @Primary
    public Flyway testFlyway(DataSource dataSource) {
        Flyway flyway =
                Flyway.configure()
                        .baselineOnMigrate(true)
                        .locations("classpath:db/migration-h2")
                        .dataSource(dataSource)
                        .load();
        flyway.migrate();
        return flyway;
    }
}
