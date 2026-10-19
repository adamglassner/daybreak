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
        mainClass = "com.adamglassner.larksong.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "Larksong"
            packageVersion = appVersion
            description = "Larksong alarm clock"
            vendor = "Adam Glassner"
            copyright = "© 2026 Adam Glassner"
            includeAllModules = true

            windows {
                iconFile.set(project.file("icons/larksong.ico"))
                menu = true
                menuGroup = "Larksong"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                // Keep this constant so new versions upgrade the installed one.
                upgradeUuid = "73abb75d-d1fb-4bf2-a2f8-7d38df414202"
            }

            linux {
                packageName = "larksong"
                iconFile.set(project.file("icons/larksong.png"))
                shortcut = true
                menuGroup = "Utility"
                appCategory = "Utility"
                debMaintainer = "adamglassner@users.noreply.github.com"
                appRelease = "1"
                debPackageVersion = appVersion
                rpmPackageVersion = appVersion
                rpmLicenseType = "Proprietary"
            }
        }
    }
}

// The app reads its own version from this resource.
tasks.processResources {
    inputs.property("appVersion", appVersion)
    filesMatching("larksong-version.txt") { expand("version" to appVersion) }
}
