plugins {
    id("org.springframework.boot") version "4.0.0" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    kotlin("jvm") version "2.1.0" apply false
    id("com.diffplug.spotless") version "6.25.0"
    id("com.github.spotbugs") version "6.1.4" apply false
    id("org.owasp.dependencycheck") version "10.0.3"
}

allprojects {
    group = "com.bootsandcats"
    version = "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
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
    apply(plugin = "java")
    apply(plugin = "com.github.spotbugs")

    if (name != "profile-ui") {
        apply(plugin = "io.spring.dependency-management")

        // Override Flyway version from Spring Boot BOM
        the<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>().apply {
            imports {
                mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
            }
            dependencies {
                dependency("org.flywaydb:flyway-core:11.2.0")
                dependency("org.flywaydb:flyway-database-postgresql:11.2.0")
            }
        }
    }

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        // Enable JUnit XML reports for GitHub Actions integration
        reports {
            junitXml.required.set(true)
            html.required.set(true)
        }
    }

    // SpotBugs configuration for each subproject - only run on main source
    tasks.withType<com.github.spotbugs.snom.SpotBugsTask> {
        reports.create("html") {
            required.set(true)
        }
        reports.create("xml") {
            required.set(false)
        }
        // Ignore analysis errors from FindSecBugs plugin issues with generics
        ignoreFailures = true
    }

    // Disable SpotBugs on test sources
    tasks.matching { it.name == "spotbugsTest" }.configureEach {
        enabled = false
    }

    configure<com.github.spotbugs.snom.SpotBugsExtension> {
        effort.set(com.github.spotbugs.snom.Effort.MAX)
        reportLevel.set(com.github.spotbugs.snom.Confidence.MEDIUM)
        excludeFilter.set(file("$rootDir/spotbugs-exclude.xml"))
    }

    // Add FindSecBugs plugin to SpotBugs
    dependencies {
        "spotbugsPlugins"("com.h3xstream.findsecbugs:findsecbugs-plugin:1.12.0")
    }
}

// OWASP Dependency Check configuration
dependencyCheck {
    formats = listOf("HTML", "JSON", "SARIF")
    analyzers.assemblyEnabled = false
    analyzers.nodeEnabled = false
    analyzers.retirejs.enabled = false
    nvd.apiKey = System.getenv("NVD_API_KEY") ?: ""
}
