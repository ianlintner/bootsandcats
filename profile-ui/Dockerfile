# Simple runtime image for profile-ui
FROM eclipse-temurin:21-jre

ARG PROFILE_JAR=profile-ui/build/libs/profile-ui-*.jar

WORKDIR /app

# Copy the prebuilt profile-ui jar from the build context (downloaded by CI)
# Use a bind mount to avoid glob failures when the path is missing and to give
# a clear error if the artifact is absent.
RUN --mount=type=bind,source=.,target=/context,ro \
		set -e; \
		JAR_PATH=$(echo /context/${PROFILE_JAR}); \
		if [ ! -e "$JAR_PATH" ]; then \
			echo "Profile UI jar not found at ${PROFILE_JAR}. Ensure the artifact is downloaded to that path before building (e.g., actions/download-artifact to profile-ui/build/libs)." >&2; \
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
