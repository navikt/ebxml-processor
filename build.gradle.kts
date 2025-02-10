import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java-library")
    kotlin("jvm") version "2.1.10" apply false
    kotlin("plugin.serialization") version "2.1.10" // apply false
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
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
        freeCompilerArgs = listOf("-opt-in=kotlin.uuid.ExperimentalUuidApi")
    }
}
