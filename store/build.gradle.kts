plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

kotlin { jvmToolchain(21) }

dependencies {
    api(project(":engine:core"))
    api(project(":engine:prayer"))
    api(project(":engine:calendar"))
    api(project(":engine:zakat"))
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("androidx.datastore:datastore-core-jvm:1.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }
