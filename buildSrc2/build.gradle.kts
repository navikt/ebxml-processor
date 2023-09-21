plugins {
    `kotlin-dsl` 
}

repositories {
    gradlePluginPortal() 
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.0")
    implementation("io.ktor.plugin:plugin:2.3.4")
    implementation("org.jetbrains.kotlin:kotlin-serialization:1.9.0")
}
