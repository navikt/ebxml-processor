/*
 * This file was generated by the Gradle 'init' task.
 */

plugins {
    kotlin("jvm")
    application
    id("io.ktor.plugin")
    kotlin("plugin.serialization")
}

tasks {

    shadowJar {
        archiveFileName.set("app.jar")
    }
    test {
        useJUnitPlatform()
    }

}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.bundles.logging)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.bundles.jakarta.mail)
    testRuntimeOnly(testLibs.junit.jupiter.engine)
    testImplementation(testLibs.mockk.jvm)
    testImplementation(testLibs.mockk.dsl.jvm)
    testImplementation(testLibs.junit.jupiter.api)
    testImplementation(testLibs.bundles.kotest)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(testLibs.ktor.server.test.host)
    testImplementation("com.icegreen:greenmail:2.1.0-alpha-3")
    testImplementation("com.icegreen:greenmail-junit5:2.1.0-alpha-3")
    testImplementation(kotlin("test"))

    implementation(kotlin("stdlib-jdk8"))
}

application {
    mainClass.set("no.nav.emottak.smtp.AppKt")
}
