/*
 * This file was generated by the Gradle 'init' task.
 */

plugins {
    kotlin("jvm") version "1.9.22"
    application
    id("io.ktor.plugin")
    kotlin("plugin.serialization")
    id("org.jlleitschuh.gradle.ktlint") version "11.6.1"
}

tasks {

    shadowJar {
        archiveFileName.set("app.jar")
    }
    test {
        useJUnitPlatform()
        testLogging {
            events("failed")
            showStandardStreams = true
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
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
    api(project(":felles"))
    api(project(":ebxml-processing-model"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.labai.jsr305x.annotations)
    implementation(libs.jakarta.xml.bind.api)
    implementation(libs.jaxb.runtime)
    implementation(libs.ebxml.protokoll)
    implementation(libs.hikari)
    implementation("no.nav:vault-jdbc:1.3.10")
    runtimeClasspath(libs.ojdbc8)
    implementation(libs.flyway.core)
    implementation(libs.bundles.exposed)
    implementation(libs.bundles.logging)
    implementation(libs.bundles.prometheus)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(testLibs.postgresql)
    implementation(libs.ktor.server.auth.jvm)
    implementation(libs.token.validation.ktor.v2)
    implementation(libs.ktor.client.auth)
    testRuntimeOnly(testLibs.junit.jupiter.engine)
    testImplementation(testLibs.mock.oauth2.server)
    testImplementation(testLibs.mockk.jvm)
    testImplementation(testLibs.mockk.dsl.jvm)
    testImplementation(testLibs.junit.jupiter.api)
    testImplementation(testLibs.bundles.kotest)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(testLibs.ktor.server.test.host)
    testRuntimeOnly(libs.ojdbc8)
    testImplementation("org.testcontainers:oracle-xe:1.19.4")
    testImplementation(kotlin("test"))

    runtimeOnly("org.postgresql:postgresql:42.6.0")
}

application {
    mainClass.set("no.nav.emottak.cpa.AppKt")
}
