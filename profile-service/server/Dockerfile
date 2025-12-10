# syntax=docker/dockerfile:1
# Simple runtime image for profile-ui
FROM eclipse-temurin:21-jre

ARG PROFILE_JAR=profile-service/server/build/libs/server-*-all.jar

WORKDIR /app

# Copy the prebuilt profile-service jar from the build context (downloaded by CI)
# Use a bind mount to avoid glob failures when the path is missing and to give
# a clear error if the artifact is absent.
RUN --mount=type=bind,source=.,target=/context,ro \
		set -e; \
		# Prefer the fat shadow JAR (contains all dependencies) from repo root context
		if ls /context/profile-service/server/build/libs/server-*-all.jar >/tmp/jar.list 2>/dev/null; then \
			JAR_PATH=$(head -n1 /tmp/jar.list); \
		# Fallback: when building from inside profile-service/server/ with context '.'
		elif ls /context/build/libs/server-*-all.jar >/tmp/jar.list 2>/dev/null; then \
			JAR_PATH=$(head -n1 /tmp/jar.list); \
		# Last resort: accept any server jar, but warn that dependencies may be missing
		elif ls /context/profile-service/server/build/libs/server-*.jar >/tmp/jar.list 2>/dev/null; then \
			echo "Warning: no *-all jar found; falling back to generic jar" >&2; \
			JAR_PATH=$(head -n1 /tmp/jar.list); \
		elif ls /context/build/libs/server-*.jar >/tmp/jar.list 2>/dev/null; then \
			echo "Warning: no *-all jar found; falling back to generic jar" >&2; \
			JAR_PATH=$(head -n1 /tmp/jar.list); \
		else \
			echo "Profile Service jar not found. Expected at profile-service/server/build/libs/server-*-all.jar (repo-root context) or build/libs/server-*-all.jar (profile-service/server/ context). Build the fat jar or download the artifact first." >&2; \
			exit 1; \
		fi; \
		cp "$JAR_PATH" /app/app.jar

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

EXPOSE 8085
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
