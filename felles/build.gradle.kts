/*
 * This file was generated by the Gradle 'init' task.
 */

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jlleitschuh.gradle.ktlint") version "11.6.1"
}

tasks {
    register<Wrapper>("wrapper") {
        gradleVersion = "8.1.1"
    }
    test {
        useJUnitPlatform()
    }
    ktlintFormat {
        this.enabled = true
    }
    ktlintCheck {
        dependsOn("ktlintFormat")
    }
    build {
        dependsOn("ktlintCheck")
    }
}

dependencies {
    implementation(project(":ebxml-processing-model"))
    implementation(libs.emottak.utils)
    implementation(libs.ebxml.protokoll)
    implementation(libs.emottak.payload.xsd)
    implementation(libs.guava)
    api("dev.reformator.stacktracedecoroutinator:stacktrace-decoroutinator-jvm:2.3.8")
    implementation(libs.flyway.core)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.apache.santuario)
    implementation(libs.bundles.logging)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    api(libs.bundles.bouncycastle)
    testImplementation(testLibs.junit.jupiter.api)
    testImplementation(testLibs.junit.jupiter.engine)

    runtimeOnly("org.postgresql:postgresql:42.7.3")
}
