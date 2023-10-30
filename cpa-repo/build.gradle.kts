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
    api(project(":felles"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.labai.jsr305x.annotations)
    implementation(libs.jakarta.xml.bind.api)
    implementation(libs.jaxb.runtime)
    implementation(libs.ebxml.protokoll)
    implementation(libs.hikari)
    implementation(libs.flyway.core)
    implementation(libs.bundles.exposed)
    implementation(libs.bundles.logging)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(testLibs.postgresql)
    testRuntimeOnly(testLibs.junit.jupiter.engine)
    testImplementation(testLibs.junit.jupiter.api)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(testLibs.ktor.server.test.host)
    testImplementation(kotlin("test"))

    runtimeOnly("org.postgresql:postgresql:42.6.0")
}

application {
    mainClass.set("no.nav.emottak.cpa.AppKt")
}
