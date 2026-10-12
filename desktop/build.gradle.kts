import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.Properties

plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.3"
}

val appVersion: String = Properties().apply {
    file("../version.properties").inputStream().use { load(it) }
}.getProperty("version")

group = "com.adamglassner"
version = appVersion

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.json:json:20240303")
    implementation("javazoom:jlayer:1.0.1") // MP3 decoding for custom alarm sounds
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
}

kotlin {
    jvmToolchain(17)
}

compose.desktop {
    application {
        mainClass = "com.adamglassner.daybreak.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "Daybreak"
            packageVersion = appVersion
            description = "Daybreak alarm clock"
            vendor = "Adam Glassner"
            copyright = "© 2026 Adam Glassner"
            includeAllModules = true

            windows {
                iconFile.set(project.file("icons/daybreak.ico"))
                menu = true
                menuGroup = "Daybreak"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                // Keep this constant so new versions upgrade the installed one.
                upgradeUuid = "6f0b5a9e-2c1d-4e8a-9b7f-3d5c1e2a4b60"
            }
        }
    }
}

// The app reads its own version from this resource.
tasks.processResources {
    inputs.property("appVersion", appVersion)
    filesMatching("daybreak-version.txt") { expand("version" to appVersion) }
}
