plugins {
    kotlin("jvm")
    application
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":engine:core"))
    implementation(project(":engine:astronomy"))
    implementation(project(":engine:calendar"))
    implementation(project(":engine:prayer"))
    implementation(project(":engine:qibla"))
    implementation(project(":engine:zakat"))
}

application { mainClass.set("com.khushu.cli.MainKt") }
