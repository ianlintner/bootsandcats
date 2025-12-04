plugins {
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management")
    id("java")
}

dependencies {
    implementation(project(":server-logic"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.security:spring-security-oauth2-authorization-server:1.2.4")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-api:1.36.0")
    implementation("io.opentelemetry:opentelemetry-sdk:1.36.0")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp:1.36.0")
    implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure:1.36.0")
    implementation("io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter:2.10.0")
    implementation("com.azure:azure-identity:1.11.4")
    implementation("com.azure:azure-security-keyvault-secrets:4.8.2")
    implementation("de.codecentric:spring-boot-admin-starter-client:3.2.3")
    implementation("de.codecentric:spring-boot-admin-starter-server:3.2.3")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("com.h2database:h2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:testcontainers:1.19.7")
    testImplementation("org.testcontainers:postgresql:1.19.7")
    testImplementation("org.testcontainers:junit-jupiter:1.19.7")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    
    compileOnly("org.projectlombok:lombok:1.18.32")
    annotationProcessor("org.projectlombok:lombok:1.18.32")
}

springBoot {
    mainClass.set("com.bootsandcats.oauth2.OAuth2AuthorizationServerApplication")
}

tasks.named<Jar>("jar") {
    enabled = false
}
