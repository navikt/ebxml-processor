plugins {
    kotlin("jvm") version "2.1.10"
    id("org.jlleitschuh.gradle.ktlint") version "11.6.1"
}

group = "no.nav.emottak"
version = "0.0.1"

tasks {
    ktlintFormat {
        this.enabled = true
    }
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
