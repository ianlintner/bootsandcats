# syntax=docker/dockerfile:1
# Simple runtime image for profile-ui
FROM eclipse-temurin:21-jre

ARG PROFILE_JAR=profile-service/server/build/libs/server-*-all.jar

WORKDIR /app

# Copy the prebuilt profile-service jar from the build context
COPY profile-service/server/build/libs/server-*-all.jar /app/app.jar

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
