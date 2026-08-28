plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation("com.github.msarhan:ummalqura-calendar:2.0.2")
    api(project(":engine:astronomy")) // lunar month view composes astronomy facts
    api(project(":engine:core"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
}

tasks.test { useJUnitPlatform() }
