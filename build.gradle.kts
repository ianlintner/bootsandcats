plugins {
    id("org.springframework.boot") version "3.2.5" apply false
    id("io.spring.dependency-management") version "1.1.4" apply false
    kotlin("jvm") version "1.9.23" apply false
    id("com.diffplug.spotless") version "6.25.0"
    id("com.github.spotbugs") version "6.0.26" apply false
}

allprojects {
    group = "com.bootsandcats"
    version = "1.0.0-SNAPSHOT"
}

// Spotless configuration for the root project and all subprojects
spotless {
    java {
        target("**/src/**/*.java")
        googleJavaFormat("1.19.2").aosp()
        removeUnusedImports()
        importOrder("java", "javax", "org", "com", "")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

subprojects {
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "java")
    apply(plugin = "com.github.spotbugs")

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    repositories {
        mavenCentral()
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    // SpotBugs configuration for each subproject
    tasks.withType<com.github.spotbugs.snom.SpotBugsTask> {
        reports.create("html") {
            required.set(true)
        }
        reports.create("xml") {
            required.set(false)
        }
    }

    configure<com.github.spotbugs.snom.SpotBugsExtension> {
        effort.set(com.github.spotbugs.snom.Effort.MAX)
        reportLevel.set(com.github.spotbugs.snom.Confidence.MEDIUM)
    }

    // Add FindSecBugs plugin to SpotBugs
    dependencies {
        "spotbugsPlugins"("com.h3xstream.findsecbugs:findsecbugs-plugin:1.12.0")
    }
}
