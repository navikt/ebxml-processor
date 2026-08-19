plugins {
    id("kotlin-conventions")
    application
    id("io.ktor.plugin")
}

tasks.named<Jar>("shadowJar") {
    archiveFileName.set("app.jar")
    isZip64 = true
}
