plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.10")
    implementation("org.jetbrains.kotlin:kotlin-serialization:2.1.10")
    implementation("org.jlleitschuh.gradle:ktlint-gradle:11.6.1")
    implementation("io.ktor.plugin:io.ktor.plugin.gradle.plugin:3.0.3")
}
