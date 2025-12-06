ARG PROFILE_UI_JAR=profile-ui-1.0.0-SNAPSHOT.jar

# Build stage: compile the Profile UI jar if it is not already present
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Frontend dependencies for profile-ui build
RUN apk add --no-cache nodejs npm

# Copy the entire project to allow Gradle to build the profile-ui module
COPY . .

# Ensure the Gradle wrapper is executable and build the profile-ui jar only if it is not already present
RUN chmod +x ./gradlew \
		&& if [ -f profile-ui/build/libs/${PROFILE_UI_JAR} ]; then \
				 echo "Using existing profile-ui jar: profile-ui/build/libs/${PROFILE_UI_JAR}"; \
			 else \
				 echo "Jar not found; building profile-ui jar"; \
				 ./gradlew :profile-ui:bootJar -x test --no-daemon --build-cache; \
			 fi

# Runtime stage: slim JRE image with only the built artifact
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

ARG PROFILE_UI_JAR
COPY --from=build /workspace/profile-ui/build/libs/${PROFILE_UI_JAR} app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
