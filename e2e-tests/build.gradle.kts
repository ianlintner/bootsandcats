plugins {
    id("java")
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.rest-assured:rest-assured:5.4.0")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation("org.jsoup:jsoup:1.17.2")
    testImplementation("com.fasterxml.jackson.core:jackson-databind")
    testImplementation("org.slf4j:slf4j-api:2.0.16")
}

tasks.test {
    useJUnitPlatform()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
