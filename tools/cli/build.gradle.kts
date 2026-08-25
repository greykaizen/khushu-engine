plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":engine:facade"))
    implementation(project(":engine:core"))
    implementation(project(":engine:astronomy"))
    implementation(project(":engine:calendar"))
    implementation(project(":engine:prayer"))
    implementation(project(":engine:qibla"))
    implementation(project(":engine:zakat"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    testImplementation(kotlin("test"))
}

application { mainClass.set("com.khushu.cli.MainKt") }
