plugins {
    id("java-library")
    kotlin("jvm") apply false
    kotlin("plugin.serialization")
    id("io.ktor.plugin") apply false
    id("org.jlleitschuh.gradle.ktlint")
    id("org.jlleitschuh.gradle.ktlint-idea")
}

tasks {
    ktlintFormat {
        this.enabled = true
    }
}
