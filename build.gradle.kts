plugins {
    id("org.springframework.boot") version "3.2.5" apply false
    id("io.spring.dependency-management") version "1.1.4" apply false
    kotlin("jvm") version "1.9.23" apply false
    id("java")
}

java {
    toolchain {
        // Spring Boot 3.2.x supports Java 17+; Java 21 is the current LTS and fully supported
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

allprojects {
    group = "com.bootsandcats"
    version = "1.0.0-SNAPSHOT"
}

subprojects {
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "java")

    repositories {
        mavenCentral()
    }

    dependencies {
        constraints {
            implementation("org.springframework.boot:spring-boot-starter")
            implementation("org.springframework.boot:spring-boot-starter-validation")
            implementation("org.springframework.boot:spring-boot-starter-security")
            implementation("org.springframework.boot:spring-boot-starter-oauth2-authorization-server")
            implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0")
            implementation("io.micrometer:micrometer-registry-prometheus")
            testImplementation("org.springframework.boot:spring-boot-starter-test")
            compileOnly("org.projectlombok:lombok:1.18.32")
            annotationProcessor("org.projectlombok:lombok:1.18.32")
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
