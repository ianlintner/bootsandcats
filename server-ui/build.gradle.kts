plugins {
    id("org.springframework.boot") version "3.2.5"
    id("io.spring.dependency-management")
    id("java")
}

dependencies {
    implementation(project(":server-logic"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-authorization-server")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0")
    implementation("io.micrometer:micrometer-registry-prometheus")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
}

springBoot {
    mainClass.set("com.bootsandcats.oauth2.OAuth2AuthorizationServerApplication")
}

// Temporarily reuse existing monolith sources during migration
sourceSets {
    val monolithMainJava = file("../src/main/java")
    val monolithMainResources = file("../src/main/resources")
    val monolithTestJava = file("../src/test/java")
    val monolithTestResources = file("../src/test/resources")

    main {
        java.srcDir(monolithMainJava)
        resources.srcDir(monolithMainResources)
    }
    test {
        java.srcDir(monolithTestJava)
        resources.srcDir(monolithTestResources)
    }
}
