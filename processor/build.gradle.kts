/*
 * This file was generated by the Gradle 'init' task.
 */
val githubPassword: String by project

plugins {
    kotlin("jvm") version "1.9.0"
    application
    id("io.ktor.plugin") version "2.3.4"
}

tasks {

        shadowJar {
            archiveFileName.set("app.jar")
        }
    
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

tasks.register<Wrapper>("wrapper") {
    gradleVersion="8.1.1"
}

dependencies {
    implementation("io.ktor:ktor-server-core:2.3.4")
    implementation("io.ktor:ktor-server-netty:2.3.4")
    implementation("com.github.labai:labai-jsr305x-annotations:0.0.2")
    implementation("jakarta.xml.bind:jakarta.xml.bind-api:4.0.0", )
    implementation("org.glassfish.jaxb:jaxb-runtime:2.4.0-b180830.0438")
    //implementation("org.glassfish.jaxb:jaxb-runtime:4.0.3") // TODO: Latest. Krever at protokoll oppdateres
    implementation("no.nav.emottak:ebxml-protokoll:0.0.4")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
}

application {
    mainClass.set("ebxml.processor.app.AppKt")
}
