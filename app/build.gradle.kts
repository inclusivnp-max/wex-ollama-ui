import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    // Shared Kotlin/JVM convention (buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts)
    id("buildsrc.convention.kotlin-jvm")

    // Compose Multiplatform + its compiler plugin + kotlinx.serialization
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(project(":utils"))

    // Compose Desktop UI
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    // Coroutines (core from catalog, swing dispatcher for desktop UI thread)
    implementation(libs.kotlinxCoroutines)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")

    // JSON (Ollama API + project persistence)
    implementation(libs.kotlinxSerialization)
}

compose.desktop {
    application {
        mainClass = "org.wexcoder.app.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.AppImage)
            packageName = "WexCoder"
            packageVersion = "1.0.0"
        }
    }
}
