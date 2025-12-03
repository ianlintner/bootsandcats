plugins {
    id("java-library")
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.2.5"))
    api(project(":server-dao"))
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("io.micrometer:micrometer-core")
    implementation("com.azure:azure-identity:1.11.4")
    implementation("com.azure:azure-security-keyvault-secrets:4.8.2")
    implementation("com.nimbusds:nimbus-jose-jwt:9.37")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    compileOnly("org.projectlombok:lombok:1.18.32")
    annotationProcessor("org.projectlombok:lombok:1.18.32")
}
