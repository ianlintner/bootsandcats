FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/canary-app-1.0.0-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
