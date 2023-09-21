/*
 * This file was generated by the Gradle 'init' task.
 */
val githubPassword: String by project

plugins {
    kotlin("jvm") version "1.9.0"
    kotlin("plugin.serialization") version "1.9.0"
    application
    id("io.ktor.plugin") version "2.3.4"
}

repositories {
    mavenCentral()
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/navikt/ebxml-protokoll")
        credentials {
            username = "x-access-token"
            password = githubPassword
        }
    }
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
    implementation("io.ktor:ktor-server-core:2.3.4")
    implementation("io.ktor:ktor-server-netty:2.3.4")
    implementation("io.ktor:ktor-server-call-logging-jvm:2.3.4")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.4")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.4")
    implementation("io.ktor:ktor-client-core:2.3.4")
    implementation("io.ktor:ktor-client-cio:2.3.4")
    implementation("jakarta.xml.bind:jakarta.xml.bind-api:4.0.0", )
    implementation("org.glassfish.jaxb:jaxb-runtime:4.0.3")
    implementation("org.bouncycastle:bcprov-jdk18on:1.76")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.76")
    implementation("ch.qos.logback:logback-classic:1.4.11")
    implementation("io.micrometer:micrometer-registry-prometheus:1.11.3")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
    implementation(kotlin("stdlib-jdk8"))
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