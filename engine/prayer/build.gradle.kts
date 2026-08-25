plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

kotlin { jvmToolchain(21) }

dependencies {
    api(project(":engine:core"))
    implementation("com.batoulapps.adhan:adhan2-jvm:0.0.7")
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
}

tasks.test { useJUnitPlatform() }
