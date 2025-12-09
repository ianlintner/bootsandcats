plugins {
    id("java-library")
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.0.0"))
    
    // Project dependencies
    api(project(":oauth2-server:server-model"))
    
    api("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    compileOnly("org.projectlombok:lombok:1.18.32")
    annotationProcessor("org.projectlombok:lombok:1.18.32")
}
