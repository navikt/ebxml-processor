plugins {
    id("kotlin-conventions")
}

dependencies {
    implementation(project(":ebxml-processing-model"))
    implementation(libs.emottak.utils)
    implementation(libs.ebxml.protokoll)
    implementation(libs.guava)
    implementation(libs.flyway.core)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.apache.santuario)
    implementation(libs.bundles.logging)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    api(libs.bundles.bouncycastle)
    testImplementation(testLibs.junit.jupiter.api)
    testImplementation(testLibs.junit.jupiter.engine)

    runtimeOnly("org.postgresql:postgresql:42.7.3")
}
