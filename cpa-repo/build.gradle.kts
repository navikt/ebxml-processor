

plugins {
    kotlin("jvm") version "2.1.10"
    application
    id("io.ktor.plugin")
    kotlin("plugin.serialization")
    id("org.jlleitschuh.gradle.ktlint") version "11.6.1"
}

tasks {

    shadowJar {
        isZip64 = true
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

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
    compilerOptions {
        freeCompilerArgs = listOf("-opt-in=kotlin.uuid.ExperimentalUuidApi,com.sksamuel.hoplite.ExperimentalHoplite")
        freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
        freeCompilerArgs.add("-opt-in=kotlin.uuid.ExperimentalUuidApi")
        freeCompilerArgs.add("-opt-in=kotlin.io.encoding.ExperimentalEncodingApi")
        freeCompilerArgs.add("-opt-in=arrow.fx.coroutines.await.ExperimentalAwaitAllApi")
        freeCompilerArgs.add("-opt-in=kotlinx.serialization.ExperimentalSerializationApi")
    }
}

dependencies {
    api(project(":felles"))
    api(project(":ebxml-processing-model"))

    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")
    implementation(libs.jwt)
    implementation(libs.nimbus.jwt)
    implementation(libs.bundles.prometheus)
    implementation(libs.arrow.core)
    implementation(libs.arrow.functions)
    implementation(libs.arrow.fx.coroutines)
    implementation(libs.arrow.resilience)
    implementation(libs.arrow.suspendapp)
    implementation(libs.arrow.suspendapp.ktor)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.server.auth.jvm)
    implementation(libs.ktor.client.auth)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.swagger.ui)
    implementation(libs.ktor.openapi)
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
    implementation(libs.hoplite.core)
    implementation(libs.hoplite.hocon)
    implementation(libs.kotlin.logging)
    implementation(testLibs.postgresql)
    implementation(libs.token.validation.ktor.v3)
    implementation(libs.emottak.utils)

    testRuntimeOnly(testLibs.junit.jupiter.engine)
    testImplementation(testLibs.mock.oauth2.server)
    testImplementation(testLibs.mockk.jvm)
    testImplementation(testLibs.mockk.dsl.jvm)
    testImplementation(testLibs.junit.jupiter.api)
    testImplementation(testLibs.bundles.kotest)
    testImplementation(testLibs.kotest.assertions.arrow)
    testImplementation(testLibs.kotest.extensions.jvm)
    testImplementation(testLibs.kotest.extensions.testcontainers)
    testImplementation(testLibs.ktor.server.test.host)
    testImplementation(testLibs.ktor.client.mock)
    testImplementation(testLibs.mock.oauth2.server)
    testImplementation(testLibs.testcontainers)
    testImplementation(testLibs.turbine)
    testRuntimeOnly(libs.ojdbc8)
    testImplementation("org.testcontainers:oracle-xe:1.19.4")
    testImplementation(kotlin("test"))

    runtimeOnly("org.postgresql:postgresql:42.7.3")
}

application {
    mainClass.set("no.nav.emottak.cpa.AppKt")
}
