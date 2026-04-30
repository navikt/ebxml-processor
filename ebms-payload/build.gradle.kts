plugins {
    id("ktor-application-conventions")
}

application {
    mainClass.set("no.nav.emottak.payload.AppKt")
}

dependencies {
    implementation(project(":felles"))
    implementation(project(":ebxml-processing-model"))
    implementation(libs.ktor.server.core)
    implementation(libs.apache.santuario)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.call.logging.jvm)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.auth.jvm)
    implementation(libs.token.validation.ktor.v3)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.hoplite.core)
    implementation(libs.hoplite.hocon)
    implementation(libs.ebxml.protokoll)
    implementation(libs.jakarta.xml.bind.api)
    implementation(libs.jaxb.runtime)
    implementation(libs.bundles.logging)
    implementation(libs.bundles.prometheus)
    implementation(libs.emottak.payload.xsd)
    implementation(libs.emottak.utils)
    implementation("net.sf.saxon:Saxon-HE:12.7")
    implementation(libs.jackson.module.kotlin)
    implementation(libs.jackson.dataformat.yaml)
    implementation(kotlin("stdlib-jdk8"))
    runtimeOnly("net.java.dev.jna:jna:5.12.1")

    testImplementation(testLibs.junit.jupiter.api)
    testImplementation(testLibs.ktor.server.test.host)
    testImplementation(testLibs.mock.oauth2.server)
    testImplementation(testLibs.mockk.jvm)
    testRuntimeOnly(testLibs.junit.jupiter.engine)
}
