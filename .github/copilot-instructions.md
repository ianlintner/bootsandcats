# Copilot Instructions for OAuth2 Authorization Server

This is a Spring Boot OAuth2 Authorization Server with OpenID Connect (OIDC), PKCE, JWT support, and observability features.

## Project Overview

- **Framework**: Spring Boot 3.2.x with Spring Security OAuth2 Authorization Server
- **Language**: Java 21
- **Build Tool**: Gradle 9.0 (multi-module Kotlin DSL)
- **Key Features**: OAuth 2.1, OIDC 1.0, PKCE, JWT tokens, OpenTelemetry, Prometheus metrics

## Build and Test Commands

```bash
# Build the project
./gradlew build

# Run unit tests only
./gradlew test

# Run all tests including integration tests
./gradlew check

# Run the application locally
./gradlew :server-ui:bootRun
```

## Code Style

- Use Google Java Format with AOSP style (4-space indentation)
- Run `./gradlew spotlessApply` to auto-format code before committing
- Run `./gradlew spotlessCheck` to verify formatting
- Import order: java, javax, org, com (blank line separated)
- Remove unused imports

## Coding Conventions

- Use Javadoc comments for public classes and methods
- Follow Spring conventions for configuration classes
- Use constructor injection for dependencies (avoid field injection)
- Use `@Value` annotations for externalized configuration
- Add `@Order` annotations when defining multiple SecurityFilterChain beans

## Security Best Practices

- Never commit secrets or credentials to the codebase
- Use environment variables or secrets management for sensitive values
- Default credentials in code are for demonstration only
- bcrypt password encoding with cost factor 12
- CSRF protection is enabled (with exceptions for token endpoints)
- Security headers configured via `SecurityHeadersConfig`

## Testing Conventions

- Unit tests: `*Test.java` (run with JUnit Platform)
- Integration tests: `*IntegrationTest.java` or `*IT.java`
- Use `@SpringBootTest` with `@ActiveProfiles("test")` for integration tests

## Project Structure

```
server-ui/          # Main Spring Boot application (UI, controllers, security config)
server-logic/       # Business logic services
server-dao/         # Data access layer (JPA entities, repositories)
canary-app/         # Canary deployment test application
```

## Static Analysis

- Run `./gradlew spotbugsMain` for static analysis with FindSecBugs
- SpotBugs is configured with "Max" effort and "Medium" threshold
