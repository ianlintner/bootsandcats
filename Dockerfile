# syntax=docker/dockerfile:1.7

# Select documentation source. Default builds docs inside the Docker build; CI can
# set DOCS_SOURCE=docs-prebuilt to reuse an uploaded MkDocs artifact and skip the
# Python/MkDocs toolchain during docker build time.
ARG DOCS_SOURCE=auto

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
RUN --mount=type=bind,source=site,target=/prebuilt,ro \
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

# Re-declare DOCS_SOURCE for this stage (inherits default/global value)
ARG DOCS_SOURCE

# Security: Create non-root user
RUN groupadd -g 1001 appgroup && \
    useradd -u 1001 -g appgroup -s /bin/bash appuser

WORKDIR /app

# Copy application layers
COPY --from=builder /app/dependencies/ ./
COPY --from=builder /app/spring-boot-loader/ ./
COPY --from=builder /app/snapshot-dependencies/ ./
COPY --from=builder /app/application/ ./

# Bring in both possible doc sources. We’ll choose in a RUN step to avoid
# variable substitution limits in COPY --from.
COPY --from=docs-builder /docs/site/ /tmp/docs-site-builder/
COPY --from=docs-prebuilt /docs/site/ /tmp/docs-site-prebuilt/

# Decide which docs to publish:
# - If DOCS_SOURCE=docs-prebuilt, require the prebuilt site to exist.
# - Otherwise, prefer prebuilt when present; fall back to locally built docs.
RUN mkdir -p ./BOOT-INF/classes/static/docs/ && \
        if [ "$DOCS_SOURCE" = "docs-prebuilt" ]; then \
            if [ -d /tmp/docs-site-prebuilt ] && [ "$(ls -A /tmp/docs-site-prebuilt)" ]; then \
                echo "Using prebuilt MkDocs site (forced)"; \
                cp -a /tmp/docs-site-prebuilt/. ./BOOT-INF/classes/static/docs/; \
            else \
                echo "DOCS_SOURCE=docs-prebuilt but no prebuilt MkDocs site was provided" >&2; \
                exit 1; \
            fi; \
        else \
            if [ -d /tmp/docs-site-prebuilt ] && [ "$(ls -A /tmp/docs-site-prebuilt)" ]; then \
                echo "Prebuilt MkDocs site detected; using it"; \
                cp -a /tmp/docs-site-prebuilt/. ./BOOT-INF/classes/static/docs/; \
            else \
                echo "Using docs built during Docker build"; \
                cp -a /tmp/docs-site-builder/. ./BOOT-INF/classes/static/docs/; \
            fi; \
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
