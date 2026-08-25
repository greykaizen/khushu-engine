// Khushu Engine — pure Kotlin/JVM. No Android, ever (see AGENTS.md).
plugins {
    kotlin("jvm") version "2.4.10" apply false
    kotlin("plugin.serialization") version "2.4.10" apply false
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.18.0"
}

apiValidation {
    // Tools are internal harnesses — never part of the published surface.
    ignoredProjects += listOf("cli")
}
