plugins {
    kotlin("jvm")
    application
    id("io.ktor.plugin")
    kotlin("plugin.serialization") apply true
    id("org.jlleitschuh.gradle.ktlint") version "11.6.1"
}

tasks {
    register<Wrapper>("wrapper") {
        gradleVersion = "8.1.1"
    }
    shadowJar {
        archiveFileName.set("app.jar")
        isZip64 = true
    }
    test {
        useJUnitPlatform()
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
    }
}

dependencies {
    implementation(project(":felles"))
    implementation(project(":ebms-provider"))
    implementation(project(":ebxml-processing-model"))
    implementation(libs.arrow.core)
    implementation(libs.arrow.fx.coroutines)
    implementation(libs.arrow.resilience)
    implementation(libs.arrow.suspendapp)
    implementation(libs.arrow.suspendapp.ktor)
    implementation(libs.ktor.server.call.logging.jvm)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.core.jvm)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.cio.jvm)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.auth)
    implementation(libs.ktor.server.auth.jvm)
    implementation(libs.labai.jsr305x.annotations)
    implementation(libs.jakarta.xml.bind.api)
    implementation(libs.jaxb.runtime)
    implementation(libs.hikari)
    implementation(libs.flyway.core)
    implementation(libs.bundles.exposed)
    implementation(libs.bundles.logging)
    implementation(libs.bundles.prometheus)
    implementation(libs.apache.santuario)
    implementation(libs.kotlin.kafka)
    implementation(libs.hoplite.core)
    implementation(libs.hoplite.hocon)
    implementation("com.sun.xml.messaging.saaj:saaj-impl:3.0.2")
    implementation(libs.emottak.payload.xsd)
    implementation(libs.emottak.utils)
    // implementation("org.glassfish.jaxb:jaxb-runtime:4.0.3") // TODO: Latest. Krever at protokoll oppdateres
    implementation(libs.ebxml.protokoll)
    implementation(libs.token.validation.ktor.v3)
    implementation(testLibs.postgresql)
    implementation("no.nav:vault-jdbc:1.3.10")

    testImplementation(project(":cpa-repo"))
    testImplementation(project(":ebms-provider"))
    testImplementation(testLibs.mock.oauth2.server)
    testImplementation(testLibs.ktor.server.test.host)
    testImplementation(testLibs.junit.jupiter.api)
    testImplementation(testLibs.mockk.jvm)
    testImplementation(testLibs.mockk.dsl.jvm)
    testImplementation(libs.apache.santuario)
    testImplementation("org.testcontainers:kafka:1.19.0")
    testRuntimeOnly(testLibs.junit.jupiter.engine)
}

application {
    mainClass.set("no.nav.emottak.ebms.async.AppKt")
}
