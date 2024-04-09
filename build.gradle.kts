
plugins {
    id("java-library")
    kotlin("jvm") version "1.9.22" apply false
    kotlin("plugin.serialization") version "1.9.0" // apply false
    id("io.ktor.plugin") version "2.3.4" apply false
    id("org.jlleitschuh.gradle.ktlint") version "11.6.1"
    id("org.jlleitschuh.gradle.ktlint-idea") version "11.6.1"
}

tasks {
    ktlintFormat {
        this.enabled = true
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
    kotlinOptions {
        jvmTarget = "21"
    }
}
