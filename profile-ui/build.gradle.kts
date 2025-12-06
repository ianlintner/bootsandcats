plugins {
    id("org.springframework.boot") version "4.0.0"
    id("io.spring.dependency-management")
    id("java")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("de.codecentric:spring-boot-admin-starter-client:3.4.0")
    
    compileOnly("org.projectlombok:lombok:1.18.32")
    annotationProcessor("org.projectlombok:lombok:1.18.32")
    
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

springBoot {
    mainClass.set("com.bootsandcats.profile.ProfileUiApplication")
}

val npmInstall by tasks.registering(Exec::class) {
    workingDir = projectDir
    commandLine("npm", "install")
    inputs.file("package.json")
    outputs.dir("node_modules")
}

val buildProfileCss by tasks.registering(Exec::class) {
    workingDir = projectDir
    commandLine("npm", "run", "build:css")
    inputs.files(fileTree("src/main/frontend"))
    inputs.file("package.json")
    inputs.file("tailwind.config.js")
    outputs.file("src/main/resources/static/css/profile.css")
    dependsOn(npmInstall)
}

tasks.named("processResources") {
    dependsOn(buildProfileCss)
}

tasks.named("clean") {
    doFirst {
        delete("node_modules", "src/main/resources/static/css/profile.css")
    }
}
