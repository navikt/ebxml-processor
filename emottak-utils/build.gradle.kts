plugins {
    kotlin("jvm") version "2.1.10"
    id("maven-publish")
    id("org.jlleitschuh.gradle.ktlint") version "11.6.1"
    id("org.jlleitschuh.gradle.ktlint-idea") version "11.6.1"
}

tasks {
    ktlintFormat {
        this.enabled = true
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = "no.nav.emottak"
            artifactId = "emottak-utils"
            version = "0.0.3"
            from(components["java"])
        }
    }
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/navikt/${rootProject.name}")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

dependencies {
    implementation(libs.ebxml.protokoll)
    implementation("no.nav.emottak:ebxml-processing-model:2025021825a9b1c43bc343c4487e454338547a5932d8daa1")
    implementation("io.ktor:ktor-http:2.3.4")
    implementation(libs.bundles.logging)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
