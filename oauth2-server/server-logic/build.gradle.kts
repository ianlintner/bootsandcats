plugins {
    id("java-library")
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.0.0"))
    api(project(":oauth2-server:server-dao"))
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.security:spring-security-oauth2-authorization-server")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("io.micrometer:micrometer-core")
    implementation("com.azure:azure-identity:1.15.0")
    implementation("com.azure:azure-security-keyvault-secrets:4.8.3")
    implementation("com.nimbusds:nimbus-jose-jwt:9.40")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    compileOnly("org.projectlombok:lombok:1.18.32")
    annotationProcessor("org.projectlombok:lombok:1.18.32")
}

tasks.register<JavaExec>("runGenerator") {
    group = "tools"
    description = "Generate EC P-256 JWK for OAuth2 token signing"
    mainClass.set("com.bootsandcats.oauth2.tools.EcJwkGenerator")
    classpath = sourceSets["main"].runtimeClasspath
    isIgnoreExitValue = false
}
