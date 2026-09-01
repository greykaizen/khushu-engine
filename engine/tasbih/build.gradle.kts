plugins { kotlin("jvm") }

kotlin { jvmToolchain(21) }

dependencies {
    api(project(":engine:core")) // typed errors + shared day-streak math
    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }
