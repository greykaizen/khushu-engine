plugins {
    kotlin("jvm") version "2.4.10"
    application
}

// Standalone on purpose: replicates the DONOR's dependency set (cosinekitty via
// jitpack) and call path. Do not add engine modules here.

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation("io.github.cosinekitty:astronomy:2.1.19")
}

application { mainClass.set("MainKt") }
