plugins {
    id("io.gatling.gradle")
}

repositories {
    mavenCentral()
}

gatling {
    gatlingVersion = "3.11.5"
}

dependencies {
    gatling("io.gatling.highcharts:gatling-charts-highcharts:3.11.5")
}
