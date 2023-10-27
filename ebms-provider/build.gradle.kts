import org.gradle.kotlin.dsl.testLibs
import org.jetbrains.kotlin.ir.backend.js.lower.collectNativeImplementations

/*
 * This file was generated by the Gradle 'init' task.
 */

plugins {
    kotlin("jvm")
    application
    id("io.ktor.plugin")
}

tasks {
        shadowJar {
            archiveFileName.set("app.jar")
        }
}

tasks.register<Wrapper>("wrapper") {
    gradleVersion="8.1.1"
}

tasks.test {
    useJUnitPlatform()
}

dependencies {
    implementation(project(":felles"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.labai.jsr305x.annotations)
    implementation(libs.jakarta.xml.bind.api)
    implementation(libs.jaxb.runtime)
    implementation(libs.hikari)
    implementation(libs.flyway.core)
    implementation(libs.bundles.exposed)
    implementation(libs.bundles.logging)
    implementation(libs.apache.santuario)
    implementation("com.sun.xml.messaging.saaj:saaj-impl:3.0.2")
    //implementation("org.glassfish.jaxb:jaxb-runtime:4.0.3") // TODO: Latest. Krever at protokoll oppdateres
    implementation(libs.ebxml.protokoll)
    implementation("io.ktor:ktor-client-cio-jvm:2.3.4")
    testImplementation(testLibs.ktor.server.test.host)
    testImplementation(testLibs.junit.jupiter.api)
    testImplementation(testLibs.mockk.jvm)
    testImplementation(testLibs.mockk.dsl.jvm)
    testImplementation(libs.apache.santuario)
   // testImplementation(testLibs.mockk.jvm)
    testRuntimeOnly(testLibs.junit.jupiter.engine)
}

application {
    mainClass.set("no.nav.emottak.ebms.AppKt")
}
