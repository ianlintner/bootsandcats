# Build stage
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

# Copy maven wrapper and pom
COPY mvnw ./
COPY .mvn .mvn
COPY pom.xml ./

# Make mvnw executable and download dependencies with retry logic for transient network failures
RUN chmod +x mvnw && \
    for i in 1 2 3; do \
        ./mvnw dependency:go-offline -B && break || \
        { echo "Attempt $i failed, retrying in 10s..."; sleep 10; }; \
    done

# Copy renamed source assets (legacy Maven build expects /src)
COPY server ./src

# Build the application with retry logic
RUN for i in 1 2 3; do \
        ./mvnw package -DskipTests -B && break || \
        { echo "Attempt $i failed, retrying in 10s..."; sleep 10; }; \
    done

# Extract layers for optimized Docker image
RUN java -Djarmode=layertools -jar target/oauth2-server-*.jar extract

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
