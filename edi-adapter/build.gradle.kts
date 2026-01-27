import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    application
    kotlin("jvm")
    kotlin("plugin.serialization") version "2.1.10"
    id("io.ktor.plugin") version "3.0.3"
    id("org.jlleitschuh.gradle.ktlint") version "11.6.1"
    id("com.gradleup.shadow")
}

tasks {
    shadowJar {
        archiveFileName.set("app.jar")
    }
    test {
        useJUnitPlatform()
    }
    ktlintFormat {
        enabled = true
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
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
        freeCompilerArgs.add("-opt-in=kotlin.uuid.ExperimentalUuidApi")
        freeCompilerArgs.add("-opt-in=kotlin.io.encoding.ExperimentalEncodingApi")
        freeCompilerArgs.add("-opt-in=arrow.fx.coroutines.await.ExperimentalAwaitAllApi")
        freeCompilerArgs.add("-opt-in=kotlinx.serialization.ExperimentalSerializationApi")
    }
}

dependencies {
    implementation(libs.arrow.core)
    implementation(libs.edi.adapter.model)
    implementation(libs.arrow.functions)
    implementation(libs.bundles.prometheus)
    implementation(libs.arrow.fx.coroutines)
    implementation(libs.arrow.resilience)
    implementation(libs.arrow.suspendapp)
    implementation(libs.arrow.suspendapp.ktor)
    implementation(libs.bundles.logging)
    implementation(libs.bundles.prometheus)
    implementation(libs.hoplite.core)
    implementation(libs.hoplite.hocon)
    implementation(libs.jwt)
    implementation(libs.nimbus.jwt)
    implementation(libs.ktor.client.auth)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.auth.jvm)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.openapi)
    implementation(libs.ktor.swagger.ui)
    implementation(libs.schema.kenerator.serialization)
    implementation(libs.ktor.server.netty)
    implementation(libs.kotlin.logging)
    implementation(libs.token.validation.ktor.v3)

    testImplementation(testLibs.bundles.kotest)
    testImplementation(testLibs.kotest.assertions.arrow)
    testImplementation(testLibs.kotest.extensions.jvm)
    testImplementation(testLibs.kotest.extensions.testcontainers)
    testImplementation(testLibs.ktor.server.test.host)
    testImplementation(testLibs.ktor.client.mock)
    testImplementation(testLibs.mock.oauth2.server)
    testImplementation(testLibs.testcontainers)
    testImplementation(testLibs.testcontainers.postgresql)
    testImplementation(testLibs.turbine)

    testImplementation(kotlin("test"))
}

application {
    mainClass.set("no.nav.emottak.ediadapter.server.AppKt")
}
