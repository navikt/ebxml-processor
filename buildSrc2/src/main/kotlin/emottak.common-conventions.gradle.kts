plugins {
    id("org.jetbrains.kotlin.jvm") 
}

repositories {
        mavenCentral()
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/navikt/ebxml-protokoll")
            credentials {
                username = "token"
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }

tasks.register<Wrapper>("wrapper") {
    gradleVersion="8.1.1"
}
