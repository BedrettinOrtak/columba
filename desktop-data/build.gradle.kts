plugins {
    kotlin("jvm")
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
    // Project modules
    implementation(project(":shared"))

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")

    // SQLite JDBC
    implementation("org.xerial:sqlite-jdbc:3.46.1.3")

    // Reticulum (pure-JVM Kotlin port). rns-android is intentionally excluded —
    // it depends on Android framework classes that aren't available on desktop.
    implementation(libs.rns.core)
    implementation(libs.rns.interfaces)
    implementation(libs.lxmf.kt)

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("ch.qos.logback:logback-classic:1.5.16")

    // Koin for dependency injection
    implementation("io.insert-koin:koin-core:3.5.6")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.14.9")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
