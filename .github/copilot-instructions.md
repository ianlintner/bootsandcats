# Copilot Instructions for OAuth2 Authorization Server

This is a Spring Boot OAuth2 Authorization Server with OpenID Connect (OIDC), PKCE, JWT support, and observability features.

## Project Overview

- **Framework**: Spring Boot 3.2.x with Spring Security OAuth2 Authorization Server
- **Language**: Java 17
- **Build Tool**: Maven 3.8+
- **Key Features**: OAuth 2.1, OIDC 1.0, PKCE, JWT tokens, OpenTelemetry, Prometheus metrics

## Build and Test Commands

```bash
# Build the project
./mvnw package

# Run unit tests only
./mvnw test

# Run all tests including integration tests
./mvnw verify

# Run tests with coverage report
./mvnw verify jacoco:report

# Run the application locally
./mvnw spring-boot:run
```

## Code Style

- Use Google Java Format with AOSP style (4-space indentation)
- Run `./mvnw spotless:apply` to auto-format code before committing
- Run `./mvnw spotless:check` to verify formatting
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

- Unit tests: `*Test.java` (run with Surefire plugin)
- Integration tests: `*IntegrationTest.java` or `*IT.java` (run with Failsafe plugin)
- Use `@SpringBootTest` with `@ActiveProfiles("test")` for integration tests
- Test coverage minimum: 70% line coverage (enforced by JaCoCo)

## Project Structure

```
src/
├── main/java/com/bootsandcats/oauth2/
│   ├── config/          # Spring configuration classes
│   ├── controller/      # REST controllers
│   └── service/         # Business logic services
├── main/resources/
│   └── application.properties
└── test/java/com/bootsandcats/oauth2/
    ├── config/          # Configuration tests
    ├── controller/      # Controller tests
    ├── integration/     # Integration tests
    ├── security/        # Security tests
    └── service/         # Service tests
```

## Static Analysis

- Run `./mvnw spotbugs:check` for static analysis with FindSecBugs
- Run `./mvnw org.owasp:dependency-check-maven:check` for dependency vulnerability scanning
- SpotBugs is configured with "Max" effort and "Medium" threshold
