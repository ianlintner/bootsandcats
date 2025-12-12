plugins {
    id("java-library")
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.0.0"))
    
    // Jackson for JSON serialization
    implementation("com.fasterxml.jackson.core:jackson-databind")

    // Jakarta Bean Validation annotations (e.g., @NotBlank) used by DTOs
    compileOnly("jakarta.validation:jakarta.validation-api")
    
    // Lombok for reducing boilerplate
    compileOnly("org.projectlombok:lombok:1.18.32")
    annotationProcessor("org.projectlombok:lombok:1.18.32")
    
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
