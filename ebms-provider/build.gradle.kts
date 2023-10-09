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
    implementation(libs.labai.jsr305x.annotations)
    implementation(libs.jakarta.xml.bind.api)
    implementation(libs.jaxb.runtime)
    implementation(libs.hikari)
    implementation(libs.flyway.core)
    implementation(libs.bundles.exposed)
    implementation(libs.bundles.logging)
    implementation(libs.apache.santuario)
    //implementation("org.glassfish.jaxb:jaxb-runtime:4.0.3") // TODO: Latest. Krever at protokoll oppdateres
    implementation(libs.ebxml.protokoll)
    testImplementation(testLibs.junit.jupiter.api)
    testRuntimeOnly(testLibs.junit.jupiter.engine)
}

application {
    mainClass.set("no.nav.emottak.ebms.AppKt")
}
