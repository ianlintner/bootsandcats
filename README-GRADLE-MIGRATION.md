# Gradle Multi-Project Migration Guide

This repository now includes a Gradle multi-project scaffold alongside existing Maven build files to enable an incremental migration.

## Modules
- `server-dao`: Data access and persistence (JDBC/JPA).
- `server-logic`: Business logic and security; depends on `server-dao`.
- `server-ui`: Spring Boot app (authorization server and APIs); depends on `server-logic`.
- `canary-app`: Lightweight Spring Boot app for canary deployments.

## Next Steps
1. Generate Gradle wrapper and verify build.
2. Move source files:
   - DAO classes (repositories, entities, data mappers) to `server-dao/src/main/java`.
   - Core services, business logic, security configuration to `server-logic/src/main/java`.
   - Controllers, app main class, UI templates and static resources to `server-ui/src/main/...`.
3. Update package imports as needed after moving.
4. Keep Maven temporarily; once Gradle builds and tests pass, remove Maven files.

## Commands (macOS zsh)
```sh
# From repo root, generate Gradle wrapper
gradle wrapper

# Build everything
./gradlew build

# Run UI server (auth server)
./gradlew :server-ui:bootRun

# Run canary app
./gradlew :canary-app:bootRun
```

## Notes
- Java 21 toolchain is configured.
- Spring Boot 3.4.x baseline with latest dependencies.
- Tests use JUnit Platform; migrate test sources similarly into their respective modules.
