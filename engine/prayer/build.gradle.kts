plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":engine:core"))
    implementation("com.batoulapps.adhan:adhan2-jvm:0.0.6")
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
}

tasks.test { useJUnitPlatform() }
