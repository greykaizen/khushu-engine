plugins {
    kotlin("jvm") version "2.4.10"
    application
}

// Standalone on purpose: replicates the DONOR's dependency set and call path.
// Do not add engine modules here — this must stay a faithful donor replica.

repositories {
    mavenCentral()
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation("com.batoulapps.adhan:adhan2-jvm:0.0.7")
}

application { mainClass.set("MainKt") }
