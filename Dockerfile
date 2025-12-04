# Build stage
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

# Copy Gradle files
COPY gradlew .
COPY gradle gradle
COPY settings.gradle.kts .
COPY build.gradle.kts .
COPY gradle.properties .

# Copy module build files
COPY server-ui/build.gradle.kts server-ui/
COPY server-logic/build.gradle.kts server-logic/
COPY server-dao/build.gradle.kts server-dao/
COPY canary-app/build.gradle.kts canary-app/

# Download dependencies (separate layer for caching)
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

# Copy source code
COPY server-ui/src server-ui/src
COPY server-logic/src server-logic/src
COPY server-dao/src server-dao/src

# Build the application
RUN ./gradlew :server-ui:bootJar --no-daemon -x test

# Extract layers for optimized Docker image
RUN java -Djarmode=layertools -jar server-ui/build/libs/oauth2-server-*.jar extract

# Runtime stage
FROM eclipse-temurin:21-jre

# Security: Create non-root user
RUN groupadd -g 1001 appgroup && \
    useradd -u 1001 -g appgroup -s /bin/bash appuser

WORKDIR /app

# Copy application layers
COPY --from=builder /app/dependencies/ ./
COPY --from=builder /app/spring-boot-loader/ ./
COPY --from=builder /app/snapshot-dependencies/ ./
COPY --from=builder /app/application/ ./

# Change ownership
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 9000

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:9000/actuator/health || exit 1

# JVM options for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:InitialRAMPercentage=50.0 \
    -Djava.security.egd=file:/dev/./urandom"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
