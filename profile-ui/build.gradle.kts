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
        annotations("com.bootsandcats.profileui.*")
    }
}

application {
    mainClass.set("com.bootsandcats.profileui.Application")
}

// Produce a self-contained (fat) jar for container images
val fatJar = tasks.register<Jar>("fatJar") {
    group = "build"
    description = "Assembles a fat jar containing all runtime dependencies"
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(sourceSets.main.get().output)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith(".jar") }
            .map { zipTree(it) }
    })

    manifest {
        attributes(
            "Main-Class" to application.mainClass.get()
        )
    }
}

tasks.named("assemble") {
    dependsOn(fatJar)
}

dependencies {
    annotationProcessor(platform("io.micronaut.platform:micronaut-platform:$micronautVersion"))
    implementation(platform("io.micronaut.platform:micronaut-platform:$micronautVersion"))

    annotationProcessor("io.micronaut:micronaut-inject-java")
    annotationProcessor("io.micronaut.validation:micronaut-validation-processor:$micronautVersion")
    annotationProcessor("io.micronaut.serde:micronaut-serde-processor")

    implementation("io.micronaut:micronaut-http-server-netty")
    implementation("io.micronaut:micronaut-management")
    implementation("io.micronaut:micronaut-jackson-databind")
    implementation("io.micronaut.security:micronaut-security-oauth2")
    implementation("io.micronaut.security:micronaut-security-jwt")
    implementation("io.micronaut.validation:micronaut-validation:$micronautVersion")
    implementation("io.micronaut.micrometer:micronaut-micrometer-registry-prometheus")
    implementation("ch.qos.logback:logback-classic")

    // MongoDB support for Azure CosmosDB
    implementation("io.micronaut.mongodb:micronaut-mongo-sync")
    implementation("io.micronaut.serde:micronaut-serde-jackson")

    runtimeOnly("org.yaml:snakeyaml")

    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation("io.micronaut:micronaut-http-client")
    testImplementation("io.micronaut.test:micronaut-test-junit5")
    testImplementation("de.flapdoodle.embed:de.flapdoodle.embed.mongo:4.12.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}
