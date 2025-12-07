# syntax=docker/dockerfile:1.7

# Documentation build stage - separate Python environment for MkDocs
FROM python:3.12-slim AS docs-builder

WORKDIR /docs

# Copy documentation sources
COPY mkdocs.yml ./
COPY docs ./docs

# Install MkDocs dependencies from shared requirements
COPY docs/requirements.txt ./requirements.txt
RUN pip install --no-cache-dir -r requirements.txt

# Build MkDocs site
RUN mkdocs build --site-dir /docs/site

# Optional prebuilt MkDocs site (downloaded by CI as artifact). If a prebuilt site
# is present in the Docker build context under ./site it will be copied here;
# otherwise this stage produces an empty /docs/site placeholder so the build
# still succeeds when DOCS_SOURCE=docs-builder.
FROM debian:bookworm-slim AS docs-prebuilt
WORKDIR /docs
RUN --mount=type=bind,source=site,target=/prebuilt,ro,required=false \
        mkdir -p /docs/site && \
        if [ -d /prebuilt ] && [ "$(ls -A /prebuilt)" ]; then \
            echo "Using prebuilt MkDocs site from build context" && \
            cp -a /prebuilt/. /docs/site; \
        else \
            echo "No prebuilt MkDocs site found in build context; relying on docs-builder"; \
        fi

# Build stage - extract layers from pre-built JAR
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

# Copy the pre-built JAR (provided by CI or local build)
# Gradle bootJar currently produces: server-ui/build/libs/server-ui-1.0.0-SNAPSHOT.jar
COPY server-ui/build/libs/server-ui-1.0.0-SNAPSHOT.jar app.jar

# Extract layers for optimized Docker image
RUN java -Djarmode=layertools -jar app.jar extract

# Runtime stage
FROM eclipse-temurin:21-jre

# Select documentation source. Default builds docs inside the Docker build; CI can
# set DOCS_SOURCE=docs-prebuilt to reuse an uploaded MkDocs artifact and skip the
# Python/MkDocs toolchain during docker build time.
ARG DOCS_SOURCE=docs-builder

# Security: Create non-root user
RUN groupadd -g 1001 appgroup && \
    useradd -u 1001 -g appgroup -s /bin/bash appuser

WORKDIR /app

# Copy application layers
COPY --from=builder /app/dependencies/ ./
COPY --from=builder /app/spring-boot-loader/ ./
COPY --from=builder /app/snapshot-dependencies/ ./
COPY --from=builder /app/application/ ./

# Copy built MkDocs site from the selected source stage into static resources
COPY --from=${DOCS_SOURCE} /docs/site/ ./BOOT-INF/classes/static/docs/

# If we were told to use a prebuilt site, ensure it actually existed
RUN if [ "$DOCS_SOURCE" = "docs-prebuilt" ] && [ ! "$(ls -A ./BOOT-INF/classes/static/docs/ 2>/dev/null)" ]; then \
            echo "DOCS_SOURCE=docs-prebuilt but no prebuilt MkDocs site was provided" >&2; \
            exit 1; \
        fi

# Change ownership
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 9000

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:9000/actuator/health || exit 1

# JVM options tuned for low-memory pods (prefer horizontal scaling)
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:+UseG1GC \
    -XX:+UseStringDeduplication \
    -XX:MaxRAMPercentage=45.0 \
    -XX:InitialRAMPercentage=15.0 \
    -XX:MaxMetaspaceSize=128m \
    -XX:MaxDirectMemorySize=128m \
    -XX:+ExitOnOutOfMemoryError \
    -Djava.security.egd=file:/dev/./urandom"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
