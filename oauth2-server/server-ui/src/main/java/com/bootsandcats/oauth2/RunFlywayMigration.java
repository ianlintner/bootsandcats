package com.bootsandcats.oauth2;

import org.flywaydb.core.Flyway;

/**
 * Standalone runner to execute Flyway migrations programmatically. Run this before starting the
 * Spring Boot application.
 */
public class RunFlywayMigration {

    public static void main(String[] args) {
        String url = System.getProperty("flyway.url", "jdbc:postgresql://localhost:5432/oauth2db");
        String user = System.getProperty("flyway.user", "postgres");
        String password = System.getProperty("flyway.password", "postgres");
        String locations = System.getProperty("flyway.locations", "classpath:db/migration");

        System.out.println("=== Running Flyway Migrations ===");
        System.out.println("URL: " + url);
        System.out.println("User: " + user);
        System.out.println("Locations: " + locations);

        Flyway flyway =
                Flyway.configure()
                        .dataSource(url, user, password)
                        .locations(locations)
                        .baselineOnMigrate(true)
                        .baselineVersion("0")
                        .load();

        System.out.println(
                "\nCurrent version: "
                        + (flyway.info().current() != null
                                ? flyway.info().current().getVersion()
                                : "none"));
        System.out.println("\nExecuting migrations...");

        int migrationsApplied = flyway.migrate().migrationsExecuted;

        System.out.println("\nMigrations applied: " + migrationsApplied);
        System.out.println(
                "Current version: "
                        + (flyway.info().current() != null
                                ? flyway.info().current().getVersion()
                                : "none"));
        System.out.println("\n=== Flyway Migrations Complete ===");
    }
}
