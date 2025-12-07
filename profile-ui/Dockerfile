# Simple runtime image for profile-ui-lite
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY build/libs/profile-ui-lite-*.jar app.jar

EXPOSE 8085
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
