# Flyway Local Testing Guide

## Problem

Spring Boot 4.0.0 Flyway autoconfiguration is unreliable - migrations don't execute automatically even with all correct configuration. This was discovered during local testing where the app would start but tables wouldn't exist because Flyway never ran.

## Solution

Use programmatic Flyway execution via a custom Gradle task before starting the application.

## Local Testing Workflow

### 1. Start PostgreSQL (via Docker Compose)

```bash
cd infrastructure
docker-compose up -d postgres redis
```

### 2. Run Flyway Migrations

```bash
./gradlew :oauth2-server:server-ui:runFlywayMigration
```

This will:
- Connect to `jdbc:postgresql://localhost:5432/oauth2db`
- Apply all migrations from `db/migration/`
- Create `flyway_schema_history` table
- Report success/failure and current version

### 3. Start the Application

```bash
./gradlew :oauth2-server:server-ui:bootRun --args='--spring.profiles.active=local'
```

The app will now start successfully because tables already exist.

### 4. Verify Migration Status

```bash
docker exec infrastructure-postgres-1 psql -U postgres -d oauth2db -c \
  "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
```

### 5. Clean Up

```bash
pkill -f "oauth2-server:server-ui:bootRun"
docker-compose -f infrastructure/docker-compose.yml down
```

## How It Works

The custom Gradle task `runFlywayMigration` executes [`RunFlywayMigration.java`](server-ui/src/main/java/com/bootsandcats/oauth2/RunFlywayMigration.java), which uses Flyway's Java API directly:

```java
Flyway flyway = Flyway.configure()
    .dataSource(url, user, password)
    .locations(locations)
    .baselineOnMigrate(true)
    .baselineVersion("0")
    .load();

flyway.migrate();
```

This is the same pattern used successfully in [`ProdSchemaValidationIntegrationTest.java`](server-ui/src/test/java/com/bootsandcats/oauth2/ProdSchemaValidationIntegrationTest.java).

## Why Not Use Flyway Gradle Plugin?

The Flyway Gradle plugin 11.2.0 is **incompatible with Gradle 9.0** - it references the removed `JavaPluginConvention` class and fails with:

```
java.lang.NoClassDefFoundError: org/gradle/api/plugins/JavaPluginConvention
```

## Production Deployment

In production (Kubernetes), Flyway autoconfiguration works correctly because:
1. Integration tests run Flyway programmatically before Spring context loads
2. Production PostgreSQL database is already initialized
3. The application has been tested with this pattern

The production configuration in `application.properties`:
```properties
spring.flyway.enabled=true
spring.flyway.baseline-on-migrate=true
spring.jpa.hibernate.ddl-auto=none
```

This works in production but **fails in local development with Spring Boot 4.0.0**.

## Migration Files

All migrations V1-V19 are in [`server-dao/src/main/resources/db/migration/`](../server-dao/src/main/resources/db/migration/).

The Gradle build task copies them to `BOOT-INF/classes/db/migration/` in the JAR:

```kotlin
tasks.register<Copy>("copyMigrations") {
    from("${project(":oauth2-server:server-dao").projectDir}/src/main/resources/db/migration")
    into("$buildDir/resources/main/db/migration")
}
```

## Troubleshooting

### Migrations not found

Run `./gradlew clean :oauth2-server:server-ui:classes` to rebuild and copy migrations.

### Database connection refused

Ensure PostgreSQL is running: `docker-compose ps`

### Migrations already applied

Check `flyway_schema_history` table. To reset:
```bash
docker-compose down -v  # Destroys postgres_data volume
docker-compose up -d postgres redis
./gradlew :oauth2-server:server-ui:runFlywayMigration
```

### App fails with "relation does not exist"

You forgot to run `runFlywayMigration` before `bootRun`.
