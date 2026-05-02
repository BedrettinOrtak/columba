import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
    kotlin("plugin.serialization")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(compose.desktop.currentOs)

    // Compose UI dependencies (match Android app)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    // Coroutines
    implementation(libs.coroutines.core)

    // Project modules
    implementation(project(":shared"))
    implementation(project(":desktop-data"))

    // Dependencies from shared libs
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${libs.versions.serialization.get()}")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("ch.qos.logback:logback-classic:1.5.16")

    // Koin for dependency injection
    implementation("io.insert-koin:koin-core:3.5.6")

    // ViewModel support
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
}

compose.desktop {
    application {
        mainClass = "network.columba.app.desktop.DesktopApplicationKt"

        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Msi, TargetFormat.Exe)

            packageName = "Columba"
            packageVersion = "1.0.0"
            description = "Columba - Reticulum messaging for desktop"
            copyright = "© 2026 Columba Project"
            vendor = "Columba Project"
            licenseFile.set(project.file("../LICENSE.md"))

            modules("java.sql", "java.naming", "jdk.crypto.ec", "java.desktop")

            linux {
                iconFile.set(project.file("icons/icon.png"))
                shortcut = true
                menuGroup = "Network"
                packageName = "columba"
                debMaintainer = "columba@example.com"
                appCategory = "Network"
            }

            windows {
                iconFile.set(project.file("icons/icon.ico"))
                menuGroup = "Columba"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                // Stable upgrade UUID — keep constant across releases.
                upgradeUuid = "0a3b6c4f-58d5-4d2c-9f0a-3e1f7c2b8e6d"
            }

            macOS {
                iconFile.set(project.file("icons/icon.png"))
                bundleID = "network.columba.app.desktop"
            }
        }

        buildTypes.release.proguard {
            configurationFiles.from(project.file("proguard-rules.pro"))
            isEnabled.set(false)
        }
    }
}
