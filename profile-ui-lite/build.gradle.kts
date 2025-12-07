plugins {
    id("io.micronaut.application") version "4.3.8"
    id("io.micronaut.test-resources") version "4.3.8"
}

micronaut {
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
    annotationProcessor(mn.micronaut.inject.java)
    annotationProcessor(mn.micronaut.validation.processor)

    implementation(mn.micronaut.http.server.netty)
    implementation(mn.micronaut.management)
    implementation(mn.micronaut.validation)
    implementation("io.micronaut.micrometer:micronaut-micrometer-registry-prometheus")
    implementation("ch.qos.logback:logback-classic")

    testImplementation(mn.micronaut.http.client)
    testImplementation(mn.micronaut.test.junit5)
    testRuntimeOnly(mn.junit.jupiter.engine)
}
