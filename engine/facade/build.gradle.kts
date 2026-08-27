plugins {
    kotlin("jvm")
}

kotlin { jvmToolchain(21) }

dependencies {
    api(project(":engine:core"))
    api(project(":engine:astronomy"))
    api(project(":engine:calendar"))
    api(project(":engine:prayer"))
    api(project(":engine:qibla"))
    api(project(":engine:zakat"))
    api(project(":engine:mushaf"))
    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }
