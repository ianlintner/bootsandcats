plugins {
    id("io.micronaut.application") version "4.3.8"
    id("io.micronaut.test-resources") version "4.3.8"
}

val micronautVersion = "4.3.8"

micronaut {
    version.set(micronautVersion)
    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("com.bootsandcats.profilelite.*")
    }
}

application {
    mainClass.set("com.bootsandcats.profilelite.Application")
}

dependencies {
    annotationProcessor(platform("io.micronaut.platform:micronaut-platform:$micronautVersion"))
    implementation(platform("io.micronaut.platform:micronaut-platform:$micronautVersion"))

    annotationProcessor("io.micronaut:micronaut-inject-java")
    annotationProcessor("io.micronaut.validation:micronaut-validation-processor:$micronautVersion")

    implementation("io.micronaut:micronaut-http-server-netty")
    implementation("io.micronaut:micronaut-management")
    implementation("io.micronaut:micronaut-jackson-databind")
    implementation("io.micronaut.validation:micronaut-validation:$micronautVersion")
    implementation("io.micronaut.micrometer:micronaut-micrometer-registry-prometheus")
    implementation("ch.qos.logback:logback-classic")

    runtimeOnly("org.yaml:snakeyaml")

    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation("io.micronaut:micronaut-http-client")
    testImplementation("io.micronaut.test:micronaut-test-junit5")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}
