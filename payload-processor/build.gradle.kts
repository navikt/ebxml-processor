/*
 * This file was generated by the Gradle 'init' task.
 */

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
    id("io.ktor.plugin")
}

tasks {
    register<Wrapper>("wrapper") {
        gradleVersion="8.1.1"
    }
    shadowJar {
        archiveFileName.set("app.jar")
    }
    test {
        useJUnitPlatform()
    }
}

dependencies {
    implementation(project(":felles"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.call.logging.jvm)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.jakarta.xml.bind.api)
    implementation("org.glassfish.jaxb:jaxb-runtime:4.0.3")
    implementation(libs.bundles.logging)
    implementation("io.micrometer:micrometer-registry-prometheus:1.11.3")

    testImplementation(testLibs.junit.jupiter.api)
    testRuntimeOnly(testLibs.junit.jupiter.engine)
    implementation(kotlin("stdlib-jdk8"))
    implementation("ch.qos.logback:logback-classic:1.2.6")
    implementation("org.apache.logging.log4j:log4j-core")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl")
}

application {
    mainClass.set("no.nav.emottak.AppKt")
}
kotlin {
    jvmToolchain(17)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
