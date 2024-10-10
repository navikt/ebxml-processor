import org.gradle.kotlin.dsl.testLibs

/*
 * This file was generated by the Gradle 'init' task.
 */

plugins {
    kotlin("jvm")
    application
    id("io.ktor.plugin")
    kotlin("plugin.serialization") apply true
    id("org.jlleitschuh.gradle.ktlint") version "11.6.1"
}

// kotlin {
//    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
// }

tasks {
    shadowJar {
        archiveFileName.set("app.jar")
    }
}

tasks.register<Wrapper>("wrapper") {
    gradleVersion = "8.1.1"
}

tasks.test {
    useJUnitPlatform()
}

tasks {

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
    implementation(project(":felles"))
    implementation(project(":ebxml-processing-api"))
    implementation("io.ktor:ktor-server-call-logging-jvm:2.3.4")
    implementation("io.ktor:ktor-server-core-jvm:2.3.4")
    testImplementation(project(":cpa-repo"))
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
    implementation(libs.bundles.prometheus)
    implementation(libs.apache.santuario)
    implementation("com.sun.xml.messaging.saaj:saaj-impl:3.0.2")
    implementation(libs.emottak.payload.xsd)
    // implementation("org.glassfish.jaxb:jaxb-runtime:4.0.3") // TODO: Latest. Krever at protokoll oppdateres
    implementation(libs.ebxml.protokoll)
    implementation(libs.ktor.client.auth)
    implementation("io.ktor:ktor-client-cio-jvm:2.3.4")
    implementation(libs.ktor.server.auth.jvm)
    implementation(libs.token.validation.ktor.v2)
    implementation(testLibs.postgresql)
    testImplementation(testLibs.mock.oauth2.server)
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
/*
ktlint {
    ignoreFailures.set(false)
}
 */
