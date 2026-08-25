plugins {
    kotlin("jvm")
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":engine:core"))
    implementation("com.github.msarhan:ummalqura-calendar:2.0.2")
    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }
