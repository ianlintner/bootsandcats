plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("java")
}

// OpenTelemetry Java Agent version - use agent approach instead of starter library
// The agent attaches via -javaagent and is compatible with any Spring Boot version
val otelAgentVersion = "2.12.0"

dependencies {
    implementation(project(":oauth2-server:server-run"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.security:spring-security-oauth2-authorization-server")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.session:spring-session-data-redis")
    implementation("org.apache.commons:commons-pool2")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.0")
    implementation("io.micrometer:micrometer-registry-prometheus")
    // OpenTelemetry API for manual instrumentation (optional, agent provides auto-instrumentation)
    implementation("io.opentelemetry:opentelemetry-api:1.44.0")
    // NOTE: micrometer-tracing-bridge-otel is NOT compatible with Spring Boot 4.0.0 
    // because it depends on OTel SDK classes that reference removed Spring classes.
    // Use the OTel Java Agent (-javaagent) for tracing instead - it instruments at runtime
    // without compile-time dependencies on OTel SDK autoconfiguration.
    // implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("com.azure:azure-identity:1.15.0")
    implementation("com.azure:azure-security-keyvault-secrets:4.8.3")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    // implementation("de.codecentric:spring-boot-admin-starter-client:3.4.0")
    // implementation("de.codecentric:spring-boot-admin-starter-server:3.4.0")
    implementation("org.flywaydb:flyway-core:11.2.0")
    implementation("org.flywaydb:flyway-database-postgresql:11.2.0")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("com.h2database:h2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-test")
    testImplementation("org.springframework.boot:spring-boot-test-autoconfigure")
    testImplementation("org.springframework.security:spring-security-test")
    // Testcontainers
    testImplementation("org.testcontainers:testcontainers:1.20.4")
    testImplementation("org.testcontainers:postgresql:1.20.4")
    testImplementation("org.testcontainers:junit-jupiter:1.20.4")
    // Redis Testcontainers (uses GenericContainer with Redis image)
    testImplementation("com.redis:testcontainers-redis:2.2.2")
    // WireMock for mocking external OIDC providers (GitHub, Google, etc.)
    testImplementation("org.wiremock:wiremock-standalone:3.9.2")
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

// Copy Flyway migrations from server-dao to server-ui resources for bootJar packaging
val copyMigrations by tasks.registering(Copy::class) {
    from(project(":oauth2-server:server-dao").file("src/main/resources/db/migration"))
    into(layout.buildDirectory.dir("resources/main/db/migration"))
}

tasks.named("processResources") {
    dependsOn(copyMigrations)
}

// Configure default test task to exclude testcontainers tests (run those separately)
tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("testcontainers")
    }
    testLogging {
        events("passed", "skipped", "failed")
    }
}

// Behavioral Test Tasks for AI-Agent-Friendly Testing
tasks.register<Test>("behavioralTests") {
    description = "Runs behavioral tests (happy/sad paths)"
    group = "verification"
    useJUnitPlatform {
        includeTags("happy", "sad")
    }
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

tasks.register<Test>("happyPathTests") {
    description = "Runs happy path tests only"
    group = "verification"
    useJUnitPlatform {
        includeTags("happy")
    }
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

tasks.register<Test>("sadPathTests") {
    description = "Runs sad path tests only"
    group = "verification"
    useJUnitPlatform {
        includeTags("sad")
    }
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

tasks.register<Test>("contractTests") {
    description = "Runs contract tests (API compliance)"
    group = "verification"
    useJUnitPlatform {
        includeTags("contract")
    }
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

tasks.register<Test>("securityTests") {
    description = "Runs security regression tests"
    group = "verification"
    useJUnitPlatform {
        includeTags("security")
    }
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

tasks.register<Test>("criticalTests") {
    description = "Runs all critical tests (must pass for deployment)"
    group = "verification"
    useJUnitPlatform {
        includeTags("critical")
    }
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

tasks.register<Test>("oauth2Tests") {
    description = "Runs all OAuth2-specific tests"
    group = "verification"
    useJUnitPlatform {
        includeTags("oauth2")
    }
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

tasks.register<Test>("fastTests") {
    description = "Runs fast unit tests (excludes integration tests)"
    group = "verification"
    useJUnitPlatform {
        excludeTags("integration", "e2e", "slow", "testcontainers")
    }
    testLogging {
        events("passed", "skipped", "failed")
    }
}

// Testcontainers Integration Test Tasks
tasks.register<Test>("testcontainersTests") {
    description = "Runs all Testcontainers integration tests (PostgreSQL, Redis, WireMock)"
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("testcontainers")
    }
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
    // Give containers more time to start
    systemProperty("junit.jupiter.execution.timeout.default", "5m")
}

tasks.register<Test>("postgresTests") {
    description = "Runs PostgreSQL Testcontainers integration tests"
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("postgres")
    }
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
    systemProperty("junit.jupiter.execution.timeout.default", "5m")
}

tasks.register<Test>("redisTests") {
    description = "Runs Redis Testcontainers integration tests"
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("redis")
    }
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
    systemProperty("junit.jupiter.execution.timeout.default", "5m")
}

tasks.register<Test>("federatedIdentityTests") {
    description = "Runs federated identity flow tests with mocked OAuth providers"
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("oauth-flow")
    }
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}
