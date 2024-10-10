/*
 * This file was generated by the Gradle 'init' task.
 */

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

tasks {
    register<Wrapper>("wrapper") {
        gradleVersion="8.1.1"
    }
    test {
        useJUnitPlatform()
    }
}

dependencies {
    implementation(libs.ebxml.protokoll)
    implementation(libs.emottak.payload.xsd)
    implementation(libs.hikari)
    api("dev.reformator.stacktracedecoroutinator:stacktrace-decoroutinator-jvm:2.3.8")
    implementation(libs.flyway.core)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.apache.santuario)
    implementation(libs.bundles.logging)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation("com.bettercloud:vault-java-driver:5.1.0")
    api(libs.bundles.bouncycastle)
    testImplementation(testLibs.junit.jupiter.api)
    testImplementation(testLibs.junit.jupiter.engine)

    runtimeOnly("org.postgresql:postgresql:42.6.0")
}

